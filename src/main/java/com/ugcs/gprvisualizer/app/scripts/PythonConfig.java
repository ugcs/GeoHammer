package com.ugcs.gprvisualizer.app.scripts;

import org.springframework.stereotype.Component;

@Component
public class PythonConfig {
	private String pythonPath = "";

	public String getPythonPath() {
		return pythonPath;
	}

	public void setPythonPath(String pythonPath) {
		this.pythonPath = pythonPath;
	}
}
