package com.ugcs.geohammer.feedback;

import com.ugcs.geohammer.util.Check;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class BytesAttachment implements Attachment {

    private final String fileName;

    private final byte[] bytes;

    public BytesAttachment(String fileName, byte[] bytes) {
        this.fileName = Check.notEmpty(fileName);
        this.bytes = Check.notNull(bytes);
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public InputStream getInput() {
        return new ByteArrayInputStream(bytes);
    }
}
