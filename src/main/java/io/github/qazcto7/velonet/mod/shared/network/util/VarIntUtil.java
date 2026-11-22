package io.github.qazcto7.velonet.mod.shared.network.util;

public final class VarIntUtil {
    private static final int[] VARINT_EXACT_BYTE_LENGTHS = new int[33];

    static {
        for (int i = 0; i <= 32; ++i) {
            VARINT_EXACT_BYTE_LENGTHS[i] = (int) Math.ceil((31.0 - (double) (i - 1)) / 7.0);
        }
        VARINT_EXACT_BYTE_LENGTHS[32] = 1;
    }

    public static int getVarIntLength(int value) {
        return VARINT_EXACT_BYTE_LENGTHS[Integer.numberOfLeadingZeros(value)];
    }

    private VarIntUtil() {
    }
}
