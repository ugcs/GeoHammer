package com.ugcs.gprvisualizer.dzt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.Trace;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.math.MinMaxAvg;
import com.ugcs.gprvisualizer.obm.ObjectByteMapper;
import com.ugcs.gprvisualizer.utils.Traces;
import org.jspecify.annotations.Nullable;

public class DztFile extends TraceFile {

	private static final int MINHEADSIZE = 1024;
	private static final int PARAREASIZE = 128;
	
	//!?
	private static final int GPSAREASIZE = 2 * 12;
	private static final int INFOAREASIZE = (MINHEADSIZE - PARAREASIZE- GPSAREASIZE) ; 
	
	@Nullable
	private File sourceFile;

	private DztHeader header = new DztHeader();			

	public DzgFile dzg = new DzgFile();

	private MinMaxAvg valuesAvg = new MinMaxAvg();

	private static final Map<Integer, SampleValues> valueGetterMap =
			Map.of(16, new Sample16Bit(),32, new Sample32Bit());

	interface SampleValues {

		int next(ByteBuffer buffer);
		//add method that read from buffer
		
		void put(ByteBuffer buffer, int value);
	}

	private static int asUnsignedShort(short s) {
        return s & 0xFFFF;
    }		
	
	private static class Sample16Bit implements SampleValues {

		@Override
		public int next(ByteBuffer buffer) {
            return asUnsignedShort(buffer.getShort()) - 32767;
		}
		
		@Override
		public void put(ByteBuffer buffer, int value) {
			int v = value +  32767;
			buffer.putShort((short) v);
		}
	}

	private static class Sample32Bit implements SampleValues {

		@Override
		public int next(ByteBuffer buffer) {
            return buffer.getInt();
		}
		
		@Override
		public void put(ByteBuffer buffer, int value) {
			buffer.putInt(value);
		}		
	}

	@Override
	public void open(File file) throws IOException {
		setFile(file);
		this.sourceFile = file;
		
		dzg.load(getDsgFile(file));
		
		try (SeekableByteChannel datachan = Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
			ByteBuffer buf = loadHeader(datachan);
			
			ObjectByteMapper obm = new ObjectByteMapper();
			obm.readObject(header, buf);
			
			logHeader();			

			datachan.position(getDataPosition());
			setTraces(loadTraces(getValueBufferMediator(), datachan));
		}
		
		if (getTraces().isEmpty()) {
			throw new RuntimeException("Corrupted file");
		}
		
		updateTraces();
		
		copyMarkedTracesToAuxElements();
		
		updateTraceDistances();
		
		setUnsaved(false);
	}

	private ByteBuffer loadHeader(SeekableByteChannel chan) throws IOException {		
		ByteBuffer buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
		chan.position(0);
		chan.read(buf);
		return buf;
	}

	public void logHeader() {
		System.out.println("| header.rh_data  " + header.rh_data);
		System.out.println("| header.rh_bits  " + header.rh_bits);
		System.out.println("| header.rh_nsamp " + header.rh_nsamp);
		System.out.println("| header.rh_zero  " + header.rh_zero);
		System.out.println("| header.rhf_sps  " + header.rhf_sps);
		System.out.println("| avgDielectric.rhf_epsr " + header.rhf_epsr);
		System.out.println("| 		   rh_spp " + header.rh_spp);
		System.out.println("|  rhf_epsr (ns) " + header.rhf_range);
		System.out.println("|  rhf_depth (m) " + header.rhf_depth);
	}

	public File getDsgFile(File file) {
		return new File(file.getAbsolutePath().toLowerCase().replace(".dzt", ".dzg"));
	}

	public int getDataPosition() {
		return header.rh_data < MINHEADSIZE
				? MINHEADSIZE * header.rh_data
				: header.rh_nchan * header.rh_data;
	}

	public SampleValues getValueBufferMediator() {
		SampleValues valueGetter = valueGetterMap.get((int)header.rh_bits);
		return valueGetter;
	}

	private List<Trace> loadTraces(SampleValues valueGetter, SeekableByteChannel datachan) {
		List<Trace> traces = new ArrayList<>();
		int counter = 0;
		
		try {
			while (datachan.position() < datachan.size()) {
				Trace tr = next(valueGetter, datachan, counter++);
				traces.add(tr);
			}		
		} catch (Exception e) {
			System.err.println("loadTraces error");
			e.printStackTrace();
		}
		
		// subtract avg value
		subtractAvgValue(traces);
		
		return traces;
	}

	public void subtractAvgValue(List<Trace> traces) {
		float avg = (float) valuesAvg.getAvg();
		
		for (Trace trace : traces) {
			for (int smp = 0; smp < trace.getNormValues().length; smp++) {
				trace.getNormValues()[smp] -= avg;
			}			
		}
	}
	
	public Trace next(SampleValues valueGetter, SeekableByteChannel datachan, int number)
			throws IOException {
        int bufferSize = getTraceBufferSize();
		ByteBuffer databuf = ByteBuffer.allocate(bufferSize)
				.order(ByteOrder.LITTLE_ENDIAN);
		datachan.read(databuf);

		databuf.position(0);

		if (databuf.position() < databuf.capacity()) {
			//read trace number
			int tn = (int) valueGetter.next(databuf);
		}
		
		float[] values = new float[header.rh_nsamp];
		int i = 0;
		
		MinMaxAvg mma = new MinMaxAvg();
		
		while (databuf.position() < databuf.capacity()) {
			int val = valueGetter.next(databuf);
			values[i++] = val;
			
			valuesAvg.put(val);
			mma.put(val);
		}

		LatLon latLon = dzg.getLatLonForTraceNumber(number);
        byte[] headerBin = null;
		TraceHeader trheader = null;
        return new Trace(headerBin, trheader, values, latLon);
	}

	public int getTraceBufferSize() {
		int bytesPerSmp = header.rh_bits / 8;
        int bufferSize = bytesPerSmp * header.rh_nsamp;
		return bufferSize;
	}
	
	@Override
	public int getSampleInterval() {
		return (int) header.rhf_range;
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
	public DztFile copyHeader() {
		DztFile copy = new DztFile();
		copy.header = this.header;
		copy.valuesAvg = this.valuesAvg;
		copy.sourceFile = this.sourceFile;
		copy.dzg = this.dzg;
		
		//TODO: make real copy
		return copy;
	}

	@Override
	public DztFile copy() {
		DztFile copy = copyHeader();
		copy.setFile(getFile());

		List<Trace> traces = Traces.copy(getTraces());
		copy.setTraces(traces);

		return copy;
	}

	@Override
	public void saveAux(File newFile) {
		dzg.save(getDsgFile(getFile()));
	}

	@Override
	public void save(File newFile) throws IOException {
		System.out.println("Save to " + newFile.getName());
		
		FileOutputStream fos = new FileOutputStream(newFile);
		FileChannel writechan = fos.getChannel();		
		
		SampleValues valueMediator = getValueBufferMediator();
		
		ByteBuffer headerBuffer = ByteBuffer.allocate(getDataPosition())
				.order(ByteOrder.LITTLE_ENDIAN);
		
		//read from old
		readHeader(headerBuffer);
		headerBuffer.position(0);
		writechan.write(headerBuffer);		
		
		float avg = (float) valuesAvg.getAvg();
		
		for (Trace trace : getTraces()) {
			ByteBuffer buffer = ByteBuffer.allocate(getTraceBufferSize())
					.order(ByteOrder.LITTLE_ENDIAN);
			
			valueMediator.put(buffer, trace.getIndex());
			
			for (int i = 0; i < header.rh_nsamp - 1; i++) {
				valueMediator.put(buffer, (int) (trace.getNormValues()[i] + avg));
			}
		
			buffer.position(0);
			writechan.write(buffer);
		}		
		
		writechan.close();
		fos.close();
	}

	public void readHeader(ByteBuffer headerBuffer) throws FileNotFoundException, IOException {
		FileInputStream is = new FileInputStream(sourceFile);
		FileChannel chan = is.getChannel();
		chan.position(0);
		chan.read(headerBuffer);
		chan.close();
		is.close();
	}
}
