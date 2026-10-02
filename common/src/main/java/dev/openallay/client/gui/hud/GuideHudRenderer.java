package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.guide.ui.GuideUiConfig;
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
    private CacheKey cacheKey;
    private List<FormattedCharSequence> replyLines = List.of();

    public GuideHudRenderer(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, GuideHudView view) {
        var context = new GuideHudVisibility.Context(minecraft.level != null, minecraft.player != null,
                minecraft.gui.hud.isHidden(), minecraft.gui.hud.getDebugOverlay().showDebugScreen(),
                minecraft.gui.screen() != null, minecraft.gui.overlay() != null);
        // Never double-render our explicit surfaces even when the user allows other Screens.
        if (minecraft.gui.screen() instanceof GuideHudEditorScreen
                || minecraft.gui.screen() instanceof GuideChatLiteScreen) return;
        if (GuideHudVisibility.isVisible(view.hud(), context)) draw(graphics, view, view.hud());
    }

    public void extractPreview(GuiGraphicsExtractor graphics, GuideHudView view, GuideUiConfig.Hud hud) {
        draw(graphics, view, hud.withCollapsed(false));
    }

    public void invalidateLayout() { cacheKey = null; }

    private void draw(GuiGraphicsExtractor graphics, GuideHudView view, GuideUiConfig.Hud hud) {
        GuideHudLayout.Rect rect = GuideHudLayout.calculate(graphics.guiWidth(), graphics.guiHeight(), hud);
        Font font = minecraft.font;
        int contentWidth = rect.contentWidth();
        int contentHeight = hud.collapsed() ? Math.min(24, rect.contentHeight()) : rect.contentHeight();
        String preview = !view.streamingPreview().isBlank() ? view.streamingPreview() : view.latestReply();
        CacheKey key = new CacheKey(preview, contentWidth, contentHeight, hud.maxReplyLines(), font, Language.getInstance());
        if (!key.equals(cacheKey)) {
            cacheKey = key;
            int lineBudget = Math.max(0, Math.min(hud.maxReplyLines(), (contentHeight - 52) / 10));
            List<FormattedCharSequence> wrapped = font.split(Component.literal(preview), Math.max(1, contentWidth - 16));
            int visibleLines = Math.min(lineBudget, wrapped.size());
            var visible = new java.util.ArrayList<>(wrapped.subList(0, visibleLines));
            if (visibleLines > 0 && wrapped.size() > visibleLines) {
                StringBuilder plain = new StringBuilder();
                visible.getLast().accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                String clipped = font.plainSubstrByWidth(plain.toString(), Math.max(1, contentWidth - 16 - font.width("…")));
                visible.set(visibleLines - 1, Component.literal(clipped + "…").getVisualOrderText());
            }
            replyLines = List.copyOf(visible);
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) rect.x(), (float) rect.y());
            graphics.pose().scale((float) rect.scale(), (float) rect.scale());
            graphics.enableScissor(0, 0, contentWidth, contentHeight);
            try {
                int background = backgroundColor(hud.backgroundOpacity());
                graphics.fill(0, 0, contentWidth, contentHeight, background);
                graphics.outline(0, 0, contentWidth, contentHeight, backgroundBorderColor(hud.backgroundOpacity()));
                String title = view.assistantName() + (view.selectedSession().isBlank() ? "" : " · " + view.selectedSession());
                graphics.text(font, font.plainSubstrByWidth(title, Math.max(1, contentWidth - 16)), 8, 7, textColor());
                if (hud.collapsed()) return;
                Component status = view.progress() == null ? Component.translatable("screen.openallay.hud.idle")
                        : Component.translatable(view.progress().activityTranslationKey());
                if (view.otherRunningTasks() > 0) status = status.copy().append(Component.translatable(
                        "screen.openallay.hud.other_tasks", view.otherRunningTasks()));
                graphics.text(font, font.plainSubstrByWidth(status.getString(), Math.max(1, contentWidth - 16)),
                        8, 22, view.progress() == null ? OpenAllayWidgetTheme.MUTED : OpenAllayWidgetTheme.MINT);
                for (int index = 0; index < replyLines.size(); index++) {
                    graphics.text(font, replyLines.get(index), 8, 36 + index * 10, textColor());
                }
                if (contentHeight >= 54) graphics.text(font, Component.translatable("screen.openallay.hud.view",
                        dev.openallay.client.gui.OpenAllayKeyMappings.OPEN_GUIDE.getTranslatedKeyMessage()),
                        8, contentHeight - 12, OpenAllayWidgetTheme.MUTED);
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

    private record CacheKey(String preview, int width, int height, int lines, Font font, Language language) {}
}
