package dev.tomewisp.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tomewisp.guide.semantic.SemanticDocument;
import dev.tomewisp.guide.semantic.SemanticMessageParser;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class SemanticLayoutTest {
    private static final SemanticLayoutEngine.Measurer MEASURER = new SemanticLayoutEngine.Measurer() {
        @Override public int width(String text, SemanticLayout.Style style) {
            return text.codePointCount(0, text.length()) * (style == SemanticLayout.Style.STRONG ? 2 : 1);
        }
        @Override public int lineHeight(SemanticLayout.Kind kind) {
            return kind == SemanticLayout.Kind.HEADING ? 14 : 10;
        }
    };

    @Test
    void laysOutSupportedSemanticBlocksAndWrapsWithoutLosingNarration() {
        SemanticDocument document = new SemanticMessageParser().parse("""
                # Heading

                - first item
                - second **strong** item

                > quoted text

                | A | B |
                |---|---|
                | C | D |

                ```java
                value();
                ```
                """);

        SemanticLayout layout = new SemanticLayoutEngine().layout(document, 12, MEASURER);

        assertTrue(layout.lines().stream().anyMatch(line -> line.kind() == SemanticLayout.Kind.HEADING));
        assertTrue(layout.lines().stream().anyMatch(line -> line.kind() == SemanticLayout.Kind.TABLE));
        assertTrue(layout.lines().stream().anyMatch(line -> line.kind() == SemanticLayout.Kind.CODE));
        assertEquals(document.fallbackText(), layout.narration());
        assertEquals(layout.height(), layout.lines().stream().mapToInt(SemanticLayout.Line::height).sum());
    }

    @Test
    void cachesByPresentationIdentityAndInvalidatesOneRow() {
        SemanticLayoutCache cache = new SemanticLayoutCache();
        SemanticDocument document = new SemanticMessageParser().parse("Hello **world**");
        String locale = Locale.SIMPLIFIED_CHINESE.toLanguageTag();

        SemanticLayout first = cache.get(
                "assistant-1", document, 80, locale, "font", true, MEASURER);
        SemanticLayout second = cache.get(
                "assistant-1", document, 80, locale, "font", true, MEASURER);
        assertSame(first, second);
        assertEquals(new SemanticLayoutCache.Stats(1, 1, 1), cache.stats());

        cache.invalidateRow("assistant-1");
        SemanticLayout third = cache.get(
                "assistant-1", document, 80, locale, "font", true, MEASURER);
        assertTrue(first != third);
        assertEquals(new SemanticLayoutCache.Stats(1, 2, 1), cache.stats());
    }
}
