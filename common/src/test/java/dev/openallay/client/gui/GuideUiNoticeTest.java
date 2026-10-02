package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class GuideUiNoticeTest {
    @Test void severityDoesNotTreatCopySuccessOrProgressAsErrors() {
        assertEquals(GuideUiNotice.Severity.SUCCESS, GuideUiNotice.success("copied").severity());
        assertEquals(OpenAllayWidgetTheme.SUCCESS, GuideUiNotice.success("copied").color());
        assertEquals(OpenAllayWidgetTheme.INFO, GuideUiNotice.info("exporting").color());
        assertEquals(OpenAllayWidgetTheme.WARNING, GuideUiNotice.warning("already consumed").color());
        assertEquals(OpenAllayWidgetTheme.ERROR, GuideUiNotice.error("clipboard unavailable").color());
        assertNotEquals(GuideUiNotice.success("done").color(), GuideUiNotice.error("failed").color());
    }
    @Test void localInputErrorsBelongToComposerAndKeepFullMessage() {
        String original = "This is the complete rejection code and suggested next action, without truncation.";
        var notice = GuideUiNotice.error(original);
        assertEquals(GuideUiNotice.Placement.COMPOSER, notice.placement());
        assertEquals(original, notice.message());
        assertFalse(notice.empty());
        assertTrue(GuideUiNotice.info("").empty());
    }
}
