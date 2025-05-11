package com.ugcs.gprvisualizer.app.auxcontrol;

import com.ugcs.gprvisualizer.gpr.Model;

public abstract class BaseObjectWithModel extends BaseObjectImpl {

    protected final Model model;

    public BaseObjectWithModel(Model model) {
        this.model = model;
    }
}
