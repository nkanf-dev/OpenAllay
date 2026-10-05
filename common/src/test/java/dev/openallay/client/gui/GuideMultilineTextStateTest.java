package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure text primitive tests. Native input/render/clipboard acceptance belongs to packaged clients. */
final class GuideMultilineTextStateTest {
    private GuideMultilineTextState state(String value, int width) {
        var state = new GuideMultilineTextState();
        state.wrap(width, String::length);
        state.setValue(value);
        return state;
    }
    @Test void preservesNewlinesEmptyLinesAndFullTextAcrossWrap() {
        var state = state("ab cd\n\nxyz\n", 3);
        assertEquals("ab cd\n\nxyz\n", state.value());
        assertEquals(List.of(new GuideMultilineTextState.Line(0, 3), new GuideMultilineTextState.Line(3, 5),
                new GuideMultilineTextState.Line(6, 6), new GuideMultilineTextState.Line(7, 10),
                new GuideMultilineTextState.Line(11, 11)), state.lines());
    }
    @Test void selectionReplacementSupportsMultilineClipboardPayloadAndUndoRedo() {
        var state = state("alpha beta", 20);
        state.seek(6, false);
        state.seek(10, true);
        assertEquals("beta", state.selected());
        state.insert("first\r\nsecond");
        assertEquals("alpha first\nsecond", state.value());
        state.undo();
        assertEquals("alpha beta", state.value());
        assertEquals("beta", state.selected());
        state.redo();
        assertEquals("alpha first\nsecond", state.value());
    }
    @Test void resizeReflowNeverMutatesSelectionCursorTextOrCallsListener() {
        var state = state("abc def ghi", 20);
        state.seek(2, false);
        state.seek(8, true);
        var changes = new ArrayList<String>();
        state.listener(changes::add);
        state.wrap(4, String::length);
        assertEquals(8, state.cursor());
        assertEquals("c def ", state.selected());
        assertEquals("abc def ghi", state.value());
        assertTrue(changes.isEmpty());
    }
    @Test void verticalMovementKeepsDesiredColumnAndHitTestingUsesNativeWidths() {
        var state = state("abcd\nx\nabcdef", 20);
        state.seek(3, false);
        state.moveVertical(1, false);
        assertEquals(6, state.cursor());
        state.moveVertical(1, true);
        assertEquals(10, state.cursor());
        assertEquals(2, state.indexAt(0, 2));
        assertEquals("\nabc", state.selected());
    }
    @Test void characterLimitsDoNotCountLinesOrSplitSupplementaryCharacters() {
        var state = state("", 3);
        state.characterLimit(5);
        state.insert("a\n\uD83D\uDE00bc");
        assertEquals("a\n\uD83D\uDE00b", state.value());
        state.seek(0, false);
        state.seek(2, true);
        state.insert("XYZ");
        assertEquals("XY\uD83D\uDE00b", state.value());
    }
    @Test void codePointDeleteAndWordNavigationPreserveSelectionSemantics() {
        var state = state("a\uD83D\uDE00b word", 20);
        state.seek(3, false);
        state.delete(-1, false);
        assertEquals("ab word", state.value());
        state.end(false, true);
        state.moveHorizontal(-1, true, true);
        assertEquals("word", state.selected());
        state.delete(1, false);
        assertEquals("ab ", state.value());
    }
    @Test void newEditClearsRedoAndExternalReplacementRetiresUndo() {
        var state = state("one", 20);
        state.insert(" two");
        state.undo();
        state.insert(" three");
        state.redo();
        assertEquals("one three", state.value());
        state.setValue("external");
        state.undo();
        assertEquals("external", state.value());
    }
    @Test void externalFullTextReplacementIsLosslessAndNeverClipboardFiltered() {
        var state = state("", 20);
        state.setValue("a\t\r\n\u00a7b");
        assertEquals("a\t\r\n\u00a7b", state.value());
    }
    @Test void selectingAllCanReplaceWithUnlimitedMultilineText() {
        var state = state("old", 2);
        state.selectAll();
        state.insert("a\nb\nc\nd\ne\nf\ng");
        assertEquals(7, state.lines().size());
        assertEquals("a\nb\nc\nd\ne\nf\ng", state.value());
    }
}
