package dev.openallay.bridge.protocol;

public final class BridgeProtocol {
    /** Raw bytes per Base64 chunk, leaving room below Minecraft's 32,767-char string cap. */
    public static final int TRANSPORT_CHUNK_BYTES = 16 * 1024;
    /** Serialized application JSON ceiling; native providers also validate their own JSON bodies. */
    public static final int MAX_OPENAI_REQUEST_BYTES = 50_000_000;
    public static final int MAX_ANTHROPIC_REQUEST_BYTES = 32_000_000;
    public static final int MAX_REQUEST_CHUNK_JSON_BYTES = 24 * 1024;
    public static final int MAX_REQUEST_CHUNK_BASE64_CHARS =
            ((TRANSPORT_CHUNK_BYTES + 2) / 3) * 4;
    public static final int MAX_REQUEST_CHUNKS =
            (MAX_OPENAI_REQUEST_BYTES + TRANSPORT_CHUNK_BYTES - 1) / TRANSPORT_CHUNK_BYTES;
    public static final java.time.Duration PARTIAL_ASSEMBLY_TIMEOUT =
            java.time.Duration.ofMinutes(5);

    private BridgeProtocol() {}
}
