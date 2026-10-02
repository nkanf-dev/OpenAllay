package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class OpenAllayWidgetThemeTest {
    @Test
    void everyInteractiveStateHasAnUnambiguousVisualTreatment() {
        assertEquals(
                OpenAllayWidgetTheme.ButtonVisualState.IDLE,
                OpenAllayWidgetTheme.buttonState(true, false, false, false));
        assertEquals(
                OpenAllayWidgetTheme.ButtonVisualState.HOVERED,
                OpenAllayWidgetTheme.buttonState(true, true, false, false));
        assertEquals(
                OpenAllayWidgetTheme.ButtonVisualState.FOCUSED,
                OpenAllayWidgetTheme.buttonState(true, false, true, false));
        assertEquals(
                OpenAllayWidgetTheme.ButtonVisualState.DISABLED,
                OpenAllayWidgetTheme.buttonState(false, true, true, false));
        assertEquals(
                OpenAllayWidgetTheme.ButtonVisualState.SELECTED,
                OpenAllayWidgetTheme.buttonState(false, false, false, true));

        for (OpenAllayWidgetTheme.ButtonVisualState state
                : OpenAllayWidgetTheme.ButtonVisualState.values()) {
            OpenAllayWidgetTheme.ButtonColors colors =
                    OpenAllayWidgetTheme.buttonColors(state);
            assertNotEquals(colors.fill(), colors.border(), state.name());
        }
    }

    @Test
    void selectionWinsOverDisabledHoverAndFocusStates() {
        for (boolean active : new boolean[] {false, true}) {
            for (boolean hovered : new boolean[] {false, true}) {
                for (boolean focused : new boolean[] {false, true}) {
                    assertEquals(OpenAllayWidgetTheme.ButtonVisualState.SELECTED,
                            OpenAllayWidgetTheme.buttonState(active, hovered, focused, true));
                }
            }
        }
    }

    @Test
    void sharedPanelsAndSemanticColorsUseReadableOpaquePalette() {
        assertEquals(OpenAllayWidgetTheme.CHARCOAL, OpenAllayWidgetTheme.PANEL);
        assertEquals(OpenAllayWidgetTheme.CHARCOAL_RAISED, OpenAllayWidgetTheme.PANEL_ALT);
        assertEquals(OpenAllayWidgetTheme.WHITE, OpenAllayWidgetTheme.TEXT);
        assertEquals(OpenAllayWidgetTheme.MINT, OpenAllayWidgetTheme.SUCCESS);
        assertEquals(OpenAllayWidgetTheme.AMBER, OpenAllayWidgetTheme.WARNING);
        assertNotEquals(OpenAllayWidgetTheme.MUTED, OpenAllayWidgetTheme.MUTED_READABLE);
        assertNotEquals(OpenAllayWidgetTheme.ERROR, OpenAllayWidgetTheme.WARNING);
        assertNotEquals(OpenAllayWidgetTheme.INFO, OpenAllayWidgetTheme.SUCCESS);
        for (int background : new int[] {OpenAllayWidgetTheme.PANEL, OpenAllayWidgetTheme.PANEL_ALT}) {
            assertEquals(0xFF, background >>> 24);
            for (int foreground : new int[] {OpenAllayWidgetTheme.TEXT, OpenAllayWidgetTheme.MUTED_READABLE,
                    OpenAllayWidgetTheme.ERROR, OpenAllayWidgetTheme.SUCCESS,
                    OpenAllayWidgetTheme.WARNING, OpenAllayWidgetTheme.INFO}) {
                assertEquals(0xFF, foreground >>> 24);
                assertTrue(contrast(foreground, background) >= 4.5,
                        Integer.toHexString(foreground) + " on " + Integer.toHexString(background));
            }
        }
    }

    @Test
    void sharedSpacingAndLineHeightHaveStablePixelValues() {
        assertEquals(4, OpenAllayWidgetTheme.SPACE_XS);
        assertEquals(8, OpenAllayWidgetTheme.SPACE_SM);
        assertEquals(12, OpenAllayWidgetTheme.SPACE_MD);
        assertEquals(16, OpenAllayWidgetTheme.SPACE_LG);
        assertEquals(24, OpenAllayWidgetTheme.SPACE_XL);
        assertEquals(12, OpenAllayWidgetTheme.LINE_HEIGHT);
    }

    @Test
    void focusUsesAmberAndSelectionUsesMint() {
        assertEquals(
                OpenAllayWidgetTheme.AMBER,
                OpenAllayWidgetTheme.buttonColors(
                                OpenAllayWidgetTheme.ButtonVisualState.FOCUSED)
                        .border());
        assertEquals(
                OpenAllayWidgetTheme.MINT,
                OpenAllayWidgetTheme.buttonColors(
                                OpenAllayWidgetTheme.ButtonVisualState.SELECTED)
                        .border());
    }

    private static double contrast(int foreground, int background) {
        return (luminance(foreground) + 0.05) / (luminance(background) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linearChannel((color >>> 16) & 0xFF)
                + 0.7152 * linearChannel((color >>> 8) & 0xFF)
                + 0.0722 * linearChannel(color & 0xFF);
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
