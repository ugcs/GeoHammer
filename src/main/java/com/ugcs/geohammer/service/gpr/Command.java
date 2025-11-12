package com.ugcs.geohammer.service.gpr;

import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;


public interface Command extends BaseCommand {
		
	void execute(TraceFile file, ProgressListener listener);

}
