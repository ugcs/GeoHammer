package com.ugcs.geohammer.service.gpr;

import com.ugcs.geohammer.model.event.WhatChanged;

public interface BaseCommand {

	String getButtonText();

	default WhatChanged.Change getChange() {
		return WhatChanged.Change.justdraw;
	}
}
