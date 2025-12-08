package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import com.ugcs.geohammer.format.HorizontalProfile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.math.MinMaxAvg;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Traces;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DztFile extends TraceFile {

	private static final Logger log = LoggerFactory.getLogger(DztFile.class);

	private static final int MINHEADSIZE = 1024;
	private static final int PARAREASIZE = 128;
	private static final int GPSAREASIZE = 2 * 12;
	private static final int INFOAREASIZE = (MINHEADSIZE - PARAREASIZE- GPSAREASIZE) ;

	private static final Map<Integer, SampleCodec> SAMPLE_CODECS = Map.of(
			16, new Sample16Bit(),
			32, new Sample32Bit());

	@Nullable
	private File sourceFile;

	private DzgFile dzg = new DzgFile();

	private DztHeader header = new DztHeader();

	private MinMaxAvg sampleAvg = new MinMaxAvg();

	@Override
	public int getSampleInterval() {
		return (int)header.rhf_range;
	}

	@Override
	public double getSamplesToCmGrn() {
		return header.rhf_depth * 100.0 / header.rh_nsamp;
	}

	@Override
	public double getSamplesToCmAir() {
		return header.rhf_depth * 100.0 / header.rh_nsamp;
	}

	private File getDzgFile(File file) {
		String path = FileNames.removeExtension(file.getAbsolutePath())
				+ ".dzg";
		return new File(path);
	}

	@Override
	public void open(File file) throws IOException {
		Check.notNull(file);

		setFile(file);
		this.sourceFile = file;

		dzg.load(getDzgFile(file));

		List<Trace> traces;
		try (SeekableByteChannel channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
			ByteBuffer buffer = readHeader(channel);

			ObjectByteMapper obm = new ObjectByteMapper();
			obm.readObject(header, buffer);
			logHeader();

			channel.position(getDataPosition());
			traces = readTraces(channel, getSampleCodec());
		}
		Check.notEmpty(traces, "Corrupted file");

		loadMeta(traces);

		subtractAverage(traces);
		setTraces(traces);

		updateTraces();
		copyMarkedTracesToAuxElements();
		updateTraceDistances();

		setUnsaved(false);
	}

	private ByteBuffer readHeader(SeekableByteChannel channel) throws IOException {
		ByteBuffer buffer = ByteBuffer
				.allocate(1024)
				.order(ByteOrder.LITTLE_ENDIAN);
		channel.position(0);
		channel.read(buffer);
		return buffer;
	}

	private void logHeader() {
		log.debug("| rh_data        {}", header.rh_data);
		log.debug("| rh_bits        {}", header.rh_bits);
		log.debug("| rh_nsamp       {}", header.rh_nsamp);
		log.debug("| rh_zero        {}", header.rh_zero);
		log.debug("| rhf_sps        {}", header.rhf_sps);
		log.debug("| rhf_epsr       {}", header.rhf_epsr);
		log.debug("| rh_spp         {}", header.rh_spp);
		log.debug("| rhf_range (ns) {}", header.rhf_range);
		log.debug("| rhf_depth (m)  {}", header.rhf_depth);
	}

	private int getDataPosition() {
		return header.rh_data < MINHEADSIZE
				? MINHEADSIZE * header.rh_data
				: header.rh_nchan * header.rh_data;
	}

	private SampleCodec getSampleCodec() {
		return SAMPLE_CODECS.get((int)header.rh_bits);
	}

	private int getTraceBufferSize(int numSamples) {
		int bytesPerSample = header.rh_bits / 8;
		return bytesPerSample * (numSamples + 1);
	}

	private int numSamplesPerTrace() {
		// first value is a trace index
		return Math.max(0, header.rh_nsamp - 1);
	}

	private List<Trace> readTraces(SeekableByteChannel channel, SampleCodec sampleCodec)
			throws IOException {

		List<Trace> traces = new ArrayList<>();
		int traceIndex = 0;

		while (channel.position() < channel.size()) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}

			Trace trace = readTrace(channel, sampleCodec, traceIndex++);
			traces.add(trace);
		}
		return traces;
	}

	private Trace readTrace(SeekableByteChannel channel, SampleCodec sampleCodec, int traceIndex)
			throws IOException {

		int numSamples = numSamplesPerTrace();

		ByteBuffer buffer = ByteBuffer
				.allocate(getTraceBufferSize(numSamples))
				.order(ByteOrder.LITTLE_ENDIAN);
		channel.read(buffer);
		buffer.position(0);

		if (buffer.position() < buffer.capacity()) {
			// read trace number
			int traceNumber = sampleCodec.read(buffer);
		}

		float[] samples = new float[numSamples];
		int sampleIndex = 0;
		sampleAvg = new MinMaxAvg();

		while (buffer.position() < buffer.capacity() && sampleIndex < samples.length) {
			int sample = sampleCodec.read(buffer);
			samples[sampleIndex++] = sample;
			sampleAvg.put(sample);
		}

		LatLon latLon = dzg.getLatLon(traceIndex);
		return new Trace(null, null, samples, latLon, null);
	}

	@Override
	public void save(File file) throws IOException {
		save(file, new IndexRange(0, numTraces()));
	}

	@Override
	public void save(File file, IndexRange range) throws IOException {
		Check.notNull(file);

		// TODO instead of reloading DZT header from source
		//  implement serialization of the header structure

		// read header from source file
		ByteBuffer headerBuffer = readSourceHeader();

		// assumes that all traces have same sample size
		int numSamples = 0;
		if (numTraces() > 0) {
			numSamples = getTraces().getFirst().numSamples();
		}

		// update num samples;
		// add extra sample for index
		headerBuffer.position(4); // rh_nsamp
		headerBuffer.putShort((short)(numSamples + 1));
		headerBuffer.position(0);

		try (FileOutputStream out = new FileOutputStream(file);
			 FileChannel channel = out.getChannel()) {

			// write header
			channel.write(headerBuffer);

			// write traces
			SampleCodec sampleCodec = getSampleCodec();

			List<Trace> fileTraces = getTraces();

			for (int i = range.from(); i < range.to(); i++) {
				Trace trace = fileTraces.get(i);
				Check.condition(numSamples == trace.numSamples());

				ByteBuffer buffer = ByteBuffer
						.allocate(getTraceBufferSize(numSamples))
						.order(ByteOrder.LITTLE_ENDIAN);
				buffer.position(0);

				sampleCodec.write(buffer, trace.getIndex());
				for (int j = 0; j < numSamples; j++) {
					sampleCodec.write(buffer, (int)trace.getSample(j));
				}

				buffer.position(0);
				channel.write(buffer);
			}
		}

		// save dzg
		List<DzgFile.IndexMapping> indexMappings = buildDzgMappings(range);
		dzg.save(getDzgFile(file), indexMappings);
	}

	private ByteBuffer readSourceHeader() throws IOException {
		Check.notNull(sourceFile);

		ByteBuffer buffer = ByteBuffer
				.allocate(getDataPosition())
				.order(ByteOrder.LITTLE_ENDIAN);

		try (FileInputStream in = new FileInputStream(sourceFile);
			 FileChannel channel = in.getChannel()) {
			channel.position(0);
			channel.read(buffer);
		}
		return buffer;
	}

	private List<DzgFile.IndexMapping> buildDzgMappings(IndexRange range) {
		Check.notNull(range);

		int from = range.from();
		int to = range.to();

		List<DzgFile.IndexMapping> mappings = new ArrayList<>();
		if (metaFile != null) {
			List<TraceGeoData> values = metaFile.getValues();
			for (int i = from; i < to; i++) {
                TraceGeoData value = values.get(i);
                int traceIndex = value.getTraceIndex();
                if (dzg.hasIndex(traceIndex)) {
                    mappings.add(new DzgFile.IndexMapping(i - from, traceIndex));
                }
			}
		} else {
			for (int i = from; i < to; i++) {
				if (dzg.hasIndex(i)) {
					mappings.add(new DzgFile.IndexMapping(i - from, i));
				}
			}
		}
		return mappings;
	}

	@Override
	public DztFile copy() {
		DztFile copy = new DztFile();
		copy.header = this.header;
		copy.sampleAvg = this.sampleAvg;
		copy.sourceFile = this.sourceFile;
		copy.dzg = this.dzg;

		copy.setFile(getFile());
		copy.setUnsaved(isUnsaved());

		List<Trace> tracesCopy = Traces.copy(traces);
		List<BaseObject> elementsCopy = AuxElements.copy(getAuxElements());

		if (metaFile != null) {
			copy.metaFile = new MetaFile();
			copy.metaFile.setMetaToState(metaFile.getMetaFromState());
			copy.syncMeta(tracesCopy);
		}

		if (groundProfile != null) {
			copy.groundProfile = new HorizontalProfile(groundProfile, copy.metaFile);
		}

		copy.setTraces(tracesCopy);
		copy.setAuxElements(elementsCopy);

		return copy;
	}

	public void subtractAverage(List<Trace> traces) {
		float avg = (float) sampleAvg.getAvg();

		for (Trace trace : traces) {
			for (int i = 0; i < trace.numSamples(); i++) {
				float value = trace.getSample(i) - avg;
				trace.setSample(i, value);
			}
		}
	}

	public void addAverage(List<Trace> traces) {
		float avg = (float) sampleAvg.getAvg();

		for (Trace trace : traces) {
			for (int i = 0; i < trace.numSamples(); i++) {
				float value = trace.getSample(i) + avg;
				trace.setSample(i, value);
			}
		}
	}

	@Override
	public void normalize() {
		subtractAverage(traces);
	}

	@Override
	public void denormalize() {
		addAverage(traces);
	}

	interface SampleCodec {

		int read(ByteBuffer buffer);

		void write(ByteBuffer buffer, int value);
	}

	static class Sample16Bit implements SampleCodec {

		@Override
		public int read(ByteBuffer buffer) {
			return asUnsignedShort(buffer.getShort()) - 32767;
		}

		@Override
		public void write(ByteBuffer buffer, int value) {
			int v = value +  32767;
			buffer.putShort((short) v);
		}

		private static int asUnsignedShort(short s) {
			return s & 0xFFFF;
		}
	}

	static class Sample32Bit implements SampleCodec {

		@Override
		public int read(ByteBuffer buffer) {
			return buffer.getInt();
		}

		@Override
		public void write(ByteBuffer buffer, int value) {
			buffer.putInt(value);
		}
	}
}
