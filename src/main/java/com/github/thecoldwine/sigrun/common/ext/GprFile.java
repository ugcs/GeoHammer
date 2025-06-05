package com.github.thecoldwine.sigrun.common.ext;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import com.ugcs.gprvisualizer.gpr.SgyLoader;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Traces;

public class GprFile extends TraceFile {
	
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

	public byte[] getTxtHdr() {
		return txtHdr;
	}

	public void setTxtHdr(byte[] txtHdr) {
		this.txtHdr = txtHdr;
	}

	public byte[] getBinHdr() {
		return binHdr;
	}

	public void setBinHdr(byte[] binHdr) {
		this.binHdr = binHdr;
	}

	public BinaryHeader getBinaryHeader() {
		return binaryHeader;
	}

	public void setBinaryHeader(BinaryHeader binaryHeader) {
		this.binaryHeader = binaryHeader;
	}

	public SampleNormalizer getSampleNormalizer() {
		return sampleNormalizer;
	}

	@Override
	public int getSampleInterval() {
		return getBinaryHeader().getSampleInterval();
	}

	@Override
	public double getSamplesToCmGrn() {
		// dist between 2 samples
		double sampleIntervalNS = getBinaryHeader().getSampleInterval() / 1000.0;
		return SPEED_SM_NS_SOIL * sampleIntervalNS / 2;
	}

	@Override
	public double getSamplesToCmAir() {
		// dist between 2 samples
		double sampleIntervalNS = getBinaryHeader().getSampleInterval() / 1000.0;
		return SPEED_SM_NS_VACUUM * sampleIntervalNS / 2;
	}

	@Override
	public void open(File file) throws Exception {
		setFile(file);
		
		BinFile binFile = BinFile.load(file); 
		
		txtHdr = binFile.getTxtHdr();
		binHdr = binFile.getBinHdr();
		
		binaryHeader = binaryHeaderReader.read(binFile.getBinHdr());
		
		System.out.println("binaryHeader.getSampleInterval() " 
				+ binaryHeader.getSampleInterval());
		System.out.println("SamplesPerDataTrace " 
				+ binaryHeader.getSamplesPerDataTrace());

		List<Trace> traces = loadTraces(binFile);

		// fill latlon where null
		Traces.fillMissingLatLon(traces);
		sampleNormalizer.normalize(traces);
		setTraces(traces);

		updateTraces();
		copyMarkedTracesToAuxElements();
		updateTraceDistances();
		
		setUnsaved(false);
		
		System.out.println("opened  '" + file.getName() 
			+ "'   load size: " + getTraces().size() 
			+ "  actual size: " + binFile.getTraces().size());

		loadMeta();
	}

	private List<Trace> loadTraces(BinFile binFile) {
		SeismicValuesConverter converter = ConverterFactory
				.getConverter(binaryHeader.getDataSampleCode());

		List<BinTrace> binTraces = binFile.getTraces();
		List<Trace> traces = new ArrayList<>(binTraces.size());
		for (BinTrace binTrace : binTraces) {
			Trace trace = loadTrace(binTrace, converter);
			if (trace == null) {
				continue;
			}
			traces.add(trace);
		}
		return traces;
	}
    
	public Trace loadTrace(BinTrace binTrace, SeismicValuesConverter converter) {
		byte[] binHeader = binTrace.header;
        TraceHeader header = traceHeaderReader.read(binHeader);

        float[] values = converter.convert(binTrace.data);
        LatLon latLon = getLatLon(header);

        Trace trace = new Trace(this, binHeader, header, values, latLon);
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
	public void save(File file) throws Exception {
		SeismicValuesConverter converter = ConverterFactory
				.getConverter(binaryHeader.getDataSampleCode());

		BinFile binFile = new BinFile();

		binFile.setTxtHdr(txtHdr);
		binFile.setBinHdr(binHdr);

		Set<Integer> marks = AuxElements.getMarkIndices(getAuxElements());

		sampleNormalizer.back(getTraces());

		for (Trace trace : getTraces()) {
			BinTrace binTrace = new BinTrace();
			
			binTrace.header = trace.getBinHeader();

			// upd coordinates
			ByteBuffer bb = ByteBuffer.wrap(binTrace.header);
			bb.order(ByteOrder.LITTLE_ENDIAN);

			bb.putDouble(190, convertBackDegreeFraction(trace.getLatLon().getLatDgr()));
			bb.putDouble(182, convertBackDegreeFraction(trace.getLatLon().getLonDgr()));
			
			// set or clear mark
			binTrace.header[MARK_BYTE_POS] =
					(byte) (marks.contains(trace.getIndexInFile()) ? -1 : 0);
			
			binTrace.data = converter.valuesToByteBuffer(trace.getNormValues()).array();
			binFile.getTraces().add(binTrace);
		}		

		binFile.save(file);

		saveMeta();
	}

	@Override
	public void saveAux(File file) {
	}

	@Override
	public GprFile copyHeader() {
		GprFile copy = new GprFile();
		copy.setBinHdr(this.getBinHdr());
		copy.setTxtHdr(this.getTxtHdr());
		copy.setBinaryHeader(this.getBinaryHeader());
		copy.sampleNormalizer.copyFrom(this.sampleNormalizer);

		return copy;
	}

	@Override
	public GprFile copy() {
		//TODO: not a full copy, the result can't be saved
		GprFile copy = new GprFile();
		copy.setFile(getFile());
		copy.sampleNormalizer.copyFrom(this.sampleNormalizer);

		List<Trace> traces = Traces.copy(getTraces(), copy);
		copy.setTraces(traces);

		return copy;
	}
}
