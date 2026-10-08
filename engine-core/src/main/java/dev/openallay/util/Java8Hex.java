package dev.openallay.util;

import java.util.Objects;

/** Lowercase, two-digit byte formatting without HexFormat. */
public final class Java8Hex {
    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private Java8Hex() {}

    public static String formatHex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return formatHex(bytes, 0, bytes.length);
    }

    public static String formatHex(byte[] bytes, int fromIndex, int toIndex) {
        Objects.requireNonNull(bytes, "bytes");
        if (fromIndex < 0 || toIndex > bytes.length || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("invalid byte range");
        }
        int length = toIndex - fromIndex;
        if (length > Integer.MAX_VALUE / 2) {
            throw new OutOfMemoryError("hex string exceeds maximum length");
        }
        char[] result = new char[length * 2];
        for (int index = 0; index < length; index++) {
            int value = bytes[fromIndex + index] & 0xff;
            result[index * 2] = DIGITS[value >>> 4];
            result[index * 2 + 1] = DIGITS[value & 0x0f];
        }
        return new String(result);
    }
}
