package com.ugcs.geohammer.model;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Check;
import javafx.scene.Node;

public class ToolNode {
	private final Node node;

	private final ActivationPolicy activationPolicy;

	public ToolNode(Node node, ActivationPolicy activationPolicy) {
		this.node = Check.notNull(node);
		this.activationPolicy = Check.notNull(activationPolicy);
	}

	public Node getNode() {
		return node;
	}

	public ActivationPolicy getActivationPolicy() {
		return activationPolicy;
	}

	public boolean activate(Model model, SgyFile file) {
		boolean active = activationPolicy.isActive(model, file);
		node.setDisable(!active);
		return active;
	}
}
