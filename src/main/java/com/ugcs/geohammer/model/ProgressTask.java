package com.ugcs.geohammer.model;

@FunctionalInterface
public interface ProgressTask {

	void run(ProgressListener listener);
}
