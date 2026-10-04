package dev.openallay.api.extension;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.Instant;

/** Immutable detached evidence mapped by the core to its existing evidence sink. */
public final class ExtensionEvidence {
    private final Authority authority;
    private final Completeness completeness;
    private final Instant capturedAt;
    private final String sourceId;
    private final String provenance;
    private final String gameVersion;
    private final String loader;
    private final Map<String, String> details;

    public ExtensionEvidence(
            Authority authority,
            Completeness completeness,
            Instant capturedAt,
            String sourceId,
            String provenance,
            String gameVersion,
            String loader,
            Map<String, String> details) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.completeness = Objects.requireNonNull(completeness, "completeness");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        this.sourceId = ApiValidation.id(sourceId, "source ID");
        this.provenance = ApiValidation.id(provenance, "provenance");
        this.gameVersion = ApiValidation.version(gameVersion, "game version");
        this.loader = ApiValidation.loader(loader);
        this.details = requireDetails(details);
    }

    public Authority authority() { return authority; }
    public Completeness completeness() { return completeness; }
    public Instant capturedAt() { return capturedAt; }
    public String sourceId() { return sourceId; }
    public String provenance() { return provenance; }
    public String gameVersion() { return gameVersion; }
    public String loader() { return loader; }
    public Map<String, String> details() { return details; }

    public enum Authority { CLIENT_VISIBLE, SERVER_AUTHORITATIVE, RESOURCE_ASSET, INTEGRATION_API, DETERMINISTIC_TEST }
    public enum Completeness { COMPLETE, PARTIAL, UNKNOWN }
    private static Map<String, String> requireDetails(Map<String, String> details) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : Objects.requireNonNull(details, "details").entrySet())
            copy.put(ApiValidation.id(entry.getKey(), "detail key"), ApiValidation.text(entry.getValue(), "detail value"));
        return Collections.unmodifiableMap(copy);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionEvidence)) return false;
        ExtensionEvidence that = (ExtensionEvidence) other;
        return Objects.equals(authority, that.authority) &&
                Objects.equals(completeness, that.completeness) &&
                Objects.equals(capturedAt, that.capturedAt) &&
                Objects.equals(sourceId, that.sourceId) &&
                Objects.equals(provenance, that.provenance) &&
                Objects.equals(gameVersion, that.gameVersion) &&
                Objects.equals(loader, that.loader) &&
                Objects.equals(details, that.details);
    }
    @Override public int hashCode() { return Objects.hash(authority, completeness, capturedAt, sourceId, provenance, gameVersion, loader, details); }
}
