package com.ugcs.geohammer.util;

public final class Unchecked {

    private Unchecked() {
    }

    public static void wrap(CheckedVoidCall call) {
        Check.notNull(call);

        try {
            call.invoke();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T wrap(CheckedCall<T> call) {
        Check.notNull(call);

        try {
            return call.invoke();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface CheckedVoidCall {

        void invoke() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedCall<T> {

        T invoke() throws Exception;
    }
}
