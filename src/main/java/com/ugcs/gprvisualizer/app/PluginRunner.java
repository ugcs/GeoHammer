package com.ugcs.gprvisualizer.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.auxcontrol.AuxRect;
import com.ugcs.gprvisualizer.app.commands.SingleCommand;
import com.ugcs.gprvisualizer.gpr.Model;

public class PluginRunner implements SingleCommand {

	private static final String NEURAL_NETWORK_IS_NOT_AVAILABLE_NOW 
		= "Neural network is not available now";

	private static final String FOUND_ANOMALIES = "Found anomalies: ";

	private Model model;
	
	private void processSgyFiles(ProgressListener listener, List<TraceFile> sgfl) {
		try {
			
			StringBuilder sb = new StringBuilder();
			for (SgyFile sf : sgfl) {
				if (sb.length() > 0) {
					sb.append(",");
				}
				sb.append(sf.getFile().getAbsolutePath());
			}
			
			String sgyprocPath = System.getenv().get("SGYPROC");
			if (StringUtils.isBlank(sgyprocPath)) {
				MessageBoxHelper.showError(NEURAL_NETWORK_IS_NOT_AVAILABLE_NOW, 
						"System environment variable 'SGYPROC' absent");
				return;
			}
			
			System.out.println(sgyprocPath);
			
			String line;

			String cmd = "python \"" + sgyprocPath 
					+ "/main.py\" "
					+ "\" " + sb.toString() + "\" "
					+ "--model \"" + sgyprocPath 
					+ "\\model.pb\" --no_progressbar";
			
			System.out.println(cmd);
			Process p = new ProcessBuilder(cmd).start();		
			//
			BufferedReader input = new BufferedReader(
					new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				
				if (!line.contains("Iterations")) {
					System.out.println(line);
					listener.progressMsg(line);
				}
				
				processV2(sgfl, line);
				
			}
			input.close();
			
			System.out.println("p.exitValue() " + p.exitValue());
			if (p.exitValue() != 0) {
				String text = IOUtils.toString(p.getErrorStream(), 
						StandardCharsets.UTF_8.name());
				System.out.println(text);
				
				throw new RuntimeException("p.exitValue() != 0 " + p.exitValue());
			}
			
			listener.progressMsg("search finished");
			
		} catch (Exception err) {
			err.printStackTrace();
			
			MessageBoxHelper.showError(NEURAL_NETWORK_IS_NOT_AVAILABLE_NOW, "");
		}
	}
	
	public PluginRunner(Model model) {
		this.model = model;
	}

	private void processV2(List<TraceFile> sgyFileList, String line) throws Exception {
		if (line.startsWith("{")) {

			JSONParser jsonParser = new JSONParser();
			JSONObject jsonObject = (JSONObject) jsonParser.parse(line);
			int pos = (int) (long) (Long) jsonObject.get("file_number");

			SgyFile sgyFile = sgyFileList.get(pos - 1);
			
			loadAnomalies(sgyFile, jsonObject);
		}
	}

	private void loadAnomalies(SgyFile sgyFile, JSONObject jsonObject) {
		JSONArray objects = (JSONArray) jsonObject.get("anomalies");
		for (Object t : objects) {

			JSONObject json = (JSONObject) t;

			sgyFile.getAuxElements().add(
				new AuxRect(json, sgyFile.getOffset()));
		}

		model.updateAuxElements();
	}

	private void process(SgyFile sgyFile, String line) {
		if (line.startsWith(FOUND_ANOMALIES)) {
			String trnums = line.substring(FOUND_ANOMALIES.length());
			if (trnums.contains("no objects")) {
				return;
			}

			model.updateAuxElements();
		}
	}

	@Override
	public String getButtonText() {
		return "Neural network scan";
	}

	@Override
	public void execute(List<TraceFile> files, ProgressListener listener) {
		processSgyFiles(listener, model.getFileManager().getGprFiles());
	}

}
