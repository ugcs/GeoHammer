package com.ugcs.geohammer.model.element;

public abstract class PositionalObject extends BaseObjectImpl {

    public abstract int getTraceIndex();

    public abstract void offset(int traceOffset);
}
