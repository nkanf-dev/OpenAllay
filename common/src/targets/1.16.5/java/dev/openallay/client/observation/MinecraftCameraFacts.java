package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;
import dev.openallay.client.gui.GuideNativeFrameTiming;
import dev.openallay.client.observation.GuideNativeCameraFov;
import dev.openallay.world.WorldFocusObservation;
import net.minecraft.client.Minecraft;

/** Minecraft 1.21/1.21.1 camera family, using the actual renderer-computed camera FOV. */
public final class MinecraftCameraFacts {
    private MinecraftCameraFacts() {}
    public static WorldFocusObservation.Camera focus(Minecraft client) {
        var camera = MinecraftClientWindow.camera(client);
        var position = camera.getPosition();
        var entity = camera.getEntity();
        float fov = (float) ((GuideNativeCameraFov) client.gameRenderer).openallay$observedFov(camera);
        return new WorldFocusObservation.Camera(position.x(), position.y(), position.z(),
                camera.getYRot(), camera.getXRot(), fov,
                client.options.getCameraType().name().toLowerCase(java.util.Locale.ROOT),
                camera.isInitialized(), camera.isDetached(), entity == null ? null : entity.getUUID());
    }
    public static WorldFocusObservation.Camera rendered(Minecraft client, WorldFocusObservation.Camera observed) {
        return focus(client);
    }
    public static int guiScale(Minecraft client) { return (int) client.getWindow().getGuiScale(); }
}
