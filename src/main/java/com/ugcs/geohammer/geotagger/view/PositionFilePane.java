package com.ugcs.geohammer.geotagger.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.Formatters;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.Template;
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
public class PositionFilePane extends FilePane {

    private static final String HEADER_STYLE = "-fx-background-color: linear-gradient(to bottom, #fafafa, #e5e5e5);" +
            "-fx-padding: 5;";

	private final Model model;

    public PositionFilePane(Model model, StatusBar statusBar) {
        super(model, statusBar, "Position Files");
		this.model = model;
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
		String fileName = Formatters.formatFileName(file);
		String templateName = Formatters.formatTemplateName(file);
		TimeRange timeRange = TimeRange.of(file);

		Instant from = timeRange != null ? timeRange.from() : null;
		Instant to = timeRange != null ? timeRange.to() : null;

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
                GeotaggerComponents.fixedLabel(Formatters.formatTime(to), 150));
        return row;
    }

    @Override
    public boolean canAdd(File file) {
		List<Template> fileTemplates = model.getFileManager().getFileTemplates().getTemplates();
		Template template = model.getFileManager().getFileTemplates().findTemplate(fileTemplates, file);
		return template != null && template.canProvideGeodata();
    }

    @Override
    public Node createPlaceholder() {
        return GeotaggerComponents.createDragAndDropPlaceholder("Drag and drop position files here");
    }

    @Override
    public void selectAndAddFile(Consumer<List<File>> consumer) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Position Files");
        List<File> files = fileChooser.showOpenMultipleDialog(AppContext.stage);
        if (files != null && !files.isEmpty())
            consumer.accept(files);
    }

    @Override
    public void selectAndAddFolder(Consumer<List<File>> consumer) {
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
