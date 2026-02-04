package com.ugcs.geohammer.geotagger.view;

import java.io.File;
import java.time.Instant;
import java.util.function.Function;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.geotagger.Formatters;
import com.ugcs.geohammer.geotagger.domain.CoverageStatus;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import org.springframework.stereotype.Component;

@Component
public class DataFilePane extends FilePane {

    private Function<SgyFile, CoverageStatus> coverageStatusFunction = file -> CoverageStatus.NOT_COVERED;

	private final Model model;

    public DataFilePane(Model model, StatusBar statusBar) {
        super(model, statusBar, "Data Files");
		this.model = model;
    }

    public void setCoverageStatusFunction(Function<SgyFile, CoverageStatus> coverageStatusFunction) {
        this.coverageStatusFunction = coverageStatusFunction;
    }

    @Override
    protected HBox createHeader() {
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
				Views.createVerticalSeparator(),
				Views.createFixedLabel("Coverage Status", 160)
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
				Views.createLabel(fileName, 270),
				Views.createSpacer(),
				Views.createVerticalSeparator(),
				Views.createLabel(templateName, 160),
				Views.createVerticalSeparator(),
				Views.createLabel(Formatters.formatDateTime(from), 160),
				Views.createVerticalSeparator(),
				Views.createLabel(Formatters.formatDateTime(to), 160),
				Views.createVerticalSeparator(),
				Views.createLabel(coverageStatus, 160));
        return row;
    }

    private String calculateCoverageStatus(SgyFile file) {
        return coverageStatusFunction.apply(file).getDisplayName();
    }

    @Override
    protected boolean canAdd(File file) {
		if (FileTypes.isDztFile(file)
				|| FileTypes.isSvlogFile(file)
				|| FileTypes.isKmlFile(file)
				|| FileTypes.isConstPointFile(file)) {
			return false;
		}
		FileManager fileManager = model.getFileManager();

		SgyFile sgyFile = fileManager.getFile(file);
		if (sgyFile != null) {
			return sgyFile instanceof GprFile || (sgyFile instanceof CsvFile && !FileTypes.isPositionFile(file));
		}
        return !FileTypes.isPositionFile(file);
    }
}
