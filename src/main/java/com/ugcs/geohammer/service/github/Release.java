package com.ugcs.geohammer.service.github;

import com.google.gson.annotations.SerializedName;
import com.ugcs.geohammer.util.Strings;

public class Release {

    @SerializedName("name")
    private String name;

    @SerializedName("tag_name")
    private String tagName;

    @SerializedName("html_url")
    private String htmlUrl;

    @SerializedName("prerelease")
    private Boolean preRelease;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public Boolean isPreRelease() {
        return preRelease;
    }

    public void setPreRelease(Boolean preRelease) {
        this.preRelease = preRelease;
    }

    public String getBuildVersion() {
        String version = tagName;
        if (version != null && version.startsWith("v.")) {
            version = version.substring(2);
        }
        return !Strings.isNullOrEmpty(version) ? version : "Undefined";
    }
}
