package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.util.List;

import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PositionSourceFileIdentifier {
	private static final Logger log = LoggerFactory.getLogger(PositionSourceFileIdentifier.class);
	private final Model model;

	PositionSourceFileIdentifier(Model model) {
		this.model = model;
	}

	public boolean isPositionFile(File file) {
		FileManager fileManager = model.getFileManager();
		List<Template> templates = fileManager.getFileTemplates().getTemplates();
		Template template = fileManager.getFileTemplates().findTemplate(templates, file);
		if (template == null) {
			log.debug("No template found for file: {}", file.getName());
			return false;
		}
		return template.isHasPositionsSource();
	}
}
