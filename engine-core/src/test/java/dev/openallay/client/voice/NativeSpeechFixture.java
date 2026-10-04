package dev.openallay.client.voice;

import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

/** Explicit public-fixture runner; never opens a capture device or loads user configuration. */
public final class NativeSpeechFixture {
    private NativeSpeechFixture() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected model directory, trusted runtime root, public WAV");
        PcmClip clip;
        try (var input = AudioSystem.getAudioInputStream(Path.of(args[2]).toFile())) {
            AudioFormat format = input.getFormat();
            if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) || format.getSampleRate() != 16_000
                    || format.getSampleSizeInBits() != 16 || format.getChannels() != 1 || format.isBigEndian()
                    || format.getFrameSize() != 2) throw new IllegalArgumentException("Fixture must be signed PCM16 mono 16kHz little-endian");
            byte[] pcm = input.readNBytes(PcmClip.MAX_SECONDS * PcmClip.BYTES_PER_SECOND + 2);
            clip = new PcmClip(pcm);
        }
        long start = System.nanoTime();
        SpeechToText.Result result = new NativeSpeechToText(Path.of(args[0]), Path.of(args[1]))
                .transcribe(new SpeechToText.Request(clip, "auto", 2), new VoiceCancellation());
        System.out.println("text=" + result.text());
        System.out.println("source=" + result.source());
        System.out.println("elapsedMillis=" + (System.nanoTime() - start) / 1_000_000);
    }
}
