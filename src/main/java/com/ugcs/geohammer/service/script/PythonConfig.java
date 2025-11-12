package com.ugcs.geohammer.service.script;

import org.springframework.stereotype.Component;

@Component
public class PythonConfig {
	private String pythonExecutorPath = "";

	public String getPythonExecutorPath() {
		return pythonExecutorPath;
	}

	public void setPythonExecutorPath(String pythonExecutorPath) {
		this.pythonExecutorPath = pythonExecutorPath;
	}
}
