package dev.openallay.client.gui;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** Actual FontRenderer line wrapping and legacy formatted String projection. */
public final class GuideNativeFont {
    private GuideNativeFont() {}
    public static int lineHeight(FontRenderer font) { return font.FONT_HEIGHT; }
    private record Line(String nativeLine) implements GuideTextLine {
        private Line { Objects.requireNonNull(nativeLine, "nativeLine"); }
        @Override public String plainText() {
            return net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(nativeLine);
        }
    }
    public static GuideTextLine line(String text) { return new Line(text); }
    public static GuideTextLine plain(String text) { return line(text); }
    public static int width(FontRenderer font, GuideTextLine line) { return font.getStringWidth(nativeLine(line)); }
    public static GuideTextLine visual(ITextComponent text) { return line(text.getFormattedText()); }
    public static List<GuideTextLine> split(FontRenderer font, ITextComponent text, int width) {
        return font.listFormattedStringToWidth(text.getFormattedText(), width).stream()
                .map(GuideNativeFont::line).toList();
    }
    public static String nativeLine(GuideTextLine text) {
        if (!(text instanceof Line line)) {
            throw new IllegalArgumentException("Text line belongs to a different native binding");
        }
        return line.nativeLine();
    }
    public static List<String> nativeLines(List<? extends GuideTextLine> lines) {
        return lines.stream().map(GuideNativeFont::nativeLine).toList();
    }
}
