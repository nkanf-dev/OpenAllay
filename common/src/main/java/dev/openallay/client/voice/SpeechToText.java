package dev.openallay.client.voice;

import java.util.Objects;

/** Offline and HTTP recognition share this client-only, Minecraft-free boundary. */
@FunctionalInterface
public interface SpeechToText {
    Result transcribe(Request request, VoiceCancellation cancellation) throws Exception;
    record Request(PcmClip clip, String language, int cpuThreads) {
        public Request {
            Objects.requireNonNull(clip, "clip");
            Objects.requireNonNull(language, "language");
            if (cpuThreads < 1 || cpuThreads > 8) throw new IllegalArgumentException("cpuThreads");
        }
    }
    record Result(String text, String source, Usage usage) {
        public Result {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(source, "source");
            text = text.strip();
            if (text.isEmpty() || text.length() > 32_768) throw new IllegalArgumentException("Empty or oversized transcript");
        }
    }
    /** Null values mean the provider did not report usage; never invent token counts. */
    record Usage(Double audioSeconds, Long inputTokens, Long outputTokens) {}
}
