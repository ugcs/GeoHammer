package com.ugcs.geohammer.format.gpr;

public enum Edge {

    EMPTY((byte)0),
    // + -> -
    FALL_CROSS((byte)1),
    // - -> +
    RISE_CROSS((byte)2),
    // local min
    MIN_PEAK((byte)3),
    // local max
    MAX_PEAK((byte)4);

    private static final Edge[] BY_CODE = indexByCode();

    private static Edge[] indexByCode() {
        int maxCode = 0;
        for (Edge edge : values()) {
            maxCode = Math.max(maxCode, edge.code);
        }
        Edge[] edges = new Edge[maxCode + 1];
        for (Edge edge : values()) {
            edges[edge.code] = edge;
        }
        return edges;
    }

    private final byte code;

    Edge(byte code) {
        this.code = code;
    }

    public static Edge of(byte code) {
        return BY_CODE[code];
    }

    public byte code() {
        return code;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public boolean isCross() {
        return this == FALL_CROSS || this == RISE_CROSS;
    }

    public boolean isPeak() {
        return this == MIN_PEAK || this == MAX_PEAK;
    }
}
