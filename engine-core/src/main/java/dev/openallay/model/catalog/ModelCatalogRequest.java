package dev.openallay.model.catalog;

import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.ModelProtocol;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Credential-free configuration-layer request for one provider model catalog. */
@dev.openallay.value.ValueType(ModelCatalogRequest.ValueSchemaProvider.class)
public final class ModelCatalogRequest {
    private final String profileId;
    private final ModelProtocol protocol;
    private final URI baseUri;
    private final String credentialRef;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    public ModelCatalogRequest(String profileId, ModelProtocol protocol, URI baseUri, String credentialRef, Duration connectTimeout, Duration requestTimeout) {

        if (profileId == null || !profileId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid model profile id");
        }
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(baseUri, "baseUri");
        validateUri(baseUri);
        String raw = baseUri.toString();
        baseUri = URI.create(raw.endsWith("/") ? raw : raw + "/");
        credentialRef = CredentialReference.parse(credentialRef).encoded();
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("model catalog timeouts must be positive");
        }

        this.profileId = profileId;
        this.protocol = protocol;
        this.baseUri = baseUri;
        this.credentialRef = credentialRef;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }
    public String profileId() { return profileId; }
    public ModelProtocol protocol() { return protocol; }
    public URI baseUri() { return baseUri; }
    public String credentialRef() { return credentialRef; }
    public Duration connectTimeout() { return connectTimeout; }
    public Duration requestTimeout() { return requestTimeout; }
@Override
    public String toString() {
        return "ModelCatalogRequest[profileId=" + profileId + ", protocol=" + protocol + "]";
    }
private static void validateUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]");
        if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
            throw new IllegalArgumentException("Remote model catalog endpoints require HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Model catalog base URL must not contain credentials, query, or fragment");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelCatalogRequest)) return false;
        ModelCatalogRequest that = (ModelCatalogRequest) other;
        return java.util.Objects.equals(profileId, that.profileId) && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(baseUri, that.baseUri) && java.util.Objects.equals(credentialRef, that.credentialRef) && java.util.Objects.equals(connectTimeout, that.connectTimeout) && java.util.Objects.equals(requestTimeout, that.requestTimeout);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(profileId);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(baseUri);
        hash = 31 * hash + java.util.Objects.hashCode(credentialRef);
        hash = 31 * hash + java.util.Objects.hashCode(connectTimeout);
        hash = 31 * hash + java.util.Objects.hashCode(requestTimeout);
        return hash;
    }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelCatalogRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelCatalogRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelCatalogRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelCatalogRequest.class, "profileId", ModelCatalogRequest::profileId), new dev.openallay.value.ValueSchema.Component<>(ModelCatalogRequest.class, "protocol", ModelCatalogRequest::protocol), new dev.openallay.value.ValueSchema.Component<>(ModelCatalogRequest.class, "baseUri", ModelCatalogRequest::baseUri), new dev.openallay.value.ValueSchema.Component<>(ModelCatalogRequest.class, "credentialRef", ModelCatalogRequest::credentialRef), new dev.openallay.value.ValueSchema.Component<>(ModelCatalogRequest.class, "connectTimeout", ModelCatalogRequest::connectTimeout), new dev.openallay.value.ValueSchema.Component<>(ModelCatalogRequest.class, "requestTimeout", ModelCatalogRequest::requestTimeout)), arguments -> new ModelCatalogRequest((String) arguments[0], (ModelProtocol) arguments[1], (URI) arguments[2], (String) arguments[3], (Duration) arguments[4], (Duration) arguments[5]));
        }
    }
}
