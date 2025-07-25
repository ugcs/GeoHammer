package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;


public interface Command extends BaseCommand {
		
	void execute(TraceFile file, ProgressListener listener);

}
