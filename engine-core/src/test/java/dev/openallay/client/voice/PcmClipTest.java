package dev.openallay.client.voice;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class PcmClipTest {
    @Test void standardBoundedWavHasExactHeaderAndSamples() {
        byte[] pcm = {0, -128, -1, 127, 0, 0};
        PcmClip clip = new PcmClip(pcm); pcm[0] = 42;
        byte[] wav = clip.wav();
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(42, header.getInt(4)); assertEquals(16_000, header.getInt(24));
        assertEquals(32_000, header.getInt(28)); assertEquals(6, header.getInt(40));
        assertEquals(-1, clip.samples()[0]); assertEquals(32767 / 32768.0f, clip.samples()[1]);
        assertEquals(0, wav[44]); assertEquals(6 / 32000.0, clip.durationSeconds());
        assertThrows(IllegalArgumentException.class, () -> new PcmClip(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new PcmClip(new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new PcmClip(new byte[60 * 32_000 + 2]));
    }
}
