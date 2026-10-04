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
        assertEquals("Card 0-0", notification.cardPreviews().getFirst().title());
        assertEquals("Description 0-0", notification.cardPreviews().getFirst().description());
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
        toast.beginVisible(clock.instant());
        controller.event(event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "Answer", 0));
        clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size()); assertEquals(1, toast.updates);
        assertEquals(1, toast.latest.cardCount()); assertTrue(toast.latest.replyCompleted());
        assertEquals("Card 0-0", toast.latest.cardPreviews().getFirst().title());
        assertEquals("Description 0-0", toast.latest.cardPreviews().getFirst().description());
        assertEquals("Answer", toast.latest.preview());
        clock.advance(5500); toast.completeVisibleAt(clock.instant()); controller.tick();
        assertFalse(fence.valid(), "native finished lifecycle retires exact task fence");
        assertEquals(0, toast.hides, "finished native object does not need a second hide command");
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

    @Test void fullGuideWithEmptyViewportKeysSuppressesSameSessionNotOtherSessionOrAlways() {
        controller.bound(service);
        controller.visible(service, "main", Set.of(), true);
        var latest = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        controller.event(latest); clock.advance(600); controller.tick();
        assertTrue(port.handles.isEmpty(), "full guide at old scroll/tail still suppresses same-session task toast");
        assertFalse(controller.seen(latest.key())); assertFalse(controller.delivered(latest.key()));
        assertEquals(1, controller.unread(service, "main"));
        var other = event("other", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        controller.event(other); clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size()); assertEquals("other", port.handles.getFirst().latest.sessionId());
        var inactive = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "inactive", 0);
        controller.visible(service, "main", Set.of(), false); controller.event(inactive);
        clock.advance(600); controller.tick(); assertEquals(2, port.handles.size());
        settings = settings.withPolicy(GuideUiConfig.NotificationPolicy.ALWAYS);
        var always = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "always", 0);
        controller.visible(service, "main", Set.of(always.key()), true); controller.event(always);
        clock.advance(600); controller.tick(); assertEquals(3, port.handles.size());
        assertFalse(controller.seen(always.key()));
    }

    @Test void enteringFullGuideImmediatelyFencesQueuedNativeSameSessionAndDropsPendingWithoutReadAck() {
        controller.bound(service);
        var main = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        var other = event("other", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        controller.event(main); controller.event(other); clock.advance(600); controller.tick();
        var mainToast = port.handles.getFirst(); var otherToast = port.handles.getLast();
        var pending = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.REPLY_FINAL, "pending", 0);
        controller.event(pending);
        controller.visible(service, "main", Set.of(), true);
        assertFalse(mainToast.latest.fence().valid()); assertEquals(1, mainToast.hides);
        assertTrue(otherToast.latest.fence().valid()); assertEquals(0, otherToast.hides);
        assertFalse(controller.seen(main.key())); assertFalse(controller.seen(pending.key()));
        assertEquals(2, controller.unread(service, "main"));
        controller.clearVisibility(service); clock.advance(600); controller.tick();
        assertEquals(2, port.handles.size(), "closing guide does not replay suppressed receipt");
        assertFalse(controller.delivered(pending.key()));
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
        var toast = port.handles.getFirst(); toast.beginVisible(clock.instant());
        clock.advance(3000); toast.completeVisibleAt(clock.instant()); controller.tick();
        assertFalse(toast.latest.fence().valid());
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
        var toast = port.handles.getFirst(); toast.beginVisible(clock.instant());
        clock.advance(6000); toast.completeVisibleAt(clock.instant()); controller.tick();
        assertFalse(toast.latest.fence().valid());
        assertFalse(controller.seen(event.key())); assertEquals(1, controller.unread(service, "main"));
    }

    @Test void cardsWithoutMetadataStayUnreadButCannotCreateEmptyCountOnlyToast() {
        controller.bound(service); UUID request = UUID.randomUUID();
        var withMetadata = event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 2);
        var empty = new GuidePresentationEvent(withMetadata.key(), withMetadata.kind(), withMetadata.content(),
                "", List.of(), withMetadata.createdAt());
        controller.event(empty); clock.advance(600); controller.tick();
        assertTrue(port.handles.isEmpty()); assertFalse(controller.delivered(empty.key()));
        assertFalse(controller.seen(empty.key())); assertEquals(1, controller.unread(service, "main"));
        controller.event(event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "Actual final answer", 0));
        clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size()); assertEquals("Actual final answer", port.handles.getFirst().latest.preview());
    }

    @Test void firstCanonicalCardStaysRepresentativeAcrossLaterCardReplyAndFailureUpgrades() {
        controller.bound(service); UUID request = UUID.randomUUID();
        var first = event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 2);
        controller.event(first); clock.advance(600); controller.tick();
        var toast = port.handles.getFirst(); var fence = toast.latest.fence();
        controller.event(event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 1));
        controller.event(event("main", request, GuidePresentationEvent.Kind.REPLY_FINAL, "Actual reply", 0));
        clock.advance(600); controller.tick();
        assertEquals(1, port.handles.size()); assertSame(fence, toast.latest.fence());
        assertEquals(3, toast.latest.cardCount()); assertEquals(first.cardPreviews().getFirst(), toast.latest.cardPreviews().getFirst());
        assertEquals("Actual reply", toast.latest.preview());
        controller.event(event("main", request, GuidePresentationEvent.Kind.TASK_FAILED, "Actual failure", 0));
        clock.advance(600); controller.tick();
        assertEquals("Actual failure", toast.latest.preview()); assertTrue(toast.latest.taskFailed());
        assertEquals(first.cardPreviews().getFirst(), toast.latest.cardPreviews().getFirst());
        assertFalse(controller.seen(first.key()));
    }

    @Test void duplicateContentRefDoesNotInflateCardCountOrReplaceMetadataAcrossReceipts() {
        controller.bound(service); UUID request = UUID.randomUUID();
        var first = event("main", request, GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        controller.event(first);
        var second = new GuidePresentationEvent(new GuidePresentationEvent.Key(service.presentationGeneration(), actor,
                first.key().sessionOwner(), "main", request, ++sequence), GuidePresentationEvent.Kind.CARD_BATCH,
                first.content(), "", List.of(new GuidePresentationEvent.CardPreview(first.content().getFirst(),
                "Different title", "Different description")), clock.instant());
        controller.event(second); clock.advance(600); controller.tick();
        assertEquals(1, port.handles.getFirst().latest.cardCount());
        assertEquals(first.cardPreviews(), port.handles.getFirst().latest.cardPreviews());
    }

    @Test void wrongActorOwnerAndGenerationCannotDeliverCardMetadataOrUnread() {
        controller.bound(service);
        var valid = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        for (int mismatch = 0; mismatch < 3; mismatch++) {
            var key = new GuidePresentationEvent.Key(mismatch == 0 ? UUID.randomUUID() : valid.key().connectionGeneration(),
                    mismatch == 1 ? UUID.randomUUID() : actor, mismatch == 2 ? UUID.randomUUID() : valid.key().sessionOwner(),
                    "main", valid.key().requestId(), ++sequence);
            controller.event(new GuidePresentationEvent(key, valid.kind(), valid.content(), "", valid.cardPreviews(), clock.instant()));
        }
        clock.advance(600); controller.tick();
        assertTrue(port.handles.isEmpty()); assertTrue(controller.receipts(service, "main").isEmpty());
    }

    @Test void nativeQueueWaitDoesNotConsumeDisplayDurationOrCancelUnpaintedThirdTask() {
        controller.bound(service);
        for (int index = 0; index < 3; index++) {
            controller.event(event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1));
        }
        clock.advance(600); controller.tick();
        assertEquals(3, port.handles.size());
        var first = port.handles.get(0); var second = port.handles.get(1); var queued = port.handles.get(2);
        first.beginVisible(clock.instant()); second.beginVisible(clock.instant());
        clock.advance(6000);
        first.completeVisibleAt(clock.instant()); second.completeVisibleAt(clock.instant());
        queued.completeVisibleAt(clock.instant()); controller.tick();
        assertFalse(first.latest.fence().valid()); assertFalse(second.latest.fence().valid());
        assertTrue(queued.latest.fence().valid(), "third two-slot toast remains queued beyond its configured duration");
        assertFalse(queued.finished()); assertEquals(0, queued.hides);
        queued.beginVisible(clock.instant()); clock.advance(5000); queued.completeVisibleAt(clock.instant()); controller.tick();
        assertTrue(queued.latest.fence().valid(), "native fully-visible clock starts on admission, not enqueue");
        clock.advance(1000); queued.completeVisibleAt(clock.instant()); controller.tick();
        assertFalse(queued.latest.fence().valid()); assertEquals(0, queued.hides);
        assertEquals(3, controller.unread(service, "main"), "native retirement remains distinct from read acknowledgement");
    }

    @Test void localPreviewAlsoWaitsForNativeLifecycleInsteadOfEnqueueClock() {
        settings = settings.withEnabled(false).withDurationSeconds(3);
        controller.settingsChanged(); // Publish disabled state before creating its independent local preview.
        controller.testNotification(settings);
        var queued = port.handles.getFirst();
        clock.advance(10000); controller.tick();
        assertTrue(queued.latest.fence().valid()); assertFalse(queued.finished());
        queued.beginVisible(clock.instant()); clock.advance(3000); queued.completeVisibleAt(clock.instant()); controller.tick();
        assertFalse(queued.latest.fence().valid()); assertEquals(0, queued.hides);
        assertTrue(controller.receipts(service, "main").isEmpty());
    }

    @Test void explicitDisableAfterLocalPreviewCreationStillCancelsQueuedOwnedObjectImmediately() {
        controller.testNotification(settings);
        var queued = port.handles.getFirst();
        assertTrue(queued.latest.fence().valid()); assertFalse(queued.finished());
        settings = settings.withEnabled(false); controller.settingsChanged();
        assertFalse(queued.latest.fence().valid()); assertEquals(1, queued.hides);
        assertFalse(queued.finished(), "explicit cancellation is not a fabricated native completion");
        assertTrue(controller.receipts(service, "main").isEmpty());
    }

    @Test void notificationMetadataIsImmutableAndRejectsDuplicateSourcesAndImpossibleCounts() {
        controller.bound(service);
        var cards = event("main", UUID.randomUUID(), GuidePresentationEvent.Kind.CARD_BATCH, "", 1);
        controller.event(cards); clock.advance(600); controller.tick();
        var display = port.handles.getFirst().latest;
        assertThrows(UnsupportedOperationException.class, () -> display.cardPreviews().clear());
        assertThrows(IllegalArgumentException.class, () -> new GuideNotificationPort.Notification(
                display.connectionGeneration(), display.actorId(), display.sessionOwner(), display.sessionId(), display.requestId(),
                "", 0, display.cardPreviews(), false, false, false, 6, display.fence()));
        assertThrows(IllegalArgumentException.class, () -> new GuideNotificationPort.Notification(
                display.connectionGeneration(), display.actorId(), display.sessionOwner(), display.sessionId(), display.requestId(),
                "", 2, List.of(cards.cardPreviews().getFirst(), cards.cardPreviews().getFirst()), false, false, false, 6, display.fence()));
    }

    private GuidePresentationEvent event(String session, UUID request, GuidePresentationEvent.Kind kind,
                                         String preview, int cards) {
        if (service.presentationSessionOwner(session).isEmpty()) service.selectSession(session).join();
        UUID owner = service.presentationSessionOwner(session).orElseThrow();
        List<GuidePresentationEvent.ContentRef> content = new ArrayList<>();
        if (cards > 0) for (int index = 0; index < cards; index++) content.add(new GuidePresentationEvent.ContentRef(0, "node:" + sequence + "-" + index));
        else content.add(new GuidePresentationEvent.ContentRef(kind == GuidePresentationEvent.Kind.TASK_FAILED ? -1 : 0,
                kind == GuidePresentationEvent.Kind.TASK_FAILED ? "task-failed" : "reply"));
        List<GuidePresentationEvent.CardPreview> cardPreviews = kind == GuidePresentationEvent.Kind.CARD_BATCH
                ? content.stream().map(ref -> new GuidePresentationEvent.CardPreview(ref,
                        "Card " + ref.contentId().substring(5), "Description " + ref.contentId().substring(5))).toList() : List.of();
        return new GuidePresentationEvent(new GuidePresentationEvent.Key(service.presentationGeneration(), actor,
                owner, session, request, ++sequence), kind, content, preview, cardPreviews, clock.instant());
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
        Instant visibleSince;
        boolean nativeFinished;
        RecordingHandle(GuideNotificationPort.Notification notification) { latest = notification; }
        public void update(GuideNotificationPort.Notification notification) { latest = notification; updates++; }
        public boolean finished() { return nativeFinished; }
        public void hide() { hides++; }
        void beginVisible(Instant now) {
            if (visibleSince == null) visibleSince = now;
        }
        void completeVisibleAt(Instant now) {
            nativeFinished = visibleSince != null && java.time.Duration.between(visibleSince, now).toMillis()
                    >= latest.durationSeconds() * 1000L;
        }
    }
}
