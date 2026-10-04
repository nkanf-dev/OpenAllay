package dev.openallay.model.tokenizer;

import java.util.Locale;

/** Stable published encoding names. AUTO never invents an unknown model's tokenizer. */
public enum ModelTokenEncoding {
    AUTO,
    CL100K_BASE,
    O200K_BASE;

    public String encoded() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ModelTokenEncoding parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tokenEncoding must be auto, cl100k_base or o200k_base");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unsupported tokenEncoding: " + value, failure);
        }
    }
}
