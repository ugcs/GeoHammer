package com.ugcs.geohammer.format.gpr;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ByteBufferProducer {

	ByteBuffer read(BlockFile blockFile) throws IOException;
}
