package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideHistoryPageState;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiRow;
import dev.openallay.guide.ui.GuideUiModelChoice;
import dev.openallay.model.ModelUsage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class OpenAllayScreenProjectionTest {
    @Test
    void assistantLabelUsesThePlayerDisplayNameWithoutChangingProductIdentity() {
        GuideDisplayConfig display = new GuideDisplayConfig(
                false, true, "小羽");

        assertEquals("小羽", OpenAllayScreen.assistantLabel(display, false).getString());
        assertTrue(OpenAllayScreen.assistantLabel(display, true).getString().contains("小羽"));
    }

    @Test
    void contentHighlightRequiresAnExplicitStableFocusIdentity() {
        assertFalse(OpenAllayScreen.isFocused(null, null));
        assertFalse(OpenAllayScreen.isFocused(null, "tool:call-1"));
        assertFalse(OpenAllayScreen.isFocused("tool:call-1", null));
        assertTrue(OpenAllayScreen.isFocused("tool:call-1", "tool:call-1"));
        assertFalse(OpenAllayScreen.isFocused("tool:call-1", "tool:call-2"));
    }

    @Test
    void onlyVisibleChatRowsExposeCopyText() {
        UUID requestId = UUID.fromString("31a2e246-d3d8-41f4-8b3a-814c73dc77ad");
        GuideUiRow.User user = new GuideUiRow.User(requestId, "player text");
        GuideUiRow.Assistant assistant = new GuideUiRow.Assistant(
                requestId,
                0,
                "assistant text",
                new dev.openallay.guide.semantic.SemanticMessageParser().parse("assistant text"),
                false,
                List.of());
        GuideUiRow.Status status = new GuideUiRow.Status(
                requestId, GuideRequestStatus.COMPLETED, "done", null);

        assertEquals("player text", OpenAllayScreen.copyableText(user));
        assertEquals("assistant text", OpenAllayScreen.copyableText(assistant));
        assertNull(OpenAllayScreen.copyableText(status));
    }

    @Test
    void deletionConfirmationTextRetainsTheCapturedSessionTarget() {
        Component first = OpenAllayScreen.deleteConfirmationMessage("session-7", false);
        Component second = OpenAllayScreen.deleteConfirmationMessage("session-7", true);

        TranslatableContents firstTranslation = assertInstanceOf(
                TranslatableContents.class, first.getContents());
        TranslatableContents secondTranslation = assertInstanceOf(
                TranslatableContents.class, second.getContents());
        assertEquals("screen.openallay.session.delete.first.message", firstTranslation.getKey());
        assertEquals("screen.openallay.session.delete.second.message", secondTranslation.getKey());
        assertEquals("session-7", firstTranslation.getArgs()[0]);
        assertEquals("session-7", secondTranslation.getArgs()[0]);
    }

    @Test
    void modelSelectorUsesStableExplicitChoiceIdentities() {
        GuideUiModelChoice client = new GuideUiModelChoice(
                GuideModelSelection.client("profile-1"), "Profile 1", true, true, false);
        GuideUiModelChoice server = new GuideUiModelChoice(
                GuideModelSelection.server(), "Server", true, false, false);

        assertEquals("model:CLIENT:profile-1", OpenAllayScreen.modelFocusId(client));
        assertEquals("model:SERVER:server", OpenAllayScreen.modelFocusId(server));
    }

    @Test
    void streamingRowMeasurementsNeverShrinkAtOneWidth() {
        OpenAllayScreen.StableRowHeights heights = new OpenAllayScreen.StableRowHeights();
        heights.begin(300);
        assertEquals(80, heights.retain("assistant:one", 80, true));
        assertEquals(80, heights.retain("assistant:one", 52, true));
        assertEquals(96, heights.retain("assistant:one", 96, true));
        assertEquals(52, heights.retain("assistant:one", 52, false));

        heights.begin(240);
        assertEquals(52, heights.retain("assistant:one", 52, true));
    }

    @Test
    void activeStreamingDefersHistoryPagingThatWouldReorderTheViewport() {
        assertFalse(OpenAllayScreen.mayPageHistory(
                true, true, GuideHistoryPageState.IDLE, 12));
        assertTrue(OpenAllayScreen.mayPageHistory(
                true, false, GuideHistoryPageState.IDLE, 12));
        assertFalse(OpenAllayScreen.mayPageHistory(
                true, false, GuideHistoryPageState.LOADING, 12));
        assertFalse(OpenAllayScreen.mayPageHistory(
                true, false, GuideHistoryPageState.IDLE, 0));
    }

    @Test
    void composerEnterPolicyMatchesNativeChatExpectations() {
        assertEquals(OpenAllayScreen.ComposerKeyAction.SUBMIT,
                OpenAllayScreen.composerKeyAction(true, true, false, false));
        assertEquals(OpenAllayScreen.ComposerKeyAction.NEWLINE,
                OpenAllayScreen.composerKeyAction(true, true, true, false));
        assertEquals(OpenAllayScreen.ComposerKeyAction.SUBMIT,
                OpenAllayScreen.composerKeyAction(true, true, true, true));
        assertEquals(OpenAllayScreen.ComposerKeyAction.DELEGATE,
                OpenAllayScreen.composerKeyAction(false, true, false, false));
        assertEquals(OpenAllayScreen.ComposerKeyAction.DELEGATE,
                OpenAllayScreen.composerKeyAction(true, false, false, false));
    }

    @Test
    void detailsOwnEscapeAndKeyboardContentFocusBeforeTheComposer() {
        assertTrue(OpenAllayScreen.closesDetailFirst(true, true));
        assertFalse(OpenAllayScreen.closesDetailFirst(false, true));
        assertFalse(OpenAllayScreen.closesDetailFirst(true, false));
        assertTrue(OpenAllayScreen.isContentFocusTarget(true, true, false));
        assertFalse(OpenAllayScreen.isContentFocusTarget(true, false, true));
        assertTrue(OpenAllayScreen.isContentFocusTarget(false, false, true));
        assertFalse(OpenAllayScreen.isContentFocusTarget(false, true, false));
    }

    @Test
    void capturedTimeUsesTheRecordedInstantAndExplicitLocaleAndZone() {
        Instant time = Instant.parse("2026-10-01T14:35:00Z");
        String rendered = OpenAllayScreen.formatCapturedAt(time, java.util.Locale.UK, java.time.ZoneOffset.UTC);
        assertTrue(rendered.contains("14:35"));
        assertTrue(rendered.contains("2026"));
        assertFalse(rendered.contains("2026-10-01T"));
    }

    @Test
    void tickCoalescerAppliesOnlyNewestPendingProjection() {
        OpenAllayScreen.TickCoalescer<String> pending = new OpenAllayScreen.TickCoalescer<>();
        pending.offer("first");
        pending.offer("second");
        pending.offer("latest");

        assertEquals("latest", pending.drain());
        assertNull(pending.drain());
    }

    @Test
    void progressDurationsAreStableAndNeverGoNegative() {
        assertEquals("0:00", OpenAllayScreen.formatDuration(Duration.ofSeconds(-4)));
        assertEquals("1:42", OpenAllayScreen.formatDuration(Duration.ofSeconds(102)));
        assertEquals("2:03:04", OpenAllayScreen.formatDuration(Duration.ofSeconds(7384)));
    }

    @Test
    void collapsedToolSummaryKeepsAtMostThreeSemanticMessages() {
        GuideToolMessage first = GuideToolMessage.of(GuideToolMessage.Key.RESULT_PENDING);
        GuideToolMessage second = GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED);
        GuideToolMessage third = GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED);
        GuideToolMessage fourth = GuideToolMessage.of(GuideToolMessage.Key.RESULT_VALUE_UNAVAILABLE);
        assertEquals(
                List.of(first, second, third),
                OpenAllayScreen.visibleToolSummaryMessages(
                        List.of(first, second, third, fourth)));
        assertEquals(51, OpenAllayScreen.toolCardHeight(3));
        assertEquals(21, OpenAllayScreen.toolCardHeight(0));
    }

    @Test
    void toolMessagesUseClosedTranslationKeysAndLiteralArguments() {
        GuideToolMessage message = GuideToolMessage.of(
                GuideToolMessage.Key.RESULT_COMPLETED,
                "minecraft:iron_block");

        Component rendered = OpenAllayScreen.toolMessage(message);
        TranslatableContents translation = assertInstanceOf(
                TranslatableContents.class, rendered.getContents());

        assertEquals(message.key().translationKey(), translation.getKey());
        Component argument = assertInstanceOf(Component.class, translation.getArgs()[0]);
        assertEquals("minecraft:iron_block", argument.getString());
        assertFalse(argument.getContents() instanceof TranslatableContents);
    }

    @Test
    void javascriptCardsRenderIntentAsLiteralTextWithSeparateResultAndLegacyFallback() {
        var input = new com.google.gson.JsonObject();
        String title = "**比较** screen.openallay.title /op player <clickEvent> 🧚";
        String description = "[[tw:item|minecraft:apple]] \"quoted\" <script>";
        input.addProperty("title", title);
        input.addProperty("description", description);
        var activity = new GuideToolActivity("call-intent", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, input, null, List.of(
                        GuideToolMessage.of(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT, title, description),
                        GuideToolMessage.of(GuideToolMessage.Key.ANALYSIS_COMPLETE, "5")), List.of());
        Component titleComponent = OpenAllayScreen.toolTitle(activity);
        Component descriptionComponent = OpenAllayScreen.toolDescription(activity.intent());
        assertEquals(title, titleComponent.getString());
        assertEquals(description, descriptionComponent.getString());
        assertFalse(titleComponent.getContents() instanceof TranslatableContents);
        assertFalse(descriptionComponent.getContents() instanceof TranslatableContents);
        assertNull(titleComponent.getStyle().getClickEvent());
        assertNull(descriptionComponent.getStyle().getClickEvent());
        List<Component> summary = OpenAllayScreen.toolSummaryComponents(activity);
        assertEquals("screen.openallay.tool.message.analysis.complete",
                assertInstanceOf(TranslatableContents.class, summary.getFirst().getContents()).getKey());
        assertEquals(description, summary.get(1).getString());
        var legacy = new GuideToolActivity("legacy", 0, "openallay:run_javascript",
                GuideToolStatus.RUNNING, null, List.of(), List.of());
        assertEquals("screen.openallay.tool.run_javascript", assertInstanceOf(TranslatableContents.class,
                OpenAllayScreen.toolTitle(legacy).getContents()).getKey());
        assertEquals("screen.openallay.tool.intent.run_javascript.description", assertInstanceOf(TranslatableContents.class,
                OpenAllayScreen.toolDescription(legacy.intent()).getContents()).getKey());
    }

    @Test
    void codeOwnedStatusIsIndependentOfLongModelIntentAndFactsComeFirst() {
        var input = new com.google.gson.JsonObject();
        String title = "Model title ".repeat(200).trim();
        String description = "Model description ".repeat(200).trim();
        input.addProperty("title", title);
        input.addProperty("description", description);
        var activity = new GuideToolActivity("long-intent", 0, "openallay:run_javascript",
                GuideToolStatus.FAILED, input, null, List.of(
                        GuideToolMessage.of(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT, title, description),
                        GuideToolMessage.of(GuideToolMessage.Key.FAILURE_GENERIC)), List.of());
        Component status = OpenAllayScreen.toolCardStatus(
                dev.openallay.guide.ui.GuideToolDisplayStatus.FAILED);
        assertFalse(status.getString().contains(title));
        Component translatedStatus = assertInstanceOf(Component.class, status.getSiblings().getFirst());
        assertEquals("screen.openallay.detail.tool.status.failed",
                assertInstanceOf(TranslatableContents.class, translatedStatus.getContents()).getKey());
        assertTrue(OpenAllayScreen.toolCardTitle(activity).getString()
                .endsWith(activity.intent().title()));
        List<Component> summary = OpenAllayScreen.toolSummaryComponents(activity);
        assertEquals("screen.openallay.tool.failure.javascript",
                assertInstanceOf(TranslatableContents.class, summary.getFirst().getContents()).getKey());
        assertEquals(description, summary.getLast().getString());
    }

    @Test
    void failureShowsActualCodeAndMessageBeforeUntrustedIntentWithoutDebug() {
        var normalized = new com.google.gson.JsonObject();
        normalized.addProperty("status", "failure");
        normalized.addProperty("code", "javascript_error");
        normalized.addProperty("message", "ReferenceError: Java is not defined (line 3) <clickEvent>");
        var input = new com.google.gson.JsonObject();
        input.addProperty("title", "Succeeded by enabling Java");
        input.addProperty("description", "Untrusted planned claim");
        var activity = new GuideToolActivity("failed", 0, "openallay:run_javascript",
                GuideToolStatus.FAILED, input, normalized, List.of(), List.of());
        var detail = dev.openallay.guide.ui.GuideToolDetailPresenter.project(activity, false);
        List<Component> reasons = OpenAllayScreen.toolFailureComponents(detail, activity.toolId());
        assertEquals(List.of("javascript_error", "ReferenceError: Java is not defined (line 3) <clickEvent>"),
                reasons.stream().map(Component::getString).toList());
        assertFalse(reasons.getLast().getContents() instanceof TranslatableContents);
        assertNull(reasons.getLast().getStyle().getClickEvent());
        assertFalse(reasons.getFirst().getString().contains("Succeeded"));
        assertTrue(detail.debug().isEmpty());
        assertEquals("javascript_error", OpenAllayScreen.toolSummaryComponents(activity).getFirst().getString());

        var stopped = new GuideToolActivity("stopped", 0, "openallay:run_javascript",
                GuideToolStatus.RUNNING, input, null, List.of(), List.of());
        assertTrue(OpenAllayScreen.toolFailureComponents(
                dev.openallay.guide.ui.GuideToolDetailPresenter.project(stopped, false).forRequest(true),
                stopped.toolId()).isEmpty());
        assertEquals("javascript_error", OpenAllayScreen.toolFailureComponents(
                detail, "other:unknown").getFirst().getString());
    }

    @Test
    void toolResultAndDebugProgramPrecedeOptionalSources() {
        var input = JsonParser.parseString("{\"source\":\"return 7;\"}").getAsJsonObject();
        var normalized = JsonParser.parseString("""
                {"status":"success","value":{"resultType":"number","cardinality":1,
                  "viewKind":"SCALAR","preview":7,"complete":true}}
                """).getAsJsonObject();
        var activity = new GuideToolActivity("ordered", 0, "openallay:run_javascript",
                GuideToolStatus.SUCCEEDED, input, normalized, List.of(), List.of());
        var normal = dev.openallay.guide.ui.GuideToolDetailPresenter.project(activity, false);
        var debug = dev.openallay.guide.ui.GuideToolDetailPresenter.project(activity, true);
        assertEquals("", OpenAllayScreen.toolProgram(normal));
        assertEquals("return 7;", OpenAllayScreen.toolProgram(debug));
        assertEquals(List.of(OpenAllayScreen.DetailSection.RESULT, OpenAllayScreen.DetailSection.INTENT,
                OpenAllayScreen.DetailSection.SOURCES), OpenAllayScreen.toolDetailSections(normal));
        assertEquals(List.of(OpenAllayScreen.DetailSection.RESULT, OpenAllayScreen.DetailSection.PROGRAM,
                OpenAllayScreen.DetailSection.INTENT, OpenAllayScreen.DetailSection.DEBUG,
                OpenAllayScreen.DetailSection.SOURCES), OpenAllayScreen.toolDetailSections(debug));
    }

    @Test
    void groupedSourceLabelUsesSourceIdentityWithoutInventingReadCounts() {
        GuideSource source = new GuideSource("openallay:run_javascript", new EvidenceMetadata(
                DataAuthority.CLIENT_VISIBLE, DataCompleteness.PARTIAL, Instant.EPOCH,
                "minecraft:client_blocks", "minecraft:captured", "26.2", "fabric",
                Map.of("minecraft:dimension", "minecraft:overworld", "minecraft:position", "1,64,1")),
                Instant.EPOCH.plusSeconds(12));
        var group = dev.openallay.guide.ui.GuideEvidencePresentation.groups(List.of(source)).getFirst();
        String label = OpenAllayScreen.sourceLabel(group, false);
        assertEquals(OpenAllayScreen.sourceLabel(source, false), label);
        assertFalse(label.contains("observations"));
        assertEquals(Instant.EPOCH, group.firstCapturedAt());
        assertEquals(Instant.EPOCH.plusSeconds(12), group.lastCapturedAt());
        assertFalse(label.contains("1,64,1"));
        assertFalse(label.contains("minecraft:captured"));
        assertEquals(source, group.records().getFirst());
    }

    @Test
    void expandedCompactBuilderSourceUsesTheRealStartForGroupAndRecordRanges() {
        Instant start = Instant.EPOCH;
        Instant completed = Instant.EPOCH.plusSeconds(2);
        Instant last = Instant.EPOCH.plusSeconds(10_001);
        EvidenceMetadata evidence = new EvidenceMetadata(
                DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE, completed,
                "openallay_builder:read-region", "openallay:builder", "26.2", "fabric",
                Map.of("openallay_builder:capture_start", start.toString(),
                        "openallay_builder:capture_end", completed.toString(),
                        "openallay_builder:dimension", "minecraft:overworld"));
        GuideSource source = new GuideSource("openallay:run_javascript", evidence, last);
        var group = dev.openallay.guide.ui.GuideEvidencePresentation.groups(List.of(source)).getFirst();

        List<TranslatableContents> ranges = OpenAllayScreen.sourceDetailComponents(group).stream()
                .map(Component::getContents)
                .filter(TranslatableContents.class::isInstance)
                .map(TranslatableContents.class::cast)
                .filter(contents -> contents.getKey().equals("screen.openallay.evidence.capture_range"))
                .toList();
        assertEquals(2, ranges.size());
        for (TranslatableContents range : ranges) {
            assertEquals(List.of(start.toString(), last.toString()), List.of(range.getArgs()));
        }
        assertEquals(completed, source.evidence().capturedAt());
        assertEquals(completed.toString(), source.evidence().details().get("openallay_builder:capture_end"));
    }

    @Test
    void expandedSourceTextRetainsEverySpecificRecordAndLongValueWithoutCaps() {
        List<GuideSource> sources = new java.util.ArrayList<>();
        String exactLongValue = "retained detail ".repeat(400);
        for (int index = 0; index < 4096; index++) {
            sources.add(new GuideSource("openallay:run_javascript", new EvidenceMetadata(
                    DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.COMPLETE,
                    Instant.EPOCH.plusSeconds(index), "openallay_builder:read", "openallay:builder",
                    "26.2", "fabric", Map.of("openallay_builder:dimension", "minecraft:overworld",
                            "openallay_builder:position", index == 4095 ? exactLongValue : index + ",64,0",
                            "openallay_builder:count", "1")),
                    Instant.EPOCH.plusSeconds(index + 1)));
        }
        var group = dev.openallay.guide.ui.GuideEvidencePresentation.groups(sources).getFirst();
        List<String> text = OpenAllayScreen.sourceDetailComponents(group).stream()
                .map(Component::getString).toList();
        assertEquals(4096, text.stream().filter(line -> line.startsWith("openallay_builder:position:")).count());
        assertTrue(text.contains("openallay_builder:position: 0,64,0"));
        assertTrue(text.contains("openallay_builder:position: 4094,64,0"));
        assertTrue(text.contains("openallay_builder:position: " + exactLongValue));
        assertEquals(1, text.stream().filter(line -> line.equals(
                "openallay_builder:dimension: minecraft:overworld")).count());
        assertTrue(text.contains("gameVersion: 26.2"));
        assertTrue(text.contains("loader: fabric"));
    }

    @Test
    void sourceDetailLayoutReusesOnlyTheSameImmutableGroupWidthAndLocale() {
        GuideSource source = new GuideSource("openallay:run_javascript", new EvidenceMetadata(
                DataAuthority.CLIENT_VISIBLE, DataCompleteness.COMPLETE, Instant.EPOCH,
                "minecraft:client_blocks", "minecraft:captured", "26.2", "fabric", Map.of()));
        var group = dev.openallay.guide.ui.GuideEvidencePresentation.groups(List.of(source)).getFirst();
        var equivalentNewSnapshot = dev.openallay.guide.ui.GuideEvidencePresentation.groups(List.of(source)).getFirst();
        var layout = new OpenAllayScreen.SourceDetailLayout(group, 240, "en_us", List.of());
        assertTrue(layout.matches(group, 240, "en_us"));
        assertFalse(layout.matches(group, 200, "en_us"));
        assertFalse(layout.matches(group, 240, "zh_cn"));
        assertFalse(layout.matches(equivalentNewSnapshot, 240, "en_us"));
    }

    @Test
    void sourceDetailDrawingVisitsOnlyLinesInsideTheViewport() {
        var detail = new dev.openallay.guide.ui.GuideUiLayout.Rect(0, 0, 240, 100);
        assertEquals(new OpenAllayScreen.VisibleDetailLines(0, 7),
                OpenAllayScreen.visibleDetailLines(detail, 21, 100_000));
        assertEquals(new OpenAllayScreen.VisibleDetailLines(3, 10),
                OpenAllayScreen.visibleDetailLines(detail, -2, 100_000));
        assertEquals(new OpenAllayScreen.VisibleDetailLines(0, 0),
                OpenAllayScreen.visibleDetailLines(detail, 100, 100_000));
        assertEquals(new OpenAllayScreen.VisibleDetailLines(2, 2),
                OpenAllayScreen.visibleDetailLines(detail, -100, 2));
        assertEquals(new OpenAllayScreen.VisibleDetailLines(0, 0),
                OpenAllayScreen.visibleDetailLines(detail, 21, 0));
    }

    @Test
    void factualStatusRowsKeepCompleteFailureTextAndInterruptedRetryMeaning() {
        UUID id = UUID.fromString("bd1ce41a-9c31-4868-9c50-3a8e0eabcc71");
        String failure = "A complete provider failure reason ".repeat(20).trim();
        assertEquals(failure, OpenAllayScreen.factualRowText(new GuideUiRow.Status(
                id, GuideRequestStatus.FAILED, failure, null)).getString());
        Component interrupted = OpenAllayScreen.factualRowText(new GuideUiRow.Status(
                id, GuideRequestStatus.INTERRUPTED, "ignored raw message", null));
        assertEquals("screen.openallay.history.interrupted",
                assertInstanceOf(TranslatableContents.class, interrupted.getContents()).getKey());
    }

    @Test
    void terminalNoResultHidesPendingNarrationButKeepsPlannedIntent() {
        var input = new com.google.gson.JsonObject();
        input.addProperty("title", "Build a platform");
        input.addProperty("description", "Place blocks for the platform");
        var activity = new GuideToolActivity("stopped-intent", 0, "openallay:run_javascript",
                GuideToolStatus.RUNNING, input, null, List.of(
                        GuideToolMessage.of(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT,
                                "Build a platform", "Place blocks for the platform"),
                        GuideToolMessage.of(GuideToolMessage.Key.RESULT_PENDING)), List.of());
        List<Component> summary = OpenAllayScreen.toolSummaryComponents(activity,
                dev.openallay.guide.ui.GuideToolDisplayStatus.NO_RESULT_RECORDED);
        assertEquals(1, summary.size());
        assertEquals("Place blocks for the platform", summary.getFirst().getString());
        assertEquals(GuideToolStatus.RUNNING, activity.status());
    }

    @Test
    void normalSourceLabelIsFriendlyAndDebugLabelUsesReadableCoverage() {
        GuideSource source = new GuideSource(
                "openallay:inspect_inventory",
                new EvidenceMetadata(
                        DataAuthority.CLIENT_VISIBLE,
                        DataCompleteness.COMPLETE,
                        Instant.EPOCH,
                        "openallay:inventory",
                        "openallay:captured_inventory",
                        "26.2",
                        "fabric",
                        Map.of()));

        String normal = OpenAllayScreen.sourceLabel(source, false);
        assertFalse(normal.contains("CLIENT_VISIBLE"));
        assertFalse(normal.contains("COMPLETE"));
        assertFalse(normal.contains("openallay:inventory"));
        assertTrue(normal.contains(Component.translatable(
                "screen.openallay.evidence.source.player").getString()));

        String debug = OpenAllayScreen.sourceLabel(source, true);
        assertTrue(debug.contains(Component.translatable(
                "screen.openallay.detail.tool.coverage.complete").getString()));
        assertFalse(debug.contains("CLIENT_VISIBLE"));
        assertFalse(debug.contains("COMPLETE"));
        assertFalse(debug.contains("authority"));
        assertEquals(Component.translatable(
                "screen.openallay.detail.tool.source.minecraft.client_registry").getString(),
                OpenAllayScreen.readableSource("minecraft:client_registry"));
        assertEquals("addon:new_source", OpenAllayScreen.readableSource("addon:new_source"));
    }

    @Test
    void toolExecutionStatusUsesExplicitPlayerWords() {
        for (GuideToolStatus status : GuideToolStatus.values()) {
            TranslatableContents translation = assertInstanceOf(
                    TranslatableContents.class, OpenAllayScreen.toolStatus(status).getContents());
            assertEquals("screen.openallay.detail.tool.status." + status.name().toLowerCase(java.util.Locale.ROOT),
                    translation.getKey());
        }
    }

    @Test
    void liveDisplaySupplierReprojectsSameSnapshotWithoutChangingHistory() {
        GuideToolActivity activity = new GuideToolActivity(
                "call-live",
                0,
                "openallay:inspect_inventory",
                GuideToolStatus.SUCCEEDED,
                JsonParser.parseString("""
                        {"status":"success","value":{"counts":{"minecraft:apple":3}}}
                        """).getAsJsonObject(),
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.RESULT_COMPLETED,
                        "minecraft:apple",
                        "3")),
                List.of());
        GuideRequestSnapshot request = new GuideRequestSnapshot(
                UUID.fromString("d43b1f0c-c527-4284-902e-fab09b799fe0"),
                "main",
                GuideTopology.CLIENT_LOCAL,
                "question",
                List.of(new GuideTimelineEntry.Tool(0, activity)),
                GuideRequestStatus.COMPLETED,
                List.of(),
                ModelUsage.empty(),
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                Instant.EPOCH.plusSeconds(1));
        GuideSnapshot snapshot = new GuideSnapshot(
                UUID.fromString("24475b25-61c3-4bd9-aec3-5eafbe6ae283"),
                "main",
                GuideModelMode.CLIENT,
                true,
                false,
                List.of(new GuideSessionSnapshot("main", List.<GuideMessage>of(), List.of(request))),
                Instant.EPOCH.plusSeconds(1));
        AtomicReference<GuideDisplayConfig> display =
                new AtomicReference<>(GuideDisplayConfig.defaults());

        GuideUiRow.Tool normal = (GuideUiRow.Tool) OpenAllayScreen
                .project(snapshot, display::get).rows().get(1);
        display.set(new GuideDisplayConfig(true, true,
                        GuideDisplayConfig.DEFAULT_ASSISTANT_NAME));
        GuideUiRow.Tool debug = (GuideUiRow.Tool) OpenAllayScreen
                .project(snapshot, display::get).rows().get(1);

        assertTrue(normal.detail().debug().isEmpty());
        assertTrue(debug.detail().debug().isPresent());
        assertSame(request, snapshot.sessions().getFirst().requests().getFirst());
    }
}
