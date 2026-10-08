package dev.openallay.client.voice;

import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.CredentialResolver;
import dev.openallay.model.config.SecretValue;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Credential adapter for the canonical cancellable multipart speech endpoint. Never retries. */
public final class HttpSpeechToText implements SpeechToText {
    private final CredentialReference credential;
    private final CredentialResolver resolver;
    private final VoiceHttpSpeechEndpoint endpoint;

    public HttpSpeechToText(URI baseUrl, String model, CredentialReference credential,
            CredentialResolver resolver, Duration timeout) {
        endpoint = new VoiceHttpSpeechEndpoint(baseUrl, model, timeout);
        this.credential = credential;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override public Result transcribe(Request request, VoiceCancellation cancellation) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        cancellation.check();
        if (!request.language().matches("auto|[a-z]{2,3}(-[A-Z]{2})?")) {
            throw new Failure("voice_invalid_request", null);
        }
        String authorization = authorize();
        cancellation.check();
        try { return endpoint.transcribe(request, cancellation, authorization); }
        catch (VoiceHttpSpeechEndpoint.Failure failure) { throw new Failure(failure.code(), failure.httpStatus()); }
    }

    private String authorize() throws Failure {
        if (credential == null) return null;
        try {
            ToolResult<SecretValue> resolved = resolver.resolve(credential);
            if (!(resolved instanceof ToolResult.Success)) throw new Failure("voice_credential_unavailable", null);
            // Reveal only at the outbound boundary, never in settings or diagnostics.
            String value = ((ToolResult.Success<SecretValue>) resolved).value().reveal();
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new Failure("voice_credential_unavailable", null);
            }
            return "Bearer " + value;
        } catch (RuntimeException failure) { throw new Failure("voice_credential_unavailable", null); }
    }

    /** Preserves the public typed diagnostics of the credential-facing adapter. */
    public static final class Failure extends VoiceHttpSpeechEndpoint.Failure {
        private static final long serialVersionUID = 1L;
        private Failure(String code, Integer status) { super(code, status); }
    }
}
