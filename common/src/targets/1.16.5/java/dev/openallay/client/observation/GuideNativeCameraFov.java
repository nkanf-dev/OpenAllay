package dev.openallay.client.observation;

import net.minecraft.client.Camera;

/** Actual renderer-computed FOV, sampled only from its native camera calculation. */
public interface GuideNativeCameraFov {
    void openallay$beginFrame();
    double openallay$observedFov(Camera camera);
}
