package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class GuideWidgetInputsTest {
    private static final class Owner implements GuideWidgetInput {
        private Object received;
        private boolean focused = true;
        private double dx;
        private double dy;
        private boolean doubleClick;
        private int releases;
        @Override public boolean guideKeyPressed(GuideInputKey event) { received = event; return true; }
        @Override public boolean guideKeyReleased(GuideInputKey event) { received = event; return true; }
        @Override public boolean guideCharTyped(GuideInputCharacter event) { received = event; return true; }
        @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
            received = event; this.doubleClick = doubleClick; return true;
        }
        @Override public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) {
            received = event; this.dx = dx; this.dy = dy; return true;
        }
        @Override public boolean guideMouseReleased(GuideInputMouse event) { received = event; return true; }
        @Override public void guideSetFocused(boolean focused) { this.focused = focused; if (!focused) releases++; }
        @Override public boolean guideIsFocused() { return focused; }
    }
    @Test void forwardsFrozenKeysAndCharactersWithoutReconstruction() {
        Owner owner = new Owner();
        GuideInputKey key = new GuideInputKey(13, 9, 13, 2, false, false, true, true, false, false, false);
        assertTrue(GuideWidgetInputs.keyPressed(owner, key));
        assertSame(key, owner.received);
        assertTrue(GuideWidgetInputs.keyReleased(owner, key));
        assertSame(key, owner.received);
        GuideInputCharacter character = new GuideInputCharacter(0x1f642, 1);
        assertTrue(GuideWidgetInputs.charTyped(owner, character));
        assertSame(character, owner.received);
    }
    @Test void forwardsMouseCoordinatesDoubleClickAndDragDeltas() {
        Owner owner = new Owner();
        GuideInputMouse mouse = new GuideInputMouse(4.5, 8.25, 0, 1, true);
        assertTrue(GuideWidgetInputs.mouseClicked(owner, mouse, true));
        assertSame(mouse, owner.received);
        assertTrue(owner.doubleClick);
        assertTrue(GuideWidgetInputs.mouseDragged(owner, mouse, -2.25, 7.5));
        assertSame(mouse, owner.received);
        assertEquals(-2.25, owner.dx);
        assertEquals(7.5, owner.dy);
        assertTrue(GuideWidgetInputs.mouseReleased(owner, mouse));
        assertSame(mouse, owner.received);
    }
    @Test void retiresOnlyRequestedFocusOwner() {
        Owner requested = new Owner();
        Owner replacement = new Owner();
        GuideWidgetInputs.releaseTextFocus(requested);
        assertFalse(requested.focused);
        assertEquals(1, requested.releases);
        assertTrue(replacement.focused);
        assertEquals(0, replacement.releases);
    }
    @Test void requiresAnActualOwner() {
        assertThrows(NullPointerException.class, () -> GuideWidgetInputs.releaseTextFocus(null));
    }
}
