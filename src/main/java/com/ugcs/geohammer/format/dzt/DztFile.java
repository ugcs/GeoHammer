package com.ugcs.geohammer.format.dzt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
	public int numChannels() {
		return channels.size();
	}

	@Override
	public Channel getChannel(int channelIndex) {
		if (channelIndex < 0 || channelIndex >= channels.size()) {
			throw new IllegalArgumentException("Channel index out of range: " + channelIndex);
		}
		return channels.get(channelIndex);
	}

	@Override
	public void open(File file) throws IOException {
		Check.notNull(file);

		setFile(file);
		this.sourceFile = file;

		dzg.load(getDzgFile(file));

		channels = new DztReader().read(file, dzg);
		
		for (DztChannel channel : channels) {
			channel.normalize();
		}

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

		// Lift in-memory invariant: writer expects raw samples on disk.
		// finally restores the normalized state whatever happens below.
		for (DztChannel channel : channels) {
			channel.denormalize();
		}
		try {
			new DztWriter().write(file, sourceFile, channels, range);
			dzg.save(getDzgFile(file), buildDzgMappings(range));
		} finally {
			for (DztChannel channel : channels) {
				channel.normalize();
			}
		}
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
		selectedChannel().normalize();
	}

	@Override
	public void denormalize() {
		selectedChannel().denormalize();
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

		copy.channels = new ArrayList<>(this.channels.size());
		for (DztChannel src : this.channels) {
			copy.channels.add(src.copy());
		}

		// Invariant: DztFile.traces and selectedChannel.getTraces() are the
		// same list reference. Take it from the freshly copied channel.
		List<Trace> tracesCopy = copy.channels.get(copy.selectedChannelIndex).getTraces();
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
}
