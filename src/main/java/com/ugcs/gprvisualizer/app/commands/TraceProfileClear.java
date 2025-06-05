package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;

public class TraceProfileClear implements Command {

	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		file.profiles = null;
	}

	@Override
	public String getButtonText() {
		return "Clear";
	}
}
