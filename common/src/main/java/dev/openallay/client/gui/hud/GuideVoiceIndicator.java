package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.client.voice.VoiceStatusPresentation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;

/** Recording and actionable failure feedback are independent of HUD and notification settings. */
public final class GuideVoiceIndicator {
    private static CacheKey cached;
    private static List<GuideTextLine> message = List.of();
    private static List<GuideTextLine> action = List.of();
    private GuideVoiceIndicator() {}

    public static void extract(GuideGraphics graphics, Minecraft minecraft, VoiceRuntime voice) {
        if (voice == null) return;
        VoiceRuntime.Status status = voice.status();
        if (!status.indicatorVisible()) return;
        VoiceStatusPresentation.Notice feedback = VoiceStatusPresentation.describe(status);
        int panelWidth = Math.min(Math.max(1, graphics.guiWidth() - 12), status.active() ? 280 : 360);
        CacheKey key = new CacheKey(feedback, panelWidth, MinecraftClientWindow.font(minecraft), GuideNativeFont.languageIdentity());
        if (!key.equals(cached)) {
            cached = key;
            message = wrap(MinecraftClientWindow.font(minecraft), feedback.translationKey(), panelWidth - 12);
            action = wrap(MinecraftClientWindow.font(minecraft), feedback.actionTranslationKey(), panelWidth - 12);
        }
        int actionLines = status.active() ? 0 : Math.min(2, action.size());
        int messageLines = Math.min(2, message.size());
        int panelHeight = Math.max(19, 10 + (messageLines + actionLines) * 10);
        int x = Math.max(0, (graphics.guiWidth() - panelWidth) / 2);
        graphics.fill(x, 6, x + panelWidth, 6 + panelHeight, OpenAllayWidgetTheme.CHARCOAL);
        graphics.outline(x, 6, panelWidth, panelHeight,
                feedback.error() || status.active() ? OpenAllayWidgetTheme.AMBER : OpenAllayWidgetTheme.MINT);
        for (int line = 0; line < messageLines; line++) graphics.text(MinecraftClientWindow.font(minecraft), message.get(line), x + 6, 11 + line * 10, OpenAllayWidgetTheme.WHITE);
        for (int line = 0; line < actionLines; line++) graphics.text(MinecraftClientWindow.font(minecraft), action.get(line), x + 6,
                11 + (messageLines + line) * 10, OpenAllayWidgetTheme.MUTED);
        if (status.active()) graphics.text(MinecraftClientWindow.font(minecraft), status.elapsedMillis() / 1000 + "s",
                x + panelWidth - 28, 11, OpenAllayWidgetTheme.AMBER);
    }

    private static List<GuideTextLine> wrap(Font font, String key, int width) {
        return key.isEmpty() ? List.of() : GuideNativeFont.split(font, MinecraftComponents.translatable(key), Math.max(1, width));
    }
    @dev.openallay.value.ValueType(CacheKey.ValueSchemaProvider.class)
private static final class CacheKey {
    private final VoiceStatusPresentation.Notice feedback;
    private final int width;
    private final Font font;
    private final Object language;
    private CacheKey(VoiceStatusPresentation.Notice feedback, int width, Font font, Object language) {
        this.feedback = feedback;
        this.width = width;
        this.font = font;
        this.language = language;
    }
    public VoiceStatusPresentation.Notice feedback() { return feedback; }
    public int width() { return width; }
    public Font font() { return font; }
    public Object language() { return language; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CacheKey)) return false;
        CacheKey that = (CacheKey) other;
        return java.util.Objects.equals(feedback, that.feedback) && width == that.width && java.util.Objects.equals(font, that.font) && java.util.Objects.equals(language, that.language);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(feedback);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(font);
        hash = 31 * hash + java.util.Objects.hashCode(language);
        return hash;
    }
    @Override public String toString() { return "CacheKey[feedback=" + feedback + ", width=" + width + ", font=" + font + ", language=" + language + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CacheKey> schema() {
            return new dev.openallay.value.ValueSchema<>(CacheKey.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CacheKey>>asList(new dev.openallay.value.ValueSchema.Component<>(CacheKey.class, "feedback", CacheKey::feedback), new dev.openallay.value.ValueSchema.Component<>(CacheKey.class, "width", CacheKey::width), new dev.openallay.value.ValueSchema.Component<>(CacheKey.class, "font", CacheKey::font), new dev.openallay.value.ValueSchema.Component<>(CacheKey.class, "language", CacheKey::language)), arguments -> new CacheKey((VoiceStatusPresentation.Notice) arguments[0], (Integer) arguments[1], (Font) arguments[2], (Object) arguments[3]));
        }
    }
}
}
