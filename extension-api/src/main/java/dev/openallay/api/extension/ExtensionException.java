package dev.openallay.api.extension;

/** Stable Extension failure code and player-safe summary; diagnostic causes remain host-owned. */
public final class ExtensionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String summary;
    public ExtensionException(String code, String summary) { this(code, summary, null); }
    public ExtensionException(String code, String summary, Throwable cause) {
        super(ApiValidation.text(summary, "summary"), cause);
        if (code == null || !code.matches("[a-z][a-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid Extension failure code: " + code);
        this.code = code;
        this.summary = summary;
    }
    public String code() { return code; }
    public String summary() { return summary; }
}
