package com.ugcs.gprvisualizer.app.kml;

import java.io.File;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.MessageBoxHelper;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.stage.FileChooser;

public class KMLExport {

	Model model;
	
	public KMLExport(Model model) {
		
		this.model = model;
	}
	
	public void execute() {
		
		
		FileChooser chooser = new FileChooser();
		
		chooser.setTitle("Save kml file");
		chooser.setInitialFileName("geohammer.kml");
		
		if (model.getSettings().lastExportFolder != null) {
			chooser.setInitialDirectory(model.getSettings().lastExportFolder);
		}
		
		
		FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("KML files (*.kml)", "*.kml");
		chooser.getExtensionFilters().add(extFilter);
		
		File tiffFile = chooser.showSaveDialog(AppContext.stage);

		if (tiffFile == null) {
			return;
		}

		
		model.getSettings().lastExportFolder = tiffFile.getParentFile();

		
		
		try {
			new KmlSaver(model).save(tiffFile);
			
			AppContext.status.showProgressText("Export to '" + tiffFile.getName() + "' finished!");
		} catch (Exception e) {
			AppContext.status.showProgressText("Error during export to' " + tiffFile.getName() + "'");
			
			e.printStackTrace();
			MessageBoxHelper.showError(
					"Error", "Can`t save file");

		}
		
	}
}
