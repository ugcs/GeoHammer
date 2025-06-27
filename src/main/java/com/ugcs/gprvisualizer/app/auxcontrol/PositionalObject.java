package com.ugcs.gprvisualizer.app.auxcontrol;

public abstract class PositionalObject extends BaseObjectImpl {

    public abstract int getTraceIndex();

    public abstract void offset(int traceOffset);
}
