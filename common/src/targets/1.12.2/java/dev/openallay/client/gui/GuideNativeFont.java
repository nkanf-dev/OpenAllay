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
    @dev.openallay.value.ValueType(Line.ValueSchemaProvider.class)
private static final class Line implements GuideTextLine {
    private final String nativeLine;
    private Line(String nativeLine) {
 Objects.requireNonNull(nativeLine, "nativeLine");
        this.nativeLine = nativeLine;
    }
    public String nativeLine() { return nativeLine; }
@Override public String plainText() {
            return net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(nativeLine);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Line)) return false;
        Line that = (Line) other;
        return java.util.Objects.equals(nativeLine, that.nativeLine);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nativeLine);
        return hash;
    }
    @Override public String toString() { return "Line[nativeLine=" + nativeLine + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Line> schema() {
            return new dev.openallay.value.ValueSchema<>(Line.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Line>>asList(new dev.openallay.value.ValueSchema.Component<>(Line.class, "nativeLine", Line::nativeLine)), arguments -> new Line((String) arguments[0]));
        }
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
