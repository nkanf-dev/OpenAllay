package dev.openallay.net;

import java.io.IOException;

/** Canonical full-exchange timeout, available on every supported Java runtime. */
public final class HttpTimeoutException extends IOException {
    private static final long serialVersionUID = 1L;

    public HttpTimeoutException(String message) { super(message); }
}
