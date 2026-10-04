package dev.openallay.client.observation;

import dev.openallay.client.gui.MinecraftClientWindow;
import dev.openallay.world.WorldFocusObservation;
import net.minecraft.client.Minecraft;

/** Native camera/frame numbers are detached at this boundary. */
public final class MinecraftCameraFacts {
    private MinecraftCameraFacts() {}
    public static WorldFocusObservation.Camera focus(Minecraft client) {
        var camera = MinecraftClientWindow.camera(client);
        var position = camera.position();
        var entity = camera.entity();
        return new WorldFocusObservation.Camera(position.x(), position.y(), position.z(),
                camera.yRot(), camera.xRot(), camera.getFov(),
                client.options.getCameraType().name().toLowerCase(java.util.Locale.ROOT),
                camera.isInitialized(), camera.isDetached(), entity == null ? null : entity.getUUID());
    }
    public static WorldFocusObservation.Camera rendered(Minecraft client, WorldFocusObservation.Camera observed) {
        var camera = MinecraftClientWindow.renderState(client).levelRenderState.cameraRenderState;
        return new WorldFocusObservation.Camera(camera.pos.x(), camera.pos.y(), camera.pos.z(),
                camera.yRot, camera.xRot, observed.fov(), observed.mode(), camera.initialized,
                observed.detached(), observed.entityUuid());
    }
    public static int guiScale(Minecraft client) {
        return MinecraftClientWindow.renderState(client).windowRenderState.guiScale;
    }
}
