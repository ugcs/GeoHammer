package com.ugcs.geohammer.geotagger.view;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.view.section.FileSectionStrategy;
import javafx.scene.control.ListCell;

class FileListCell extends ListCell<SgyFile> {
	private final FileSectionStrategy strategy;

	FileListCell(FileSectionStrategy strategy) {
		this.strategy = strategy;
	}

	@Override
	protected void updateItem(SgyFile item, boolean empty) {
		super.updateItem(item, empty);

		if (empty || item == null) {
			setGraphic(null);
		} else {
			setGraphic(strategy.createDataRow(item));
		}
	}
}
