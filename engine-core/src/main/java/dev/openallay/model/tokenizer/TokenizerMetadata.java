package dev.openallay.model.tokenizer;

import java.util.Objects;

/** External text-tokenizer facts; native framing is estimated and image cost is separate. */
@dev.openallay.value.ValueType(TokenizerMetadata.ValueSchemaProvider.class)
public final class TokenizerMetadata {
    private final String backend;
    private final String backendVersion;
    private final String encoding;
    private final Mode mode;
    private final ImageAccounting imageAccounting;
    public TokenizerMetadata(String backend, String backendVersion, String encoding, Mode mode, ImageAccounting imageAccounting) {

        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(backendVersion, "backendVersion");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(imageAccounting, "imageAccounting");

        this.backend = backend;
        this.backendVersion = backendVersion;
        this.encoding = encoding;
        this.mode = mode;
        this.imageAccounting = imageAccounting;
    }
    public String backend() { return backend; }
    public String backendVersion() { return backendVersion; }
    public String encoding() { return encoding; }
    public Mode mode() { return mode; }
    public ImageAccounting imageAccounting() { return imageAccounting; }
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
public TokenizerMetadata(String backend, String backendVersion, String encoding, Mode mode) {
        this(backend, backendVersion, encoding, mode, ImageAccounting.UNKNOWN);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TokenizerMetadata)) return false;
        TokenizerMetadata that = (TokenizerMetadata) other;
        return java.util.Objects.equals(backend, that.backend) && java.util.Objects.equals(backendVersion, that.backendVersion) && java.util.Objects.equals(encoding, that.encoding) && java.util.Objects.equals(mode, that.mode) && java.util.Objects.equals(imageAccounting, that.imageAccounting);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(backend);
        hash = 31 * hash + java.util.Objects.hashCode(backendVersion);
        hash = 31 * hash + java.util.Objects.hashCode(encoding);
        hash = 31 * hash + java.util.Objects.hashCode(mode);
        hash = 31 * hash + java.util.Objects.hashCode(imageAccounting);
        return hash;
    }
    @Override public String toString() { return "TokenizerMetadata[backend=" + backend + ", backendVersion=" + backendVersion + ", encoding=" + encoding + ", mode=" + mode + ", imageAccounting=" + imageAccounting + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TokenizerMetadata> schema() {
            return new dev.openallay.value.ValueSchema<>(TokenizerMetadata.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TokenizerMetadata>>asList(new dev.openallay.value.ValueSchema.Component<>(TokenizerMetadata.class, "backend", TokenizerMetadata::backend), new dev.openallay.value.ValueSchema.Component<>(TokenizerMetadata.class, "backendVersion", TokenizerMetadata::backendVersion), new dev.openallay.value.ValueSchema.Component<>(TokenizerMetadata.class, "encoding", TokenizerMetadata::encoding), new dev.openallay.value.ValueSchema.Component<>(TokenizerMetadata.class, "mode", TokenizerMetadata::mode), new dev.openallay.value.ValueSchema.Component<>(TokenizerMetadata.class, "imageAccounting", TokenizerMetadata::imageAccounting)), arguments -> new TokenizerMetadata((String) arguments[0], (String) arguments[1], (String) arguments[2], (Mode) arguments[3], (ImageAccounting) arguments[4]));
        }
    }
}
