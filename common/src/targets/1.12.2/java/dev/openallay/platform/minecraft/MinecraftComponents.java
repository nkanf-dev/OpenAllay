package dev.openallay.platform.minecraft;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/** Genuine 1.12 component/style operations. No MutableComponent identity is fabricated. */
public final class MinecraftComponents {
    private MinecraftComponents() {}
    public static ITextComponent literal(String text) { return new TextComponentString(text); }
    public static ITextComponent translatable(String key) { return new TextComponentTranslation(key); }
    public static ITextComponent translatable(String key, Object... arguments) { return new TextComponentTranslation(key, arguments); }
    public static ITextComponent empty() { return new TextComponentString(""); }
    public static ITextComponent append(ITextComponent target, String text) { return target.appendText(text); }
    public static ITextComponent append(ITextComponent target, ITextComponent child) { return target.appendSibling(child); }
    public static ITextComponent copy(ITextComponent target) { return target.createCopy(); }
    public static ITextComponent style(ITextComponent target, TextFormatting formatting) {
        Style style = target.getStyle().createShallowCopy();
        if (formatting.isColor()) style.setColor(formatting);
        else switch ((formatting)) {
case OBFUSCATED:
{
style.setObfuscated(true);
break;
}
case BOLD:
{
style.setBold(true);
break;
}
case STRIKETHROUGH:
{
style.setStrikethrough(true);
break;
}
case UNDERLINE:
{
style.setUnderlined(true);
break;
}
case ITALIC:
{
style.setItalic(true);
break;
}
case RESET:
{
style = new Style();
break;
}
default:
{
{ }
break;
}
}

        return target.setStyle(style);
    }
    public static ITextComponent style(ITextComponent target, TextFormatting... formatting) {
        for (TextFormatting value : formatting) style(target, value);
        return target;
    }
    public static ITextComponent style(ITextComponent target, Style style) { return target.setStyle(style); }
    public static ITextComponent style(ITextComponent target, java.util.function.UnaryOperator<Style> operator) {
        return target.setStyle(operator.apply(target.getStyle().createShallowCopy()));
    }
    public static String getString(ITextComponent target) { return target.getUnformattedText(); }
}
