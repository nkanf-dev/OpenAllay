package dev.openallay.client.observation;
import dev.openallay.world.WorldFocusObservation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
/** Actual renderer-owned camera sample and GUI scaling. */
public final class MinecraftCameraFacts {
    private MinecraftCameraFacts() {}
    public static WorldFocusObservation.Camera focus(Minecraft client) { return ((GuideNativeCameraFov)client.entityRenderer).openallay$camera(); }
    public static WorldFocusObservation.Camera rendered(Minecraft client, WorldFocusObservation.Camera observed) { return focus(client); }
    public static int guiScale(Minecraft client) { return new ScaledResolution(client).getScaleFactor(); }
}
