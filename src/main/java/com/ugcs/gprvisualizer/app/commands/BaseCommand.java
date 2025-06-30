package com.ugcs.gprvisualizer.app.commands;

import com.ugcs.gprvisualizer.event.WhatChanged;

public interface BaseCommand {

	String getButtonText();

	default WhatChanged.Change getChange() {
		return WhatChanged.Change.justdraw;
	}
}
