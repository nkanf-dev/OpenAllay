package dev.openallay.net;

import dev.openallay.util.Java8Strings;
import java.time.Duration;
import java.util.Objects;

/** Shared connection and decoder policy selected by a domain adapter. */
public final class HttpTransportPolicy {
    private final Duration connectTimeout;
    private final String decoderThreadName;

    public HttpTransportPolicy(Duration connectTimeout, String decoderThreadName) {
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || decoderThreadName == null || Java8Strings.isBlank(decoderThreadName)) {
            throw new IllegalArgumentException("invalid HTTP transport policy");
        }
        this.connectTimeout = connectTimeout;
        this.decoderThreadName = decoderThreadName;
    }

    public Duration connectTimeout() { return connectTimeout; }
    public String decoderThreadName() { return decoderThreadName; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HttpTransportPolicy)) return false;
        HttpTransportPolicy that = (HttpTransportPolicy) other;
        return connectTimeout.equals(that.connectTimeout) && decoderThreadName.equals(that.decoderThreadName);
    }
    @Override public int hashCode() {
        return 31 * connectTimeout.hashCode() + decoderThreadName.hashCode();
    }
    @Override public String toString() {
        return "HttpTransportPolicy[connectTimeout=" + connectTimeout
                + ", decoderThreadName=" + decoderThreadName + "]";
    }
}
