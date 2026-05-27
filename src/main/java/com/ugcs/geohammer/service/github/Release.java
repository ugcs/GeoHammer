package com.ugcs.geohammer.service.github;

import com.google.gson.annotations.SerializedName;
import com.ugcs.geohammer.release.Version;
import com.ugcs.geohammer.util.Strings;

public class Release {

    @SerializedName("name")
    private String name;

    @SerializedName("tag_name")
    private String tagName;

    @SerializedName("html_url")
    private String htmlUrl;

    @SerializedName("prerelease")
    private boolean preRelease;

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

    public boolean isPreRelease() {
        return preRelease;
    }

    public void setPreRelease(boolean preRelease) {
        this.preRelease = preRelease;
    }

    public Version getVersion() {
        String versionString = Strings.nullToEmpty(tagName);
        // strip leading v. or v
        if (versionString.startsWith("v.")) {
            versionString = versionString.substring(2);
        }
        if (versionString.startsWith("v")) {
            versionString = versionString.substring(1);
        }
        return Version.parse(versionString);
    }
}
