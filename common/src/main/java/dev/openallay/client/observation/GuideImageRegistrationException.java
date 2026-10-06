package dev.openallay.client.observation;

/** A failed registration whose selected native binding has already retired image custody. */
public final class GuideImageRegistrationException extends RuntimeException {
    public GuideImageRegistrationException(Throwable cause) { super("Native image registration failed after image custody was retired", cause); }
}
