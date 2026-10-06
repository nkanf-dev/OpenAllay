package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class GuideTextLineTest {
    @Test void plainProjectionHasNoNativeClassDependency() {
        GuideTextLine line = () -> "Latin 中 🙂";
        assertEquals("Latin 中 🙂", line.plainText());
    }
}
