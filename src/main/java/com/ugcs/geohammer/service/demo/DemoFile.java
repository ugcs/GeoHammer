package com.ugcs.geohammer.service.demo;

public enum DemoFile {

    GPR("GPR Grid Survey Demo", "demo-gpr.sgy"),
    CSV("MagNIMBUS Survey Demo", "demo-csv.csv");

    private final String title;

    private final String fileName;

    DemoFile(String title, String fileName) {
        this.title = title;
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }
}
