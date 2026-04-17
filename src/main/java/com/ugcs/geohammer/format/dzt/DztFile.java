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

import com.ugcs.geohammer.format.MultiChannelFile;
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

public class DztFile extends TraceFile implements MultiChannelFile {

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

	private List<DztHeader> channelHeaders = new ArrayList<>();

	private List<List<Trace>> channels = new ArrayList<>();

	private List<MinMaxAvg> channelSampleAverages = new ArrayList<>();

	private int activeChannelIndex = 0;

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

	@Override
	public int getChannelCount() {
		return channels.size();
	}

	@Override
	public int getActiveChannelIndex() {
		return activeChannelIndex;
	}

	@Override
	public void setActiveChannelIndex(int channelIndex) throws IOException {
		if (channelIndex < 0 || channelIndex >= channels.size()) {
			throw new IllegalArgumentException("Channel index out of range: " + channelIndex);
		}
		activeChannelIndex = channelIndex;
		header = channelHeaders.get(channelIndex);
		reloadTracesForChannel(channelIndex);
		rebuildDerivedState();
	}

	private void reloadTracesForChannel(int channelIndex) throws IOException {
		IndexRange sampleRange = metaFile != null ? metaFile.getSampleRange() : null;
		List<Trace> channelTraces = channels.get(channelIndex);
		setTraces(channelTraces);
		loadMeta(channelTraces);
		if (sampleRange != null && metaFile != null) {
			metaFile.setSampleRange(sampleRange);
		}
	}

	private void rebuildDerivedState() {
		updateTraces();
		copyMarkedTracesToAuxElements();
		updateTraceDistances();
	}

	@Override
	public String getChannelLabel(int channelIndex) {
		if (channelIndex < 0 || channelIndex >= channelHeaders.size()) {
			return "Channel " + (channelIndex + 1);
		}
		DztHeader header = channelHeaders.get(channelIndex);
		String antName = header.rh_antname != null && !header.rh_antname.isBlank() ? header.rh_antname.trim() : "Unknown antenna";
		return String.format("Channel %d: %s — %.0f ns / %.1f m", channelIndex + 1, antName, header.rhf_range, header.rhf_depth);
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

		try (SeekableByteChannel channel = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
			channelHeaders = readAllHeaders(channel);
			header = channelHeaders.getFirst();
			logHeader();

			int numChannels = channelHeaders.size();
			channel.position(getDataPosition());

			ChannelData data = readTracesMultiChannel(channel, numChannels);
			channels = data.channels();
			channelSampleAverages = data.averages();
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				Check.notEmpty(channels.get(channelIndex), "Corrupted file: channel " + (channelIndex + 1) + " has no traces");
				shiftSamples(channels.get(channelIndex), -(float) channelSampleAverages.get(channelIndex).getAverage());
			}
		}

		setActiveChannelIndex(0);
		setUnsaved(false);
	}

	private List<DztHeader> readAllHeaders(SeekableByteChannel channel) throws IOException {
		List<DztHeader> headers = new ArrayList<>();
		ObjectByteMapper byteMapper = new ObjectByteMapper();

		ByteBuffer firstBuffer = ByteBuffer.allocate(MINHEADSIZE).order(ByteOrder.LITTLE_ENDIAN);
		channel.position(0);
		channel.read(firstBuffer);
		DztHeader firstHeader = new DztHeader();
		byteMapper.readObject(firstHeader, firstBuffer);
		headers.add(firstHeader);

		int numChannels = Math.max(1, firstHeader.rh_nchan);
		for (int channelIndex = 1; channelIndex < numChannels; channelIndex++) {
			ByteBuffer buffer = ByteBuffer.allocate(MINHEADSIZE).order(ByteOrder.LITTLE_ENDIAN);
			channel.position((long) channelIndex * MINHEADSIZE);
			channel.read(buffer);
			DztHeader header = new DztHeader();
			byteMapper.readObject(header, buffer);
			headers.add(header);
		}
		return headers;
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
		DztHeader firstChannel = channelHeaders.getFirst();
		return firstChannel.rh_data < MINHEADSIZE
				? MINHEADSIZE * firstChannel.rh_data
				: firstChannel.rh_nchan * firstChannel.rh_data;
	}

	private SampleCodec getSampleCodec() {
		return SAMPLE_CODECS.get((int)header.rh_bits);
	}

	private int getTraceBufferSize(DztHeader channelHeader, int numSamples) {
		int bytesPerSample = channelHeader.rh_bits / 8;
		return bytesPerSample * (numSamples + 1);
	}

	private int numSamplesPerTrace() {
		// first value is a trace index
		return Math.max(0, header.rh_nsamp - 1);
	}

	private record ChannelData(List<List<Trace>> channels, List<MinMaxAvg> averages) {}

	private ChannelData readTracesMultiChannel(SeekableByteChannel channel,
			int numChannels) throws IOException {

		List<List<Trace>> result = new ArrayList<>();
		List<MinMaxAvg> averages = new ArrayList<>();
		for (int i = 0; i < numChannels; i++) {
			result.add(new ArrayList<>());
			averages.add(new MinMaxAvg());
		}

		int globalIndex = 0;
		while (channel.position() < channel.size()) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			int channelIndex = globalIndex % numChannels;
			int scanIndex = globalIndex / numChannels;
			Trace trace = readTrace(channel, channelHeaders.get(channelIndex), scanIndex, averages.get(channelIndex));
			result.get(channelIndex).add(trace);
			globalIndex++;
		}
		return new ChannelData(result, averages);
	}

	private Trace readTrace(SeekableByteChannel channel, DztHeader channelHeader,
			int traceIndex, MinMaxAvg average) throws IOException {

		int numSamples = Math.max(0, channelHeader.rh_nsamp - 1);
		SampleCodec sampleCodec = SAMPLE_CODECS.get((int) channelHeader.rh_bits);
		if (sampleCodec == null) {
			throw new IOException("Unsupported sample bit depth: " + channelHeader.rh_bits);
		}
		int bufferSize = getTraceBufferSize(channelHeader, numSamples);

		ByteBuffer buffer = ByteBuffer
				.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);
		channel.read(buffer);
		buffer.position(0);

		if (buffer.position() < buffer.capacity()) {
			// read trace number
			sampleCodec.read(buffer);
		}

		float[] samples = new float[numSamples];
		int sampleIndex = 0;

		while (buffer.position() < buffer.capacity() && sampleIndex < samples.length) {
			int sample = sampleCodec.read(buffer);
			samples[sampleIndex++] = sample;
			average.put(sample);
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
						.allocate(getTraceBufferSize(header, numSamples))
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
        // ground profile is not copied
        DztFile copy = new DztFile();
		copy.header = this.header;
		copy.sourceFile = this.sourceFile;
		copy.dzg = this.dzg;
		copy.channelHeaders = new ArrayList<>(this.channelHeaders);
		copy.channelSampleAverages = new ArrayList<>(this.channelSampleAverages);
		copy.channels = new ArrayList<>(this.channels.size());
		for (List<Trace> ch : this.channels) {
			copy.channels.add(new ArrayList<>(ch));
		}
		copy.activeChannelIndex = this.activeChannelIndex;

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
		copy.channels.set(copy.activeChannelIndex, tracesCopy);
		copy.setAuxElements(elementsCopy);

		return copy;
	}

	private void shiftSamples(List<Trace> traces, float delta) {
		for (Trace trace : traces) {
			for (int i = 0; i < trace.numSamples(); i++) {
				trace.setSample(i, trace.getSample(i) + delta);
			}
		}
	}

	public void subtractAverage(List<Trace> traces) {
		shiftSamples(traces, -(float) channelSampleAverages.get(activeChannelIndex).getAverage());
	}

	public void addAverage(List<Trace> traces) {
		shiftSamples(traces, (float) channelSampleAverages.get(activeChannelIndex).getAverage());
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
