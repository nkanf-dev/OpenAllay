package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiLayout;
import dev.openallay.guide.ui.hud.GuideHudScrollState;
import dev.openallay.guide.ui.hud.GuideHudView;
import dev.openallay.guide.ui.hud.GuideHudVisibility;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Passive extraction only. No Screen, pointer, task, history, or context operations. */
public final class GuideHudRenderer {
    private final Minecraft minecraft;
    private final GuideHudResultRenderer results = new GuideHudResultRenderer();
    private CacheKey cacheKey;
    private List<dev.openallay.guide.ui.GuideUiRow> pageRows;
    private long pageStartedAt;
    private List<FormattedCharSequence> previewLines = List.of();

    public GuideHudRenderer(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }
    public GuideHudResultRenderer.Receipt resultReceipt() { return results.receipt(); }

    public void extractRenderState(GuiGraphicsExtractor graphics, GuideHudView view) {
        var context = new GuideHudVisibility.Context(minecraft.level != null, minecraft.player != null,
                minecraft.gui.hud.isHidden(), minecraft.gui.hud.getDebugOverlay().showDebugScreen(),
                minecraft.gui.screen() != null, minecraft.gui.overlay() != null);
        if (minecraft.gui.screen() instanceof GuideHudEditorScreen
                || minecraft.gui.screen() instanceof GuideChatLiteScreen) { results.releaseNativeViews(); return; }
        if (GuideHudVisibility.isVisible(view.hud(), context)) draw(graphics, view, view.hud());
        else results.releaseNativeViews();
    }
    public void extractPreview(GuiGraphicsExtractor graphics, GuideHudView view, GuideUiConfig.Hud hud) {
        draw(graphics, view, hud.withCollapsed(false));
    }
    public void invalidateLayout() { cacheKey = null; results.invalidate(); }

    private void draw(GuiGraphicsExtractor graphics, GuideHudView view, GuideUiConfig.Hud hud) {
        GuideHudLayout.Rect rect = GuideHudLayout.calculate(graphics.guiWidth(), graphics.guiHeight(), hud);
        Font font = minecraft.font;
        int contentWidth = rect.contentWidth();
        int contentHeight = hud.collapsed() ? Math.min(24, rect.contentHeight()) : rect.contentHeight();
        int available = Math.max(0, contentHeight - 52);
        int bodyHeight = hud.maxReplyLines() == 0 ? available : Math.min(available, hud.maxReplyLines() * 11);
        GuideUiLayout.Rect body = new GuideUiLayout.Rect(8, 36, Math.max(1, contentWidth - 16), bodyHeight);
        long ticks = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (pageRows != view.rows()) {
            if (!Objects.equals(pageRows, view.rows())) pageStartedAt = ticks;
            pageRows = view.rows(); // Equal telemetry-only snapshots must not restart the passive page.
        }
        long pageTicks = Math.max(0, ticks - pageStartedAt);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) rect.x(), (float) rect.y());
            graphics.pose().scale((float) rect.scale(), (float) rect.scale());
            graphics.enableScissor(0, 0, contentWidth, contentHeight);
            try {
                int rgb = view.presentation().theme() == GuideUiConfig.Theme.MINT ? 0x172A27 : OpenAllayWidgetTheme.CHARCOAL;
                graphics.fill(0, 0, contentWidth, contentHeight, hud.backgroundArgb(rgb));
                graphics.outline(0, 0, contentWidth, contentHeight, backgroundBorderColor(hud.backgroundOpacity()));
                String title = view.assistantName() + (view.selectedSession().isBlank() ? "" : " · " + view.selectedSession());
                graphics.text(font, font.plainSubstrByWidth(title, Math.max(1, contentWidth - 16)), 8, 7, textColor());
                if (hud.collapsed()) { results.releaseNativeViews(); return; }
                Component status = view.progress() == null ? Component.translatable("screen.openallay.hud.idle")
                        : Component.translatable(view.progress().activityTranslationKey());
                if (view.otherRunningTasks() > 0) status = status.copy().append(Component.translatable(
                        "screen.openallay.hud.other_tasks", view.otherRunningTasks()));
                graphics.text(font, font.plainSubstrByWidth(status.getString(), Math.max(1, contentWidth - 16)),
                        8, 22, view.progress() == null ? OpenAllayWidgetTheme.MUTED : OpenAllayWidgetTheme.MINT);
                int pages = 1;
                if (!view.rows().isEmpty() && bodyHeight > 0) {
                    results.prepare(view, font, Math.max(1, body.width() - 6), body.height());
                    int offset = GuideHudScrollState.passivePageOffset(results.scroll().totalHeight(), bodyHeight, pageTicks);
                    pages = GuideHudScrollState.passivePageCount(results.scroll().totalHeight(), bodyHeight);
                    results.render(graphics, font, view, body, offset, Integer.MIN_VALUE, Integer.MIN_VALUE, false, ticks);
                } else {
                    results.releaseNativeViews();
                    String preview = !view.streamingPreview().isBlank() ? view.streamingPreview() : view.latestReply();
                    CacheKey key = new CacheKey(preview, body.width(), body.height(), font, Language.getInstance());
                    if (!key.equals(cacheKey)) {
                        cacheKey = key;
                        previewLines = font.split(Component.literal(preview), body.width());
                    }
                    for (int i = 0; i < Math.min(bodyHeight / 10, previewLines.size()); i++) {
                        graphics.text(font, previewLines.get(i), body.x(), body.y() + i * 10, textColor());
                    }
                }
                if (contentHeight >= 54) {
                    Component hint = Component.translatable("screen.openallay.hud.read",
                            dev.openallay.client.gui.OpenAllayKeyMappings.INTERACT_HUD.getTranslatedKeyMessage());
                    if (pages > 1) hint = Component.translatable("screen.openallay.hud.pages",
                            1 + (int) Math.floorMod(pageTicks / 160, pages), pages).copy().append(" · ").append(hint);
                    graphics.text(font, font.plainSubstrByWidth(hint.getString(), Math.max(1, contentWidth - 16)),
                            8, contentHeight - 12, OpenAllayWidgetTheme.MUTED);
                }
            } finally { graphics.disableScissor(); }
        } finally { graphics.pose().popMatrix(); }
    }
    public static int backgroundColor(double opacity) {
        return ((int) Math.round(opacity * 255) << 24) | (OpenAllayWidgetTheme.CHARCOAL & 0xFFFFFF);
    }
    public static int backgroundBorderColor(double opacity) {
        return ((int) Math.round(opacity * 255) << 24) | (OpenAllayWidgetTheme.SLATE_BORDER & 0xFFFFFF);
    }
    public static int textColor() { return OpenAllayWidgetTheme.WHITE; }
    private record CacheKey(String preview, int width, int height, Font font, Language language) {}
}
