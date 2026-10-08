package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class GuideWidgetInputTest {
    private static class FocusOwner implements GuideWidgetInput {
        private boolean focused;
        @Override public void guideSetFocused(boolean focused) { this.focused = focused; }
        @Override public boolean guideIsFocused() { return focused; }
    }
    @Test void unimplementedCallbacksNeverConsumeInput() {
        GuideWidgetInput widget = new FocusOwner();
        GuideInputKey key = new GuideInputKey(17, 0, 17, 0, false, false, false, false, false, false, false);
        GuideInputCharacter character = new GuideInputCharacter('x', 0);
        GuideInputMouse mouse = new GuideInputMouse(3, 4, 0, 0, true);
        assertFalse(widget.guideKeyPressed(key));
        assertFalse(widget.guideKeyReleased(key));
        assertFalse(widget.guideCharTyped(character));
        assertFalse(widget.guideMouseClicked(mouse, false));
        assertFalse(widget.guideMouseDragged(mouse, 2, 3));
        assertFalse(widget.guideMouseReleased(mouse));
    }
    @Test void focusOwnershipIsExplicitAndReversible() {
        GuideWidgetInput widget = new FocusOwner();
        assertFalse(widget.guideIsFocused());
        widget.guideSetFocused(true);
        assertTrue(widget.guideIsFocused());
        widget.guideSetFocused(false);
        assertFalse(widget.guideIsFocused());
    }
    @Test void callbackReceivesFrozenInputWithoutNativeTranslation() {
        GuideInputKey event = new GuideInputKey(17, 8, 17, 2, false, false, true, true, false, false, false);
        GuideInputKey[] received = new GuideInputKey[1];
        GuideWidgetInput widget = new FocusOwner() {
            @Override public boolean guideKeyPressed(GuideInputKey key) {
                received[0] = key;
                return key.isPaste();
            }
        };
        assertTrue(widget.guideKeyPressed(event));
        assertSame(event, received[0]);
        assertEquals(8, received[0].scancode());
        assertTrue(received[0].controlDown());
    }
}
