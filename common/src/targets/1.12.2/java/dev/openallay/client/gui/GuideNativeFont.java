package dev.openallay.client.gui;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** Actual FontRenderer line wrapping and legacy formatted String projection. */
public final class GuideNativeFont {
    private GuideNativeFont() {}
    public static String plainSubstrByWidth(FontRenderer font, String text, int width, net.minecraft.util.text.Style style) {
        String formatted = new net.minecraft.util.text.TextComponentString(text).setStyle(style).getFormattedText();
        return net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(font.trimStringToWidth(formatted, width));
    }

    public static Object languageIdentity() { return net.minecraft.client.Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage(); }
    public static int width(FontRenderer font, String text) { return font.getStringWidth(text); }
    public static int width(FontRenderer font, ITextComponent text) { return font.getStringWidth(text.getFormattedText()); }
    public static String plainSubstrByWidth(FontRenderer font, String text, int width) { return font.trimStringToWidth(text, width); }
    public static ITextComponent substrByWidth(FontRenderer font, ITextComponent text, int width) {
        return new net.minecraft.util.text.TextComponentString(font.trimStringToWidth(text.getFormattedText(), width));
    }

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
