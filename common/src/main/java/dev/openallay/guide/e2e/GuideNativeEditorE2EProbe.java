package dev.openallay.guide.e2e;

import dev.openallay.client.gui.GuideMultilineEditor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The old primitive has a selected-target acceptance driver; other native UI cases are unchanged. */
final class GuideNativeEditorE2EProbe {
    static GuideNativeEditorE2EProbe create(Minecraft client, String loader, String gameVersion,
            Map<String, Object> report) { return null; }
    boolean started() { return false; }
    void begin(Screen owner, GuideMultilineEditor editor) {}
    boolean tick(Screen owner, GuideMultilineEditor editor) { return true; }
    void close() {}
}
