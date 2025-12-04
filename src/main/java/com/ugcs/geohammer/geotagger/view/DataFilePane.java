package com.ugcs.geohammer.geotagger.view;

import java.io.File;
import java.time.Instant;
import java.util.List;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.Formatters;
import com.ugcs.geohammer.geotagger.domain.CoverageStatus;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import org.springframework.stereotype.Component;

@Component
public class DataFilePane extends FilePane {

    public DataFilePane(Model model, StatusBar statusBar) {
        super(model, statusBar, "Data Files");
    }

    @Override
    protected HBox createHeader() {
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
				Views.createVerticalSeparator(),
				Views.createFixedLabel("Coverage Status", 150)
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
				Views.createLabel(fileName, 300),
				Views.createSpacer(),
				Views.createVerticalSeparator(),
				Views.createLabel(templateName, 150),
				Views.createVerticalSeparator(),
				Views.createLabel(Formatters.formatTime(from), 150),
				Views.createVerticalSeparator(),
				Views.createLabel(Formatters.formatTime(to), 150),
				Views.createVerticalSeparator(),
				Views.createLabel(coverageStatus, 150));
        return row;
    }

    private String calculateCoverageStatus(SgyFile file) {
        List<SgyFile> positionFiles = getFiles();
        return CoverageStatus.compute(file, positionFiles).getDisplayName();
    }

    @Override
    protected boolean canAdd(File file) {
        return FileTypes.isGprFile(file) || (FileTypes.isCsvFile(file) && !FileTypes.isPositionFile(file));
    }
}
