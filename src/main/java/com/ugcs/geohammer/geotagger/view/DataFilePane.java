package com.ugcs.geohammer.geotagger.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.CoverageStatusResolver;
import com.ugcs.geohammer.geotagger.Formatters;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.FileTypes;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DataFilePane extends FilePane {

    private static final String HEADER_STYLE = "-fx-background-color: linear-gradient(to bottom, #fafafa, #e5e5e5);" +
            "-fx-padding: 5;";


    public DataFilePane(Model model, StatusBar statusBar) {
        super(model, statusBar, "Data Files");
    }

    @Override
    protected HBox createHeader() {
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
                GeotaggerComponents.headerLabel("End Time", 150),
                GeotaggerComponents.createVerticalSeparator(),
                GeotaggerComponents.headerLabel("Coverage Status", 150)
        );
        return header;
    }

    @Override
    protected HBox createDataRow(SgyFile file) {
		String fileName = Formatters.formatFileName(file);
		String templateName = Formatters.formatTemplateName(file);
		TimeRange timeRange = TimeRange.of(file);

		Instant from = timeRange != null ? timeRange.from() : null;
		Instant to = timeRange != null ? timeRange.to() : null;
        String coverageStatus = calculateCoverageStatus(file);

        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, -5, 0, 0));
        row.getChildren().addAll(
                GeotaggerComponents.fixedLabel(fileName, 300),
                GeotaggerComponents.spacer(),
                GeotaggerComponents.createVerticalSeparator(),
                GeotaggerComponents.fixedLabel(templateName, 150),
                GeotaggerComponents.createVerticalSeparator(),
                GeotaggerComponents.fixedLabel(Formatters.formatTime(from), 150),
                GeotaggerComponents.createVerticalSeparator(),
                GeotaggerComponents.fixedLabel(Formatters.formatTime(to), 150),
                GeotaggerComponents.createVerticalSeparator(),
                GeotaggerComponents.fixedLabel(coverageStatus, 150));
        return row;
    }

    private String calculateCoverageStatus(SgyFile file) {
        List<SgyFile> positionFiles = getFiles();
        return CoverageStatusResolver.determineCoverageStatus(positionFiles, file).getDisplayName();
    }

    @Override
    protected boolean canAdd(File file) {
        return FileTypes.isGprFile(file) || FileTypes.isCsvFile(file);
    }

    @Override
    protected Node createPlaceholder() {
        return GeotaggerComponents.createDragAndDropPlaceholder("Drag and drop data files here");
    }

    @Override
    protected void selectAndAddFile(Consumer<List<File>> consumer) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Data Files");
        List<File> files = fileChooser.showOpenMultipleDialog(AppContext.stage);
        if (files != null && !files.isEmpty())
            consumer.accept(files);
    }

    @Override
    protected void selectAndAddFolder(Consumer<List<File>> consumer) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");
        File dir = directoryChooser.showDialog(AppContext.stage);
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles(File::isFile);
            if (files != null) {
                consumer.accept(List.of(files));
            }
        }
    }
}
