package dev.openallay.adapter.minecraft.v26_2.world;

import com.google.gson.JsonObject;

/** Detached native material facts for the opt-in server-owner acceptance oracle. */
public final class NativeBuilderPaletteProbe {
    private NativeBuilderPaletteProbe() {}
    public static JsonObject capture() {
        try { return NativeBlockCodec.materialPalette(); }
        finally { NativeBlockCodec.releasePalette(); }
    }
}
