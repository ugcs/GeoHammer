package com.ugcs.geohammer.geotagger.view;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.Formatters;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;

@Component
public class PositionFilePane extends FilePane {

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
				Views.createFixedLabel("File", 270),
                Views.createSpacer(),
				Views.createVerticalSeparator(),
				Views.createFixedLabel("Template", 160),
				Views.createVerticalSeparator(),
				Views.createFixedLabel("Start Time", 160),
				Views.createVerticalSeparator(),
				Views.createFixedLabel("End Time", 160),
				// empty space for alignment
				Views.createFixedLabel("", 161));
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
                Views.createLabel(fileName, 270),
                Views.createSpacer(),
                Views.createVerticalSeparator(),
                Views.createLabel(templateName, 160),
                Views.createVerticalSeparator(),
                Views.createLabel(Formatters.formatDateTime(from), 160),
                Views.createVerticalSeparator(),
                Views.createLabel(Formatters.formatDateTime(to), 160),
				// empty space for alignment
				Views.createFixedLabel("", 161));
        return row;
    }

    @Override
    public boolean canAdd(File file) {
        return model.getFileManager().isPositionalFile(file);
    }
}
