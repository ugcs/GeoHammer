package com.ugcs.geohammer.format.gpr;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import com.github.thecoldwine.sigrun.common.BinaryHeader;
import com.github.thecoldwine.sigrun.common.ConverterFactory;
import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.github.thecoldwine.sigrun.converters.SeismicValuesConverter;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormat;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderReader;
import com.github.thecoldwine.sigrun.serialization.TextHeaderReader;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderFormat;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderReader;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.BinFile.BinTrace;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.SgyLoader;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.LengthUnit;
import com.ugcs.geohammer.util.Traces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GprFile extends TraceFile {

	private static final Logger log = LoggerFactory.getLogger(GprFile.class);

    private static final int FILE_NUM_SAMPLES_POS = 20;

    private static final int RECEIVER_ELEVATION_POS = 40;

    private static final int NUM_SAMPLES_POS = 114;

    private static final int LONGITUDE_POS = 182;

    private static final int LATITUDE_POS = 190;

	private static final int MARK_BYTE_POS = 238;

	private static final Charset charset = StandardCharsets.UTF_8;

	private static final BinaryHeaderFormat binaryHeaderFormat
    	= SgyLoader.makeBinHeaderFormat();

	private static final TraceHeaderFormat traceHeaderFormat
    	= SgyLoader.makeTraceHeaderFormat();

    public static final TextHeaderReader textHeaderReader
    	= new TextHeaderReader(charset);

	public static final BinaryHeaderReader binaryHeaderReader
    	= new BinaryHeaderReader(binaryHeaderFormat);

	public static final TraceHeaderReader traceHeaderReader
    	= new TraceHeaderReader(traceHeaderFormat);

    // unchanged blocks from original file
    private byte[] txtHdr;

	private byte[] binHdr;

    private BinaryHeader binaryHeader;

    private SampleNormalizer sampleNormalizer = new SampleNormalizer();

	@Override
	public int getSampleInterval() {
		return binaryHeader.getSampleInterval();
	}

	@Override
	public double getSamplesToCmGrn() {
		// dist between 2 samples
		double sampleIntervalNS = binaryHeader.getSampleInterval() / 1000.0;
		return SPEED_SM_NS_SOIL * sampleIntervalNS / 2;
	}

	@Override
	public double getSamplesToCmAir() {
		// dist between 2 samples
		double sampleIntervalNS = binaryHeader.getSampleInterval() / 1000.0;
		return SPEED_SM_NS_VACUUM * sampleIntervalNS / 2;
	}

	@Override
	public void open(File file) throws IOException {
		Check.notNull(file);

		setFile(file);

		BinFile binFile = BinFile.load(file);

		txtHdr = binFile.getTxtHdr();
		binHdr = binFile.getBinHdr();

		binaryHeader = binaryHeaderReader.read(binFile.getBinHdr());

		log.debug("Sample interval: {}", binaryHeader.getSampleInterval());
		log.debug("Samples per data trace {}", binaryHeader.getSamplesPerDataTrace());

		List<Trace> traces = readTraces(binFile);
		// fill latlon where null
		Traces.fillMissingLatLon(traces);

		loadMeta(traces);

		sampleNormalizer.normalize(traces);
		setTraces(traces);

		updateTraces();
		copyMarkedTracesToAuxElements();
		updateTraceDistances();

		setUnsaved(false);

		log.debug("opened '{}', load size: {}, actual size: {}", file.getName(), getTraces().size(), binFile.getTraces().size());
	}

	private List<Trace> readTraces(BinFile binFile) {
		SeismicValuesConverter converter = ConverterFactory
				.getConverter(binaryHeader.getDataSampleCode());

		List<BinTrace> binTraces = binFile.getTraces();
		List<Trace> traces = new ArrayList<>(binTraces.size());
		for (BinTrace binTrace : binTraces) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}

			Trace trace = readTrace(binTrace, converter);
			if (trace == null) {
				continue;
			}
			traces.add(trace);
		}
		return traces;
	}

	private Trace readTrace(BinTrace binTrace, SeismicValuesConverter converter) {
		byte[] binHeader = binTrace.header;
        TraceHeader header = traceHeaderReader.read(binHeader);

        float[] values = converter.convert(binTrace.data);
        LatLon latLon = getLatLon(header);
		Instant time = getTimestamp(header);

        Trace trace = new Trace(binHeader, header, values, latLon, time);
        if (binHeader[MARK_BYTE_POS] != 0) {
        	trace.setMarked(true);
        }
        trace.setReceiverAltitude(getReceiverElevation(binHeader));
        return trace;
	}

	private LatLon getLatLon(TraceHeader header) {
		double lon = retrieveVal(header.getLongitude(), header.getSourceX());
		double lat = retrieveVal(header.getLatitude(), header.getSourceY());

		if (Double.isNaN(lon) || Double.isNaN(lat)
				|| Math.abs(lon) < 0.0001
				|| Math.abs(lat) < 0.0001
				|| Math.abs(lon) > 18000
				|| Math.abs(lat) > 18000) {
			// try handle source coordinates as scaled integers
			return getScaledSourceLatLon(header);
		}

		double rlon = convertDegreeFraction(lon);
		double rlat = convertDegreeFraction(lat);

		return new LatLon(rlat, rlon);
	}

    private Float getReceiverElevation(byte[] binHeader) {
        ByteBuffer buffer = ByteBuffer.wrap(binHeader).order(ByteOrder.LITTLE_ENDIAN);
        float elevation = Float.intBitsToFloat(buffer.getInt(RECEIVER_ELEVATION_POS));
        // convert to meters if file uses feet
        if (binaryHeader.getMeasurementSystem() == 2) {
            elevation = (float)LengthUnit.FOOT.toMeters(elevation);
        }
        return elevation;
    }

	private Instant getTimestamp(TraceHeader header) {
		Short year = header.getYearDataRecorded();
		Short day = header.getDayOfYear();
		Short hours = header.getHourOfDay();
		Short minutes = header.getMinuteOfHour();
		Short seconds = header.getSecondOfMinute();
		Short millis = header.getTraceWeightingFactor();

		if (!isValidTimestamp(year, day, hours, minutes, seconds, millis)) {
			return null;
		}

		return LocalDateTime.of(year, 1, 1, 0, 0, 0, 0)
				.withDayOfYear(day)
				.withHour(hours)
				.withMinute(minutes)
				.withSecond(seconds)
				.withNano(millis * 1_000_000)
				.toInstant(ZoneOffset.UTC);
	}

	private boolean isValidTimestamp(Short year, Short day, Short hours,
									 Short minutes, Short seconds, Short millis) {
		int currentYear = LocalDateTime.now().getYear();

		return isInRange(year, 1, currentYear)
				&& isInRange(day, 1, 366)
				&& isInRange(hours, 0, 23)
				&& isInRange(minutes, 0, 59)
				&& isInRange(seconds, 0, 59)
				&& isInRange(millis, 0, 999);
	}

	private boolean isInRange(Short value, int min, int max) {
		return value != null && value >= min && value <= max;
	}

	private double retrieveVal(Double v1, Float v2) {
		if (v1 != null && Math.abs(v1) > 0.01) {
			return v1;
		}
		return v2 != null ? v2.doubleValue() : 0;
	}

	private LatLon getScaledSourceLatLon(TraceHeader header) {
		Float sourceX = header.getSourceX();
		Float sourceY = header.getSourceY();
		if (sourceX == null || sourceY == null) {
			return null;
		}

		// scaled arc seconds as integers
		int x = Float.floatToIntBits(sourceX);
		int y = Float.floatToIntBits(sourceY);

		double k = 1.0;
		Short scalar = header.getScalarForCoordinates();
		if (scalar != null) {
			k = scalar >= 0 ? scalar : 1.0 / -scalar;
		}

		// apply scale factor and convert to degrees
		double lon = k * x / 3600.0;
		double lat = k * y / 3600.0;

		return LatLon.isValidLatitude(lat) && LatLon.isValidLongitude(lon)
				? new LatLon(lat, lon)
				: null;
	}

	@Override
	public void save(File file) throws IOException {
		save(file, new IndexRange(0, numTraces()));
	}

	@Override
	public void save(File file, IndexRange range) throws IOException {
		Check.notNull(file);
		Check.notNull(range);

        short numSamples = (short)getMaxSamples();
        // update number of samples in the header
        if (numSamples != binaryHeader.getSamplesPerDataTrace()) {
            binaryHeader.setSamplesPerDataTrace(numSamples);
            ByteBuffer headerBuffer = ByteBuffer.wrap(binHdr).order(ByteOrder.LITTLE_ENDIAN);
            headerBuffer.putShort(FILE_NUM_SAMPLES_POS, numSamples);
        }

        SeismicValuesConverter converter = ConverterFactory
				.getConverter(binaryHeader.getDataSampleCode());

		BinFile binFile = new BinFile();

		binFile.setTxtHdr(txtHdr);
		binFile.setBinHdr(binHdr);

        Set<Integer> marks = AuxElements.getMarkIndices(getAuxElements(), range);

		List<Trace> fileTraces = getTraces();

        for (int i = range.from(); i < range.to(); i++) {
			Trace trace = fileTraces.get(i);

			BinTrace binTrace = new BinTrace();
			binTrace.header = trace.getBinHeader();

			// upd coordinates
			ByteBuffer buffer = ByteBuffer.wrap(binTrace.header);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
            updateTraceBuffer(trace, buffer);

			// set or clear mark
			binTrace.header[MARK_BYTE_POS] =
					(byte) (marks.contains(i) ? -1 : 0);

			binTrace.data = converter.valuesToByteBuffer(trace).array();
			binFile.getTraces().add(binTrace);
		}

		binFile.save(file);
	}

    private void updateTraceBuffer(Trace trace, ByteBuffer buffer) {
        buffer.putShort(NUM_SAMPLES_POS, (short)trace.numSamples());
        buffer.putDouble(LONGITUDE_POS, convertBackDegreeFraction(trace.getLatLon().getLonDgr()));
        buffer.putDouble(LATITUDE_POS, convertBackDegreeFraction(trace.getLatLon().getLatDgr()));

        float elevation = trace.getReceiverAltitude();
        // convert if file uses feet
        if (binaryHeader.getMeasurementSystem() == 2) {
            elevation = (float)LengthUnit.FOOT.fromMeters(elevation);
        }
        // elevation is stored as float bytes
        // with no scalar applied
        buffer.putInt(RECEIVER_ELEVATION_POS, Float.floatToIntBits(elevation));
    }

	@Override
	public GprFile copy() {
        // ground profile is not copied
		GprFile copy = new GprFile();
		copy.binHdr = this.binHdr;
		copy.txtHdr = this.txtHdr;
		copy.binaryHeader = this.binaryHeader;
		copy.sampleNormalizer.copyFrom(this.sampleNormalizer);

		copy.setFile(getFile());
		copy.setUnsaved(isUnsaved());

		List<Trace> tracesCopy = Traces.copy(traces);
		List<BaseObject> elementsCopy = AuxElements.copy(getAuxElements());

		if (metaFile != null) {
			copy.metaFile = new MetaFile();
			copy.metaFile.setMetaToState(metaFile.getMetaFromState());
			copy.syncMeta(tracesCopy);
		}

		copy.setTraces(tracesCopy);
		copy.setAuxElements(elementsCopy);

		return copy;
	}

	@Override
	public void normalize() {
		sampleNormalizer.normalize(traces);
	}

	@Override
	public void denormalize() {
		sampleNormalizer.back(traces);
	}
}
