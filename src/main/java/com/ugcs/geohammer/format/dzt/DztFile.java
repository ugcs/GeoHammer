package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ugcs.geohammer.format.Channel;
import com.ugcs.geohammer.format.MultiChannelFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Traces;
import org.jspecify.annotations.Nullable;

public class DztFile extends TraceFile implements MultiChannelFile {

	@Nullable
	private File sourceFile;

	private DzgFile dzg = new DzgFile();

	private List<DztChannel> channels = new ArrayList<>();

	private int selectedChannelIndex = 0;

	private DztChannel selectedChannel() {
		return channels.get(selectedChannelIndex);
	}

	@Override
	public int getSampleInterval() {
		return (int) selectedChannel().getHeader().rhf_range;
	}

	@Override
	public double getSamplesToCmGrn() {
		DztHeader header = selectedChannel().getHeader();
		return header.rhf_depth * 100.0 / header.rh_nsamp;
	}

	@Override
	public double getSamplesToCmAir() {
		DztHeader header = selectedChannel().getHeader();
		return header.rhf_depth * 100.0 / header.rh_nsamp;
	}

	@Override
	public List<Channel> getChannels() {
		return Collections.unmodifiableList(channels);
	}

	@Override
	public int getSelectedChannelIndex() {
		return selectedChannelIndex;
	}

	@Override
	public void selectChannel(int channelIndex) throws IOException {
		if (channelIndex < 0 || channelIndex >= channels.size()) {
			throw new IllegalArgumentException("Channel index out of range: " + channelIndex);
		}
		selectedChannelIndex = channelIndex;

		DztChannel channel = selectedChannel();
		IndexRange sampleRange = metaFile != null ? metaFile.getSampleRange() : null;
		setTraces(channel.getTraces());
		loadMeta(channel.getTraces());
		if (sampleRange != null && metaFile != null) {
			metaFile.setSampleRange(sampleRange);
			syncMeta(channel.getTraces());
		}

		updateTraces();
		copyMarkedTracesToAuxElements();
		updateTraceDistances();
	}

	@Override
	public int numChannel() {
		return channels.size();
	}

	@Override
	public void open(File file) throws IOException {
		Check.notNull(file);

		setFile(file);
		this.sourceFile = file;

		dzg.load(getDzgFile(file));

		channels = new DztReader().read(file, dzg);

		selectChannel(0);
		setUnsaved(false);
	}

	@Override
	public void save(File file) throws IOException {
		save(file, new IndexRange(0, numTraces()));
	}

	@Override
	public void save(File file, IndexRange range) throws IOException {
		Check.notNull(file);
		Check.notNull(sourceFile);

		new DztWriter().write(file, sourceFile, channels, range);
		dzg.save(getDzgFile(file), buildDzgMappings(range));
	}

	private File getDzgFile(File file) {
		String path = FileNames.removeExtension(file.getAbsolutePath())
				+ ".dzg";
		return new File(path);
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
	public void normalize() {
		Traces.shiftSamples(traces,
				-(float) selectedChannel().getSampleAverage().getAverage());
	}

	@Override
	public void denormalize() {
		Traces.shiftSamples(traces,
				(float) selectedChannel().getSampleAverage().getAverage());
	}

	@Override
	public DztFile copy() {
		// ground profile is not copied
		DztFile copy = new DztFile();
		copy.sourceFile = this.sourceFile;
		copy.dzg = this.dzg;
		copy.selectedChannelIndex = this.selectedChannelIndex;

		copy.setFile(getFile());
		copy.setUnsaved(isUnsaved());

		List<Trace> tracesCopy = Traces.copy(traces);
		List<BaseObject> elementsCopy = AuxElements.copy(getAuxElements());

		copy.channels = new ArrayList<>(this.channels.size());
		for (int i = 0; i < this.channels.size(); i++) {
			DztChannel src = this.channels.get(i);
			if (i == this.selectedChannelIndex) {
				copy.channels.add(new DztChannel(src.getIndex(), src.getName(),
						src.getHeader(), tracesCopy, src.getSampleAverage()));
			} else {
				copy.channels.add(src);
			}
		}

		if (metaFile != null) {
			copy.metaFile = new MetaFile();
			copy.metaFile.setMetaToState(metaFile.getMetaFromState());
			copy.syncMeta(tracesCopy);
		}

		copy.setTraces(tracesCopy);
		copy.setAuxElements(elementsCopy);

		return copy;
	}
}
