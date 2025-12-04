package com.ugcs.geohammer.geotagger.view;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.geotagger.Formatters;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.Template;
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
				Views.createFixedLabel("File", 300),
                Views.createSpacer(),
				Views.createVerticalSeparator(),
				Views.createFixedLabel("Template", 150),
				Views.createVerticalSeparator(),
				Views.createFixedLabel("Start Time", 150),
				Views.createVerticalSeparator(),
				Views.createFixedLabel("End Time", 150),
				// empty space for alignment
				Views.createFixedLabel("", 151));
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
                Views.createLabel(fileName, 300),
                Views.createSpacer(),
                Views.createVerticalSeparator(),
                Views.createLabel(templateName, 150),
                Views.createVerticalSeparator(),
                Views.createLabel(Formatters.formatTime(from), 150),
                Views.createVerticalSeparator(),
                Views.createLabel(Formatters.formatTime(to), 150),
				// empty space for alignment
				Views.createFixedLabel("", 151));
        return row;
    }

    @Override
    public boolean canAdd(File file) {
		FileManager fileManager = model.getFileManager();

		Template template;
		SgyFile sgyFile = fileManager.getFile(file);
		if (sgyFile != null) {
			template = sgyFile instanceof CsvFile csvFile
					? csvFile.getTemplate()
					: null;
		} else {
			FileTemplates fileTemplates = fileManager.getFileTemplates();
			template = fileTemplates.findTemplate(fileTemplates.getTemplates(), file);
		}
		return template != null && template.isPositional();
    }
}
