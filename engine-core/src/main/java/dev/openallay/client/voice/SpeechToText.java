package dev.openallay.client.voice;

import dev.openallay.util.Java8Strings;
import java.util.Objects;

/** Offline and HTTP recognition share this client-only, Minecraft-free boundary. */
@FunctionalInterface
public interface SpeechToText {
    Result transcribe(Request request, VoiceCancellation cancellation) throws Exception;
    final class Request {
        private final PcmClip clip;
        private final String language;
        private final int cpuThreads;
        public Request(PcmClip clip, String language, int cpuThreads) {
            Objects.requireNonNull(clip, "clip");
            Objects.requireNonNull(language, "language");
            if (cpuThreads < 1 || cpuThreads > 8) throw new IllegalArgumentException("cpuThreads");
            this.clip = clip;
            this.language = language;
            this.cpuThreads = cpuThreads;
        }
        public PcmClip clip() { return clip; }
        public String language() { return language; }
        public int cpuThreads() { return cpuThreads; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Request)) return false;
            Request that = (Request) other;
            return Objects.equals(this.clip, that.clip) && Objects.equals(this.language, that.language) && this.cpuThreads == that.cpuThreads;
        }
        @Override public int hashCode() { return (31 * (31 * (31 * 0 + Objects.hashCode(clip)) + Objects.hashCode(language)) + Integer.hashCode(cpuThreads)); }
        @Override public String toString() { return "Request[clip=" + clip + ", language=" + language + ", cpuThreads=" + cpuThreads + "]"; }
    }
    final class Result {
        private final String text;
        private final String source;
        private final Usage usage;
        public Result(String text, String source, Usage usage) {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(source, "source");
            text = Java8Strings.strip(text);
            if (text.isEmpty() || text.length() > 32_768) throw new IllegalArgumentException("Empty or oversized transcript");
            this.text = text;
            this.source = source;
            this.usage = usage;
        }
        public String text() { return text; }
        public String source() { return source; }
        public Usage usage() { return usage; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Result)) return false;
            Result that = (Result) other;
            return Objects.equals(this.text, that.text) && Objects.equals(this.source, that.source) && Objects.equals(this.usage, that.usage);
        }
        @Override public int hashCode() { return (31 * (31 * (31 * 0 + Objects.hashCode(text)) + Objects.hashCode(source)) + Objects.hashCode(usage)); }
        @Override public String toString() { return "Result[text=" + text + ", source=" + source + ", usage=" + usage + "]"; }
    }
    /** Null values mean the provider did not report usage; never invent token counts. */
    final class Usage {
        private final Double audioSeconds;
        private final Long inputTokens;
        private final Long outputTokens;
        public Usage(Double audioSeconds, Long inputTokens, Long outputTokens) {

            this.audioSeconds = audioSeconds;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
        public Double audioSeconds() { return audioSeconds; }
        public Long inputTokens() { return inputTokens; }
        public Long outputTokens() { return outputTokens; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Usage)) return false;
            Usage that = (Usage) other;
            return Objects.equals(this.audioSeconds, that.audioSeconds) && Objects.equals(this.inputTokens, that.inputTokens) && Objects.equals(this.outputTokens, that.outputTokens);
        }
        @Override public int hashCode() { return (31 * (31 * (31 * 0 + Objects.hashCode(audioSeconds)) + Objects.hashCode(inputTokens)) + Objects.hashCode(outputTokens)); }
        @Override public String toString() { return "Usage[audioSeconds=" + audioSeconds + ", inputTokens=" + inputTokens + ", outputTokens=" + outputTokens + "]"; }
    }
}
