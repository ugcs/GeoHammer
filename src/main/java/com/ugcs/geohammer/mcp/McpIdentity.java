package com.ugcs.geohammer.mcp;

import com.ugcs.geohammer.util.Check;

public record McpIdentity(String name, String url) {

    public McpIdentity {
        Check.notEmpty(name);
        Check.notEmpty(url);
    }
}
