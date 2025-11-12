package com.ugcs.geohammer.format.gpr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

public class BlockFile {
	
	private InputStream is;
	private FileChannel chan;
	private int position = 0;
	
	public static BlockFile open(File file) throws FileNotFoundException {

		FileInputStream is = new FileInputStream(file);
		return new BlockFile(is);
		
	}
	
	private BlockFile(FileInputStream is) {
		this.is = is; 
		this.chan = is.getChannel();
	}

	public FileChannel getChannel() {
		return chan;
	}	
	
	public Block next(int length) {
		Block block = new Block(position, length);
		position = block.getFinishPos();
		
		return block;
	}
	
	public boolean hasNext() {
		try {
			return position < chan.size();
		} catch (IOException e) {
			return false;			
		}
	}

	public boolean hasNext(long size) {
		try {
			return position+size <= chan.size();
		} catch (IOException e) {
			return false;			
		}
	}

	public void close() {
		try {
			chan.close();
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	

}
