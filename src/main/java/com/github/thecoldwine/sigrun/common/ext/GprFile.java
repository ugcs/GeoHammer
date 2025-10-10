package com.github.thecoldwine.sigrun.common.ext;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import com.github.thecoldwine.sigrun.common.BinaryHeader;
import com.github.thecoldwine.sigrun.common.ConverterFactory;
import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.github.thecoldwine.sigrun.common.ext.BinFile.BinTrace;
import com.github.thecoldwine.sigrun.converters.SeismicValuesConverter;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormat;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderReader;
import com.github.thecoldwine.sigrun.serialization.TextHeaderReader;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderFormat;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderReader;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.gpr.SgyLoader;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Traces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GprFile extends TraceFile {

	private static final Logger log = LoggerFactory.getLogger(GprFile.class);
	
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

        Trace trace = new Trace(binHeader, header, values, latLon);
        if (binHeader[MARK_BYTE_POS] != 0) {
        	trace.setMarked(true);
        }
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
		save(file, new Range(0, numTraces() - 1));
	}

	@Override
	public void save(File file, Range range) throws IOException {
		Check.notNull(file);
		Check.notNull(range);

		SeismicValuesConverter converter = ConverterFactory
				.getConverter(binaryHeader.getDataSampleCode());

		BinFile binFile = new BinFile();

		binFile.setTxtHdr(txtHdr);
		binFile.setBinHdr(binHdr);

		Set<Integer> marks = AuxElements.getMarkIndices(getAuxElements(), range);

		List<Trace> fileTraces = getTraces();

		int from = range.getMin().intValue();
		int to = range.getMax().intValue() + 1; // exclusive

		for (int i = from; i < to; i++) {
			Trace trace = fileTraces.get(i);

			BinTrace binTrace = new BinTrace();
			binTrace.header = trace.getBinHeader();

			// upd coordinates
			ByteBuffer buffer = ByteBuffer.wrap(binTrace.header);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			buffer.putShort(114, (short)trace.numSamples());
			buffer.putDouble(190, convertBackDegreeFraction(trace.getLatLon().getLatDgr()));
			buffer.putDouble(182, convertBackDegreeFraction(trace.getLatLon().getLonDgr()));

			// set or clear mark
			binTrace.header[MARK_BYTE_POS] =
					(byte) (marks.contains(trace.getIndex()) ? -1 : 0);
			
			binTrace.data = converter.valuesToByteBuffer(trace).array();
			binFile.getTraces().add(binTrace);
		}

		binFile.save(file);
	}

	@Override
	public GprFile copy() {
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
			copy.metaFile.initTraces(tracesCopy);
		}

		if (groundProfile != null) {
			copy.groundProfile = new HorizontalProfile(groundProfile, copy.metaFile);
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
