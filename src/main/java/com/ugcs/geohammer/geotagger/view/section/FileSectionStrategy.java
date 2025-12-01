package com.ugcs.geohammer.geotagger.view.section;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.SgyFile;
import javafx.scene.Node;
import javafx.scene.layout.HBox;

public interface FileSectionStrategy {
	HBox createHeader();
	HBox createDataRow(SgyFile file);
	boolean isValidFile(File file);

	Node createPlaceholder();

	void selectAndAddFile(Consumer<List<File>> addFiles);

	void selectAndAddFolder(Consumer<List<File>> addFiles);
}
