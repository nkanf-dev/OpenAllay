package dev.openallay.client.context;

import net.minecraft.client.Options;

/** Selected native public options report and render-distance facts. */
public final class MinecraftClientOptionsFacts {
    private MinecraftClientOptionsFacts() {}
    public static String report(Options options) { return options.dumpOptionsForReport(); }
    public static int renderDistance(Options options) { return options.getEffectiveRenderDistance(); }
    public static String reportDiagnostic() { return "Vanilla options and key mappings are complete for the public report; mod-owned configuration screens require explicit public adapters"; }
}
