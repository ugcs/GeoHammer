package com.ugcs.geohammer.model;

import java.util.List;

import javafx.scene.Node;

public interface ToolProducer {

    default List<Node> getToolNodes() {
		return List.of();
	};
}
