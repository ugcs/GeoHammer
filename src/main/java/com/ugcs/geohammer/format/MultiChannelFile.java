package com.ugcs.geohammer.format;

import java.io.IOException;
import java.util.List;

public interface MultiChannelFile {

	int numChannel();

	List<Channel> getChannels();

	int getSelectedChannelIndex();

	void selectChannel(int channelIndex) throws IOException;
}
