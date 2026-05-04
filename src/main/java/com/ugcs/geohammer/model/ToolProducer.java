package com.ugcs.geohammer.model;

import java.util.List;

import javafx.scene.Node;

public interface ToolProducer {

    default ToolNodes getToolNodes() {
        return ToolNodes.empty();
    }

    record ToolNodes(List<Node> nodes, boolean fileDependent) {

        private static final ToolNodes EMPTY = new ToolNodes(List.of(), false);

        public static ToolNodes empty() {
            return EMPTY;
        }

        public static ToolNodes of(Node... nodes) {
            return new ToolNodes(List.of(nodes), false);
        }

        public static ToolNodes fileDependent(Node... nodes) {
            return new ToolNodes(List.of(nodes), true);
        }
    }
}
