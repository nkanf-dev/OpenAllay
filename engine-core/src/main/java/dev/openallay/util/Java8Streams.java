package dev.openallay.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** Stream byte operations. Neither stream is closed by these methods. */
public final class Java8Streams {
    private Java8Streams() {}
    public static byte[] readAllBytes(InputStream input) throws IOException {
        Objects.requireNonNull(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transferTo(input, output);
        return output.toByteArray();
    }
    public static byte[] readNBytes(InputStream input, int length) throws IOException {
        Objects.requireNonNull(input);
        if (length < 0) throw new IllegalArgumentException("len < 0");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(length, 8192));
        byte[] buffer = new byte[Math.min(length, 8192)]; int remaining = length;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) break;
            if (count == 0) {
                int one = input.read(); if (one < 0) break;
                output.write(one); remaining--;
            } else { output.write(buffer, 0, count); remaining -= count; }
        }
        return output.toByteArray();
    }
    public static long transferTo(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(output, "out");
        byte[] buffer = new byte[8192]; long transferred = 0;
        for (;;) {
            int count = input.read(buffer);
            if (count < 0) return transferred;
            if (count == 0) {
                int one = input.read(); if (one < 0) return transferred;
                output.write(one); transferred++;
            } else { output.write(buffer, 0, count); transferred += count; }
        }
    }
}
