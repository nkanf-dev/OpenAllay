package dev.openallay.guide.e2e;

import com.google.gson.JsonObject;

/** Typed, opt-in native oracle port. Common does not depend on the game adapter. */
public final class NativeMaterialPaletteOracle {
    @FunctionalInterface
    public interface Capture { JsonObject capture(); }
    private static volatile Capture nativeCapture;
    private NativeMaterialPaletteOracle() {}
    public static synchronized void registerOnce(Capture capture) {
        if(!Boolean.getBoolean("openallay.e2e.enabled"))return;
        if(nativeCapture==null)nativeCapture=java.util.Objects.requireNonNull(capture,"capture");
    }
    static JsonObject capture() {
        if(!Boolean.getBoolean("openallay.e2e.enabled"))
            throw new IllegalStateException("Native material oracle requires the explicit E2E profile");
        Capture capture=nativeCapture;
        if(capture==null)throw new IllegalStateException("Native material oracle was not registered by the client loader");
        return java.util.Objects.requireNonNull(capture.capture(),"Native material oracle returned no palette");
    }
}
