package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideHistoryWindowSnapshot;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuidePendingMessage;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideTopology;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
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

    @Test void followUpAcceptanceMatchesTheRealReceiptNotAnotherPendingMessage() {
        UUID receipt = UUID.randomUUID();
        var unrelated = pending(UUID.randomUUID(), GuidePendingMessage.Kind.STEER);
        var queued = pending(receipt, GuidePendingMessage.Kind.FOLLOW_UP);
        var notice = GuideUiNotice.acceptedSubmission(GuideClientUiState.SubmissionRoute.FOLLOW_UP, null,
                new ToolResult.Success<>(receipt), snapshot(List.of(unrelated, queued), List.of()), "main");
        assertInfo("screen.openallay.composer.accepted.follow_up", notice);
    }

    @Test void actualSteerAndSteerFallbackUseTheActualQueueKind() {
        UUID receipt = UUID.randomUUID();
        var result = new ToolResult.Success<>(receipt);
        assertInfo("screen.openallay.composer.accepted.steer", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.STEER, null, result,
                snapshot(List.of(pending(receipt, GuidePendingMessage.Kind.STEER)), List.of()), "main"));
        assertInfo("screen.openallay.composer.accepted.follow_up", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.STEER, null, result,
                snapshot(List.of(pending(receipt, GuidePendingMessage.Kind.FOLLOW_UP)), List.of()), "main"));
    }

    @Test void onlyAKnownRealRequestIdMaySaySent() {
        UUID receipt = UUID.randomUUID();
        var request = GuideRequestSnapshot.start(receipt, "main", GuideTopology.CLIENT_LOCAL, "task", Instant.EPOCH);
        assertInfo("screen.openallay.composer.accepted.sent", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.ASK, null, new ToolResult.Success<>(receipt),
                snapshot(List.of(), List.of(request)), "main"));
        var otherRequest = GuideRequestSnapshot.start(UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL, "task", Instant.EPOCH);
        assertInfo("screen.openallay.composer.accepted.message", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.FOLLOW_UP, null, new ToolResult.Success<>(receipt),
                snapshot(List.of(), List.of(otherRequest)), "main"));
    }

    @Test void missingOrAlreadyConsumedReceiptsOnlyProveAcceptance() {
        UUID receipt = UUID.randomUUID();
        for (var route : List.of(GuideClientUiState.SubmissionRoute.ASK,
                GuideClientUiState.SubmissionRoute.FOLLOW_UP, GuideClientUiState.SubmissionRoute.STEER)) {
            assertInfo("screen.openallay.composer.accepted.message", GuideUiNotice.acceptedSubmission(
                    route, null, new ToolResult.Success<>(receipt), snapshot(List.of(), List.of()), "main"));
        }
    }

    @Test void acceptedPendingEditsKeepTheirCapturedTargetAndItsActualKind() {
        UUID target = UUID.randomUUID();
        assertInfo("screen.openallay.composer.accepted.follow_up", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.EDIT_PENDING, target, new ToolResult.Success<>(true),
                snapshot(List.of(pending(target, GuidePendingMessage.Kind.FOLLOW_UP)), List.of()), "main"));
        assertInfo("screen.openallay.composer.accepted.steer", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.EDIT_PENDING, target, new ToolResult.Success<>(true),
                snapshot(List.of(pending(target, GuidePendingMessage.Kind.STEER)), List.of()), "main"));
        var rejected = GuideUiNotice.acceptedSubmission(GuideClientUiState.SubmissionRoute.EDIT_PENDING, target,
                new ToolResult.Success<>(false), snapshot(List.of(), List.of()), "main");
        assertEquals(GuideUiNotice.Severity.WARNING, rejected.severity());
        assertEquals(Component.translatable("screen.openallay.pending.already_consumed").getString(), rejected.message());
    }

    @Test void backendRejectionAndLaterQueuedFailureKeepTheirCompleteCodeAndMessage() {
        UUID receipt = UUID.randomUUID();
        String body = "Complete failure body ".repeat(40).trim();
        var rejected = GuideUiNotice.acceptedSubmission(GuideClientUiState.SubmissionRoute.STEER, null,
                new ToolResult.Failure<>("input_unavailable", body), snapshot(List.of(), List.of()), "main");
        assertEquals(GuideUiNotice.Severity.ERROR, rejected.severity());
        assertEquals("input_unavailable: " + body, rejected.message());
        var queued = pending(receipt, GuidePendingMessage.Kind.FOLLOW_UP).failed(new GuideFailure("model_unavailable", body));
        var failedQueue = GuideUiNotice.acceptedSubmission(GuideClientUiState.SubmissionRoute.FOLLOW_UP, null,
                new ToolResult.Success<>(receipt), snapshot(List.of(queued), List.of()), "main");
        assertEquals(GuideUiNotice.Severity.ERROR, failedQueue.severity());
        assertEquals("model_unavailable: " + body, failedQueue.message());
    }

    @Test void feedbackCannotBorrowAnotherSessionsMatchingId() {
        UUID receipt = UUID.randomUUID();
        var snapshot = snapshot(List.of(pending(receipt, GuidePendingMessage.Kind.STEER)), List.of());
        assertInfo("screen.openallay.composer.accepted.message", GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.FOLLOW_UP, null, new ToolResult.Success<>(receipt), snapshot, "other"));
    }

    @Test void malformedSuccessAndInvalidEditDoNotCreateAcceptedReceipts() {
        var snapshot = snapshot(List.of(), List.of());
        for (ToolResult<?> malformed : List.<ToolResult<?>>of(new ToolResult.Success<>(true),
                new ToolResult.Success<>(false), new ToolResult.Success<>("unknown"))) {
            assertEquals(GuideUiNotice.Severity.ERROR, GuideUiNotice.acceptedSubmission(
                    GuideClientUiState.SubmissionRoute.ASK, null, malformed, snapshot, "main").severity());
        }
        assertEquals(GuideUiNotice.Severity.ERROR, GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.EDIT_INVALID, UUID.randomUUID(),
                new ToolResult.Success<>(UUID.randomUUID()), snapshot, "main").severity());
        assertEquals(GuideUiNotice.Severity.ERROR, GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.EDIT_PENDING, null,
                new ToolResult.Success<>(true), snapshot, "main").severity());
        assertEquals(GuideUiNotice.Severity.ERROR, GuideUiNotice.acceptedSubmission(
                GuideClientUiState.SubmissionRoute.ASK, null, null, snapshot, "main").severity());
    }

    private static GuidePendingMessage pending(UUID receipt, GuidePendingMessage.Kind kind) {
        return new GuidePendingMessage(receipt, kind, ModelMessage.userText("literal queue text /steer"),
                Instant.EPOCH, kind == GuidePendingMessage.Kind.STEER ? UUID.randomUUID() : null);
    }
    private static GuideSnapshot snapshot(List<GuidePendingMessage> pending, List<GuideRequestSnapshot> requests) {
        var session = new GuideSessionSnapshot("main", List.of(), requests, List.of(),
                GuideModelSelection.client("default"), GuideHistoryWindowSnapshot.disabled(requests.size()), pending, null);
        return new GuideSnapshot(UUID.randomUUID(), "main", GuideModelMode.CLIENT, true, false, List.of(session), Instant.EPOCH);
    }
    private static void assertInfo(String key, GuideUiNotice notice) {
        assertEquals(GuideUiNotice.Severity.INFO, notice.severity());
        assertEquals(GuideUiNotice.Placement.COMPOSER, notice.placement());
        assertEquals(Component.translatable(key).getString(), notice.message());
        assertFalse(notice.empty());
    }
}
