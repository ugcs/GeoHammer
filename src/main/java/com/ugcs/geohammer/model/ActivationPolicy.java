package com.ugcs.geohammer.model;

import com.ugcs.geohammer.format.SgyFile;

@FunctionalInterface
public interface ActivationPolicy {
	boolean isActive(Model model, SgyFile file);

	static ActivationPolicy always() {
		return (model, file) -> true;
	}

	static ActivationPolicy fileSelected() {
		return (model, file) -> file != null;
	}

	static ActivationPolicy traceSelected() {
		return (model, file) -> model.getSelectedTraceInCurrentChart() != null;
	}
}
