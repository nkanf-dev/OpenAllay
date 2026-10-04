package dev.openallay.client.observation;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.ui.GuideUiLayout;
import org.junit.jupiter.api.Test;

final class ObservationComposerLayoutTest {
    @Test void observationRowAndPasteStripNeverOverlapOrEscapeMeasuredComposerBounds() {
        for (int width : new int[] {0, 20, 74, 96, 160, 240, 400}) {
            for (int height : new int[] {0, 8, 12, 14, 26, 40}) {
                var strip = new GuideUiLayout.Rect(10, 20, width, height);
                var layout = ObservationComposerLayout.calculate(strip, true);
                if (layout.row().width() == 0 || layout.row().height() == 0) continue;
                assertTrue(layout.row().x() >= strip.x()); assertTrue(layout.row().right() <= strip.right());
                assertTrue(layout.row().y() >= strip.y()); assertTrue(layout.row().bottom() <= strip.bottom());
                assertTrue(layout.remaining().x() >= strip.x()); assertTrue(layout.remaining().right() <= strip.right());
                assertTrue(layout.remaining().y() >= strip.y()); assertTrue(layout.remaining().bottom() <= strip.bottom());
                if (layout.remaining().width() > 0 && layout.remaining().height() > 0) {
                    assertTrue(layout.row().right() <= layout.remaining().x()
                            || layout.row().bottom() <= layout.remaining().y());
                }
            }
        }
    }
}
