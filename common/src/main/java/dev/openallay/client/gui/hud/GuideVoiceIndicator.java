package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.client.voice.VoiceStatusPresentation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Recording and actionable failure feedback are independent of HUD and notification settings. */
public final class GuideVoiceIndicator {
    private static CacheKey cached;
    private static List<FormattedCharSequence> message = List.of();
    private static List<FormattedCharSequence> action = List.of();
    private GuideVoiceIndicator() {}

    public static void extract(GuideGraphics graphics, Minecraft minecraft, VoiceRuntime voice) {
        if (voice == null) return;
        VoiceRuntime.Status status = voice.status();
        if (!status.indicatorVisible()) return;
        VoiceStatusPresentation.Notice feedback = VoiceStatusPresentation.describe(status);
        int panelWidth = Math.min(Math.max(1, graphics.guiWidth() - 12), status.active() ? 280 : 360);
        CacheKey key = new CacheKey(feedback, panelWidth, minecraft.font, Language.getInstance());
        if (!key.equals(cached)) {
            cached = key;
            message = wrap(minecraft.font, feedback.translationKey(), panelWidth - 12);
            action = wrap(minecraft.font, feedback.actionTranslationKey(), panelWidth - 12);
        }
        int actionLines = status.active() ? 0 : Math.min(2, action.size());
        int messageLines = Math.min(2, message.size());
        int panelHeight = Math.max(19, 10 + (messageLines + actionLines) * 10);
        int x = Math.max(0, (graphics.guiWidth() - panelWidth) / 2);
        graphics.fill(x, 6, x + panelWidth, 6 + panelHeight, OpenAllayWidgetTheme.CHARCOAL);
        graphics.outline(x, 6, panelWidth, panelHeight,
                feedback.error() || status.active() ? OpenAllayWidgetTheme.AMBER : OpenAllayWidgetTheme.MINT);
        for (int line = 0; line < messageLines; line++) graphics.text(minecraft.font, message.get(line), x + 6, 11 + line * 10, OpenAllayWidgetTheme.WHITE);
        for (int line = 0; line < actionLines; line++) graphics.text(minecraft.font, action.get(line), x + 6,
                11 + (messageLines + line) * 10, OpenAllayWidgetTheme.MUTED);
        if (status.active()) graphics.text(minecraft.font, status.elapsedMillis() / 1000 + "s",
                x + panelWidth - 28, 11, OpenAllayWidgetTheme.AMBER);
    }

    private static List<FormattedCharSequence> wrap(Font font, String key, int width) {
        return key.isEmpty() ? List.of() : font.split(Component.translatable(key), Math.max(1, width));
    }
    private record CacheKey(VoiceStatusPresentation.Notice feedback, int width, Font font, Language language) {}
}
