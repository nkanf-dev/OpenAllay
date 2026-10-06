package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

final class GuideNativeFontTest {
    @Test void preservesActualFormattedGlyphOwnerAndSupplementaryCodePoints() {
        FormattedCharSequence nativeLine = FormattedCharSequence.forward("A🙂", Style.EMPTY.withBold(true));
        GuideTextLine projected = GuideNativeFont.line(nativeLine);
        assertSame(nativeLine, GuideNativeFont.nativeLine(projected));
        assertEquals("A🙂", projected.plainText());
        GuideNativeFont.nativeLine(projected).accept((index, style, codePoint) -> {
            assertTrue(style.isBold());
            return true;
        });
    }
    @Test void lineListsPreserveOrderAndAreImmutable() {
        FormattedCharSequence first = FormattedCharSequence.forward("first", Style.EMPTY);
        FormattedCharSequence second = FormattedCharSequence.forward("second", Style.EMPTY);
        List<GuideTextLine> projected = GuideNativeFont.lines(List.of(first, second));
        assertEquals(List.of("first", "second"), projected.stream().map(GuideTextLine::plainText).toList());
        assertSame(first, GuideNativeFont.nativeLines(projected).get(0));
        assertSame(second, GuideNativeFont.nativeLines(projected).get(1));
        assertThrows(UnsupportedOperationException.class, () -> projected.add(GuideNativeFont.plain("third")));
    }
    @Test void rejectsAValueFromAnotherBindingRatherThanFlatteningItsStyles() {
        GuideTextLine foreign = () -> "text";
        assertThrows(IllegalArgumentException.class, () -> GuideNativeFont.nativeLine(foreign));
    }
}
