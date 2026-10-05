package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

/** Real native rectangle/state math and native-neutral scope lifetime; no renderer acceptance. */
final class GuideViewportPaintTest {
    @Test void firstOffscreenScrollingLabelNeedsTheViewportParent() {
        var pose = new Matrix3x2f();
        var unparented = new ActiveTextCollector.Parameters(pose);
        var offscreen = unparented.withScissor(105, 145, 8, 24).scissor();
        assertEquals(new ScreenRectangle(105, 8, 40, 16), offscreen);
        // The native renderer clamps right to 100, so this positive rectangle emits zero width.
        assertEquals(0, Math.max(0, Math.min(offscreen.right(), 100) - offscreen.left()));
        var parented = new ActiveTextCollector.Parameters(pose, 1.0F, viewport());
        assertEquals(ScreenRectangle.empty(), parented.withScissor(105, 145, 8, 24).scissor());
        assertNull(parented.withScissor(105, 145, 8, 24).scissor().intersection(viewport()));
    }

    @Test void partiallyOffscreenLabelRetainsExactlyItsVisibleIntersection() {
        var parameters = new ActiveTextCollector.Parameters(new Matrix3x2f(), 1.0F, viewport());
        assertEquals(new ScreenRectangle(90, 8, 10, 16), parameters.withScissor(90, 145, 8, 24).scissor());
        assertEquals(new ScreenRectangle(0, 0, 20, 10), parameters.withScissor(-10, 20, -5, 10).scissor());
        assertEquals(new ScreenRectangle(95, 75, 5, 5), parameters.withScissor(95, 105, 75, 95).scissor());
    }

    @Test void zeroReversedTouchingAndOffscreenClipsStayNoDraw() {
        var parameters = new ActiveTextCollector.Parameters(new Matrix3x2f(), 1.0F, viewport());
        for (int[] clip : List.of(new int[] {4, 4, 2, 12}, new int[] {4, 14, 2, 2},
                new int[] {14, 4, 2, 12}, new int[] {4, 14, 12, 2},
                new int[] {100, 120, 2, 12}, new int[] {4, 14, 80, 90},
                new int[] {-20, -1, 2, 12}, new int[] {4, 14, -20, -1})) {
            var scissor = parameters.withScissor(clip[0], clip[1], clip[2], clip[3]).scissor();
            assertEquals(ScreenRectangle.empty(), scissor, java.util.Arrays.toString(clip));
            assertNull(scissor.intersection(viewport()));
        }
    }

    @Test void nestedClipsCannotReopenAnEmptyParent() {
        var parent = new ActiveTextCollector.Parameters(new Matrix3x2f(), 1.0F, viewport());
        var child = parent.withScissor(10, 30, 10, 30);
        var empty = child.withScissor(40, 60, 40, 60);
        assertEquals(ScreenRectangle.empty(), empty.scissor());
        assertEquals(ScreenRectangle.empty(), empty.withScissor(0, 100, 0, 80).scissor());
        assertEquals(new ScreenRectangle(10, 10, 20, 20), child.scissor());
        assertEquals(viewport(), parent.scissor());
    }

    @Test void identityViewportThenOriginalPosePreservesScaledNativeClips() {
        var pose = new Matrix3x2fStack(8);
        pose.translate(17.25F, 12.5F).scale(1.5F, 0.5F);
        var before = new Matrix3x2f(pose);
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        var clips = new java.util.ArrayDeque<ScreenRectangle>();
        scopes.paint(new Object(), 100, 80, () -> {
            pose.pushMatrix();
            try {
                pose.identity();
                clips.addLast(viewport().transformAxisAligned(pose));
            } finally { pose.popMatrix(); }
        }, clips::removeLast, () -> {
            assertEquals(viewport(), clips.peekLast());
            assertEquals(before, new Matrix3x2f(pose), "Original pose must return before extraction");
            var child = new ActiveTextCollector.Parameters(pose, 1.0F, clips.peekLast()).withScissor(0, 10, 0, 10);
            assertEquals(new ScreenRectangle(17, 12, 15, 5), child.scissor());
        });
        assertTrue(clips.isEmpty());
        assertEquals(before, new Matrix3x2f(pose));
    }

    @Test void nativeNullBoundsRejectEmptyDrawStatesInsteadOfSendingZeroScissors() {
        var rejected = new ColoredRectangleRenderState(null, null, new Matrix3x2f(),
                0, 0, 100, 80, -1, -1, ScreenRectangle.empty());
        assertNull(rejected.bounds());
        var state = new GuiRenderState();
        state.addGuiElement(rejected);
        var count = new AtomicInteger();
        state.forEachElement(ignored -> count.incrementAndGet(), GuiRenderState.TraverseRange.ALL);
        assertEquals(0, count.get());
        var visible = new ColoredRectangleRenderState(null, null, new Matrix3x2f(),
                90, 5, 120, 25, -1, -1, viewport());
        assertEquals(new ScreenRectangle(90, 5, 10, 20), visible.bounds());
        state.addGuiElement(visible);
        state.forEachElement(ignored -> count.incrementAndGet(), GuiRenderState.TraverseRange.ALL);
        assertEquals(1, count.get());
    }

    @Test void sameCanvasNestedPaintEntersOnlyOnceAndExitsAfterAllDraws() {
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        Object canvas = new Object();
        var calls = new ArrayList<String>();
        scopes.paint(canvas, 100, 80, () -> calls.add("enter"), () -> calls.add("exit"), () -> {
            calls.add("outer draw");
            scopes.paint(canvas, 100, 80, () -> fail("redundant viewport"), () -> fail("early exit"),
                    () -> calls.add("nested native widget"));
            calls.add("outer native viewer");
        });
        assertEquals(List.of("enter", "outer draw", "nested native widget", "outer native viewer", "exit"), calls);
    }

    @Test void differentCanvasRestoresOuterOwnerAndFinalExitRemovesOwnership() {
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        Object a = new Object(), b = new Object();
        var calls = new ArrayList<String>();
        scopes.paint(a, 100, 80, () -> calls.add("a enter"), () -> calls.add("a exit"), () -> {
            scopes.paint(b, 60, 40, () -> calls.add("b enter"), () -> calls.add("b exit"), () -> calls.add("b draw"));
            scopes.paint(a, 100, 80, () -> fail("lost outer owner"), () -> fail("early outer exit"), () -> calls.add("a draw"));
        });
        scopes.paint(a, 100, 80, () -> calls.add("a reenter"), () -> calls.add("a reexit"), () -> {});
        assertEquals(List.of("a enter", "b enter", "b draw", "b exit", "a draw", "a exit", "a reenter", "a reexit"), calls);
    }

    @Test void emptyViewportDoesNotEnterOrExtractDraws() {
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        for (int[] size : List.of(new int[] {0, 0}, new int[] {0, 80}, new int[] {100, 0},
                new int[] {-1, 80}, new int[] {100, -1})) {
            scopes.paint(new Object(), size[0], size[1], () -> fail("empty enter"),
                    () -> fail("empty exit"), () -> fail("empty draw"));
        }
        assertThrows(NullPointerException.class, () -> scopes.paint(new Object(), 0, 0, () -> {}, () -> {}, null));
    }

    @Test void exceptionUnwindsViewportAndBalancedNestedClipRestoresPriorParent() {
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        Object canvas = new Object();
        var parent = new ScreenRectangle(10, 10, 50, 50);
        var stack = new java.util.ArrayDeque<ScreenRectangle>();
        stack.addLast(parent);
        var failure = new IllegalStateException("paint failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> scopes.paint(canvas, 100, 80,
                () -> stack.addLast(viewport().intersection(stack.peekLast())), stack::removeLast, () -> {
                    assertEquals(parent, stack.peekLast());
                    stack.addLast(new ScreenRectangle(20, 20, 10, 10).intersection(stack.peekLast()));
                    try { throw failure; }
                    finally { stack.removeLast(); }
                })));
        assertEquals(1, stack.size());
        assertSame(parent, stack.peekLast());
        var calls = new ArrayList<String>();
        scopes.paint(canvas, 100, 80, () -> calls.add("enter"), () -> calls.add("exit"), () -> calls.add("draw"));
        assertEquals(List.of("enter", "draw", "exit"), calls);
    }

    @Test void failedDifferentCanvasAndFailedExitStillRestoreTheOuterOwner() {
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        Object a = new Object(), b = new Object();
        var failure = new IllegalStateException("exit failed");
        scopes.paint(a, 100, 80, () -> {}, () -> {}, () -> {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> scopes.paint(b, 100, 80, () -> {}, () -> { throw failure; }, () -> {})));
            scopes.paint(a, 100, 80, () -> fail("lost prior owner"), () -> fail("early exit"), () -> {});
        });
        var entered = new AtomicInteger();
        scopes.paint(a, 100, 80, entered::incrementAndGet, () -> {}, () -> {});
        assertEquals(1, entered.get());
    }

    @Test void failedEntryDoesNotPaintOrInstallAnOwner() {
        var scopes = new GuideNativeGraphics.ViewportPaint<Object>();
        Object canvas = new Object();
        var failure = new IllegalStateException("enter failed");
        var pose = new Matrix3x2fStack(4);
        pose.translate(9.5F, 12.25F).scale(2.0F, 1.5F);
        var before = new Matrix3x2f(pose);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> scopes.paint(canvas, 100, 80,
                () -> {
                    pose.pushMatrix();
                    try { pose.identity(); throw failure; }
                    finally { pose.popMatrix(); }
                }, () -> fail("unentered exit"), () -> fail("unentered draw"))));
        assertEquals(before, new Matrix3x2f(pose));
        var entered = new AtomicInteger();
        scopes.paint(canvas, 100, 80, entered::incrementAndGet, () -> {}, () -> {});
        assertEquals(1, entered.get());
    }

    private static ScreenRectangle viewport() { return new ScreenRectangle(0, 0, 100, 80); }
}
