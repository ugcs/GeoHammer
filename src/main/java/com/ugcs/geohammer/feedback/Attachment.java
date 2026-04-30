package com.ugcs.geohammer.feedback;

import java.io.InputStream;

public interface Attachment {

    long getSize();

    String getFileName();

    InputStream getInput();
}
