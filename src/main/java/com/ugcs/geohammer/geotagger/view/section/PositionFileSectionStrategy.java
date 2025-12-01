package com.ugcs.geohammer.geotagger.view.section;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.PositionSourceFileIdentifier;
import com.ugcs.geohammer.geotagger.SgyFileInfoExtractor;
import com.ugcs.geohammer.geotagger.view.GeotaggerComponents;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class PositionFileSectionStrategy implements FileSectionStrategy {

	private static final String HEADER_STYLE = "-fx-background-color: linear-gradient(to bottom, #fafafa, #e5e5e5);" +
			"-fx-padding: 5;";

	private final Supplier<Window> stage;
	private final PositionSourceFileIdentifier positionSourceFileIdentifier;
	private final SgyFileInfoExtractor sgyFileInfoExtractor;

	public PositionFileSectionStrategy(Supplier<Window> stage, PositionSourceFileIdentifier positionSourceFileIdentifier, SgyFileInfoExtractor sgyFileInfoExtractor) {
		this.stage = stage;
		this.positionSourceFileIdentifier = positionSourceFileIdentifier;
		this.sgyFileInfoExtractor = sgyFileInfoExtractor;
	}

	@Override
	public HBox createHeader() {
		HBox header = new HBox(5);
		header.setAlignment(Pos.CENTER_LEFT);
		header.setStyle(HEADER_STYLE);
		header.getChildren().addAll(
				GeotaggerComponents.headerLabel("File", 300),
				GeotaggerComponents.spacer(),
				GeotaggerComponents.createVerticalSeparator(),
				GeotaggerComponents.headerLabel("Template", 150),
				GeotaggerComponents.createVerticalSeparator(),
				GeotaggerComponents.headerLabel("Start Time", 150),
				GeotaggerComponents.createVerticalSeparator(),
				GeotaggerComponents.headerLabel("End Time", 150)
		);
		return header;
	}

	@Override
	public HBox createDataRow(SgyFile file) {
		SgyFileInfoExtractor.SgyFileInfo info = sgyFileInfoExtractor.extractInfo(file);

		HBox row = new HBox(5);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(0, -5, 0, 0));
		row.getChildren().addAll(
				GeotaggerComponents.fixedLabel(info.fileName(), 300),
				GeotaggerComponents.spacer(),
				GeotaggerComponents.createVerticalSeparator(),
				GeotaggerComponents.fixedLabel(info.templateName(), 150),
				GeotaggerComponents.createVerticalSeparator(),
				GeotaggerComponents.fixedLabel(info.startTime(), 150),
				GeotaggerComponents.createVerticalSeparator(),
				GeotaggerComponents.fixedLabel(info.endTime(), 150));
		return row;
	}

	@Override
	public boolean isValidFile(File file) {
		return positionSourceFileIdentifier.isPositionFile(file);
	}

	@Override
	public Node createPlaceholder() {
		return GeotaggerComponents.createDragAndDropPlaceholder("Drag and drop position files here");
	}

	@Override
	public void selectAndAddFile(Consumer<List<File>> consumer) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Position Files");
		List<File> files = fileChooser.showOpenMultipleDialog(stage.get());
		if (files != null && !files.isEmpty())
			consumer.accept(files);
	}

	@Override
	public void selectAndAddFolder(Consumer<List<File>> consumer) {
		DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setTitle("Select Folder");
		File dir = directoryChooser.showDialog(stage.get());
		if (dir != null && dir.isDirectory()) {
			File[] files = dir.listFiles(File::isFile);
			if (files != null) {
				consumer.accept(List.of(files));
			}
		}
	}
}
