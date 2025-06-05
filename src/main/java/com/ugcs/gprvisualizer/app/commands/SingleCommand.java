package com.ugcs.gprvisualizer.app.commands;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;

public interface SingleCommand extends BaseCommand {

	void execute(List<TraceFile> files, ProgressListener listener);
}
