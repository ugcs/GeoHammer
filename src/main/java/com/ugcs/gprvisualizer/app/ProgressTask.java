package com.ugcs.gprvisualizer.app;

@FunctionalInterface
public interface ProgressTask {

	void run(ProgressListener listener);
}
