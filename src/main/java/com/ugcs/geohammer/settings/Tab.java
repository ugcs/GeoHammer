package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.util.Check;
import javafx.scene.Node;

public record Tab(String title, Node content) {

    public Tab {
        Check.notEmpty(title);
        Check.notNull(content);
    }
}
