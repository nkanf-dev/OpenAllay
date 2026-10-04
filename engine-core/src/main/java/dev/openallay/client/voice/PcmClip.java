package dev.openallay.client.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Standard signed 16-bit little-endian mono, 16 kHz. No homemade conversion. */
public final class PcmClip {
    public static final int SAMPLE_RATE = 16_000;
    public static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;
    public static final int MAX_SECONDS = 60;
    private final byte[] pcm;
    public PcmClip(byte[] pcm) {
        if (pcm == null || pcm.length == 0 || pcm.length % 2 != 0
                || pcm.length > MAX_SECONDS * BYTES_PER_SECOND) {
            throw new IllegalArgumentException("Invalid bounded PCM clip");
        }
        this.pcm = pcm.clone();
    }
    public byte[] pcm() { return pcm.clone(); }
    public double durationSeconds() { return pcm.length / (double) BYTES_PER_SECOND; }
    public byte[] wav() {
        ByteBuffer out = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        ascii(out, "RIFF"); out.putInt(36 + pcm.length); ascii(out, "WAVE");
        ascii(out, "fmt "); out.putInt(16); out.putShort((short) 1); out.putShort((short) 1);
        out.putInt(SAMPLE_RATE); out.putInt(BYTES_PER_SECOND); out.putShort((short) 2); out.putShort((short) 16);
        ascii(out, "data"); out.putInt(pcm.length); out.put(pcm);
        return out.array();
    }
    public float[] samples() {
        float[] result = new float[pcm.length / 2];
        ByteBuffer input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < result.length; i++) result[i] = input.getShort() / 32768.0f;
        return result;
    }
    private static void ascii(ByteBuffer out, String value) { out.put(value.getBytes(StandardCharsets.US_ASCII)); }
}
