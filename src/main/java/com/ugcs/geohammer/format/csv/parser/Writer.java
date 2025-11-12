package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;

import java.io.File;
import java.io.IOException;

public abstract class Writer {

    protected final Template template;

    public Writer(Template template) {
        this.template = Check.notNull(template);
    }

    public abstract void write(CsvFile csvFile, File toFile) throws IOException;
}
