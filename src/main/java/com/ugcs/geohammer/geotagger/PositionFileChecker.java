package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.util.List;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PositionFileChecker {
	private static final Logger log = LoggerFactory.getLogger(PositionFileChecker.class);
	private final Model model;

	PositionFileChecker(Model model) {
		this.model = model;
	}

	boolean isPositionFile(File file) {
		List<Template> templates = model.getFileManager().getFileTemplates().getTemplates();
		Template template = model.getFileManager().getFileTemplates().findTemplate(templates, file);
		if (template == null) {
			log.debug("No template found for file: {}", file.getName());
			return false;
		}
		return template.isHasPositionsSource();
	}
}
