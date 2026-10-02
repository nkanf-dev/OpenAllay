package dev.openallay.client.voice;

/** Screen/HUD hooks. Recording cancellation never calls the agent task Stop action. */
public interface VoiceInputActions {
    boolean enabled();
    VoiceRuntime.Status status();
    void press();
    void release();
    void cancel(VoiceRuntime.CancelReason reason);
}
