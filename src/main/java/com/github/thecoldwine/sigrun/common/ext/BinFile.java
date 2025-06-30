package com.github.thecoldwine.sigrun.common.ext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.BinaryHeader;
import com.github.thecoldwine.sigrun.common.TextHeader;
import com.github.thecoldwine.sigrun.common.TraceHeader;

public class BinFile {

	//File file;
	public static class BinTrace {		
		byte[] header;
		byte[] data;
	}
	
	private byte[] txtHdr;
	private byte[] binHdr;
	private List<BinTrace> traces = new ArrayList<>();
	
	
	public BinFile() {
				
	}
	
	public BinFile(BinFile copy) {
		this.txtHdr = Arrays.copyOf(copy.txtHdr, copy.txtHdr.length);
		this.binHdr = Arrays.copyOf(copy.binHdr, copy.binHdr.length);
	}
	
	public static BinFile load(File file) throws IOException {
		
		BinFile binFile = new BinFile();
		
		BlockFile blockFile = BlockFile.open(file);		
		try {
			Block txtHdrBlock = blockFile.next(TextHeader.TEXT_HEADER_SIZE);
			Block binHdrBlock = blockFile.next(BinaryHeader.BIN_HEADER_LENGTH);
			
			binFile.txtHdr = txtHdrBlock.read(blockFile).array();
			binFile.binHdr = binHdrBlock.read(blockFile).array();
			
			BinaryHeader binaryHeader = GprFile.binaryHeaderReader.read(binHdrBlock.read(blockFile).array());
			
			System.out.println(binaryHeader.getDataSampleCode() + " " + binaryHeader.getDataSampleCode().getSize());
			
			while (blockFile.hasNext(TraceHeader.TRACE_HEADER_LENGTH)) {
				Block traceHdrBlock = blockFile.next(TraceHeader.TRACE_HEADER_LENGTH);
				
				BinTrace binTrace = new BinTrace();
				binTrace.header = traceHdrBlock.read(blockFile).array();
				
				
		        TraceHeader header = GprFile.traceHeaderReader.read(binTrace.header);
		        
		        
		        int dataLength = binaryHeader.getDataSampleCode().getSize() * header.getNumberOfSamples();
		        
		        if (dataLength > 0 && blockFile.hasNext(dataLength)) {
		        	Block traceDataBlock = blockFile.next(dataLength);				
		        	binTrace.data = traceDataBlock.read(blockFile).array();
		        	binFile.traces.add(binTrace);
		        }
			}
		} finally {
			blockFile.close();
		}
		
		return binFile;
	}
	
	public void save(File file) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		FileChannel writechan = fos.getChannel();		
		
		writechan.write(ByteBuffer.wrap(txtHdr));
		writechan.write(ByteBuffer.wrap(binHdr));		
		
		for (BinTrace trace : traces) {
			writechan.write(ByteBuffer.wrap(trace.header));
			writechan.write(ByteBuffer.wrap(trace.data));
		}		
		
		writechan.close();
		fos.close();
	}

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

	public List<BinTrace> getTraces() {
		return traces;
	}

	public void setTraces(List<BinTrace> traces) {
		this.traces = traces;
	}
	
}
