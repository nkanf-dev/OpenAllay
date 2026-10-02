package dev.openallay.client.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.openallay.guide.*;
import dev.openallay.guide.ui.GuideUiConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideNotificationControllerTest {
    private final MutableClock clock = new MutableClock();
    private final RecordingPort port = new RecordingPort();
    private GuideUiConfig.Notifications settings = GuideUiConfig.Notifications.defaults().withEnabled(true);
    private final GuideNotificationController controller = new GuideNotificationController(() -> settings, clock, port);
    private final UUID actor = UUID.randomUUID(), sessionOwner = UUID.randomUUID();
    private final GuideService service = service(actor);
    private long sequence;

    @Test void overlayOffNotificationsOnDeliversCardsFinalAndTaskSummaryAsOneOwnedToast() {
        controller.bound(service);
        UUID request = UUID.randomUUID();
        GuidePresentationEvent first = event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 2);
        controller.event(first); clock.advance(200);
        GuidePresentationEvent second = event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        controller.event(second); clock.advance(200);
        GuidePresentationEvent reply = event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "Complete original answer", 0);
        controller.event(reply);
        controller.event(event("main", request, GuidePresentationEvent.Kind.TASK_COMPLETED, "", 0));
        controller.tick(); assertTrue(port.handles.isEmpty());
        clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size());
        var notification = port.handles.getFirst().latest;
        assertEquals(3, notification.cardCount());
        assertTrue(notification.replyCompleted()); assertTrue(notification.taskCompleted());
        assertEquals("Complete original answer", notification.preview());
        assertTrue(controller.delivered(first.key())); assertFalse(controller.seen(first.key()));
        assertEquals(1, controller.unread(service, "main"));
        controller.event(reply); clock.advance(1000); controller.tick();
        assertEquals(1, port.handles.size(), "duplicate sequence never replays");
        assertEquals(0, port.handles.getFirst().updates);
    }

    @Test void lateSameTaskCardUpgradesCurrentObjectWithoutRestartingExpiry() {
        controller.bound(service); UUID request = UUID.randomUUID();
        controller.event(event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 1));
        clock.advance(600); controller.tick();
        var toast = port.handles.getFirst(); var fence = toast.latest.fence();
        controller.event(event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "Answer", 0));
        clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size()); assertEquals(1, toast.updates);
        assertEquals(1, toast.latest.cardCount()); assertTrue(toast.latest.replyCompleted());
        clock.advance(5500); controller.tick();
        assertFalse(fence.valid(), "same task update does not extend native lifespan indefinitely");
        assertEquals(1, toast.hides);
        assertEquals(1, controller.unread(service, "main"), "timeout is never a read ack");
    }

    @Test void disabledDoesNotReplayAndHideOnlyOwnedObjectsImmediately() {
        controller.bound(service); UUID request = UUID.randomUUID();
        var reply = event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "answer", 0);
        controller.event(reply); clock.advance(600); controller.tick();
        var shown = port.handles.getFirst();
        controller.event(event("other", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "queued", 0));
        settings = settings.withEnabled(false); controller.settingsChanged();
        assertFalse(shown.latest.fence().valid()); assertEquals(1, shown.hides);
        var whileOff = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "while off", 0);
        controller.event(whileOff);
        clock.advance(1000); controller.tick();
        settings = settings.withEnabled(true); controller.settingsChanged();
        clock.advance(1000); controller.tick();
        assertEquals(1, port.handles.size());
        assertFalse(controller.delivered(whileOff.key())); assertFalse(controller.seen(whileOff.key()));
        controller.event(event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "future", 0));
        clock.advance(600); controller.tick(); assertEquals(2, port.handles.size());
    }

    @Test void realSameSessionViewportSuppressesOnlyExactKeysWithoutMarkingSeen() {
        controller.bound(service); UUID request = UUID.randomUUID();
        var reply = event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "newest", 0);
        var summary = event("main", request, GuidePresentationEvent.Kind.TASK_COMPLETED, "", 0);
        controller.event(reply); controller.event(summary);
        controller.visible(service, "main", Set.of(reply.key(), summary.key()), true);
        clock.advance(600); controller.tick();
        assertTrue(port.handles.isEmpty()); assertFalse(controller.seen(reply.key()));
        assertFalse(controller.delivered(reply.key()));
        assertEquals(1, controller.unread(service, "main"));
        controller.markSeen(Set.of(reply.key(), summary.key()));
        assertEquals(0, controller.unread(service, "main"));
    }

    @Test void oldScrollOtherSessionInactiveWindowAndAlwaysDoNotBlanketSuppress() {
        controller.bound(service);
        var old = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "old", 0);
        controller.event(old); controller.visible(service, "main", Set.of(old.key()), true);
        clock.advance(600); controller.tick(); assertEquals(0, port.handles.size());
        var latest = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "latest", 0);
        controller.event(latest); clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size(), "same session viewing old history is not viewing new result");
        var other = event("other", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "other result", 0);
        controller.event(other); controller.visible(service, "main", Set.of(other.key()), true);
        clock.advance(600); controller.tick(); assertEquals(2, port.handles.size());
        var inactive = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "inactive", 0);
        controller.event(inactive); controller.visible(service, "main", Set.of(inactive.key()), false);
        clock.advance(600); controller.tick(); assertEquals(3, port.handles.size());
        settings = settings.withPolicy(GuideUiConfig.NotificationPolicy.ALWAYS);
        var always = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "always", 0);
        controller.event(always); controller.visible(service, "main", Set.of(always.key()), true);
        clock.advance(600); controller.tick(); assertEquals(4, port.handles.size());
        assertFalse(controller.seen(always.key()));
    }

    @Test void actorGenerationReplacementFencesPendingAndAlreadyNativeQueuedObjects() {
        controller.bound(service);
        var old = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "old", 0);
        controller.event(old); clock.advance(600); controller.tick();
        var oldFence = port.handles.getFirst().latest.fence();
        controller.event(event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "old pending", 0));
        controller.invalidated(service.presentationGeneration());
        assertFalse(oldFence.valid());
        assertEquals(0, controller.unread(service, "main"));
        GuideService reconnect = service(actor); controller.bound(reconnect);
        controller.event(old); clock.advance(1000); controller.tick();
        assertEquals(1, port.handles.size(), "same actor and session cannot replay previous generation");
        var fresh = new GuidePresentationEvent(new GuidePresentationEvent.Key(reconnect.presentationGeneration(), actor,
                reconnect.presentationSessionOwner("main").orElseThrow(), "main", UUID.randomUUID(), 1), GuidePresentationEvent.Kind.REPLY_FINAL,
                List.of(new GuidePresentationEvent.ContentRef(0, "reply")), "new", clock.instant());
        controller.event(fresh); clock.advance(600); controller.tick();
        assertEquals(2, port.handles.size());
        controller.close(); assertFalse(port.handles.getLast().latest.fence().valid());
        controller.event(fresh); assertTrue(controller.receipts(reconnect, "main").isEmpty());
    }

    @Test void deletingAndRecreatingSameSessionNameDoesNotRetainOrDeliverOldOwner() {
        controller.bound(service);
        var old = event("replaceable", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "old", 0);
        controller.event(old); clock.advance(600); controller.tick();
        var fence = port.handles.getFirst().latest.fence();
        service.closeSession("replaceable").join(); service.selectSession("replaceable").join();
        controller.event(old); controller.tick();
        assertFalse(fence.valid());
        assertEquals(0, controller.unread(service, "replaceable"));
        var fresh = event("replaceable", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "new", 0);
        controller.event(fresh); clock.advance(600); controller.tick();
        assertEquals(2, port.handles.size());
        assertEquals(1, controller.unread(service, "replaceable"));
    }

    @Test void explicitClearOfSessionFencesItsOldOwnedReceiptWithoutChangingTaskExecution() {
        controller.bound(service);
        var old = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "old", 0);
        controller.event(old); clock.advance(600); controller.tick();
        var fence = port.handles.getFirst().latest.fence();
        service.clearSelectedSession().join(); controller.tick();
        assertFalse(fence.valid()); assertEquals(0, controller.unread(service, "main"));
        controller.event(old); clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size());
    }

    @Test void optionsGateTaskCompletedWithReplyAndNeverCreateUnconfigurableCompletion() {
        controller.bound(service); settings = settings.withEvents(false, false, true);
        UUID request = UUID.randomUUID();
        controller.event(event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "reply", 0));
        controller.event(event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 2));
        controller.event(event("main", request, GuidePresentationEvent.Kind.TASK_COMPLETED, "", 0));
        clock.advance(1000); controller.tick(); assertTrue(port.handles.isEmpty());
        controller.event(event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.TASK_FAILED, "failure", 0));
        clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size()); assertTrue(port.handles.getFirst().latest.taskFailed());
    }

    @Test void boundedTaskQueueSummarizesOverflowWithoutDroppingOriginalReceiptsOrHistory() {
        controller.bound(service);
        for (int index = 0; index < 12; index++) {
            controller.event(event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "result " + index, 0));
        }
        clock.advance(600); controller.tick();
        assertEquals(8, port.handles.size());
        assertEquals(4, port.handles.getFirst().latest.additionalTasks());
        assertEquals(12, controller.receipts(service, "main").size());
        assertEquals(12, controller.unread(service, "main"));
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty(), "controller never asks or edits history");
    }

    @Test void localPreviewWorksDisabledUnboundAndDoesNotInventUnread() {
        settings = settings.withEnabled(false).withDurationSeconds(3);
        controller.testNotification(settings);
        assertEquals(1, port.handles.size()); assertEquals(3, port.handles.getFirst().latest.durationSeconds());
        assertTrue(controller.receipts(service, "main").isEmpty());
        assertEquals(0, controller.unread(service, "main"));
        clock.advance(3000); controller.tick(); assertFalse(port.handles.getFirst().latest.fence().valid());
    }

    @Test void nativeExpiryAndPreviewTruncationNeverConsumeReceiptAndUnicodeStaysValid() {
        controller.bound(service);
        String original = "🌲".repeat(600);
        var event = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, original, 0);
        controller.event(event); clock.advance(600); controller.tick();
        assertEquals(original, event.preview(), "input semantic answer is untouched");
        String preview = port.handles.getFirst().latest.preview();
        assertEquals(513, preview.codePointCount(0, preview.length()));
        assertEquals('…', preview.charAt(preview.length() - 1));
        clock.advance(6000); controller.tick();
        assertFalse(controller.seen(event.key())); assertEquals(1, controller.unread(service, "main"));
    }

    private GuidePresentationEvent event(String session, UUID request, GuidePresentationEvent.Kind kind,
                                         String preview, int cards) {
        if (service.presentationSessionOwner(session).isEmpty()) service.selectSession(session).join();
        UUID owner = service.presentationSessionOwner(session).orElseThrow();
        List<GuidePresentationEvent.ContentRef> content = new ArrayList<>();
        if (cards > 0) for (int index = 0; index < cards; index++) content.add(new GuidePresentationEvent.ContentRef(0, "node:" + sequence + "-" + index));
        else content.add(new GuidePresentationEvent.ContentRef(kind == GuidePresentationEvent.Kind.TASK_FAILED ? -1 : 0,
                kind == GuidePresentationEvent.Kind.TASK_FAILED ? "task-failed" : "reply"));
        return new GuidePresentationEvent(new GuidePresentationEvent.Key(service.presentationGeneration(), actor,
                owner, session, request, ++sequence), kind, content, preview, clock.instant());
    }
    private static GuideService service(UUID actor) {
        return new GuideService(actor, null, new GuideRemoteEndpoint() {
            public boolean serverModelAvailable() { return false; }
            public boolean serverToolsAvailable() { return false; }
            public boolean ask(UUID request, String session, String question, java.util.function.Consumer<dev.openallay.agent.AgentEvent> events) { return false; }
            public boolean cancel(UUID request) { return false; }
            public void disconnect() {}
        }, (caps, correlation) -> new dev.openallay.tool.ToolResult.Success<>(
                dev.openallay.context.ToolInvocationContext.developmentConsole(correlation)), Runnable::run,
                Clock.systemUTC(), new Gson());
    }
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-02T00:00:00Z");
        void advance(long millis) { now = now.plusMillis(millis); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    private static final class RecordingPort implements GuideNotificationPort {
        final List<RecordingHandle> handles = new ArrayList<>();
        public Handle show(Notification notification) {
            RecordingHandle handle = new RecordingHandle(notification); handles.add(handle); return handle;
        }
    }
    private static final class RecordingHandle implements GuideNotificationPort.Handle {
        GuideNotificationPort.Notification latest;
        int updates, hides;
        RecordingHandle(GuideNotificationPort.Notification notification) { latest = notification; }
        public void update(GuideNotificationPort.Notification notification) { latest = notification; updates++; }
        public void hide() { hides++; }
    }
}
