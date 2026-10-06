package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Native boundary contracts without constructing a client or changing saved controls. */
final class GuideHudLatestContractsTest {
    @Test void boundFooterKeepsTheNativeTranslatedKeyComponentInsteadOfAnAssumedDefault() {
        Component custom = Component.translatable("key.keyboard.g");
        var contents = (TranslatableContents) GuideHudRenderer.readHint(false, custom).getContents();
        assertEquals("screen.openallay.hud.read", contents.getKey());
        assertEquals(1, contents.getArgs().length);
        assertSame(custom, contents.getArgs()[0]);
        assertEquals(GuideHudRenderer.readHint(false, custom),
                GuideHudRenderer.readHintTooltip(false, custom, Component.literal("K")));
    }

    @Test void explicitlyUnboundFooterNeverLeaksTheRawUnknownKeyAndOffersControlsAndFullscreen() {
        Component unknown = Component.translatable("key.keyboard.unknown");
        Component openGuide = Component.translatable("key.keyboard.k");
        Component hint = GuideHudRenderer.readHint(true, unknown);
        var caption = (TranslatableContents) hint.getContents();
        assertEquals("screen.openallay.hud.view_unbound", caption.getKey());
        assertEquals(0, caption.getArgs().length);
        Component tooltip = GuideHudRenderer.readHintTooltip(true, unknown, openGuide);
        assertEquals(hint.getContents(), tooltip.getContents());
        assertEquals(2, tooltip.getSiblings().size());
        assertEquals("\n", tooltip.getSiblings().getFirst().getString());
        var reminder = (TranslatableContents) tooltip.getSiblings().getLast().getContents();
        assertEquals("screen.openallay.hud.bind_controls", reminder.getKey());
        assertSame(openGuide, reminder.getArgs()[0]);
        assertEquals(0, hint.getSiblings().size(), "the tooltip must not mutate the concise footer caption");
    }

    @Test void whenBothEntryKeysAreUnboundTheReminderOnlyOffersReachableControls() {
        Component tooltip = GuideHudRenderer.readHintTooltip(true, Component.translatable("key.keyboard.unknown"), null);
        var caption = (TranslatableContents) tooltip.getContents();
        var reminder = (TranslatableContents) tooltip.getSiblings().getLast().getContents();
        assertEquals("screen.openallay.hud.view_unbound", caption.getKey());
        assertEquals("screen.openallay.hud.bind_controls_only", reminder.getKey());
        assertEquals(0, reminder.getArgs().length, "no fake K shortcut or raw Unknown key is offered");
    }

    @Test void f8IsOnlyARegistrationDefaultAndNeverAResetOfThePlayersNativePreference() throws Exception {
        String keys = source("common/src/main/java/dev/openallay/client/gui/OpenAllayKeyMappings.java");
        int start = keys.indexOf("INTERACT_HUD =");
        int end = keys.indexOf("VOICE_PTT =", start);
        String interaction = keys.substring(start, end);
        assertTrue(interaction.contains("GuideNativeKeyMappings.create("));
        assertTrue(interaction.contains("\"key.openallay.interact_hud\", dev.openallay.client.gui.GuideInputCodes.KEY_F8"));
        assertFalse(interaction.contains("unbound("));
        String nativeKeys = source("common/src/main/java/dev/openallay/client/gui/GuideNativeKeyMappings.java");
        assertTrue(nativeKeys.contains("new KeyMapping(name, key, CATEGORY)"));
        assertTrue(keys.contains("return GuideNativeKeyMappings.unbound(name)"));
        assertTrue(nativeKeys.contains("new KeyMapping(name, GuideNativeInput.keyboardType(), InputConstants.UNKNOWN.getValue(), CATEGORY)"));
        for (String mutation : new String[] {".setKey(", ".setDown(", "KeyMapping.resetMapping", "options.save("}) {
            assertFalse((keys + nativeKeys).contains(mutation), mutation);
        }
        assertTrue(keys.contains("OPEN_GUIDE = GuideNativeKeyMappings.create("));
        assertTrue(keys.contains("\"key.openallay.open_guide\", dev.openallay.client.gui.GuideInputCodes.KEY_K"));
        String nativeCodes = source("common/src/main/java/dev/openallay/client/gui/GuideInputCodes.java");
        assertTrue(nativeCodes.contains("KEY_F8 = GLFW.GLFW_KEY_F8"));
        assertTrue(nativeCodes.contains("KEY_K = GLFW.GLFW_KEY_K"));
        assertTrue(keys.contains("VOICE_PTT = unbound(\"key.openallay.voice_ptt\")"));
    }

    @Test void passiveDrawUsesMeasuredTailWithoutTimeBasedPagesOrInventedTinyBodyText() throws Exception {
        String renderer = source("common/src/main/java/dev/openallay/client/gui/hud/GuideHudRenderer.java");
        assertTrue(renderer.contains("results.scroll().latest()"));
        assertTrue(renderer.contains("body, results.scroll().maximum()"));
        assertTrue(renderer.indexOf("results.prepare(view") < renderer.indexOf("results.scroll().latest()"));
        assertTrue(renderer.contains("view.rows().isEmpty() && bodyHeight >= 10"));
        assertTrue(renderer.contains("previewLines.size() - shown"));
        assertTrue(renderer.contains("previewLines.get(first + line)"));
        for (String obsolete : new String[] {"passivePageOffset", "passivePageCount", "pageRows", "pageStartedAt", "pageTicks",
                "screen.openallay.hud.pages"}) assertFalse(renderer.contains(obsolete), obsolete);
        assertTrue(renderer.contains("GuideTooltipPlacement.DEFAULT"));
        assertTrue(renderer.contains("GuideNativeFont.split(font, tooltip"));
        assertTrue(renderer.contains("Math.min(260, graphics.guiWidth() - 24)"));
        assertTrue(renderer.contains("plainSubstrByWidth(font, MinecraftComponents.getString(footer)"));
        assertTrue(renderer.contains("!dev.openallay.client.context.MinecraftMouseCoordinates.grabbed(minecraft)"));
        String nativeMouse = source("common/src/main/java/dev/openallay/client/context/MinecraftMouseCoordinates.java");
        assertTrue(nativeMouse.contains("grabbed(net.minecraft.client.Minecraft client) { return client.mouseHandler.isMouseGrabbed(); }"));
        String nativeFont = source("common/src/main/java/dev/openallay/client/gui/GuideNativeFont.java");
        assertTrue(nativeFont.contains("return lines(font.split(text, width))"));
        for (String ownership : new String[] {"releaseMouse(", "grabMouse(", "setScreen("}) {
            assertFalse(renderer.contains(ownership), ownership);
        }
    }

    @Test void interactiveProjectionIsReadOnlyAndDoesNotFetchBeyondItsAdmittedWindow() throws Exception {
        String presenter = source("engine-core/src/main/java/dev/openallay/guide/ui/hud/GuideHudPresenter.java");
        assertTrue(presenter.contains("for (GuideRequestSnapshot request : selected.requests())"));
        assertTrue(presenter.contains("retained.add(result.requestId())"));
        assertTrue(presenter.contains("admitted.indexOf(active)"));
        assertFalse(presenter.contains(".sorted("), "the admitted request sequence is authoritative, not clock/UUID order");
        for (String effect : new String[] {"GuideService", "Files.", "requestHistoryWindow(", "history.page", "capture(", ".ask("}) {
            assertFalse(presenter.contains(effect), effect);
        }
        assertFalse(presenter.contains("MAX_PREVIEW_CODE_POINTS"));
    }

    private static String source(String relative) throws Exception {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root unavailable");
        return Files.readString(path.resolve(relative));
    }
}
