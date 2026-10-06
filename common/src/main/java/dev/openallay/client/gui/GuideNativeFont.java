package dev.openallay.client.gui;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Native formatted lines stay real native values behind the product text contract. */
public final class GuideNativeFont {
    private GuideNativeFont() {}
    public static int lineHeight(Font font) { return font.lineHeight; }
    private record Line(FormattedCharSequence nativeLine) implements GuideTextLine {
        private Line { Objects.requireNonNull(nativeLine, "nativeLine"); }
        @Override public String plainText() {
            StringBuilder text = new StringBuilder();
            nativeLine.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
            return text.toString();
        }
    }
    public static GuideTextLine line(FormattedCharSequence text) { return new Line(text); }
    public static GuideTextLine plain(String text) {
        return line(FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY));
    }
    public static int width(Font font, GuideTextLine line) { return font.width(nativeLine(line)); }
    public static GuideTextLine visual(Component text) { return line(text.getVisualOrderText()); }
    public static List<GuideTextLine> lines(List<? extends FormattedCharSequence> lines) {
        return lines.stream().map(GuideNativeFont::line).toList();
    }
    public static List<GuideTextLine> split(Font font, Component text, int width) {
        return lines(font.split(text, width));
    }
    public static FormattedCharSequence nativeLine(GuideTextLine text) {
        if (!(text instanceof Line line)) {
            throw new IllegalArgumentException("Text line belongs to a different native binding");
        }
        return line.nativeLine();
    }
    public static List<FormattedCharSequence> nativeLines(List<? extends GuideTextLine> lines) {
        return lines.stream().map(GuideNativeFont::nativeLine).toList();
    }
}
