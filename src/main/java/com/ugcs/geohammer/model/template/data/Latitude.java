package com.ugcs.geohammer.model.template.data;

import com.ugcs.geohammer.model.Semantic;

public class Latitude extends BaseData {

    @Override
    public String getSemantic() {
        return Semantic.LATITUDE.getName();
    }
}
