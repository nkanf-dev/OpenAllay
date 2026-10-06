package dev.openallay.guide.e2e;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.openallay.client.gui.*;
import dev.openallay.client.gui.hud.GuideNativeToastBinding;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Disposable-profile fixture. Only real owner ticks and native paint/removal callbacks produce receipts. */
public final class GuideNativeEditorE2EProbe {
    public interface ToastReadback {
        void openallay$observePaint(Toast toast, float top);
        ToastSnapshot openallay$snapshot();
    }
    public record ToastSnapshot(List<Toast> queue, List<Boolean> occupied, Map<Toast, Float> tops,
            Map<Toast, Integer> removals, boolean pendingEmpty) {}
    private final Minecraft client;
    private final Map<String, Object> report;
    private GuideMultilineEditor editor;
    private GuideNativeMultilineEditor widget;
    private Screen owner;
    private String clipboard, draft;
    private int width, height, x, y, phase, originalLimit;
    private long frame;
    private GuideNativeMultilineEditor.ProbeReceipt narrow;
    private final FixtureOwned a = new FixtureOwned(), b = new FixtureOwned(), queued = new FixtureOwned();
    private final FixtureNormal normal = new FixtureNormal(), follower = new FixtureNormal();
    private ToastComponent manager;
    private ToastReadback readback;
    private boolean active;
    static GuideNativeEditorE2EProbe create(Minecraft client, String loader, String version, Map<String, Object> report) {
        return "forge".equals(loader) && "1.18.2".equals(version) && Boolean.getBoolean(GuideClientE2EConfig.ENABLED)
                ? new GuideNativeEditorE2EProbe(client, report) : null;
    }
    private GuideNativeEditorE2EProbe(Minecraft client, Map<String, Object> report) { this.client = client; this.report = report; }
    boolean started() { return editor != null; }
    void begin(Screen owner, GuideMultilineEditor editor) {
        this.owner = owner; this.editor = editor;
        check(editor.widget() instanceof GuideNativeMultilineEditor, "selected actual old editor");
        widget = (GuideNativeMultilineEditor) editor.widget();
        check(owner.getFocused() == widget && owner.children().contains(widget), "registered focused native owner");
        clipboard = client.keyboardHandler.getClipboard(); draft = editor.getValue();
        originalLimit = widget.e2eCharacterLimit(); active = true;
        width = widget.getWidth(); height = widget.getHeight(); x = widget.x; y = widget.y;
        editor.setValue(""); editor.setCharacterLimit(4); type("abcde"); check("abcd".equals(editor.getValue()), "character limit callback");
        editor.setCharacterLimit(Integer.MAX_VALUE); editor.setValue("");
        type("alpha 中文"); key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT); type("beta");
        String text = "alpha 中文\nbeta";
        check(text.equals(editor.getValue()), "committed character and Shift+Enter callbacks");
        key(GLFW.GLFW_KEY_A, control()); key(GLFW.GLFW_KEY_C, control());
        check(text.equals(client.keyboardHandler.getClipboard()), "actual OS clipboard copy");
        key(GLFW.GLFW_KEY_X, control()); check(editor.getValue().isEmpty(), "cut");
        key(GLFW.GLFW_KEY_Z, control()); check(text.equals(editor.getValue()), "undo cut");
        key(GLFW.GLFW_KEY_Y, control()); check(editor.getValue().isEmpty(), "redo cut");
        key(GLFW.GLFW_KEY_V, control()); check(text.equals(editor.getValue()), "paste actual clipboard");
        key(GLFW.GLFW_KEY_A, control()); editor.resize(40, height, x, y); frame = painted().frame();
    }
    boolean tick(Screen screen, GuideMultilineEditor current) {
        check(screen == owner && current == editor && current.widget() == widget, "same initialized owner/widget");
        if (phase == 0) {
            if (painted().frame() <= frame || painted().width() != 40) return false;
            narrow = painted(); check(narrow.start() == 0 && narrow.end() == editor.getValue().length(), "painted selection");
            frame = narrow.frame(); editor.resize(width, height, x, y); phase++; return false;
        }
        if (phase == 1) {
            if (painted().frame() <= frame || painted().width() != width) return false;
            var wide = painted(); check(wide.lines() < narrow.lines() && wide.start() == narrow.start()
                    && wide.end() == narrow.end() && wide.cursor() == narrow.cursor() && wide.focused(), "native font reflow retains selection/caret/focus");
            report.put("nativeEditorReflow", Map.of("narrow", narrow, "wide", wide, "widgetIdentity", System.identityHashCode(widget)));
            type("Q"); key(GLFW.GLFW_KEY_Z, control()); check("alpha 中文\nbeta".equals(editor.getValue()), "selection replacement undo");
            editor.setValue("replacement"); key(GLFW.GLFW_KEY_Z, control());
            check("replacement".equals(editor.getValue()), "external full replacement resets history");
            check(widget.mouseClicked(x + 4, y + 5, 0), "native mouse click");
            check(widget.mouseDragged(x + 4 + client.font.width("replace"), y + 5, 0, client.font.width("replace"), 0), "native mouse drag");
            widget.mouseReleased(x + 4 + client.font.width("replace"), y + 5, 0);
            frame = wide.frame(); phase++; return false;
        }
        if (phase == 2) {
            if (painted().frame() <= frame) return false;
            check(painted().start() == 0 && painted().end() == 7, "actual native drag selection paint");
            report.put("nativeEditorCallbacks", Map.of("paint", painted(), "text", editor.getValue(),
                    "clipboardCopyCutPaste", true, "undoRedo", true, "externalReplacement", true,
                    "proof", "committed-character callbacks; no hardware IME claim"));
            editor.setCharacterLimit(originalLimit); editor.setValue(draft); client.keyboardHandler.setClipboard(clipboard);
            check(clipboard.equals(client.keyboardHandler.getClipboard()), "clipboard restored");
            manager = MinecraftClientWindow.toastManager(client); readback = (ToastReadback) manager;
            check(readback.openallay$snapshot().queue().isEmpty() && readback.openallay$snapshot().occupied().stream().noneMatch(Boolean::booleanValue), "isolated native toast fixture");
            manager.addToast(a); manager.addToast(b); manager.addToast(normal); phase++; return false;
        }
        ToastSnapshot s = readback.openallay$snapshot();
        if (phase == 3) {
            if (a.frames == 0 || b.frames == 0 || normal.frames == 0) return false;
            check(s.occupied().stream().allMatch(Boolean::booleanValue), "native 2+2+1 five-slot capacity");
            check(s.tops().get(a) == 0 && s.tops().get(b) == 64 && s.tops().get(normal) == 128, "actual native matrix positions without overlap/overflow");
            report.put("nativeToastMixed", facts(s));
            manager.addToast(queued); manager.addToast(follower); normal.hidden = true; phase++; return false;
        }
        if (phase == 4) {
            if (!s.removals().containsKey(normal)) return false;
            check(s.occupied().equals(List.of(true, true, true, true, false)) && s.queue().equals(List.of(queued, follower))
                    && queued.frames == 0 && follower.frames == 0, "64px FIFO head waits at one-slot tail");
            report.put("nativeToastTailWait", facts(s)); a.hidden = true; phase++; return false;
        }
        if (phase == 5) {
            if (queued.frames == 0 || follower.frames == 0) return false;
            check(a.completions == 1 && s.removals().get(a) == 1 && s.removals().get(normal) == 1
                    && s.queue().isEmpty() && s.tops().get(queued) == 0 && s.tops().get(follower) == 128, "native removal once and FIFO capacity reuse");
            report.put("nativeToastReuse", facts(s)); manager.clear(); phase++; return false;
        }
        s = readback.openallay$snapshot();
        check(s.queue().isEmpty() && s.occupied().stream().noneMatch(Boolean::booleanValue)
                && s.tops().isEmpty() && s.removals().isEmpty() && s.pendingEmpty()
                && manager.getToast(FixtureOwned.class, Toast.NO_TOKEN) == null
                && manager.getToast(FixtureNormal.class, Toast.NO_TOKEN) == null, "native clear drops references");
        report.put("nativeToastClear", facts(s)); close(); return true;
    }
    private Map<String, Object> facts(ToastSnapshot s) {
        return Map.of("occupied", s.occupied(), "queuedCount", s.queue().size(), "pendingEmpty", s.pendingEmpty(),
                "frames", List.of(a.frames, b.frames, normal.frames, queued.frames, follower.frames),
                "ownedCompletions", List.of(a.completions, b.completions, queued.completions),
                "actualNativeTops", List.of(s.tops().getOrDefault(a, -1F), s.tops().getOrDefault(b, -1F),
                        s.tops().getOrDefault(normal, -1F), s.tops().getOrDefault(queued, -1F), s.tops().getOrDefault(follower, -1F)),
                "setup", "dev-only native manager fixtures; not accepted UI actions");
    }
    private GuideNativeMultilineEditor.ProbeReceipt painted() {
        var receipt = widget.e2eReceipt(); check(receipt != null, "actual editor paint before input"); return receipt;
    }
    private int control() { return Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL; }
    private void key(int key, int modifiers) { check(widget.keyPressed(key, 0, modifiers), "native key callback " + key); }
    private void type(String text) { for (char c : text.toCharArray()) check(widget.charTyped(c, 0), "native character callback"); }
    void close() {
        if (!active) return;
        editor.resize(width, height, x, y); editor.setCharacterLimit(originalLimit); editor.setValue(draft);
        client.keyboardHandler.setClipboard(clipboard);
        report.put("nativeEditorClipboardRestored", clipboard.equals(client.keyboardHandler.getClipboard()));
        report.put("nativeEditorStateRestored", editor.getValue().equals(draft)
                && widget.e2eCharacterLimit() == originalLimit && widget.getWidth() == width
                && widget.getHeight() == height && widget.x == x && widget.y == y);
        if (manager != null) manager.clear(); active = false;
    }
    private static void check(boolean condition, String fact) { if (!condition) throw new IllegalStateException(fact); }
    private static final class FixtureNormal implements Toast {
        int frames; boolean hidden;
        @Override public Visibility render(PoseStack pose, ToastComponent manager, long visible) {
            frames++; GuideGraphics.wrap(pose).fill(0, 0, 160, 32, 0xFF334455);
            return hidden ? Visibility.HIDE : Visibility.SHOW;
        }
    }
    private static final class FixtureOwned extends GuideNativeToastBinding {
        int frames, completions; boolean hidden;
        @Override public int height() { return 64; }
        @Override public boolean finished() { return completions > 0; }
        @Override public void onFinishedRendering() { completions++; }
        @Override protected boolean guideToastActive() { return true; }
        @Override protected int guideSlotCount() { return 2; }
        @Override protected Visibility guideWantedVisibility() { return hidden ? Visibility.HIDE : Visibility.SHOW; }
        @Override protected void updateGuideToast(long visible) {}
        @Override protected void paintGuideToast(GuideGraphics graphics, Font font, long visible) {
            frames++; graphics.fill(0, 0, 160, 64, 0xFF556677);
        }
    }
}
