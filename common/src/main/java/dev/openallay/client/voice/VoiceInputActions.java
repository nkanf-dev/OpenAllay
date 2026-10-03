package dev.openallay.client.voice;

/** Screen/HUD hooks. Recording cancellation never calls the agent task Stop action. */
public interface VoiceInputActions {
    boolean enabled();
    VoiceRuntime.Status status();
    /** Fullscreen/mic input always adds to the editable draft. */
    void press();
    /** Explicit external PTT from gameplay or Lite/HUD uses the captured gameplay choice. */
    default void pressPtt() { press(); }
    /** Lite/HUD hold-to-talk owns release through native screen callbacks, not gameplay key state. */
    default void pressExternalPtt() { pressPtt(); }
    void release();
    void cancel(VoiceRuntime.CancelReason reason);
}
