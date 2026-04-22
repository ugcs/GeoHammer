package com.ugcs.geohammer.format;

import java.io.IOException;

public interface MultiChannelFile {

	int numChannels();

	Channel getChannel(int channelIndex);

	int getSelectedChannelIndex();

	void selectChannel(int channelIndex) throws IOException;
}
