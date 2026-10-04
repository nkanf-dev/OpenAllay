package dev.openallay.model.tokenizer;

import java.util.Objects;

/** External text-tokenizer facts; native framing is estimated and image cost is separate. */
public record TokenizerMetadata(
        String backend, String backendVersion, String encoding, Mode mode, ImageAccounting imageAccounting) {
    /** Describes whether an estimate is text-only or has unaccounted image tokens. */
    public enum ImageAccounting {
        TEXT_ONLY,
        UNKNOWN;

        public String encoded() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

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
        Objects.requireNonNull(imageAccounting, "imageAccounting");
    }

    /** A text encoding is not evidence for any model's image-token accounting. */
    public TokenizerMetadata(String backend, String backendVersion, String encoding, Mode mode) {
        this(backend, backendVersion, encoding, mode, ImageAccounting.UNKNOWN);
    }
}
