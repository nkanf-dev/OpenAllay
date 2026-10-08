package dev.openallay.client.gui;

/** Client-owned native teardown observation used for frame admission. */
public interface MinecraftTeardownState {
    boolean openallay$teardownInProgress();
}
