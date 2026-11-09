package com.ugcs.geohammer.service.demo;

public enum DemoFile {

    GPR("Gas Pipes GPR Demo", "gpr", "demo-gpr.sgy"),
    CSV("Magnimbus Survey Demo", "csv", "demo-csv.csv");

    private final String title;

    private final String folder;

    private final String fileName;

    DemoFile(String title, String folder, String fileName) {
        this.title = title;
        this.folder = folder;
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getFolder() {
        return folder;
    }

    public String getFileName() {
        return fileName;
    }
}
