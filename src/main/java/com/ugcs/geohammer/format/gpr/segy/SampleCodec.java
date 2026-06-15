package com.ugcs.geohammer.format.gpr.segy;

import com.github.thecoldwine.sigrun.common.DataSample;

import java.nio.ByteOrder;

public interface SampleCodec {

    static SampleCodec create(DataSample sample, ByteOrder order) {
        return switch (sample) {
            case IBM_FP -> new IBM360Codec(order);
            case IEEE_FP -> new IEEECodec(order);
            case TC_4B_I -> new TC4BICodec(order);
            case TC_2B_I -> new TC2BICodec(order);
            default -> throw new UnsupportedOperationException("No sample codec for "
                    + sample.getCode() + " " + sample.getDescription() + " " + sample.getSize());
        };
    }

    float[] decode(byte[] bytes, int offset, int length);

    default float[] decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    byte[] encode(float[] samples, int offset, int length);

    default byte[] encode(float[] samples) {
        return encode(samples, 0, samples.length);
    }
}
