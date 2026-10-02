package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SettingsLayoutTest {
    @Test
    void topLevelSectionsSeparateExtensionsAndSkillsWithoutRecipes() {
        assertEquals(List.of(
                        SettingsSection.GENERAL,
                        SettingsSection.UI,
                        SettingsSection.MODELS,
                        SettingsSection.EXTENSIONS,
                        SettingsSection.SKILLS,
                        SettingsSection.HISTORY,
                        SettingsSection.DIAGNOSTICS,
                        SettingsSection.ABOUT),
                SettingsSection.topLevel());
        assertFalse(SettingsSection.topLevel().stream()
                .anyMatch(section -> section.name().equals("RECIPES")));
    }

    @Test
    void generalAndAboutScrollUseEditorExtentAndNeverTheFooter() {
        for (int[] size : new int[][] {{427, 320}, {569, 320}, {900, 500}, {240, 180}}) {
            SettingsLayout layout = SettingsLayout.calculate(size[0], size[1]);
            int contentHeight = layout.editor().height() + 180;
            int maximum = layout.maximumPageScroll(contentHeight);
            assertEquals(188, maximum);
            assertEquals(layout.editor().y() - maximum, layout.pageOrigin(maximum));
            assertTrue(layout.pageOrigin(maximum) + contentHeight < layout.footer().y());
            assertTrue(layout.pageWidgetVisible(layout.editor().y(), 20));
            assertFalse(layout.pageWidgetVisible(layout.editor().y() - 1, 20));
            assertFalse(layout.pageWidgetVisible(layout.editor().bottom() - 10, 20));
            assertEquals(0, layout.maximumPageScroll(20));
        }
    }

    @Test
    void singlePagesDoNotWasteAnEmptyListAndNavigationScrollReachesEightCategories() {
        for (SettingsSection section : List.of(SettingsSection.GENERAL, SettingsSection.UI,
                SettingsSection.ABOUT, SettingsSection.HISTORY, SettingsSection.DIAGNOSTICS)) {
            SettingsLayout layout = SettingsLayout.calculate(900, 180, section);
            assertEquals(0, layout.list().width());
            assertEquals(layout.navigation().right() + 6, layout.editor().x());
            assertEquals(layout.content().right(), layout.editor().right());
            assertTrue(layout.maximumNavigationScroll(SettingsSection.topLevel().size()) > 0);
            int lastRow = layout.navigation().y() + 8 + (SettingsSection.topLevel().size() - 1) * 24
                    - layout.maximumNavigationScroll(SettingsSection.topLevel().size());
            assertTrue(lastRow + 20 <= layout.navigation().bottom());
            assertEquals(layout.content().bottom(), layout.footer().y());
        }
        assertTrue(SettingsLayout.calculate(900, 500, SettingsSection.MODELS).list().width() > 0);
    }

    @Test
    void wideLayoutHasRailListAndEditorWithoutOverlap() {
        SettingsLayout layout = SettingsLayout.calculate(960, 600);

        assertTrue(layout.wide());
        assertTrue(layout.navigation().right() <= layout.list().x());
        assertTrue(layout.list().right() <= layout.editor().x());
        assertEquals(layout.content().bottom(), layout.footer().y());
    }

    @Test
    void narrowLayoutUsesOneContentPanelAndBackNavigation() {
        SettingsLayout layout = SettingsLayout.calculate(480, 320);

        assertFalse(layout.wide());
        assertEquals(0, layout.navigation().width());
        assertEquals(0, layout.list().width());
        assertEquals(layout.content(), layout.editor());
        assertTrue(layout.showBack());
    }
}
