package com.ugcs.geohammer.service.script;

public class DependencyImportException extends Exception {

	private final String moduleName;

	public DependencyImportException(String moduleName) {
		super("Module '" + moduleName + "' is installed but cannot be imported.");
		this.moduleName = moduleName;
	}

	public String getModuleName() {
		return moduleName;
	}
}
