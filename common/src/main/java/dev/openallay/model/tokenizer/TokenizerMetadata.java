package dev.openallay.model.tokenizer;

import java.util.Objects;

/** External tokenizer facts; native message/tool framing remains an estimate. */
public record TokenizerMetadata(String backend, String backendVersion, String encoding, Mode mode) {
    public enum Mode {
        MODEL_MAPPING,
        EXPLICIT_ENCODING,
        CONSERVATIVE_SURROGATE,
        CUSTOM
    }

    public TokenizerMetadata {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(backendVersion, "backendVersion");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(mode, "mode");
    }
}
