package dev.openallay.guide.e2e;

import dev.openallay.client.gui.GuideMultilineEditor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** The 1.18-only primitive/toast fixture is not selected for this native family. */
final class GuideNativeEditorE2EProbe {
    static GuideNativeEditorE2EProbe create(Minecraft client, String loader, String gameVersion,
            Map<String, Object> report) { return null; }
    boolean started() { return false; }
    void begin(GuiScreen owner, GuideMultilineEditor editor) {}
    boolean tick(GuiScreen owner, GuideMultilineEditor editor) { return true; }
    void close() {}
}
