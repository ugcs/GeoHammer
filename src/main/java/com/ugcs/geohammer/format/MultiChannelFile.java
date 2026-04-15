package com.ugcs.geohammer.format;

import java.io.IOException;

public interface MultiChannelFile {

	int getChannelCount();

	int getActiveChannelIndex();

	void setActiveChannelIndex(int channel) throws IOException;

	String getChannelLabel(int channel);
}
