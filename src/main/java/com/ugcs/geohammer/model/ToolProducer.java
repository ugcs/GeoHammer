package com.ugcs.geohammer.model;

import java.util.List;

public interface ToolProducer {

    default List<ToolNode> getToolNodes() {
		return List.of();
	}
}
