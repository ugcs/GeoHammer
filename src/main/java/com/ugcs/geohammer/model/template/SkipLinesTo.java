package com.ugcs.geohammer.model.template;

import org.jspecify.annotations.NullUnmarked;

/**
 * This class represents a configuration for skipping lines in a file until a certain pattern is matched.
 */
@NullUnmarked
public class SkipLinesTo {

    private String matchRegex;

    private boolean skipMatchedLine;

    /**
     * Gets the regular expression pattern to match.
     * 
     * @return The regular expression pattern.
     */
    public String getMatchRegex() {
        return matchRegex;
    }

    /**
     * Sets the regular expression pattern to match.
     * 
     * @param matchRegex The regular expression pattern to set.
     */
    public void setMatchRegex(String matchRegex) {
        this.matchRegex = matchRegex;
    }

    /**
     * Checks if the matched line should be skipped.
     * 
     * @return True if the matched line should be skipped, false otherwise.
     */
    public boolean isSkipMatchedLine() {
        return skipMatchedLine;
    }

    /**
     * Sets whether the matched line should be skipped.
     * 
     * @param skipMatchedLine True to skip the matched line, false otherwise.
     */
    public void setSkipMatchedLine(boolean skipMatchedLine) {
        this.skipMatchedLine = skipMatchedLine;
    }
}