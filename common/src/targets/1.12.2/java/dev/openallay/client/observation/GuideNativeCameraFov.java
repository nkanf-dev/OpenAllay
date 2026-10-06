package dev.openallay.client.observation;
import dev.openallay.world.WorldFocusObservation;
public interface GuideNativeCameraFov {
    void openallay$beginFrame();
    double openallay$observedFov();
    WorldFocusObservation.Camera openallay$camera();
}
