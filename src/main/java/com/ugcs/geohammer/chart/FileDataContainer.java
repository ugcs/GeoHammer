package com.ugcs.geohammer.chart;

import javafx.scene.Node;

/**
 * The FileDataContainer interface represents a container for file data.
 * It provides methods to retrieve the root node and select a file represented by component.
 */
public interface FileDataContainer {

    Node getRootNode();

    void selectFile();

}
