package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiLayout;
import dev.openallay.guide.ui.hud.GuideHudView;
import dev.openallay.guide.ui.hud.GuideHudVisibility;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.openallay.client.gui.GuideTooltipPlacement;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;

/** Passive extraction only. No Screen, input ownership, task, history, or context operations. */
public final class GuideHudRenderer {
    private final Minecraft minecraft;
    private final GuideHudResultRenderer results = new GuideHudResultRenderer();
    private CacheKey cacheKey;
    private List<GuideTextLine> previewLines = List.of();

    public GuideHudRenderer(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }
    public GuideHudResultRenderer.Receipt resultReceipt() { return results.receipt(); }

    public void extractRenderState(GuideGraphics graphics, GuideHudView view) {
        var context = new GuideHudVisibility.Context(minecraft.level != null, minecraft.player != null,
                MinecraftClientWindow.hudHidden(minecraft), MinecraftClientWindow.debugScreenVisible(minecraft),
                MinecraftClientWindow.screen(minecraft) != null, MinecraftClientWindow.overlay(minecraft) != null);
        if (MinecraftClientWindow.screen(minecraft) instanceof GuideHudEditorScreen
                || MinecraftClientWindow.screen(minecraft) instanceof GuideChatLiteScreen) { results.releaseNativeViews(); return; }
        if (GuideHudVisibility.isVisible(view.hud(), context)) draw(graphics, view, view.hud());
        else results.releaseNativeViews();
    }
    public void extractPreview(GuideGraphics graphics, GuideHudView view, GuideUiConfig.Hud hud) {
        draw(graphics, view, hud.withCollapsed(false));
    }
    public void invalidateLayout() { cacheKey = null; results.invalidate(); }

    private void draw(GuideGraphics graphics, GuideHudView view, GuideUiConfig.Hud hud) {
        GuideHudLayout.Rect rect = GuideHudLayout.calculate(graphics.guiWidth(), graphics.guiHeight(), hud);
        Font font = minecraft.font;
        int contentWidth = rect.contentWidth();
        int contentHeight = hud.collapsed() ? Math.min(24, rect.contentHeight()) : rect.contentHeight();
        int available = Math.max(0, contentHeight - 52);
        int bodyHeight = hud.maxReplyLines() == 0 ? available : Math.min(available, hud.maxReplyLines() * 11);
        GuideUiLayout.Rect body = new GuideUiLayout.Rect(8, 36, Math.max(1, contentWidth - 16), bodyHeight);
        long ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        Component footer = null;
        graphics.pushPose();
        try {
            graphics.translatePose((float) rect.x(), (float) rect.y());
            graphics.scalePose((float) rect.scale(), (float) rect.scale());
            graphics.enableScissor(0, 0, contentWidth, contentHeight);
            try {
                int rgb = view.presentation().theme() == GuideUiConfig.Theme.MINT ? 0x172A27 : OpenAllayWidgetTheme.CHARCOAL;
                graphics.fill(0, 0, contentWidth, contentHeight, hud.backgroundArgb(rgb));
                graphics.outline(0, 0, contentWidth, contentHeight, backgroundBorderColor(hud.backgroundOpacity()));
                String title = view.assistantName() + (view.selectedSession().isBlank() ? "" : " · " + view.selectedSession());
                graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, title, Math.max(1, contentWidth - 16)), 8, 7, textColor());
                if (hud.collapsed()) { results.releaseNativeViews(); return; }
                Component status = view.progress() == null ? MinecraftComponents.translatable("screen.openallay.hud.idle")
                        : MinecraftComponents.translatable(view.progress().activityTranslationKey());
                if (view.otherRunningTasks() > 0) status = MinecraftComponents.append(MinecraftComponents.copy(status), MinecraftComponents.translatable(
                        "screen.openallay.hud.other_tasks", view.otherRunningTasks()));
                graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, MinecraftComponents.getString(status), Math.max(1, contentWidth - 16)),
                        8, 22, view.progress() == null ? OpenAllayWidgetTheme.MUTED : OpenAllayWidgetTheme.MINT);
                if (!view.rows().isEmpty() && bodyHeight > 0) {
                    results.prepare(view, font, Math.max(1, body.width() - 6), body.height());
                    // Passive draw owns follow-latest. Native measurement is authoritative on every frame.
                    results.scroll().latest();
                    results.render(graphics, font, view, body, results.scroll().maximum(),
                            Integer.MIN_VALUE, Integer.MIN_VALUE, false, ticks);
                } else {
                    results.releaseNativeViews();
                    if (view.rows().isEmpty() && bodyHeight >= 10) {
                        String preview = !view.streamingPreview().isBlank() ? view.streamingPreview() : view.latestReply();
                        CacheKey key = new CacheKey(preview, body.width(), body.height(), font, GuideNativeFont.languageIdentity());
                        if (!key.equals(cacheKey)) {
                            cacheKey = key;
                            previewLines = GuideNativeFont.split(font, MinecraftComponents.literal(preview), body.width());
                        }
                        int shown = Math.min(bodyHeight / 10, previewLines.size());
                        int first = Math.max(0, previewLines.size() - shown);
                        for (int line = 0; line < shown; line++) {
                            graphics.text(font, previewLines.get(first + line), body.x(), body.y() + line * 10, textColor());
                        }
                    }
                }
                if (contentHeight >= 54) {
                    footer = readHint(OpenAllayKeyMappings.INTERACT_HUD.isUnbound(),
                            OpenAllayKeyMappings.INTERACT_HUD.getTranslatedKeyMessage());
                    graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, MinecraftComponents.getString(footer), Math.max(1, contentWidth - 16)),
                            8, contentHeight - 12, OpenAllayWidgetTheme.MUTED);
                }
            } finally { graphics.disableScissor(); }
        } finally { graphics.popPose(); }
        // Native hover is available only when another screen has already released the mouse.
        // This passive renderer never changes input ownership to make its footer interactive.
        if (footer != null && !minecraft.mouseHandler.isMouseGrabbed()) {
            int mouseX = (int) dev.openallay.client.context.MinecraftMouseCoordinates.x(minecraft.mouseHandler, minecraft.getWindow());
            int mouseY = (int) dev.openallay.client.context.MinecraftMouseCoordinates.y(minecraft.mouseHandler, minecraft.getWindow());
            double localX = (mouseX - rect.x()) / rect.scale();
            double localY = (mouseY - rect.y()) / rect.scale();
            if (localX >= 8 && localX < contentWidth - 8 && localY >= contentHeight - 14 && localY < contentHeight) {
                Component tooltip = readHintTooltip(OpenAllayKeyMappings.INTERACT_HUD.isUnbound(),
                        OpenAllayKeyMappings.INTERACT_HUD.getTranslatedKeyMessage(),
                        OpenAllayKeyMappings.OPEN_GUIDE.isUnbound() ? null : OpenAllayKeyMappings.OPEN_GUIDE.getTranslatedKeyMessage());
                graphics.setTooltipForNextFrame(font,
                        GuideNativeFont.split(font, tooltip, Math.max(1, Math.min(260, graphics.guiWidth() - 24))),
                        GuideTooltipPlacement.DEFAULT, mouseX, mouseY, false);
            }
        }
    }
    static Component readHint(boolean unbound, Component nativeKey) {
        return unbound ? MinecraftComponents.translatable("screen.openallay.hud.view_unbound")
                : MinecraftComponents.translatable("screen.openallay.hud.read", Objects.requireNonNull(nativeKey, "nativeKey"));
    }
    static Component readHintTooltip(boolean unbound, Component nativeKey, Component nativeOpenGuideKey) {
        Component hint = readHint(unbound, nativeKey);
        if (!unbound) return hint;
        Component reminder = nativeOpenGuideKey == null
                ? MinecraftComponents.translatable("screen.openallay.hud.bind_controls_only")
                : MinecraftComponents.translatable("screen.openallay.hud.bind_controls", nativeOpenGuideKey);
        return MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(hint), "\n"), reminder);
    }
    public static int backgroundColor(double opacity) {
        return ((int) Math.round(opacity * 255) << 24) | (OpenAllayWidgetTheme.CHARCOAL & 0xFFFFFF);
    }
    public static int backgroundBorderColor(double opacity) {
        return ((int) Math.round(opacity * 255) << 24) | (OpenAllayWidgetTheme.SLATE_BORDER & 0xFFFFFF);
    }
    public static int textColor() { return OpenAllayWidgetTheme.WHITE; }
    private record CacheKey(String preview, int width, int height, Font font, Object language) {}
}
