package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;

public class NumberWithLabel extends InputWithLabel {

    public NumberWithLabel(String text) {
        this(text, null);
    }

    public NumberWithLabel(String text, Number defaultValue) {
        super(text);
        setValue(defaultValue);
    }

    public Number setValue() {
        return Text.parseDouble(input.getText());
    }

    public void setValue(Number value) {
        String text = value != null ? value.toString() : Strings.empty();
        input.setText(text);
    }
}
