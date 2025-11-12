package com.ugcs.geohammer.format.gpr;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Block implements ByteBufferProducer {

	//private FileChannel chan;
	private int start;
	private int length;

	
	public Block(int start, int length) {
		
		this.start = start;
		this.length = length;
	}
	
	public Block(Block prev, int length) {		
		this.start = prev.getFinishPos();
		this.length = length;
	}

	@Override
	public ByteBuffer read(BlockFile blockFile) throws IOException {
		
		ByteBuffer buf = ByteBuffer.allocate(length);
		
		blockFile.getChannel().position(start);
	    if (blockFile.getChannel().read(buf) != length) {
	    	throw new IOException();
	    }
	    
	    return buf;
	}
	
	public int getFinishPos() {
		return start+length;
	}
	

	
}
