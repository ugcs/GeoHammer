package com.ugcs.geohammer.model.template.data;

import com.ugcs.geohammer.model.Semantic;

public class Altitude extends BaseData {

    @Override
    public String getSemantic() {
        return Semantic.ALTITUDE.getName();
    }
}
