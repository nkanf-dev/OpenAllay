package dev.openallay.guide.e2e;

import dev.openallay.client.gui.MinecraftClientWindow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.client.gui.OpenAllayKeyMappings;
import dev.openallay.client.gui.OpenAllayScreen;
import dev.openallay.client.gui.OpenAllaySettingsScreen;
import dev.openallay.client.gui.hud.GuideChatLiteScreen;
import dev.openallay.client.gui.hud.GuideHudEditorScreen;
import dev.openallay.client.gui.hud.GuideHudLayout;
import dev.openallay.client.gui.hud.GuideHudReadingLayout;
import dev.openallay.client.gui.settings.SettingsLayout;
import dev.openallay.client.gui.settings.SettingsSection;
import dev.openallay.client.voice.AudioCapture;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayConfigLoader;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.SettingsOperation;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import dev.openallay.client.gui.GuideNativeButton;
import dev.openallay.client.gui.GuideNativeSlider;
import dev.openallay.client.gui.GuideNativeEditBox;
import dev.openallay.client.gui.GuideMultilineEditor;
import net.minecraft.client.gui.screens.Screen;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import dev.openallay.client.gui.GuideNativeInput;
import dev.openallay.client.gui.GuideInputMouse;

/** Opt-in native GUI scenario. Does not manufacture model results or bypass Done/Export callbacks. */
final class GuideGraphicalRegressionProbe {
    private final GuideClientE2EConfig config;
    private final GuideService service;
    private final ClientSettingsService settings;
    private final Gson gson;
    private final Consumer<GuideService> openGuide;
    private final Supplier<Object> hudReceipt;
    private final Supplier<Object> toastReceipt;
    private final boolean developmentProbeEnabled = Boolean.getBoolean(GuideClientE2EConfig.ENABLED);
    private static final String LIVE_PREFIX = "OpenAllay E2E UI live UX regressions";
    private static final String LIVE_FOLLOW_UP = "OpenAllay E2E UI live UX follow-up";
    private static final String LIVE_STEER = "OpenAllay E2E UI live UX steer";
    private static final String LIVE_TAIL = "全文末尾：原生实时 UX 验收完成 · LATEST-48。";
    private final Supplier<VoiceSettingsActions> voiceSettings;
    private final BiFunction<String, UUID, java.util.Optional<String>> traceLookup;
    private final Consumer<Map<String, Object>> completed;
    private final Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
    private final GuideNativeEditorE2EProbe nativePrimitiveProbe;
    private final Instant started = Instant.now();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> checkpoints = new ArrayList<>();
    private final List<Map<String, Object>> actions = new ArrayList<>();
    private final Map<String, CompletableFuture<Map<String, Object>>> frames = new LinkedHashMap<>();
    private final Runnable restoreInteractKey;
    private final String originalInteractBinding;
    private final Runnable restorePttKey;
    private final String originalPttBinding;
    private GuideRequestSnapshot latestRequest;
    private GuideRequestSnapshot toastRequest;
    private UUID liveFollowUpId;
    private UUID liveSteerId;
    private String liveRecipeToolId;
    private String liveHeaderName;
    private long liveHoverFrame;
    private int liveHoverAttempts;
    private final List<Map<String, Object>> liveHoverDiagnostics = new ArrayList<>();
    private long liveHudFrame;
    private int liveReaderScroll;
    private String liveReaderAnchor;
    private CompletableFuture<Integer> liveTransportRelease;
    private String liveToastQuestion;
    private OpenAllayScreen liveHeaderOwner;
    private Object liveHeaderWidget;
    private long liveHeaderFrame;
    private NativeScreenTransition<Screen> liveGuideReopen;
    private LiveGuideReopenOwner liveGuideReopenOwner;
    private record LiveGuideReopenOwner(GuideChatLiteScreen reader,
            dev.openallay.client.gui.GuideClientUiState state, long generation,
            Object world, UUID actor, UUID sessionOwner) {}
    private final Path frameRoot;
    private final int originalWindowWidth;
    private final int originalWindowHeight;
    private final int originalGuiScale;
    private CompletableFuture<ToolResult<List<AudioCapture.Device>>> microphoneMetadata;
    private GuideRequestSnapshot request;
    private int stage;
    private int ticks;
    private int stageWait;
    private int themeChanges;
    private boolean done;
    private long hudFrameBeforeDone;
    private String expectedTheme;
    private String expectedName;
    private CompletableFuture<Void> displayFault;
    private Path displayPath;
    private Path displayBackup;
    private byte[] displayBefore;
    private long generationBeforeFailure;
    private String validNameBeforeFailure;
    private int sliderChoice;
    private long nativeRecipeFrame;
    private long passiveRecipeFrame;
    private long interactiveBeforeWheelFrame;
    private final List<Map<String, Object>> exits = new ArrayList<>();
    private final Map<String, Object> hudDrag = new LinkedHashMap<>();
    private final List<Map<String, Object>> hudPointerEvents = new ArrayList<>();
    private GuideDisplayConfig hudDisplayBeforeDrag;
    private GuideDisplayConfig hudDisplayAfterDrag;
    private GuideHudLayout.Rect hudBoundsBeforeDrag;
    private GuideHudLayout.Rect hudBoundsAfterDrag;
    private CompletableFuture<Map<String, Object>> hudDisplayReadback;
    private long hudDragGenerationBeforeApply;
    private long hudDragPreviewBefore;
    private long hudDragPreviewAfter;
    private long hudDragFrameBeforePassive;
    private double hudDragPointerX;
    private double hudDragPointerY;
    private double hudDragDx;
    private double hudDragDy;

    GuideGraphicalRegressionProbe(GuideClientE2EConfig config, String loader, String gameVersion,
            String modVersion, GuideService service, ClientSettingsService settings, Gson gson,
            Consumer<GuideService> openGuide, Supplier<Object> hudReceipt,
            Supplier<VoiceSettingsActions> voiceSettings, Supplier<Object> toastReceipt,
            BiFunction<String, UUID, java.util.Optional<String>> traceLookup,
            Consumer<Map<String, Object>> completed) {
        this.config = config;
        nativePrimitiveProbe = GuideNativeEditorE2EProbe.create(client, loader, gameVersion, report);
        this.service = service;
        this.settings = settings;
        this.gson = gson;
        this.openGuide = openGuide;
        this.hudReceipt = hudReceipt;
        this.toastReceipt = toastReceipt;
        this.voiceSettings = voiceSettings;
        this.traceLookup = traceLookup;
        this.completed = completed;
        String root = System.getProperty("openallay.e2e.screenshotRoot", "");
        if (root.isBlank()) throw new IllegalStateException("Native frame output is required");
        frameRoot = Path.of(root);
        originalWindowWidth = dev.openallay.client.gui.MinecraftClientWindow.framebufferWidth(client);
        originalWindowHeight = dev.openallay.client.gui.MinecraftClientWindow.framebufferHeight(client);
        originalGuiScale = dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client));
        report.put("windowBefore", Map.of("width", originalWindowWidth, "height", originalWindowHeight, "guiScale", originalGuiScale));
        originalPttBinding = GuideProbeKeyBindings.description(OpenAllayKeyMappings.VOICE_PTT);
        restorePttKey = GuideProbeKeyBindings.restoration(OpenAllayKeyMappings.VOICE_PTT);
        originalInteractBinding = GuideProbeKeyBindings.description(OpenAllayKeyMappings.INTERACT_HUD);
        restoreInteractKey = GuideProbeKeyBindings.restoration(OpenAllayKeyMappings.INTERACT_HUD);
        report.put("loader", loader);
        report.put("gameVersion", gameVersion);
        report.put("modVersion", modVersion);
        report.put("scenario", config.scenario());
        report.put("model", "deterministic-loopback-fixture-not-live-model");
        report.put("visualReview", "REQUIRED: native PNGs must be reviewed separately");
        report.put("microphoneCaptureAttempted", false);
        report.put("checkpoints", checkpoints);
        report.put("actions", actions);
        report.put("interactKeyBefore", originalInteractBinding);
        report.put("sourceIdentity", Map.of("revision", System.getProperty("openallay.e2e.sourceRevision", "UNRECORDED"),
                "manifestSha256", System.getProperty("openallay.e2e.sourceManifestSha256", "UNRECORDED")));
        report.put("voiceProof", "NOT TESTED: no capture/STT device; synthetic external companion is separate from hardware proof");
    }

    void tick() {
        if (done) return;
        try {
            if (Duration.between(started, Instant.now()).toSeconds()
                    > Long.getLong("openallay.e2e.timeoutSeconds", 300L)) {
                throw new IllegalStateException("Native graphical scenario exceeded its timeout at stage " + stage);
            }
            // At FPS10 this leaves several actual extraction/render frames between actions.
            if (++ticks < 12 || MinecraftClientWindow.overlayPresent(client)) return;
            ticks = 0;
            if ("ui-live-ux-regressions".equals(config.scenario())) runLiveStage();
            else runStage();
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    private void runStage() {
        switch (stage) {
            case 0 -> {
                requireLoopbackFixture();
                require("zh_cn".equals(dev.openallay.platform.minecraft.MinecraftOptions.language(dev.openallay.client.context.MinecraftClientContextFacts.options(client))), "Chinese language must be prepared before launch");
                MinecraftClientWindow.setWindowed(client, 850, 480);
                GuideProbeKeyBindings.keyboard(OpenAllayKeyMappings.INTERACT_HUD, dev.openallay.client.gui.GuideInputCodes.KEY_F8);
                GuideProbeKeyBindings.refresh();
                report.put("interactKeyDuring", GuideProbeKeyBindings.description(OpenAllayKeyMappings.INTERACT_HUD));
                report.put("world", dev.openallay.server.NativeServerOwner.worldName(dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client)));
                report.put("commandsAllowed", GuideProbeWorldSettings.commandsAllowed(dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client)));
                require(!GuideProbeWorldSettings.commandsAllowed(dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client)), "Disposable world commands must be off");
                openGuide.accept(service);
                advance();
            }
            case 1 -> {
                guide();
                GuideMultilineEditor composer = composer();
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer, config.question(), true);
                advance();
            }
            case 2 -> { press("screen.openallay.action.send"); advance(); }
            case 3 -> {
                request = service.snapshot().sessions().stream()
                        .filter(value -> value.sessionId().equals(config.sessionId()))
                        .flatMap(value -> value.requests().stream())
                        .filter(value -> value.userMessage().equals(config.question())).findFirst().orElse(null);
                if (request == null || !request.terminal()) { waitFor("actual GUI request completion"); return; }
                require(request.status() == GuideRequestStatus.COMPLETED, "Actual GUI request failed");
                require(request.tools().size() == 2 && request.tools().stream().allMatch(value ->
                        value.toolId().equals("openallay:run_javascript") && value.status() == GuideToolStatus.SUCCEEDED
                                && value.normalized() != null), "Actual two native read-only recipe Tools did not complete");
                var firstNative = request.tools().get(0).normalized().getAsJsonObject("value");
                require("RECIPE".equals(firstNative.get("viewKind").getAsString()),
                        "First Tool did not return an actual trusted native recipe card");
                require(request.assistantText().contains("全文末尾：原生图形长回复验收完成"), "The full local test response was not retained");
                report.put("requestId", request.requestId().toString());
                report.put("requestOutcome", request.status().name());
                report.put("actualTools", request.tools().stream().map(value -> Map.of(
                        "toolId", value.toolId(), "status", value.status().name())).toList());
                report.put("assistantTextSha256", sha256(request.assistantText()));
                guide().clearGuideWidgetFocus();
                GuideNativeInput.keyPressed(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_HOME, 0));
                stage = 41;
                stageWait = 0;
            }
            case 41 -> {
                if (!nativeRecipePainted()) return;
                checkpoint("01-fullscreen-original", true);
                validateGuideReceipts();
                press("screen.openallay.settings.short");
                stage = 4;
                stageWait = 0;
            }
            case 4 -> {
                settingsScreen();
                navigate("screen.openallay.settings.general");
                advance();
            }
            case 5 -> {
                expectedName = "小羽 · 原生图形回归";
                String label = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.general.assistant_name.label"));
                GuideNativeEditBox name = ((dev.openallay.client.gui.GuideNativeScreen) MinecraftClientWindow.screen(client)).guideWidgetChildren().stream().filter(GuideNativeEditBox.class::isInstance)
                        .map(GuideNativeEditBox.class::cast).filter(value -> MinecraftComponents.getString(value.getMessage()).equals(label))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Actual assistant-name editor unavailable"));
                name.setValue(expectedName);
                press("screen.openallay.settings.done");
                advance();
            }
            case 6 -> {
                if (!doneReturned()) return;
                require(expectedName.equals(settings.snapshot().display().assistantName()), "Done lost the name draft");
                checkpoint("02-name-done-ack", true);
                exits.add(Map.of("entry", "Done", "savedName", expectedName, "ackBeforeClose", true));
                report.put("exitReceipts", exits);
                press("screen.openallay.settings.short");
                stage = 42;
                stageWait = 0;
            }
            case 7 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 8 -> { press("screen.openallay.settings.ui.fullscreen"); advance(); }
            case 9 -> { press("screen.openallay.settings.ui.reset"); advance(); }
            case 10 -> {
                expectedTheme = "CHARCOAL";
                press("screen.openallay.settings.done");
                advance();
            }
            case 11 -> {
                if (!doneReturned()) return;
                require(expectedTheme.equals(theme()), "Done did not acknowledge Reset");
                checkpoint("03-reset-compact-tools", true);
                validateGuideReceipts();
                advance();
            }
            case 12 -> {
                press("screen.openallay.settings.short");
                advance();
            }
            case 13 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 14 -> { press("screen.openallay.settings.ui.fullscreen"); advance(); }
            case 15 -> {
                expectedTheme = "MINT".equals(theme()) ? "CHARCOAL" : "MINT";
                press("screen.openallay.settings.ui.theme");
                advance();
            }
            case 16 -> {
                checkpoint(String.format("theme-%02d-settings-draft", themeChanges + 1), false);
                press("screen.openallay.settings.done");
                advance();
            }
            case 17 -> {
                if (!doneReturned()) return;
                require(expectedTheme.equals(theme()), "Done did not acknowledge theme draft");
                themeChanges++;
                checkpoint(String.format("theme-%02d-fullscreen-%s", themeChanges, theme().toLowerCase()), true);
                validateGuideReceipts();
                if (themeChanges < 4) { stage = 12; stageWait = 0; }
                else advance();
            }
            case 18 -> { press("screen.openallay.settings.short"); advance(); }
            case 19 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 20 -> { press("screen.openallay.settings.ui.hud"); advance(); }
            case 21 -> { press("screen.openallay.settings.ui.reset"); advance(); }
            case 22 -> {
                press("screen.openallay.settings.ui.hud_enabled");
                sliderChoice = 0;
                stage = 60;
                stageWait = 0;
            }
            case 23 -> {
                hudFrameBeforeDone = number(gson.toJsonTree(hudReceipt.get()).getAsJsonObject(), "extractedFrame");
                press("screen.openallay.settings.done");
                advance();
            }
            case 24 -> {
                if (!doneReturned()) return;
                require(settings.snapshot().display().ui().hud().enabled(), "HUD Done lost its enabled draft");
                int lines = settings.snapshot().display().ui().hud().maxReplyLines();
                require(lines == 0 || lines > 10, "HUD still imposes the former 10-line cap");
                checkpoint("04-hud-done-return", true);
                guide().onClose();
                advance();
            }
            case 25 -> {
                require(MinecraftClientWindow.screen(client) == null, "Passive HUD captured the gameplay screen");
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                require(number(receipt, "extractedFrame") > hudFrameBeforeDone, "HUD receipt is stale after Done");
                requireHudLatest(receipt, request);
                checkpoint("05-passive-hud-latest-tail", false);
                report.put("passiveHudLatestTail", receipt);
                passiveRecipeFrame = number(receipt, "extractedFrame");
                stage = 65;
                stageWait = 0;
            }
            case 26 -> {
                Screen lite = MinecraftClientWindow.screen(client);
                require(lite != null && lite.getClass().getSimpleName().equals("GuideChatLiteScreen"), "Native HUD interaction key did not open HUD");
                checkpoint("06-interactive-hud-top", false);
                JsonObject before = gson.toJsonTree(readReceipt(lite, "resultReceipt")).getAsJsonObject();
                require(number(before, "maximumScroll") > 0, "Long HUD result has no scrollable full content");
                interactiveBeforeWheelFrame = number(before, "extractedFrame");
                var card = GuideChatLiteScreen.Card.calculate(lite.width, lite.height);
                var results = GuideHudReadingLayout.calculate(card.x(), card.y(), card.width(), card.height()).results();
                require(results.width() > 0 && results.height() > 0, "Interactive HUD has no native result viewport");
                double wheelX = results.x() + results.width() / 2.0;
                double wheelY = results.y() + results.height() / 2.0;
                boolean handled = ((GuideChatLiteScreen) lite).guideMouseScrolled(wheelX, wheelY, 0, -10000);
                actions.add(Map.of("type", "native-wheel", "target", "interactive-hud-bottom", "stage", stage,
                        "at", Instant.now().toString(), "x", wheelX, "y", wheelY,
                        "scrollX", 0, "scrollY", -10000, "handled", handled, "resultViewport", results));
                require(handled, "Native HUD result viewport did not consume the actual wheel callback");
                advance();
            }
            case 27 -> {
                Screen lite = MinecraftClientWindow.screen(client);
                JsonObject receipt = gson.toJsonTree(readReceipt(lite, "resultReceipt")).getAsJsonObject();
                require(number(receipt, "extractedFrame") > interactiveBeforeWheelFrame,
                        "HUD wheel receipt was not freshly extracted");
                require(number(receipt, "scroll") == number(receipt, "maximumScroll")
                                && number(receipt, "scroll") == number(receipt, "contentHeight") - number(receipt, "viewportHeight"),
                        "HUD wheel did not expose全文 bottom");
                require(receipt.has("renderedNodeIds") && !(receipt.getAsJsonArray("renderedNodeIds").size() == 0),
                        "HUD bottom has no actually extracted semantic text-node identity");
                var finalAssistant = request.timeline().stream()
                        .filter(dev.openallay.guide.GuideTimelineEntry.Assistant.class::isInstance)
                        .map(dev.openallay.guide.GuideTimelineEntry.Assistant.class::cast)
                        .reduce((earlierAssistant, laterAssistant) -> laterAssistant).orElseThrow();
                String tailNode = finalAssistant.semantic().blocks().get(finalAssistant.semantic().blocks().size() - 1).nodeId();
                require(dev.openallay.json.JsonReaders.elements(receipt.getAsJsonArray("renderedNodeIds")).stream()
                                .anyMatch(value -> tailNode.equals(value.getAsString())),
                        "Actual HUD extracted nodes do not include the full response's final text block");
                String paintedTail = receipt.has("lastRenderedText") ? receipt.get("lastRenderedText").getAsString().strip() : "";
                require(!paintedTail.isEmpty() && "全文末尾：原生图形长回复验收完成。".endsWith(paintedTail),
                        "HUD full response tail was not actually painted after wheel scrolling");
                checkpoint("07-interactive-hud-bottom", false);
                dev.openallay.client.gui.GuideWidgetInputs.keyPressed((dev.openallay.client.gui.GuideWidgetInput) lite, GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE, 0));
                recordAction("native-key", "ESC/HUD-to-game");
                advance();
            }
            case 28 -> {
                require(MinecraftClientWindow.screen(client) == null, "HUD ESC left a Screen or focus behind");
                checkpoint("08-passive-hud-after-esc", false);
                openGuide.accept(service);
                advance();
            }
            case 29 -> { press("screen.openallay.settings.short"); advance(); }
            case 30 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 31 -> { press("screen.openallay.settings.ui.fullscreen"); advance(); }
            case 32 -> {
                expectedTheme = "MINT".equals(theme()) ? "CHARCOAL" : "MINT";
                press("screen.openallay.settings.ui.theme");
                advance();
            }
            case 33 -> { press("screen.openallay.settings.done"); advance(); }
            case 34 -> {
                if (!doneReturned()) return;
                require(expectedTheme.equals(theme()), "Cached-caption Done failed its theme acknowledgement");
                checkpoint("09-cached-caption-done-return", true);
                hudFrameBeforeDone = number(gson.toJsonTree(hudReceipt.get()).getAsJsonObject(), "extractedFrame");
                guide().onClose();
                advance();
            }
            case 35 -> {
                require(MinecraftClientWindow.screen(client) == null, "Cached HUD failed to return to gameplay");
                require(number(gson.toJsonTree(hudReceipt.get()).getAsJsonObject(), "extractedFrame") > hudFrameBeforeDone,
                        "Cached-caption HUD did not perform a fresh extraction after settings returned");
                checkpoint("10-passive-hud-cached-caption", false);
                openGuide.accept(service);
                // Editor previews advance passive paging. Keep the existing native recipe/item/tail
                // assertions before this case instead of changing their timing or acceptance checks.
                stage = 70;
                stageWait = 0;
            }
            case 36 -> {
                invoke(guide(), "e2eExportSelectedSession");
                recordAction("actual-export-callback", "selected-session");
                advance();
            }
            case 37 -> {
                JsonObject receipt = gson.toJsonTree(readReceipt(guide(), "e2eExportReceipt")).getAsJsonObject();
                if (receipt.get("running").getAsBoolean()) { waitFor("actual session export"); return; }
                require(receipt.has("lastFilename") && !receipt.get("lastFilename").isJsonNull()
                        && !receipt.get("lastFilename").getAsString().isBlank()
                        && "SUCCESS".equals(receipt.get("noticeSeverity").getAsString()),
                        "Actual export callback did not publish a successful file");
                String name = receipt.get("lastFilename").getAsString();
                Path exports = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("openallay/exports").toAbsolutePath().normalize();
                Path exported = exports.resolve(name).normalize();
                require(exported.getParent().equals(exports) && Files.isRegularFile(exported), "Actual export file is unavailable");
                try {
                    String text = Files.readString(exported, StandardCharsets.UTF_8);
                    require(text.contains(config.question()) && text.contains("原生图形长回复验收"), "Actual export lost current conversation text");
                    report.put("export", Map.of("filename", name, "bytes", Files.size(exported), "sha256", sha256(text),
                            "actualCallbackReceipt", receipt, "containsCurrentQuestionAndAnswer", true));
                } catch (IOException failure) { throw new IllegalStateException("Actual export readback failed", failure); }
                checkpoint("11-actual-export-success", true);
                microphoneMetadata = voiceSettings.get().refreshDevices();
                recordAction("metadata-only-device-refresh", "no-capture-open-start-or-read");
                advance();
            }
            case 38 -> {
                if (!microphoneMetadata.isDone()) { waitFor("metadata-only microphone enumeration"); return; }
                ToolResult<List<AudioCapture.Device>> devices = microphoneMetadata.join();
                if (devices instanceof ToolResult.Success<List<AudioCapture.Device>> success) {
                    List<AudioCapture.Device> list = success.value();
                    report.put("microphoneMetadata", Map.of("outcome", "SUCCEEDED", "deviceCount", list.size(),
                            "hasDefault", list.stream().anyMatch(value -> value.id().equals("default")),
                            "explicitDeviceCount", list.stream().filter(value -> !value.id().equals("default")).count(),
                            "captureAttempted", false));
                } else {
                    ToolResult.Failure<List<AudioCapture.Device>> failure = (ToolResult.Failure<List<AudioCapture.Device>>) devices;
                    report.put("microphoneMetadata", Map.of("outcome", "UNAVAILABLE", "code", failure.code(), "captureAttempted", false));
                }
                advance();
            }
            case 39 -> {
                require(traceLookup != null, "Actual client trace lookup is unavailable");
                var trace = traceLookup.apply(request.modelSelection().profileId(), request.requestId());
                if (trace.isEmpty()) { waitFor("actual request trace publication"); return; }
                try {
                    Files.createDirectories(config.tracePath().toAbsolutePath().getParent());
                    Files.writeString(config.tracePath(), trace.orElseThrow(), StandardCharsets.UTF_8);
                } catch (IOException failure) { throw new IllegalStateException("Actual trace retention failed", failure); }
                advance();
            }
            case 40 -> {
                if (frames.values().stream().anyMatch(value -> !value.isDone())) { waitFor("native GPU frame publication"); return; }
                report.put("nativeFrames", frames.values().stream().map(CompletableFuture::join).toList());
                report.put("themeChangeCount", themeChanges + 1);
                report.put("elapsedMillis", Duration.between(started, Instant.now()).toMillis());
                report.put("outcome", "COMPLETED");
                restoreKey();
                done = true;
                completed.accept(report);
            }
            case 42 -> { navigate("screen.openallay.settings.general"); advance(); }
            case 43 -> {
                expectedName = "小羽 · Escape 保存";
                nameEditor().setValue(expectedName);
                GuideNativeInput.keyPressed(settingsScreen(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE, 0));
                recordAction("native-key", "ESC/dirty-settings");
                advance();
            }
            case 44 -> {
                if (!doneReturned()) return;
                require(expectedName.equals(settings.snapshot().display().assistantName()), "ESC lost the dirty name");
                exits.add(Map.of("entry", "nativeEscape", "savedName", expectedName, "ackBeforeClose", true));
                checkpoint("exit-escape-saved", true);
                MinecraftClientWindow.setWindowed(client, 320, 480);
                press("screen.openallay.settings.short");
                advance();
            }
            case 45 -> { navigate("screen.openallay.settings.general"); advance(); }
            case 46 -> {
                expectedName = "小羽 · 返回保存";
                nameEditor().setValue(expectedName);
                GuideNativeButton back = findButton("screen.openallay.settings.back", false);
                require(back != null, "Native narrow Back button is not visible");
                report.put("narrowBack", Map.of("visible", true, "guiWidth", settingsScreen().width,
                        "guiHeight", settingsScreen().height, "guiScale", dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client))));
                checkpoint("exit-back-dirty-narrow", false);
                press("screen.openallay.settings.back");
                advance();
            }
            case 47 -> {
                if (!doneReturned()) return;
                require(expectedName.equals(settings.snapshot().display().assistantName()), "Native Back lost the dirty name");
                exits.add(Map.of("entry", "nativeBackButton", "savedName", expectedName, "ackBeforeClose", true));
                checkpoint("exit-back-saved", true);
                MinecraftClientWindow.setWindowed(client, 850, 480);
                press("screen.openallay.settings.short");
                advance();
            }
            case 48 -> { navigate("screen.openallay.settings.general"); advance(); }
            case 49 -> {
                validNameBeforeFailure = settings.snapshot().display().assistantName();
                generationBeforeFailure = settings.snapshot().generation();
                nameEditor().setValue("   ");
                press("screen.openallay.settings.done");
                advance();
            }
            case 50 -> {
                settingsScreen();
                require("   ".equals(nameEditor().getValue()), "Invalid Done lost the edited field");
                require(validNameBeforeFailure.equals(settings.snapshot().display().assistantName()), "Invalid Done changed the published name");
                checkpoint("failure-invalid-name-retained", false);
                report.put("invalidDone", Map.of("stayedOnSettings", true, "draftRetained", true, "lastValidUnchanged", true));
                displayFault = CompletableFuture.runAsync(this::installDisplayFault);
                advance();
            }
            case 51 -> {
                if (!displayFault.isDone()) { waitFor("isolated display write-failure preparation"); return; }
                displayFault.join();
                nameEditor().setValue("小羽 · 写入失败草稿");
                press("screen.openallay.settings.general.assistant_name.save");
                advance();
            }
            case 52 -> {
                if (!writeFailureRetained()) return;
                checkpoint("failure-apply-write-retained", false);
                press("screen.openallay.settings.done");
                advance();
            }
            case 53 -> {
                if (!writeFailureRetained()) return;
                checkpoint("failure-done-write-retained", false);
                displayFault = CompletableFuture.runAsync(this::restoreDisplayFile);
                advance();
            }
            case 54 -> {
                if (!displayFault.isDone()) { waitFor("isolated display file restoration"); return; }
                displayFault.join();
                nameEditor().setValue(validNameBeforeFailure);
                press("screen.openallay.settings.done");
                advance();
            }
            case 55 -> {
                if (!doneReturned()) return;
                require(validNameBeforeFailure.equals(settings.snapshot().display().assistantName()), "Write-failure cleanup did not retain the last valid name");
                checkpoint("failure-recovery-done", true);
                press("screen.openallay.settings.short");
                stage = 7;
                stageWait = 0;
            }
            case 60 -> {
                int selected = new int[]{0, 18, 80}[sliderChoice];
                if (!selectReplySlider(selected)) return;
                advance();
            }
            case 61 -> {
                checkpoint("hud-slider-" + new int[]{0, 18, 80}[sliderChoice] + "-draft", false);
                press("screen.openallay.settings.done");
                advance();
            }
            case 62 -> {
                if (!doneReturned()) return;
                int selected = new int[]{0, 18, 80}[sliderChoice];
                require(settings.snapshot().display().ui().hud().maxReplyLines() == selected, "Actual HUD slider selection was not saved");
                report.put("hudSlider" + selected, Map.of("nativeControl", true, "savedValue", selected, "doneAcknowledged", true));
                checkpoint("hud-slider-" + selected + "-saved", true);
                sliderChoice++;
                if (sliderChoice == 3) { stage = 24; stageWait = 0; return; }
                press("screen.openallay.settings.short");
                advance();
            }
            case 63 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 64 -> { press("screen.openallay.settings.ui.hud"); stage = 60; stageWait = 0; }
            case 65 -> {
                require(MinecraftClientWindow.screen(client) == null, "Passive latest tail captured gameplay");
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                if (number(receipt, "extractedFrame") <= passiveRecipeFrame) {
                    waitFor("fresh passive HUD latest-tail extraction"); return;
                }
                requireHudLatest(receipt, request);
                checkpoint("05-passive-hud-latest-tail-stable", false);
                GuideProbeKeyBindings.click(dev.openallay.client.gui.GuideInputCodes.KEY_F8);
                recordAction("native-keymapping-click", "INTERACT_HUD/F8");
                stage = 26; stageWait = 0;
            }
            case 70 -> { press("screen.openallay.settings.short"); advance(); }
            case 71 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 72 -> { press("screen.openallay.settings.ui.hud"); advance(); }
            case 73 -> {
                if (hudDisplayBeforeDrag == null) {
                    JsonObject state = gson.toJsonTree(readReceipt(settingsScreen(), "e2eSettingsState")).getAsJsonObject();
                    require(!state.get("saving").getAsBoolean() && !state.get("uiDirty").getAsBoolean()
                                    && settings.snapshot().operation().kind() == SettingsOperation.Kind.IDLE,
                            "HUD drag case must enter with acknowledged, clean settings");
                    require(displayFault == null || displayFault.isDone(), "Display fault preparation/restoration is still running");
                    require(displayBackup == null || !Files.exists(displayBackup),
                            "Display fault backup was not restored before HUD editing");
                    hudDisplayBeforeDrag = settings.snapshot().display();
                    var hud = hudDisplayBeforeDrag.ui().hud();
                    require(hud.enabled() && hud.maxReplyLines() == 80, "HUD drag must retain the saved 80-line native candidate");
                    requireHudOffsetFields(hud);
                    report.put("hudLayoutDragApply", hudDrag);
                    hudDrag.put("scope", "normal Apply only; actual native editor callbacks and current saved display");
                    hudDrag.put("beforeDisplay", hudDisplayBeforeDrag);
                    hudDrag.put("beforePosition", Map.of("anchor", hud.anchor().name(),
                            "offsetX", hud.offsetX(), "offsetY", hud.offsetY()));
                    hudDrag.put("beforeOffsetFields", Map.of("offsetX", hudOffsetEditor("offset_x").getValue(),
                            "offsetY", hudOffsetEditor("offset_y").getValue()));
                    hudDrag.put("pointerEvents", hudPointerEvents);
                    hudDrag.put("visualReview", "REQUIRED: native before/dragged/reopened PNGs; no pixel or cached editor-bounds assertion");
                }
                if (!revealHudEditorButton()) return;
                checkpoint("hud-layout-settings-before-edit", false);
                clickHudCaseButton("screen.openallay.settings.ui.edit_hud");
                advance();
            }
            case 74 -> {
                GuideHudEditorScreen editor = hudEditor();
                require(dev.openallay.client.gui.MinecraftClientWindow.focused(client), "HUD editor requires an active native window for dragging");
                var hud = hudDisplayBeforeDrag.ui().hud();
                hudBoundsBeforeDrag = GuideHudLayout.calculate(editor.width, editor.height, hud);
                requireHudInsideWindow(hudBoundsBeforeDrag, editor.width, editor.height);
                hudDragDx = boundedHudDelta(hudBoundsBeforeDrag.x(), hudBoundsBeforeDrag.width(), editor.width, 48);
                hudDragDy = boundedHudDelta(hudBoundsBeforeDrag.y(), hudBoundsBeforeDrag.height(), editor.height, 32);
                var moved = GuideHudLayout.placementAt(editor.width, editor.height, hud,
                        hudBoundsBeforeDrag.x() + hudDragDx, hudBoundsBeforeDrag.y() + hudDragDy);
                require(moved.offsetX() != hud.offsetX() && moved.offsetY() != hud.offsetY(),
                        "The bounded HUD drag must change both saved offset fields");
                hudDisplayAfterDrag = hudDisplayBeforeDrag.withUi(hudDisplayBeforeDrag.ui().withHud(moved));
                hudBoundsAfterDrag = GuideHudLayout.calculate(editor.width, editor.height, moved);
                requireHudInsideWindow(hudBoundsAfterDrag, editor.width, editor.height);
                require(Math.abs(hudBoundsAfterDrag.x() - hudBoundsBeforeDrag.x() - hudDragDx) < .01
                                && Math.abs(hudBoundsAfterDrag.y() - hudBoundsBeforeDrag.y() - hudDragDy) < .01,
                        "HUD drag planning hit an anchor/window clamp");
                // This point is inside the real HUD body, not its lower-right resize handle.
                hudDragPointerX = hudBoundsBeforeDrag.x() + Math.min(24, hudBoundsBeforeDrag.width() / 4);
                hudDragPointerY = hudBoundsBeforeDrag.y() + Math.min(24, hudBoundsBeforeDrag.height() / 4);
                require(hudBoundsBeforeDrag.contains(hudDragPointerX, hudDragPointerY)
                                && hudDragPointerY < hudBoundsBeforeDrag.bottom() - 10,
                        "HUD drag start is not in the native move region");
                hudDragPreviewBefore = number(gson.toJsonTree(hudReceipt.get()).getAsJsonObject(), "extractedFrame");
                hudDrag.put("viewport", Map.of("width", editor.width, "height", editor.height,
                        "guiScale", dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client))));
                hudDrag.put("plannedBeforeBounds", hudBoundsBeforeDrag);
                hudDrag.put("plannedAfterBounds", hudBoundsAfterDrag);
                hudDrag.put("plannedDelta", Map.of("x", hudDragDx, "y", hudDragDy));
                hudDrag.put("expectedAfterDisplay", hudDisplayAfterDrag);
                hudDrag.put("geometrySource", "read-only GuideHudLayout expectations; not a rendered-position receipt");
                checkpoint("hud-layout-editor-before-drag", false);
                advance();
            }
            case 75 -> {
                GuideHudEditorScreen editor = hudEditor();
                var event = GuideNativeInput.mouseEvent(hudDragPointerX, hudDragPointerY, dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT, 0);
                boolean handled = GuideNativeInput.mouseClicked(editor, event, false);
                recordHudPointer("mouseClicked", event, 0, 0, handled);
                require(handled, "Native HUD editor did not consume the real pointer press");
                advance();
            }
            case 76 -> {
                GuideHudEditorScreen editor = hudEditor();
                require(dev.openallay.client.gui.MinecraftClientWindow.focused(client), "Native HUD drag lost window focus");
                var event = GuideNativeInput.mouseEvent(hudDragPointerX + hudDragDx, hudDragPointerY + hudDragDy, dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT, 0);
                boolean handled = GuideNativeInput.mouseDragged(editor, event, hudDragDx, hudDragDy);
                recordHudPointer("mouseDragged", event, hudDragDx, hudDragDy, handled);
                require(handled, "Native HUD editor did not consume the real drag callback");
                require(hudDisplayBeforeDrag.equals(settings.snapshot().display()), "Dragging saved settings before Apply");
                advance();
            }
            case 77 -> {
                GuideHudEditorScreen editor = hudEditor();
                var event = GuideNativeInput.mouseEvent(hudDragPointerX + hudDragDx, hudDragPointerY + hudDragDy, dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT, 0);
                boolean handled = GuideNativeInput.mouseReleased(editor, event);
                recordHudPointer("mouseReleased", event, 0, 0, handled);
                require(handled, "Native HUD editor did not finish the actual pointer drag");
                require(hudDisplayBeforeDrag.equals(settings.snapshot().display()), "Pointer release saved settings before Apply");
                advance();
            }
            case 78 -> {
                hudEditor();
                JsonObject preview = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                require(number(preview, "extractedFrame") > hudDragPreviewBefore,
                        "HUD drag has no fresh actual editor-preview extraction");
                hudDragPreviewAfter = number(preview, "extractedFrame");
                hudDrag.put("actualDraggedPreviewReceipt", preview);
                checkpoint("hud-layout-editor-dragged", false);
                hudDragGenerationBeforeApply = settings.snapshot().generation();
                clickHudCaseButton("screen.openallay.hud.editor.apply");
                advance();
            }
            case 79 -> {
                if (!hudCandidateReturned()) return;
                require(settings.snapshot().generation() > hudDragGenerationBeforeApply,
                        "HUD Apply has no new settings publication");
                require(settings.snapshot().notice() != null
                                && settings.snapshot().notice().level() == dev.openallay.settings.SettingsNotice.Level.SUCCESS
                                && "display_saved".equals(settings.snapshot().notice().code()),
                        "Actual HUD Apply did not publish a successful display save acknowledgement");
                require(hudDisplayAfterDrag.equals(settings.snapshot().display()),
                        "HUD Apply lost dragged offsets or changed another display field");
                requireHudOffsetFields(hudDisplayAfterDrag.ui().hud());
                hudDrag.put("applyAcknowledgement", Map.of("generationBefore", hudDragGenerationBeforeApply,
                        "generationAfter", settings.snapshot().generation(), "operation", settings.snapshot().operation().kind().name(),
                        "noticeCode", settings.snapshot().notice().code(), "ackBeforeDone", true,
                        "actualSettingsState", readReceipt(settingsScreen(), "e2eSettingsState")));
                hudDrag.put("savedDisplay", settings.snapshot().display());
                var savedHud = settings.snapshot().display().ui().hud();
                hudDrag.put("savedPosition", Map.of("anchor", savedHud.anchor().name(),
                        "offsetX", savedHud.offsetX(), "offsetY", savedHud.offsetY()));
                checkpoint("hud-layout-apply-acknowledged", false);
                hudDisplayReadback = CompletableFuture.supplyAsync(() -> readHudDisplay(hudDisplayAfterDrag));
                advance();
            }
            case 80 -> {
                if (!hudDisplayReadback.isDone()) { waitFor("actual dragged HUD display file readback"); return; }
                hudDrag.put("displayFileAfterApply", hudDisplayReadback.join());
                press("screen.openallay.settings.done");
                advance();
            }
            case 81 -> {
                if (!doneReturned()) return;
                require(hudDisplayAfterDrag.equals(settings.snapshot().display()), "Settings Done replaced the applied HUD position");
                checkpoint("hud-layout-done-return", true);
                hudDragFrameBeforePassive = number(gson.toJsonTree(hudReceipt.get()).getAsJsonObject(), "extractedFrame");
                guide().onClose();
                advance();
            }
            case 82 -> {
                require(MinecraftClientWindow.screen(client) == null, "Applied HUD did not return to actual gameplay");
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                if (number(receipt, "extractedFrame") <= hudDragFrameBeforePassive) {
                    waitFor("actual passive HUD extraction after layout Apply");
                    return;
                }
                require(hudDisplayAfterDrag.equals(settings.snapshot().display()), "Passive HUD lost the acknowledged placement");
                require(number(receipt, "viewportHeight") == expectedHudBodyHeight(hudDisplayAfterDrag.ui().hud(), hudBoundsAfterDrag),
                        "Actual HUD extraction changed the dragged candidate's result viewport");
                hudDrag.put("actualPassiveReceiptAfterApply", receipt);
                checkpoint("hud-layout-passive-after-apply", false);
                openGuide.accept(service);
                advance();
            }
            case 83 -> { press("screen.openallay.settings.short"); advance(); }
            case 84 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 85 -> { press("screen.openallay.settings.ui.hud"); advance(); }
            case 86 -> {
                require(hudDisplayAfterDrag.equals(settings.snapshot().display()), "Reopened settings lost the dragged position");
                requireHudOffsetFields(hudDisplayAfterDrag.ui().hud());
                if (!revealHudEditorButton()) return;
                clickHudCaseButton("screen.openallay.settings.ui.edit_hud");
                advance();
            }
            case 87 -> {
                GuideHudEditorScreen editor = hudEditor();
                require(editor.width == dev.openallay.client.gui.MinecraftClientWindow.guiWidth(client)
                                && editor.height == dev.openallay.client.gui.MinecraftClientWindow.guiHeight(client),
                        "Reopened native HUD editor uses an unexpected viewport");
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                require(number(receipt, "extractedFrame") > hudDragPreviewAfter,
                        "Reopened HUD editor did not extract a new native preview");
                hudDrag.put("actualReopenedPreviewReceipt", receipt);
                checkpoint("hud-layout-editor-reopened", false);
                advance();
            }
            case 88 -> {
                hudEditor();
                // A real Apply with no further edits must leave the complete saved candidate unchanged.
                // This checks transaction retention, not whether the PNG pixels show the expected position.
                clickHudCaseButton("screen.openallay.hud.editor.apply");
                advance();
            }
            case 89 -> {
                if (!hudCandidateReturned()) return;
                require(hudDisplayAfterDrag.equals(settings.snapshot().display()),
                        "Reopened editor Apply did not retain the complete dragged candidate");
                requireHudOffsetFields(hudDisplayAfterDrag.ui().hud());
                hudDisplayReadback = CompletableFuture.supplyAsync(() -> readHudDisplay(hudDisplayAfterDrag));
                advance();
            }
            case 90 -> {
                if (!hudDisplayReadback.isDone()) { waitFor("actual reopened HUD candidate display file readback"); return; }
                hudDrag.put("displayFileAfterReopenedApply", hudDisplayReadback.join());
                hudDrag.put("reopenedApplyRetainedCandidate", true);
                hudDrag.put("reopenedApplySettingsState", readReceipt(settingsScreen(), "e2eSettingsState"));
                press("screen.openallay.settings.done");
                advance();
            }
            case 91 -> {
                if (!doneReturned()) return;
                require(hudDisplayAfterDrag.equals(settings.snapshot().display()), "Reopened editor Done replaced the dragged position");
                hudDrag.put("savedPositionVerified", true);
                hudDrag.put("outcome", "COMPLETED");
                checkpoint("hud-layout-reopened-done-return", true);
                stage = 36;
                stageWait = 0;
            }
            default -> throw new IllegalStateException("Unknown native graphical stage " + stage);
        }
    }

    /** Separate bounded native scenario. Unit/source tests do not certify these frames. */
    private void runLiveStage() {
        switch (stage) {
            case 0 -> {
                require(developmentProbeEnabled, "Development opt-in was disabled at construction");
                requireLoopbackFixture();
                require(config.question().startsWith(LIVE_PREFIX) && config.question().contains(" hold"),
                        "Live UX scenario requires the explicit held loopback question prefix");
                require("zh_cn".equals(dev.openallay.platform.minecraft.MinecraftOptions.language(dev.openallay.client.context.MinecraftClientContextFacts.options(client))), "Chinese language must be prepared before launch");
                var voice = dev.openallay.client.voice.VoiceConfigStore.decode(readCurrentConfig("voice.json"));
                require(!voice.enabled(), "Native GUI scenario must not open a microphone");
                report.put("voiceConfig", gson.toJsonTree(voice));
                require(GuideProbeKeyBindings.isKeyboard(OpenAllayKeyMappings.INTERACT_HUD, dev.openallay.client.gui.GuideInputCodes.KEY_F8),
                        "Fresh native profile must retain the new F8 default without a harness override");
                liveHeaderName = settings.snapshot().display().assistantName();
                MinecraftClientWindow.setWindowed(client, 850, 480);
                GuideProbeKeyBindings.keyboard(OpenAllayKeyMappings.VOICE_PTT, dev.openallay.client.gui.GuideInputCodes.KEY_V);
                GuideProbeKeyBindings.refresh();
                openGuide.accept(service);
                advance();
            }
            case 1 -> {
                require(guide().guideWidgetFocused(composer().widget()), "New Guide did not focus its actual native composer");
                require(GuideNativeInput.charTyped(guide(), GuideNativeInput.characterEvent('x')), "Initial native character was not routed to composer");
                require("x".equals(composer().getValue()), "Initial character callback did not edit native input");
                checkpoint("live-01-initial-character-focus", true);
                clickAt(guide(), 1, 1, "blank-outside-composer");
                require(!guide().guideWidgetFocused(composer().widget()), "Blank click did not blur native text input");
                MinecraftClientWindow.setWindowed(client, 900, 540);
                advance();
            }
            case 2 -> {
                if (nativePrimitiveProbe != null && nativePrimitiveProbe.started()) {
                    if (!nativePrimitiveProbe.tick(guide(), composer())) return;
                    dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer(), config.question(), true);
                    advance();
                    return;
                }
                require(!guide().guideWidgetFocused(composer().widget()), "Resize/rebuild incorrectly refocused blurred composer");
                require("x".equals(composer().getValue()), "Resize lost player draft");
                clickAt(guide(), dev.openallay.client.gui.GuideNativeWidgetGeometry.x(composer().widget()) + 8, dev.openallay.client.gui.GuideNativeWidgetGeometry.y(composer().widget()) + 8, "composer-focus");
                require(guide().guideWidgetFocused(composer().widget()), "Native input click did not restore text focus");
                String beforeTypedPttKey = composer().getValue();
                boolean endHandled = GuideNativeInput.keyPressed(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_END, 0));
                recordAction("native-key", "END/focused-composer-before-typed-PTT-key");
                require(endHandled && guide().guideWidgetFocused(composer().widget()), "Native End did not retain composer focus");
                GuideNativeInput.keyPressed(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_V, 0));
                boolean characterHandled = GuideNativeInput.charTyped(guide(), GuideNativeInput.characterEvent('v'));
                GuideNativeInput.keyReleased(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_V, 0));
                report.put("typedPttKeyNativeEdit", Map.of("beforeValue", beforeTypedPttKey,
                        "afterValue", composer().getValue(), "endKeyHandled", endHandled,
                        "characterHandled", characterHandled, "composerFocused", guide().guideWidgetFocused(composer().widget())));
                require(characterHandled && guide().guideWidgetFocused(composer().widget()) && "xv".equals(composer().getValue()),
                        "Focused PTT-bound typed key did not insert at native composer End");
                report.put("focusReceipts", Map.of("initialChar", true, "blankBlur", true,
                        "resizeRemainsBlurred", true, "typedPttKeyEditsText", true,
                        "voiceDisabledNoCapture", true));
                checkpoint("live-02-blur-resize-typed-ptt", true);
                if (nativePrimitiveProbe != null) {
                    nativePrimitiveProbe.begin(guide(), composer());
                    return;
                }
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer(), config.question(), true);
                advance();
            }
            case 3 -> { press("screen.openallay.action.send"); advance(); }
            case 4 -> {
                request = requestFor(config.question());
                if (request == null || request.tools().isEmpty()
                        || request.tools().get(0).status() != GuideToolStatus.SUCCEEDED) {
                    waitFor("actual first native recipe Tool while continuation transport is held"); return;
                }
                require(!request.terminal(), "Transport hold did not keep a real task active");
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer(), LIVE_FOLLOW_UP + " queued-native-callback", true);
                advance();
            }
            case 5 -> { press("screen.openallay.pending.follow_up"); advance(); }
            case 6 -> {
                var pending = session().pendingMessages();
                if (pending.isEmpty()) { waitFor("actual Follow-up admission receipt"); return; }
                var follow = pending.get(0);
                require(follow.kind() == dev.openallay.guide.GuidePendingMessage.Kind.FOLLOW_UP
                        && follow.text().startsWith(LIVE_FOLLOW_UP), "Real Follow-up queue has wrong kind/text");
                liveFollowUpId = follow.id();
                report.put("followUpAccepted", gson.toJsonTree(follow));
                var notice = (dev.openallay.client.gui.GuideUiNotice) readField(guide(), "notice");
                require(notice.message().equals(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.accepted.follow_up"))),
                        "Actual Follow-up callback did not show queued admission feedback");
                report.put("followUpNotice", notice);
                checkpoint("live-03-follow-up-accepted-active", true);
                var extras = (dev.openallay.guide.ui.GuideUiLayout.ComposerExtras) readField(guide(), "composerExtras");
                clickAt(guide(), extras.footer().x() + 4, extras.footer().y() + 4, "composer-mode-steer");
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer(), LIVE_STEER + " admitted-native-callback", true);
                advance();
            }
            case 7 -> { press("screen.openallay.pending.steer"); advance(); }
            case 8 -> {
                var pending = session().pendingMessages();
                if (pending.size() < 2) { waitFor("actual Steer admission receipt"); return; }
                var steer = pending.get(1);
                require(pending.get(0).id().equals(liveFollowUpId)
                        && steer.kind() == dev.openallay.guide.GuidePendingMessage.Kind.STEER
                        && steer.text().startsWith(LIVE_STEER) && steer.requestId().equals(request.requestId()),
                        "Actual pending receipt did not preserve Follow-up then Steer order");
                liveSteerId = steer.id();
                report.put("steerAccepted", gson.toJsonTree(steer));
                var notice = (dev.openallay.client.gui.GuideUiNotice) readField(guide(), "notice");
                require(notice.message().equals(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.accepted.steer"))),
                        "Actual Steer callback did not show pending admission feedback");
                report.put("steerNotice", notice);
                report.put("pendingReceiptOrder", pending.stream().map(value -> value.id().toString()).toList());
                checkpoint("live-04-steer-accepted-active", true);
                liveTransportRelease = releaseLiveTransport();
                advance();
            }
            case 9 -> {
                if (!liveTransportRelease.isDone()) { waitFor("loopback fixture transport release"); return; }
                require(liveTransportRelease.join() == 204, "Loopback transport release was rejected");
                request = requestFor(config.question());
                latestRequest = session().requests().stream().filter(value -> value.userMessage().startsWith(LIVE_FOLLOW_UP))
                        .findFirst().orElse(null);
                if (request == null || !request.terminal() || latestRequest == null || !latestRequest.terminal()) {
                    waitFor("actual original task and queued Follow-up terminal receipts"); return;
                }
                validateLiveRequest(request);
                validateLiveRequest(latestRequest);
                require(session().pendingMessages().isEmpty(), "Completed real queue retained pending drafts");
                var admitted = request.timeline().stream().filter(dev.openallay.guide.GuideTimelineEntry.User.class::isInstance)
                        .map(dev.openallay.guide.GuideTimelineEntry.User.class::cast).toList();
                require(admitted.stream().anyMatch(value -> value.messageId().equals(liveSteerId)
                        && value.text().startsWith(LIVE_STEER)), "Steer was not actually admitted into original timeline");
                report.put("admittedSteerTimeline", admitted);
                report.put("followUpRequestId", latestRequest.requestId().toString());
                require(latestRequest.createdAt().compareTo(request.createdAt()) >= 0, "Follow-up request preceded original request");
                var layout = (dev.openallay.guide.ui.GuideUiLayout) readField(guide(), "layout");
                liveHoverFrame = number(jsonReceipt(guide(), "e2eTelemetryTooltipReceipt"), "requestedNativeFrame");
                hoverNative(layout.telemetry().x() + 8, layout.telemetry().y() + 8);
                recordNativeHoverDiagnostic("stage9-after-native-cursor-request");
                advance();
            }
            case 10 -> {
                recordNativeHoverDiagnostic("stage10-before-reissue");
                JsonObject tooltip = jsonReceipt(guide(), "e2eTelemetryTooltipReceipt");
                if (number(tooltip, "requestedNativeFrame") <= liveHoverFrame) {
                    var layout = (dev.openallay.guide.ui.GuideUiLayout) readField(guide(), "layout");
                    // Alternate inside the same native target to request a real OS cursor callback.
                    // Never invoke extraction or write the MouseHandler's coordinates.
                    hoverNative(layout.telemetry().x() + 8 + (++liveHoverAttempts % 2), layout.telemetry().y() + 8);
                    recordNativeHoverDiagnostic("stage10-after-native-cursor-reissue");
                    require(liveHoverAttempts < 5, "Native telemetry hover did not extract after 5 real cursor requests; inspect nativeHoverDiagnostics");
                    return;
                }
                require(number(tooltip, "logicalLineCount") >= 3 && number(tooltip, "requestedLineCount") >= 3
                        && number(tooltip, "requestedWidth") > 0 && number(tooltip, "requestedWidth") <= 300,
                        "Native telemetry tooltip did not request bounded multiline paint");
                report.put("nativeTelemetryHover", tooltip);
                checkpoint("live-05-native-hover-known-budget-unknown-cost", true);
                hoverNative(1, 1);
                clickAt(guide(), 1, 1, "blur-before-native-transcript-home");
                GuideNativeInput.keyPressed(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_HOME, 0));
                advance();
            }
            case 11 -> {
                JsonObject summary = revealRecipeSummary(request);
                if (summary == null) return;
                liveRecipeToolId = summary.get("id").getAsString();
                report.put("compactComfortableSummary", summary);
                JsonObject second = revealToolSummary(request, 1);
                if (second == null) return;
                require(number(second.getAsJsonObject("bounds"), "height") == 28
                        && second.get("description").getAsString().isBlank(), "Title-only actual native Tool card is not 28 pixels");
                report.put("actualTitleOnly28Summary", second);
                summary = revealRecipeSummary(request);
                if (summary == null) return;
                validateCompactSummaries(jsonReceipt(guide(), "e2eToolsReceipt"));
                checkpoint("live-06-comfortable-tool-summary", true);
                clickAt(guide(), summary.get("blankClickX").getAsDouble(), summary.get("blankClickY").getAsDouble(), "tool-row-blank-padding");
                advance();
            }
            case 12 -> {
                if (!detailRecipePainted(liveRecipeToolId)) return;
                report.put("actualNativeRecipeDetail", jsonReceipt(guide(), "e2eToolsReceipt"));
                checkpoint("live-07-tool-detail-native-recipe", true);
                GuideNativeInput.keyPressed(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE, 0));
                advance();
            }
            case 13 -> {
                JsonObject summary = revealRecipeSummary(request);
                if (summary == null) return;
                var capsules = summary.getAsJsonArray("capsules");
                require(!(capsules.size() == 0), "Actual native recipe summary icon was not painted");
                JsonObject capsule = capsules.get(0).getAsJsonObject();
                JsonObject bounds = capsule.getAsJsonObject("bounds");
                clickAt(guide(), number(bounds, "x") + number(bounds, "width") / 2.0,
                        number(bounds, "y") + number(bounds, "height") / 2.0, "native-summary-capsule-priority");
                report.put("actualCapsuleClicked", capsule);
                advance();
            }
            case 14 -> {
                if (MinecraftClientWindow.screen(client) instanceof OpenAllayScreen)
                    require(jsonReceipt(guide(), "e2eToolsReceipt").get("detailToolId").getAsString().isEmpty(),
                            "Native child capsule opened parent Tool drawer");
                else { dev.openallay.client.gui.GuideWidgetInputs.keyPressed((dev.openallay.client.gui.GuideWidgetInput) MinecraftClientWindow.screen(client), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE, 0)); openGuide.accept(service); }
                checkpoint("live-08-native-child-priority-no-parent-drawer", true);
                press("screen.openallay.settings.short"); advance();
            }
            case 15 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 16 -> { press("screen.openallay.settings.ui.fullscreen"); advance(); }
            case 17 -> { press("screen.openallay.settings.ui.density"); press("screen.openallay.settings.done"); advance(); }
            case 18 -> {
                if (!doneReturned()) return;
                require(settings.snapshot().display().ui().fullscreen().density() == GuideUiConfig.Density.COMPACT,
                        "Actual settings callback did not save Compact density");
                clickAt(guide(), 1, 1, "blur-before-compact-home");
                GuideNativeInput.keyPressed(guide(), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_HOME, 0)); advance();
            }
            case 19 -> {
                JsonObject summary = revealRecipeSummary(request);
                if (summary == null) return;
                require(number(summary.getAsJsonObject("bounds"), "height") == 40,
                        "Actual descriptive native summary card is not 40 pixels");
                validateCompactSummaries(jsonReceipt(guide(), "e2eToolsReceipt"));
                report.put("compactDensitySummary", summary);
                checkpoint("live-09-compact-tool-summary", true);
                press("screen.openallay.settings.short"); advance();
            }
            case 20 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 21 -> { press("screen.openallay.settings.ui.hud"); advance(); }
            case 22 -> { press("screen.openallay.settings.ui.hud_enabled"); press("screen.openallay.settings.done"); advance(); }
            case 23 -> {
                if (!doneReturned()) return;
                require(settings.snapshot().display().ui().hud().enabled(), "HUD enabled draft was not acknowledged");
                liveHudFrame = number(gson.toJsonTree(hudReceipt.get()).getAsJsonObject(), "extractedFrame");
                guide().onClose(); advance();
            }
            case 24 -> {
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                if (number(receipt, "extractedFrame") <= liveHudFrame) { waitFor("fresh gameplay HUD tail extraction"); return; }
                requireHudLatest(receipt, latestRequest);
                if (++stageWait < 22) return; // 264 real client ticks, longer than the removed carousel interval.
                report.put("passiveHudLatestStable", receipt);
                checkpoint("live-10-passive-hud-latest-48-no-carousel", false);
                GuideProbeKeyBindings.unbind(OpenAllayKeyMappings.INTERACT_HUD);
                GuideProbeKeyBindings.refresh(); advance();
            }
            case 25 -> {
                report.put("explicitUnboundKeyLabel", MinecraftComponents.getString(dev.openallay.client.gui.GuideNativeKeyMappings.display(OpenAllayKeyMappings.INTERACT_HUD)));
                require(dev.openallay.client.gui.GuideNativeKeyMappings.unbound(OpenAllayKeyMappings.INTERACT_HUD), "Explicit unbound key was silently reset");
                checkpoint("live-11-passive-hud-explicit-unbound-hint", false);
                GuideProbeKeyBindings.keyboard(OpenAllayKeyMappings.INTERACT_HUD, dev.openallay.client.gui.GuideInputCodes.KEY_F8);
                GuideProbeKeyBindings.refresh();
                GuideProbeKeyBindings.click(dev.openallay.client.gui.GuideInputCodes.KEY_F8);
                recordAction("native-keymapping-click", "INTERACT_HUD/default-F8"); advance();
            }
            case 26 -> {
                Screen lite = lite();
                JsonObject receipt = jsonReceipt(lite, "resultReceipt");
                requireHudLatest(receipt, latestRequest);
                checkpoint("live-12-interactive-hud-opens-at-latest", false);
                wheelHud(lite, 8);
                advance();
            }
            case 27 -> {
                JsonObject receipt = jsonReceipt(lite(), "resultReceipt");
                require(number(receipt, "scroll") < number(receipt, "maximumScroll"), "Native wheel up did not leave latest tail");
                liveReaderScroll = (int) number(receipt, "scroll");
                liveReaderAnchor = receipt.getAsJsonArray("renderedRowIds").get(0).getAsString();
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer(), LIVE_FOLLOW_UP + " reader-new-content", true);
                liveHudFrame = number(receipt, "extractedFrame");
                report.put("readerSendPrepared", readerSendDiagnostic());
                stage = 127; stageWait = 0;
            }
            case 127 -> {
                JsonObject receipt = jsonReceipt(lite(), "resultReceipt");
                Map<String, Object> diagnostic = readerSendDiagnostic();
                report.put("readerSendBeforeCallback", diagnostic);
                require((boolean) diagnostic.get("draftMatches") && config.sessionId().equals(diagnostic.get("sessionId")),
                        "Native HUD reader draft or session changed before Send");
                require(!(boolean) diagnostic.get("submitting") && !(boolean) diagnostic.get("intentSubmissionInFlight")
                        && !(boolean) diagnostic.get("intentInvalid"), "Native HUD reader has an in-flight or invalid input intent before Send");
                if (number(receipt, "extractedFrame") < liveHudFrame + 3) {
                    waitFor("three actual HUD reader extractions after draft preparation"); return;
                }
                require((boolean) diagnostic.get("sendVisible") && (boolean) diagnostic.get("sendActive"),
                        "Native HUD reader Send was not active after three real draft projection frames");
                press("screen.openallay.action.send");
                stage = 28; stageWait = 0;
            }
            case 28 -> {
                var next = requestFor(LIVE_FOLLOW_UP + " reader-new-content");
                if (next == null || !next.terminal()) { waitFor("actual reader new-content request"); return; }
                validateLiveRequest(next);
                latestRequest = next;
                JsonObject receipt = jsonReceipt(lite(), "resultReceipt");
                require(number(receipt, "scroll") == liveReaderScroll
                        && dev.openallay.json.JsonReaders.elements(receipt.getAsJsonArray("renderedRowIds")).stream().anyMatch(value -> value.getAsString().equals(liveReaderAnchor)),
                        "New actual content displaced the reader's wheel-up anchor");
                report.put("readerAnchorAfterActualNewContent", receipt);
                checkpoint("live-13-reader-anchor-with-new-content", false);
                press("screen.openallay.hud.latest"); advance();
            }
            case 29 -> {
                requireHudLatest(jsonReceipt(lite(), "resultReceipt"), latestRequest);
                checkpoint("live-14-reader-native-latest-restores", false);
                wheelHud(lite(), 10000); advance();
            }
            case 30 -> {
                JsonObject receipt = jsonReceipt(lite(), "resultReceipt");
                require(number(receipt, "scroll") == 0 && number(receipt, "toolRows") > 0,
                        "Full native reader cannot reach prior loaded request Tool cards");
                require(dev.openallay.json.JsonReaders.elements(receipt.getAsJsonArray("renderedRowIds")).stream()
                        .anyMatch(value -> value.getAsString().contains(request.requestId().toString())),
                        "Reader first page lost the prior original request identity");
                report.put("priorRequestReader", receipt);
                checkpoint("live-15-reader-prior-request-cards", false);
                beginReaderGuideReopen();
                stage = 130; stageWait = 0;
            }
            case 130 -> {
                if (!readerGuideReopenReady()) return;
                requireReaderGuideReopenOwner();
                liveGuideReopen.consume(() -> MinecraftClientWindow.screen(client), screen -> {
                    GuideNativeButton button = findButton(screen, "screen.openallay.settings.short", true);
                    require(button != null, "Reopened Guide lost its initialized Settings button");
                    GuideNativeInput.press(button, GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_RETURN, 0));
                    recordAction("native-button", "screen.openallay.settings.short");
                    require(readField(screen, "attachment") == null,
                            "Settings callback did not detach the exact reopened Guide");
                });
                require(readField(settingsScreen(), "service") == settings,
                        "Settings callback opened another settings coordinator");
                stage = 31; stageWait = 0;
            }
            case 31 -> { navigate("screen.openallay.settings.ui"); advance(); }
            case 32 -> { press("screen.openallay.settings.ui.notifications"); advance(); }
            case 33 -> { press("screen.openallay.settings.ui.notifications_enabled"); press("screen.openallay.settings.done"); advance(); }
            case 34 -> {
                if (!doneReturned()) return;
                require(settings.snapshot().display().ui().notifications().enabled()
                        && settings.snapshot().display().ui().notifications().policy() == GuideUiConfig.NotificationPolicy.WHEN_GUIDE_NOT_VISIBLE,
                        "Native notification opt-in did not retain Guide-not-visible policy");
                JsonObject receipt = toastJson();
                require(receipt == null || !receipt.get("visible").getAsBoolean(), "Guide visible with empty cards admitted a popup");
                report.put("guideVisibleSuppression", receipt);
                liveToastQuestion = LIVE_PREFIX + " gameplay-toast";
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer(), liveToastQuestion, true);
                press("screen.openallay.action.send");
                guide().onClose(); advance();
            }
            case 35 -> {
                toastRequest = requestFor(liveToastQuestion);
                JsonObject receipt = toastJson();
                if (toastRequest == null || receipt == null || !receipt.get("visible").getAsBoolean()
                        || number(receipt, "frame") == 0) { waitFor("actual CardProduced owned native toast paint"); return; }
                require(receipt.get("requestId").getAsString().equals(toastRequest.requestId().toString()), "Native toast has another request identity");
                require(!receipt.get("title").getAsString().isBlank() && !receipt.get("description").getAsString().isBlank(),
                        "Actual card toast did not retain meaningful title and description");
                require(number(receipt, "width") <= 240 && number(receipt, "height") == 64 && number(receipt, "slots") == 2
                        && number(receipt, "titleLineCount") == 1 && number(receipt, "descriptionLineCount") <= 2,
                        "Actual native toast changed fixed geometry or exceeded text slots");
                require(receipt.get("noClickTarget").getAsBoolean(), "Native toast added a click target");
                require(toastRequest.tools().stream().anyMatch(tool -> tool.intent().title().equals(receipt.get("title").getAsString())
                        && tool.intent().description().equals(receipt.get("description").getAsString())),
                        "Native toast title/description do not match an actual meaningful Tool card");
                report.put("actualCardNativeToast", receipt);
                checkpoint("live-16-actual-card-title-description-native-toast", false);
                openGuide.accept(service); advance();
            }
            case 36 -> {
                JsonObject receipt = toastJson();
                require(receipt != null && !receipt.get("visible").getAsBoolean() && receipt.get("ownedHidden").getAsBoolean()
                        && number(receipt, "hideOrder") > number(receipt, "showOrder"),
                        "Opening same-session Guide did not immediately hide owned native popup");
                liveTransportRelease = releaseLiveTransport();
                report.put("actualOwnedToastHiddenOnGuide", receipt);
                checkpoint("live-17-visible-guide-owned-toast-hidden", true); advance();
            }
            case 37 -> {
                if (!liveTransportRelease.isDone()) { waitFor("gameplay toast fixture transport release"); return; }
                require(liveTransportRelease.join() == 204, "Gameplay toast release was rejected");
                toastRequest = requestFor(liveToastQuestion);
                if (toastRequest == null || !toastRequest.terminal()) { waitFor("real gameplay toast task completion"); return; }
                validateLiveRequest(toastRequest);
                require(liveHeaderName.equals(settings.snapshot().display().assistantName()), "Live UI scenario changed assistant full name");
                OpenAllayScreen owner = guide();
                require(liveHeaderOwner == null || liveHeaderOwner == owner,
                        "Live terminal header screen owner was replaced");
                Object title = readField(owner, "headerTitleWidget");
                long frame = number(jsonReceipt(owner, "e2eToolsReceipt"), "lastNativeFrame");
                if (liveHeaderOwner == null || liveHeaderWidget != title) {
                    liveHeaderOwner = owner;
                    liveHeaderWidget = title;
                    liveHeaderFrame = frame;
                }
                JsonObject header = jsonReceipt(owner, "e2eHeaderReceipt");
                Map<String, Object> terminalHeader = liveTerminalHeaderDiagnostic(owner, title, frame, header);
                report.put("liveTerminalHeader", terminalHeader);
                // Terminal projection can recreate HeaderTitle on this end-tick, before extraction.
                // Wait for this exact widget, not an old screen/header or a fabricated visible verdict.
                if (frame <= liveHeaderFrame || readField(title, "paintedTitle") == null) {
                    report.put("liveTerminalHeaderAwaitingExtraction", terminalHeader);
                    waitFor("current terminal header native extraction"); return;
                }
                require(header.get("fullVisible").getAsBoolean(), "Live header clipped the existing full title");
                stage = 39; stageWait = 0;
            }
            case 39, 40 -> runStage();
            default -> throw new IllegalStateException("Unknown live native stage " + stage);
        }
    }

    /** Read-only state at the terminal header assertion, including unpainted-widget waits. */
    private Map<String, Object> liveTerminalHeaderDiagnostic(
            OpenAllayScreen owner, Object title, long frame, JsonObject header) {
        var layout = (dev.openallay.guide.ui.GuideUiLayout) readField(owner, "layout");
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("stage", stage);
        receipt.put("capturedAt", Instant.now().toString());
        receipt.put("requestId", toastRequest.requestId().toString());
        receipt.put("requestStatus", toastRequest.status().name());
        receipt.put("screenIdentity", System.identityHashCode(owner));
        receipt.put("titleWidgetIdentity", System.identityHashCode(title));
        receipt.put("nativeExtractionFrame", frame);
        receipt.put("terminalWidgetFrameFence", liveHeaderFrame);
        receipt.put("titleExtracted", readField(title, "paintedTitle") != null);
        receipt.put("screenWidth", owner.width);
        receipt.put("screenHeight", owner.height);
        receipt.put("guiWidth", dev.openallay.client.gui.MinecraftClientWindow.guiWidth(client));
        receipt.put("guiHeight", dev.openallay.client.gui.MinecraftClientWindow.guiHeight(client));
        receipt.put("windowLogicalWidth", dev.openallay.client.gui.MinecraftClientWindow.windowWidth(client));
        receipt.put("windowLogicalHeight", dev.openallay.client.gui.MinecraftClientWindow.windowHeight(client));
        receipt.put("framebufferWidth", dev.openallay.client.gui.MinecraftClientWindow.framebufferWidth(client));
        receipt.put("framebufferHeight", dev.openallay.client.gui.MinecraftClientWindow.framebufferHeight(client));
        receipt.put("titleBounds", layout.header().title());
        receipt.put("header", header);
        return Map.copyOf(receipt);
    }

    /** Reopen once. Observation capture/custody/release can install the owner on later client turns. */
    private void beginReaderGuideReopen() {
        GuideChatLiteScreen reader = (GuideChatLiteScreen) lite();
        var state = (dev.openallay.client.gui.GuideClientUiState) readField(reader, "state");
        require(readField(reader, "service") == service
                && config.sessionId().equals(readField(reader, "session"))
                && readField(reader, "attachment") != null,
                "Reader reopen source is not the attached scenario service/session");
        require(client.player != null && dev.openallay.client.gui.MinecraftClientWindow.world(client) != null, "Reader reopen lost its native connection");
        liveGuideReopenOwner = new LiveGuideReopenOwner(reader, state, state.generation(),
                dev.openallay.client.gui.MinecraftClientWindow.world(client), dev.openallay.client.gui.MinecraftClientWindow.actor(client), service.presentationSessionOwner(config.sessionId()).orElseThrow());
        requireReaderGuideReopenOwner();
        require(liveGuideReopen == null, "Reader Guide reopen was already requested");
        liveGuideReopen = new NativeScreenTransition<>(reader);
        report.put("readerGuideReopenRequested", readerGuideReopenDiagnostic(reader));
        liveGuideReopen.request(() -> MinecraftClientWindow.screen(client), () -> {
            reader.onClose();
            recordAction("native-screen-close", "HUD-reader-before-Guide-reopen");
            require(readField(reader, "attachment") == null
                    && !state.visible(dev.openallay.client.gui.GuideClientUiState.Surface.HUD_INPUT, config.sessionId()),
                    "Closed HUD reader retained its native view attachment");
        }, () -> {
            openGuide.accept(service);
            recordAction("coordinator-open-guide", "reader-same-service-session-once");
        });
    }

    private void requireReaderGuideReopenOwner() {
        LiveGuideReopenOwner owner = liveGuideReopenOwner;
        require(owner != null && !owner.state().closed() && owner.state().generation() == owner.generation()
                && dev.openallay.client.gui.MinecraftClientWindow.world(client) == owner.world() && client.player != null
                && owner.actor().equals(dev.openallay.client.gui.MinecraftClientWindow.actor(client)) && owner.actor().equals(service.snapshot().actorId())
                && config.sessionId().equals(service.snapshot().selectedSession())
                && config.sessionId().equals(owner.state().selectedSession())
                && service.presentationSessionOwner(config.sessionId()).filter(owner.sessionOwner()::equals).isPresent(),
                "Reader Guide reopen lost its captured connection/state/session owner");
    }

    private boolean readerGuideReopenReady() {
        requireReaderGuideReopenOwner();
        Screen current = MinecraftClientWindow.screen(client);
        report.put("readerGuideReopenAwaiting", readerGuideReopenDiagnostic(current));
        boolean ready = liveGuideReopen.ready(current, screen -> screen instanceof OpenAllayScreen
                && readField(screen, "service") == service
                && readField(screen, "uiState") == liveGuideReopenOwner.state()
                && config.sessionId().equals(((dev.openallay.guide.ui.GuideUiView) readField(screen, "view")).selectedSession()),
                screen -> {
                    long frame = number(jsonReceipt(screen, "e2eToolsReceipt"), "lastNativeFrame");
                    if (frame == 0) return false;
                    var state = liveGuideReopenOwner.state();
                    var custody = state.observationImagesSettled();
                    require(state.observationInitialized(config.sessionId()),
                            "Reopened Guide lost its captured observation producer state");
                    if (!custody.isDone()) return false;
                    require(custody.join() instanceof ToolResult.Success<Boolean> success && Boolean.TRUE.equals(success.value()),
                            "Reopened Guide observation producer custody failed");
                    require(readField(screen, "attachment") != null && screen.width > 0 && screen.height > 0
                            && ((dev.openallay.client.gui.GuideNativeScreen) screen).guideWidgetRegistered(((GuideMultilineEditor) readField(screen, "composer")).widget())
                            && findButton(screen, "screen.openallay.settings.short", true) != null,
                            "Reopened Guide native extraction has no initialized owned composer/Settings widgets");
                    return true;
                });
        if (!ready) { waitFor("one captured reader-to-Guide owner installation and actual native extraction"); return false; }
        report.put("readerGuideReopenReady", readerGuideReopenDiagnostic(current));
        return true;
    }

    private Map<String, Object> readerGuideReopenDiagnostic(Screen screen) {
        LiveGuideReopenOwner owner = liveGuideReopenOwner;
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("sourceScreenIdentity", System.identityHashCode(owner.reader()));
        receipt.put("currentScreenIdentity", screen == null ? 0 : System.identityHashCode(screen));
        receipt.put("currentScreen", screen == null ? "gameplay" : screen.getClass().getName());
        receipt.put("uiOwnerId", owner.state().ownerId());
        receipt.put("uiGeneration", owner.generation());
        receipt.put("sessionId", config.sessionId());
        receipt.put("sessionOwner", owner.sessionOwner().toString());
        receipt.put("observationInitialized", owner.state().observationInitialized(config.sessionId()));
        receipt.put("observationCustodySettled", owner.state().observationImagesSettled().isDone());
        receipt.put("sourceAttachmentReleased", readField(owner.reader(), "attachment") == null);
        if (screen instanceof OpenAllayScreen)
            receipt.put("nativeExtractionFrame", number(jsonReceipt(screen, "e2eToolsReceipt"), "lastNativeFrame"));
        return Map.copyOf(receipt);
    }

    /** Test-only arrival latch. A callback can destroy its Screen; never search its widgets afterward. */
    static final class NativeScreenTransition<S> {
        private final S source;
        private S installed;
        private boolean requested;
        private boolean arrivalReady;
        private boolean consumed;

        NativeScreenTransition(S source) { this.source = java.util.Objects.requireNonNull(source, "source"); }

        void request(Supplier<S> current, Runnable closeSource, Runnable openTarget) {
            require(!requested && current.get() == source, "Native screen transition source was replaced or requested twice");
            requested = true;
            closeSource.run();
            require(current.get() == null, "Native screen transition source did not close to gameplay");
            openTarget.run();
        }

        boolean ready(S current, Predicate<S> expectedOwner, Predicate<S> initialized) {
            require(requested && !consumed, "Native screen transition is not awaiting one arrival");
            if (installed == null) {
                if (current == null) return false;
                require(current != source && expectedOwner.test(current), "Native screen transition installed another owner");
                installed = current;
            }
            require(current == installed && expectedOwner.test(current), "Native screen transition owner was replaced");
            arrivalReady = initialized.test(installed);
            return arrivalReady;
        }

        void consume(Supplier<S> current, Consumer<S> callback) {
            require(arrivalReady && installed != null && current.get() == installed && !consumed,
                    "Native screen transition callback target was replaced or already consumed");
            consumed = true;
            callback.accept(installed);
        }
    }

    private GuideMultilineEditor composer() {
        OpenAllayScreen owner = guide();
        GuideMultilineEditor editor = (GuideMultilineEditor) readField(owner, "composer");
        require(editor != null && owner.guideWidgetRegistered(editor.widget()),
                "Actual native composer is unavailable");
        return editor;
    }

    private dev.openallay.guide.GuideSessionSnapshot session() {
        return service.snapshot().sessions().stream().filter(value -> value.sessionId().equals(config.sessionId()))
                .findFirst().orElseThrow();
    }

    private GuideRequestSnapshot requestFor(String question) {
        return session().requests().stream().filter(value -> value.userMessage().equals(question)).findFirst().orElse(null);
    }

    private void validateLiveRequest(GuideRequestSnapshot value) {
        require(value.status() == GuideRequestStatus.COMPLETED, "Actual live UI request did not complete");
        require(value.tools().size() == 2 && value.tools().stream().allMatch(tool -> tool.toolId().equals("openallay:run_javascript")
                && tool.status() == GuideToolStatus.SUCCEEDED && tool.normalized() != null), "Actual native recipe Tools are incomplete");
        require("RECIPE".equals(value.tools().get(0).normalized().getAsJsonObject("value").get("viewKind").getAsString()),
                "First actual tool result is not a trusted native recipe");
        require(value.assistantText().contains(LIVE_TAIL) && value.assistantText().contains("本地验收阅读段 48"),
                "Actual full provider response lost latest48 marker");
        report.put("requestId", request.requestId().toString());
        report.put("assistantTextSha256", sha256(request.assistantText()));
        report.put("actualTools", request.tools().stream().map(tool -> Map.of("invocationId", tool.invocationId(),
                "toolId", tool.toolId(), "status", tool.status().name(), "title", tool.intent().title(),
                "description", tool.intent().description())).toList());
    }

    private JsonObject revealRecipeSummary(GuideRequestSnapshot value) { return revealToolSummary(value, 0); }

    private JsonObject revealToolSummary(GuideRequestSnapshot value, int index) {
        String expected = "tool:" + value.requestId() + ":" + value.tools().get(index).invocationId();
        JsonObject tools = jsonReceipt(guide(), "e2eToolsReceipt");
        JsonObject found = dev.openallay.json.JsonReaders.elements(tools.getAsJsonArray("toolSummaries")).stream().map(element -> element.getAsJsonObject())
                .filter(summary -> expected.equals(summary.get("id").getAsString())).findFirst().orElse(null);
        if (found == null) {
            var virtualizer = (dev.openallay.guide.ui.GuideTranscriptVirtualizer) readField(guide(), "virtualizer");
            var rows = virtualizer.rows();
            int rowIndex = java.util.stream.IntStream.range(0, rows.size())
                    .filter(row -> rows.get(row).id().equals(expected)).findFirst().orElseThrow();
            int currentScroll = (Integer) readField(guide(), "scroll");
            double direction = virtualizer.offset(rowIndex) < currentScroll ? 2 : -2;
            var layout = (dev.openallay.guide.ui.GuideUiLayout) readField(guide(), "layout");
            guide().guideMouseScrolled(layout.transcript().x() + layout.transcript().width() / 2.0,
                    layout.transcript().y() + layout.transcript().height() / 2.0, 0, direction);
            recordAction("native-wheel", "reveal-real-compact-tool-summary");
            waitFor("actual painted Tool summary");
        }
        return found;
    }

    private void validateCompactSummaries(JsonObject tools) {
        int spacing = settings.snapshot().display().ui().fullscreen().density() == GuideUiConfig.Density.COMPACT ? 4 : 8;
        for (var element : tools.getAsJsonArray("toolSummaries")) {
            JsonObject summary = element.getAsJsonObject();
            int cardHeight = summary.get("description").getAsString().isBlank() ? 28 : 40;
            require(number(summary.getAsJsonObject("bounds"), "height") == cardHeight
                    && number(summary, "rowHeight") == cardHeight + spacing,
                    "Actual Tool summary changed 28/40 native card height or row spacing");
            var actual = session().requests().stream().flatMap(value -> value.tools().stream()
                    .filter(tool -> ("tool:" + value.requestId() + ":" + tool.invocationId()).equals(summary.get("id").getAsString())))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Native summary has no actual service Tool identity"));
            require(summary.get("title").getAsString().equals(actual.intent().title())
                    && summary.get("description").getAsString().equals(actual.intent().description())
                    && !summary.get("status").getAsString().isBlank(), "Native summary lost actual title/description/status");
        }
        require(!tools.has("expandedToolCount") && !tools.has("toolsCollapsedDefault"), "Removed fold state remains in receipt");
    }

    private boolean detailRecipePainted(String toolId) {
        JsonObject tools = jsonReceipt(guide(), "e2eToolsReceipt");
        require(toolId.equals(tools.get("detailToolId").getAsString()), "Blank row click opened a different Tool detail");
        if ((tools.getAsJsonArray("detailNativeRecipeIds").size() == 0) || (tools.getAsJsonArray("detailCardIds").size() == 0)) {
            var layout = (dev.openallay.guide.ui.GuideUiLayout) readField(guide(), "layout");
            guide().guideMouseScrolled(layout.detail().x() + layout.detail().width() / 2.0,
                    layout.detail().y() + layout.detail().height() / 2.0, 0, -1);
            recordAction("native-wheel", "reveal-full-native-recipe-in-tool-detail");
            waitFor("actual detail registry render returned true"); return false;
        }
        require(number(tools, "lastNativeFrame") > nativeRecipeFrame, "Actual detail receipt is stale");
        nativeRecipeFrame = number(tools, "lastNativeFrame");
        require(dev.openallay.json.JsonReaders.elements(tools.getAsJsonArray("detailNativeRecipeIds")).stream().allMatch(value -> value.getAsString().startsWith(toolId + ":")),
                "Painted native recipe detail has another Tool identity");
        return true;
    }

    private void requireHudLatest(JsonObject receipt, GuideRequestSnapshot source) {
        require(number(receipt, "maximumScroll") > 0 && number(receipt, "scroll") == number(receipt, "maximumScroll"),
                "Actual native HUD did not render measured latest-tail offset");
        var finalAssistant = source.timeline().stream().filter(dev.openallay.guide.GuideTimelineEntry.Assistant.class::isInstance)
                .map(dev.openallay.guide.GuideTimelineEntry.Assistant.class::cast)
                .reduce((earlierAssistant, laterAssistant) -> laterAssistant).orElseThrow();
        String nodeId = finalAssistant.semantic().blocks().get(finalAssistant.semantic().blocks().size() - 1).nodeId();
        require(dev.openallay.json.JsonReaders.elements(receipt.getAsJsonArray("renderedRowIds")).stream()
                        .anyMatch(value -> value.getAsString().contains(source.requestId().toString())),
                "Native HUD tail has another request source identity");
        require(dev.openallay.json.JsonReaders.elements(receipt.getAsJsonArray("renderedNodeIds")).stream().anyMatch(value -> value.getAsString().equals(nodeId)),
                "Native latest tail does not include source final semantic node");
        String painted = receipt.get("lastRenderedText").getAsString().strip();
        String expectedTail = "ui-live-ux-regressions".equals(config.scenario()) ? LIVE_TAIL : "全文末尾：原生图形长回复验收完成。";
        require(!painted.isEmpty() && expectedTail.endsWith(painted), "Native latest text tail was not actually painted");
    }

    /** Actual draft/button projection after native ticks, never an active override or direct submit. */
    private Map<String, Object> readerSendDiagnostic() {
        Screen screen = lite();
        var state = (dev.openallay.client.gui.GuideClientUiState) readField(screen, "state");
        String sessionId = (String) readField(screen, "session");
        var intent = state.intent(sessionId);
        GuideNativeButton actualSend = (GuideNativeButton) readField(screen, "send");
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("sessionId", sessionId);
        diagnostic.put("expectedFixtureDraft", LIVE_FOLLOW_UP + " reader-new-content");
        diagnostic.put("draftMatches", (LIVE_FOLLOW_UP + " reader-new-content").equals(composer().getValue()));
        diagnostic.put("sendVisible", actualSend.visible);
        diagnostic.put("sendActive", actualSend.active);
        diagnostic.put("sendLabel", MinecraftComponents.getString(actualSend.getMessage()));
        diagnostic.put("submitting", readField(screen, "submitting"));
        diagnostic.put("intentSubmissionInFlight", state.intentSubmissionInFlight(sessionId));
        diagnostic.put("intentSteer", intent.steer());
        diagnostic.put("intentEditing", intent.editing());
        diagnostic.put("intentInvalid", intent.editInvalid());
        diagnostic.put("nativeResultFrame", number(jsonReceipt(screen, "resultReceipt"), "extractedFrame"));
        return Map.copyOf(diagnostic);
    }

    private Screen lite() {
        require(MinecraftClientWindow.screen(client) instanceof GuideChatLiteScreen, "Native F8 callback did not open full HUD reader");
        return MinecraftClientWindow.screen(client);
    }

    private void wheelHud(Screen screen, double scrollY) {
        var card = GuideChatLiteScreen.Card.calculate(screen.width, screen.height);
        var results = GuideHudReadingLayout.calculate(card.x(), card.y(), card.width(), card.height()).results();
        double x = results.x() + results.width() / 2.0;
        double y = results.y() + results.height() / 2.0;
        require(screen instanceof GuideChatLiteScreen, "Native wheel target is not the owned HUD reader");
        require(((GuideChatLiteScreen) screen).guideMouseScrolled(x, y, 0, scrollY),
                "Actual HUD result viewport did not consume native wheel");
        actions.add(Map.of("type", "native-wheel", "stage", stage, "x", x, "y", y, "scrollY", scrollY));
    }

    private void clickAt(Screen screen, double x, double y, String target) {
        var event = GuideNativeInput.mouseEvent(x, y, dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT, 0);
        boolean clicked = dev.openallay.client.gui.GuideWidgetInputs.mouseClicked((dev.openallay.client.gui.GuideWidgetInput) screen, event, false);
        boolean released = dev.openallay.client.gui.GuideWidgetInputs.mouseReleased((dev.openallay.client.gui.GuideWidgetInput) screen, event);
        actions.add(Map.of("type", "native-mouse-callback", "target", target, "stage", stage,
                "x", x, "y", y, "clickedHandled", clicked, "releasedHandled", released));
    }

    private void hoverNative(double guiX, double guiY) {
        double nativeX = guiX * dev.openallay.client.gui.MinecraftClientWindow.windowWidth(client) / dev.openallay.client.gui.MinecraftClientWindow.guiWidth(client);
        double nativeY = guiY * dev.openallay.client.gui.MinecraftClientWindow.windowHeight(client) / dev.openallay.client.gui.MinecraftClientWindow.guiHeight(client);
        GuideProbeNativeCursor.move(client, nativeX, nativeY);
        actions.add(Map.of("type", "native-" + GuideProbeNativeCursor.backend() + "-cursor", "stage", stage, "guiX", guiX, "guiY", guiY,
                "windowX", nativeX, "windowY", nativeY, "screenWidth", dev.openallay.client.gui.MinecraftClientWindow.windowWidth(client),
                "screenHeight", dev.openallay.client.gui.MinecraftClientWindow.windowHeight(client), "source", "OS-programmatic-cursor-request"));
        if (Math.abs(dev.openallay.client.context.MinecraftMouseCoordinates.x(client) - guiX) > 1
                || Math.abs(dev.openallay.client.context.MinecraftMouseCoordinates.y(client) - guiY) > 1)
            dispatchNativeCursorMove(nativeX, nativeY);
    }

    /** Controlled native event callback, not physical OS input, direct render, or private field mutation. */
    private void dispatchNativeCursorMove(double nativeX, double nativeY) {
        require(developmentProbeEnabled, "Development probe was disabled at construction");
        dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> {
            try {
                GuideProbeNativeCursor.dispatchMove(client, nativeX, nativeY);
                actions.add(Map.of("type", "controlled-native-cursor-callback", "stage", stage,
                        "source", "selected-native-cursor-callback/" + GuideProbeNativeCursor.backend(), "physicalInput", false,
                        "windowX", nativeX, "windowY", nativeY,
                        "scaledXAfterCallback", dev.openallay.client.context.MinecraftMouseCoordinates.x(client),
                        "scaledYAfterCallback", dev.openallay.client.context.MinecraftMouseCoordinates.y(client)));
            } catch (RuntimeException failure) {
                fail(new IllegalStateException("Controlled native mouse callback failed", failure));
            }
        });
    }

    /** Bounded native input observations only; these never manufacture a hover or paint receipt. */
    private void recordNativeHoverDiagnostic(String phase) {
        require(developmentProbeEnabled, "Development probe was disabled at construction");
        double[] nativeCursor = GuideProbeNativeCursor.position(client);
        var layout = (dev.openallay.guide.ui.GuideUiLayout) readField(guide(), "layout");
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("phase", phase);
        diagnostic.put("stage", stage);
        diagnostic.put("attempt", liveHoverAttempts);
        diagnostic.put("screenIdentity", System.identityHashCode(guide()));
        diagnostic.put("screenClass", guide().getClass().getName());
        diagnostic.put("screenWidth", guide().width);
        diagnostic.put("screenHeight", guide().height);
        diagnostic.put("windowFocused", dev.openallay.client.gui.MinecraftClientWindow.focused(client));
        diagnostic.put("clientWindowActive", dev.openallay.client.gui.MinecraftClientWindow.focused(client));
        diagnostic.put("windowLogicalWidth", dev.openallay.client.gui.MinecraftClientWindow.windowWidth(client));
        diagnostic.put("windowLogicalHeight", dev.openallay.client.gui.MinecraftClientWindow.windowHeight(client));
        diagnostic.put("framebufferWidth", dev.openallay.client.gui.MinecraftClientWindow.framebufferWidth(client));
        diagnostic.put("framebufferHeight", dev.openallay.client.gui.MinecraftClientWindow.framebufferHeight(client));
        diagnostic.put("guiWidth", dev.openallay.client.gui.MinecraftClientWindow.guiWidth(client));
        diagnostic.put("guiHeight", dev.openallay.client.gui.MinecraftClientWindow.guiHeight(client));
        diagnostic.put("nativeCursorBackend", GuideProbeNativeCursor.backend());
        diagnostic.put("nativeCursorX", nativeCursor[0]);
        diagnostic.put("nativeCursorY", nativeCursor[1]);
        diagnostic.put("mouseHandlerX", GuideProbeNativeCursor.position(client)[0]);
        diagnostic.put("mouseHandlerY", GuideProbeNativeCursor.position(client)[1]);
        diagnostic.put("mouseHandlerScaledX", dev.openallay.client.context.MinecraftMouseCoordinates.x(client));
        diagnostic.put("mouseHandlerScaledY", dev.openallay.client.context.MinecraftMouseCoordinates.y(client));
        diagnostic.put("mouseGrabbed", dev.openallay.client.context.MinecraftMouseCoordinates.grabbed(client));
        diagnostic.put("telemetryBounds", layout.telemetry());
        diagnostic.put("modelSelectorOpen", readField(guide(), "modelSelectorOpen"));
        diagnostic.put("sessionOverlay", readField(guide(), "sessionOverlay"));
        diagnostic.put("overflowOpen", readField(guide(), "overflowOpen"));
        diagnostic.put("toolsNativeFrame", number(jsonReceipt(guide(), "e2eToolsReceipt"), "lastNativeFrame"));
        diagnostic.put("tooltipReceipt", jsonReceipt(guide(), "e2eTelemetryTooltipReceipt"));
        if (liveHoverDiagnostics.size() < 12) liveHoverDiagnostics.add(Map.copyOf(diagnostic));
        else liveHoverDiagnostics.set(11, Map.copyOf(diagnostic));
        report.put("nativeHoverDiagnostics", liveHoverDiagnostics);
    }

    /** Guarded read-only geometry. No reflective writes, callbacks, or service data insertion. */
    private Object readField(Object owner, String name) {
        require(developmentProbeEnabled, "Development probe was disabled at construction");
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native geometry field unavailable: " + name, failure); }
    }

    private JsonObject jsonReceipt(Object owner, String method) {
        return gson.toJsonTree(readReceipt(owner, method)).getAsJsonObject();
    }

    private JsonObject toastJson() {
        require(toastReceipt != null, "Actual native toast owner receipt is unattached");
        var value = gson.toJsonTree(toastReceipt.get());
        return value.isJsonNull() ? null : value.getAsJsonObject();
    }

    private String readCurrentConfig(String name) {
        try { return Files.readString(dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay").resolve(name), StandardCharsets.UTF_8); }
        catch (IOException failure) { throw new IllegalStateException("Current native config is unavailable: " + name, failure); }
    }

    private CompletableFuture<Integer> releaseLiveTransport() {
        var profile = settings.snapshot().models().config().profiles().stream().filter(value -> value.enabled()).findFirst().orElseThrow();
        require("127.0.0.1".equals(profile.baseUri().getHost()) && "http".equals(profile.baseUri().getScheme()), "Fixture release must stay loopback");
        var uri = profile.baseUri().resolve("/__e2e/live-ux/release");
        return java.net.http.HttpClient.newHttpClient().sendAsync(java.net.http.HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10)).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).thenApply(java.net.http.HttpResponse::statusCode);
    }

    private GuideHudEditorScreen hudEditor() {
        require(MinecraftClientWindow.screen(client) instanceof GuideHudEditorScreen, "Actual native HUD layout editor is not open");
        return (GuideHudEditorScreen) MinecraftClientWindow.screen(client);
    }

    private GuideNativeEditBox hudOffsetEditor(String key) {
        String label = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.ui." + key));
        return ((dev.openallay.client.gui.GuideNativeScreen) settingsScreen()).guideWidgetChildren().stream().filter(GuideNativeEditBox.class::isInstance).map(GuideNativeEditBox.class::cast)
                .filter(value -> MinecraftComponents.getString(value.getMessage()).equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Actual HUD offset editor unavailable: " + key));
    }

    private void requireHudOffsetFields(GuideUiConfig.Hud hud) {
        require(Integer.toString(hud.offsetX()).equals(hudOffsetEditor("offset_x").getValue())
                        && Integer.toString(hud.offsetY()).equals(hudOffsetEditor("offset_y").getValue()),
                "Actual settings offset widgets do not retain the acknowledged HUD candidate");
    }

    private boolean revealHudEditorButton() {
        if (findButton("screen.openallay.settings.ui.edit_hud", false) != null) return true;
        OpenAllaySettingsScreen screen = settingsScreen();
        var area = SettingsLayout.calculate(screen.width, screen.height, SettingsSection.UI).editor();
        double x = area.x() + area.width() / 2.0;
        double y = area.y() + area.height() / 2.0;
        require(screen.guideMouseScrolled(x, y, 0, -2), "Actual UI HUD page did not consume native scrolling");
        recordAction("native-wheel", "reveal-visible-Edit-HUD-button");
        waitFor("visible enabled native Edit HUD button");
        return false;
    }

    private void clickHudCaseButton(String key) {
        GuideNativeButton button = findButton(key, false);
        require(button != null, "Actual visible enabled HUD case button unavailable: " + key);
        Screen screen = MinecraftClientWindow.screen(client);
        double x = dev.openallay.client.gui.GuideNativeWidgetGeometry.x(button) + button.getWidth() / 2.0;
        double y = dev.openallay.client.gui.GuideNativeWidgetGeometry.y(button) + button.getHeight() / 2.0;
        require(dev.openallay.client.gui.GuideNativeWidgetGeometry.x(button) >= 0 && dev.openallay.client.gui.GuideNativeWidgetGeometry.y(button) >= 0
                        && dev.openallay.client.gui.GuideNativeWidgetGeometry.x(button) + button.getWidth() <= screen.width
                        && dev.openallay.client.gui.GuideNativeWidgetGeometry.y(button) + button.getHeight() <= screen.height,
                "Actual HUD case button is outside the native viewport");
        var event = GuideNativeInput.mouseEvent(x, y, dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT, 0);
        boolean clicked = dev.openallay.client.gui.GuideWidgetInputs.mouseClicked((dev.openallay.client.gui.GuideWidgetInput) screen, event, false);
        boolean released = dev.openallay.client.gui.GuideWidgetInputs.mouseReleased((dev.openallay.client.gui.GuideWidgetInput) screen, event);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "native-mouse-button");
        action.put("target", key);
        action.put("stage", stage);
        action.put("at", Instant.now().toString());
        action.put("screen", screen.getClass().getName());
        action.put("eventType", GuideNativeInput.mouseEventType());
        action.put("x", x);
        action.put("y", y);
        action.put("button", dev.openallay.client.gui.GuideInputCodes.MOUSE_BUTTON_LEFT);
        action.put("modifiers", 0);
        action.put("doubleClick", false);
        action.put("mouseClickedHandled", clicked);
        action.put("mouseReleasedHandled", released);
        actions.add(action);
        require(clicked, "Native Screen did not route the actual HUD case button click: " + key);
    }

    private void recordHudPointer(String callback, GuideInputMouse event, double dx, double dy, boolean handled) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "native-hud-pointer");
        action.put("target", "HUD-layout-drag");
        action.put("callback", callback);
        action.put("stage", stage);
        action.put("at", Instant.now().toString());
        action.put("eventType", GuideNativeInput.mouseEventType());
        action.put("x", event.x());
        action.put("y", event.y());
        action.put("button", event.button());
        action.put("modifiers", 0);
        action.put("deltaX", dx);
        action.put("deltaY", dy);
        action.put("doubleClick", false);
        action.put("handled", handled);
        action.put("screen", hudEditor().getClass().getName());
        hudPointerEvents.add(action);
        actions.add(action);
    }

    private boolean hudCandidateReturned() {
        settingsScreen();
        JsonObject state = gson.toJsonTree(readReceipt(settingsScreen(), "e2eSettingsState")).getAsJsonObject();
        if (state.get("saving").getAsBoolean() || settings.snapshot().operation().kind() != SettingsOperation.Kind.IDLE) {
            waitFor("real HUD editor Apply save acknowledgement");
            return false;
        }
        require(!state.get("uiDirty").getAsBoolean(), "HUD Apply returned without committing its UI candidate");
        return true;
    }

    private Map<String, Object> readHudDisplay(GuideDisplayConfig expected) {
        Path current = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay/display.json").toAbsolutePath().normalize();
        require(Files.isRegularFile(current), "Current display settings file is unavailable after HUD Apply");
        try {
            byte[] bytes = Files.readAllBytes(current);
            var loaded = new GuideDisplayConfigLoader().load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            require(loaded.failure() == null, "Actual HUD display file does not satisfy current exact shape validation");
            require(expected.equals(loaded.config()), "Actual display file lost the dragged HUD or changed another field");
            return Map.of("path", current.toString(), "bytes", bytes.length, "sha256", bytesSha256(bytes),
                    "actualDisplay", loaded.config(), "completeCandidateMatches", true,
                    "source", "current display.json bytes read after actual save acknowledgement");
        } catch (IOException failure) { throw new IllegalStateException("Actual HUD display readback failed", failure); }
    }

    private static double boundedHudDelta(double origin, double size, int viewport, double requested) {
        double positive = viewport - GuideHudLayout.MARGIN - size - origin;
        double negative = origin - GuideHudLayout.MARGIN;
        double delta = positive >= negative ? Math.min(requested, positive) : -Math.min(requested, negative);
        require(Math.abs(delta) >= 8, "Native HUD has no bounded room for an observable drag");
        return delta;
    }

    private static void requireHudInsideWindow(GuideHudLayout.Rect bounds, int width, int height) {
        require(bounds.width() > 0 && bounds.height() > 0
                        && bounds.x() >= GuideHudLayout.MARGIN && bounds.y() >= GuideHudLayout.MARGIN
                        && bounds.right() <= width - GuideHudLayout.MARGIN
                        && bounds.bottom() <= height - GuideHudLayout.MARGIN,
                "HUD drag expectation is not anchored within the native window boundaries");
    }

    private static int expectedHudBodyHeight(GuideUiConfig.Hud hud, GuideHudLayout.Rect bounds) {
        int available = Math.max(0, bounds.contentHeight() - 52);
        return hud.maxReplyLines() == 0 ? available : Math.min(available, hud.maxReplyLines() * 11);
    }

    private boolean nativeRecipePainted() {
        String expected = "tool:" + request.requestId() + ":" + request.tools().get(0).invocationId();
        JsonObject tools = jsonReceipt(guide(), "e2eToolsReceipt");
        if (!expected.equals(tools.get("detailToolId").getAsString())) {
            JsonObject summary = revealRecipeSummary(request);
            if (summary == null) return false;
            clickAt(guide(), summary.get("blankClickX").getAsDouble(), summary.get("blankClickY").getAsDouble(),
                    "manual-real-tool-blank-padding-open-detail");
            waitFor("actual right Tool detail extraction");
            return false;
        }
        if (!detailRecipePainted(expected)) return false;
        report.put("fullscreenNativeRecipePaint", Map.of("expectedToolId", expected,
                "actuallyPaintedStableIds", tools.getAsJsonArray("detailNativeRecipeIds"),
                "detailCardIds", tools.getAsJsonArray("detailCardIds"), "nativeExtractedFrame", nativeRecipeFrame,
                "receiptSource", "actual right-detail native registry render returned painted=true"));
        return true;
    }

    private GuideNativeEditBox nameEditor() {
        String label = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.general.assistant_name.label"));
        return ((dev.openallay.client.gui.GuideNativeScreen) settingsScreen()).guideWidgetChildren().stream().filter(GuideNativeEditBox.class::isInstance).map(GuideNativeEditBox.class::cast)
                .filter(value -> MinecraftComponents.getString(value.getMessage()).equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Actual assistant-name editor unavailable"));
    }

    private boolean writeFailureRetained() {
        settingsScreen();
        if (settings.snapshot().operation().kind() != SettingsOperation.Kind.IDLE) {
            waitFor("actual settings write failure receipt");
            return false;
        }
        require("小羽 · 写入失败草稿".equals(nameEditor().getValue()), "Write failure lost the actual edited field");
        require(validNameBeforeFailure.equals(settings.snapshot().display().assistantName()), "Write failure changed the last valid published display");
        require(settings.snapshot().notice() != null
                && settings.snapshot().notice().level() == dev.openallay.settings.SettingsNotice.Level.FAILURE,
                "Actual settings writer did not report a failure");
        report.put("writeFailure", Map.of("e2eScope", true, "stayedOnSettings", true,
                "draftRetained", true, "lastValidUnchanged", true,
                "noticeCode", settings.snapshot().notice().code()));
        return true;
    }

    private boolean selectReplySlider(int selected) {
        String label = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.ui.reply_lines"));
        GuideNativeSlider slider = ((dev.openallay.client.gui.GuideNativeScreen) settingsScreen()).guideWidgetChildren().stream()
                .filter(GuideNativeSlider.class::isInstance).map(GuideNativeSlider.class::cast)
                .filter(value -> MinecraftComponents.getString(value.getMessage()).startsWith(label + " · ")).findFirst().orElseThrow();
        if (!slider.visible) {
            settingsScreen().guideMouseScrolled(settingsScreen().width / 2.0, settingsScreen().height / 2.0, 0, -2);
            recordAction("native-wheel", "reveal-reply-slider");
            waitFor("actual reply-lines slider visibility");
            return false;
        }
        require(slider.active, "Actual reply-lines slider is not enabled");
        double x = dev.openallay.client.gui.GuideNativeWidgetGeometry.x(slider) + 4 + selected / 80.0 * (slider.getWidth() - 8);
        var mouse = GuideNativeInput.mouseEvent(x, dev.openallay.client.gui.GuideNativeWidgetGeometry.y(slider) + slider.getHeight() / 2.0, 0, 0);
        GuideNativeInput.click(slider, mouse, false);
        GuideNativeInput.release(slider, mouse);
        recordAction("native-slider-click", "reply-lines=" + selected);
        return true;
    }

    private void installDisplayFault() {
        displayPath = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay/display.json").toAbsolutePath().normalize();
        require(Files.isRegularFile(displayPath), "Disposable display settings file is unavailable");
        try {
            displayBefore = Files.readAllBytes(displayPath);
            var beforeAttributes = Files.readAttributes(displayPath, java.nio.file.attribute.BasicFileAttributes.class);
            synchronized (report) {
                report.put("displayFileMetadataBefore", Map.of("size", beforeAttributes.size(),
                        "lastModified", beforeAttributes.lastModifiedTime().toString(),
                        "creationTime", beforeAttributes.creationTime().toString()));
            }
            displayBackup = displayPath.resolveSibling("display.e2e-preserved-" + UUID.randomUUID());
            Files.move(displayPath, displayBackup);
            Files.createDirectory(displayPath);
            Files.writeString(displayPath.resolve("e2e-nonempty-directory"), "controlled test-only replacement conflict", StandardCharsets.UTF_8);
            synchronized (report) {
                report.put("displayFileFault", Map.of("e2eScope", true,
                        "oldSha256", bytesSha256(displayBefore), "originalBytes", displayBefore.length,
                        "preservedAttributes", "original file moved without rewriting"));
            }
        } catch (IOException failure) { throw new IllegalStateException("Disposable write-failure preparation failed", failure); }
    }

    private void restoreDisplayFile() {
        if (displayBackup == null || !Files.exists(displayBackup)) return;
        try {
            Files.deleteIfExists(displayPath.resolve("e2e-nonempty-directory"));
            Files.deleteIfExists(displayPath);
            Files.move(displayBackup, displayPath);
            String restored = bytesSha256(Files.readAllBytes(displayPath));
            var restoredAttributes = Files.readAttributes(displayPath, java.nio.file.attribute.BasicFileAttributes.class);
            synchronized (report) {
                report.put("displayFileMetadataRestored", Map.of("size", restoredAttributes.size(),
                        "lastModified", restoredAttributes.lastModifiedTime().toString(),
                        "creationTime", restoredAttributes.creationTime().toString()));
            }
            require(restored.equals(bytesSha256(displayBefore)), "Disposable display file restoration changed bytes");
            synchronized (report) {
                report.put("displayFileRestoration", Map.of("e2eScope", true,
                        "restoredSha256", restored, "byteExact", true, "originalFileMetadataPreserved", true));
            }
        } catch (IOException failure) { throw new IllegalStateException("Disposable display file restoration failed", failure); }
    }

    private static String bytesSha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void requireLoopbackFixture() {
        var profiles = settings.snapshot().models().config().profiles().stream().filter(value -> value.enabled()).toList();
        require(!profiles.isEmpty(), "An actual local fixture profile is required");
        require(profiles.stream().allMatch(value -> "http".equals(value.baseUri().getScheme())
                && "127.0.0.1".equals(value.baseUri().getHost()) && "openallay-e2e-fixture".equals(value.model())),
                "Graphical scenario must not contact a paid or external provider");
    }

    private void validateGuideReceipts() {
        JsonObject header = gson.toJsonTree(readReceipt(guide(), "e2eHeaderReceipt")).getAsJsonObject();
        require(header.get("fullVisible").getAsBoolean(), "Native header extraction clipped the assistant name");
        JsonObject telemetry = gson.toJsonTree(readReceipt(guide(), "e2eTelemetryReceipt")).getAsJsonObject();
        if (telemetry.get("card").getAsBoolean()) require(number(telemetry, "rowCount") == 3, "Native fee card is not three lines");
        JsonObject tools = gson.toJsonTree(readReceipt(guide(), "e2eToolsReceipt")).getAsJsonObject();
        require(!tools.has("expandedToolCount") && !tools.has("toolsCollapsedDefault"), "Removed fold state remains in native receipt");
        validateCompactSummaries(tools);
    }

    private boolean doneReturned() {
        if (MinecraftClientWindow.screen(client) instanceof OpenAllaySettingsScreen) { waitFor("Done save acknowledgement and return"); return false; }
        guide();
        require(settings.snapshot().operation().kind() == SettingsOperation.Kind.IDLE, "Done returned before save acknowledgement");
        return true;
    }

    private void navigate(String key) {
        OpenAllaySettingsScreen screen = settingsScreen();
        String name = key.substring(key.lastIndexOf('.') + 1).toUpperCase(java.util.Locale.ROOT);
        try {
            screen.getClass().getMethod("e2eChooseSection", dev.openallay.client.gui.settings.SettingsSection.class)
                    .invoke(screen, dev.openallay.client.gui.settings.SettingsSection.valueOf(name));
            recordAction("native-section-button", key);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Actual settings section callback failed", failure);
        }
    }

    private GuideNativeButton findButton(String key, boolean prefix) {
        return findButton(MinecraftClientWindow.screen(client), key, prefix);
    }

    private GuideNativeButton findButton(Screen screen, String key, boolean prefix) {
        require(screen != null, "Actual native button owner is unavailable: " + key);
        String text = MinecraftComponents.getString(MinecraftComponents.translatable(key));
        return ((dev.openallay.client.gui.GuideNativeScreen) screen).guideWidgetChildren().stream().filter(GuideNativeButton.class::isInstance).map(GuideNativeButton.class::cast)
                .filter(value -> value.visible && value.active)
                .filter(value -> MinecraftComponents.getString(value.getMessage()).equals(text)
                        || prefix && MinecraftComponents.getString(value.getMessage()).startsWith(text + " · "))
                .findFirst().orElse(null);
    }

    private void press(String key) {
        GuideNativeButton button = findButton(key, true);
        require(button != null, "Actual visible enabled native button unavailable: " + key);
        GuideNativeInput.press(button, GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_RETURN, 0));
        recordAction("native-button", key);
    }

    private OpenAllayScreen guide() {
        require(MinecraftClientWindow.screen(client) instanceof OpenAllayScreen, "Actual fullscreen guide is not open");
        return (OpenAllayScreen) MinecraftClientWindow.screen(client);
    }

    private OpenAllaySettingsScreen settingsScreen() {
        require(MinecraftClientWindow.screen(client) instanceof OpenAllaySettingsScreen, "Actual coordinator settings screen is not open");
        return (OpenAllaySettingsScreen) MinecraftClientWindow.screen(client);
    }

    private void checkpoint(String name, boolean includeGuide) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("name", name);
        receipt.put("stage", stage);
        receipt.put("capturedAt", Instant.now().toString());
        receipt.put("nativeScreen", MinecraftClientWindow.screen(client) == null ? "gameplay" : MinecraftClientWindow.screen(client).getClass().getName());
        receipt.put("theme", theme());
        receipt.put("guiWidth", dev.openallay.client.gui.MinecraftClientWindow.guiWidth(client));
        receipt.put("guiHeight", dev.openallay.client.gui.MinecraftClientWindow.guiHeight(client));
        receipt.put("guiScale", dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client)));
        receipt.put("settingsGeneration", settings.snapshot().generation());
        receipt.put("settingsOperation", settings.snapshot().operation().kind().name());
        var memory = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        receipt.put("heapUsedBytes", memory.getUsed());
        receipt.put("heapCommittedBytes", memory.getCommitted());
        receipt.put("elapsedMillis", Duration.between(started, Instant.now()).toMillis());
        receipt.put("hud", hudReceipt.get());
        if (includeGuide) {
            receipt.put("header", readReceipt(guide(), "e2eHeaderReceipt"));
            receipt.put("telemetry", readReceipt(guide(), "e2eTelemetryReceipt"));
            receipt.put("tools", readReceipt(guide(), "e2eToolsReceipt"));
        } else if (MinecraftClientWindow.screen(client) instanceof OpenAllaySettingsScreen) {
            receipt.put("settingsState", readReceipt(MinecraftClientWindow.screen(client), "e2eSettingsState"));
        } else if (MinecraftClientWindow.screen(client) != null && MinecraftClientWindow.screen(client).getClass().getSimpleName().equals("GuideChatLiteScreen")) {
            receipt.put("interactiveHud", readReceipt(MinecraftClientWindow.screen(client), "resultReceipt"));
        }
        checkpoints.add(receipt);
        captureFrame(name);
    }

    private void captureFrame(String name) {
        CompletableFuture<Map<String, Object>> saved = new CompletableFuture<>();
        frames.put(name, saved);
        Path path = frameRoot.resolve(name + ".png");
        try {
            Files.createDirectories(frameRoot);
            dev.openallay.client.observation.MinecraftNativeImageCapture.capture(
                    client).whenComplete((image, captureFailure) -> {
                if (captureFailure != null) {
                    saved.completeExceptionally(captureFailure);
                    return;
                }
                CompletableFuture.runAsync(() -> {
                    try (image) {
                        int width = image.width();
                        int height = image.height();
                        dev.openallay.client.observation.MinecraftNativeImageCapture.write(image, path);
                        byte[] bytes = Files.readAllBytes(path);
                        saved.complete(Map.of("name", name, "path", path.toString(), "bytes", bytes.length,
                                "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                                "width", width, "height", height, "source", "native-mainRenderTarget"));
                    } catch (Exception failure) {
                        saved.completeExceptionally(new IllegalStateException("Native frame write failed", failure));
                    }
                });
            });
        } catch (IOException | RuntimeException failure) {
            saved.completeExceptionally(failure);
            throw new IllegalStateException("Native frame output is unavailable", failure);
        }
    }

    private void retainFailureFrameReceipts() {
        List<Map<String, Object>> published = new ArrayList<>();
        List<Map<String, Object>> unavailable = new ArrayList<>();
        frames.forEach((name, saved) -> {
            if (!saved.isDone()) {
                unavailable.add(Map.of("name", name, "path", frameRoot.resolve(name + ".png").toString(),
                        "status", "PENDING", "source", "native-mainRenderTarget"));
            } else {
                try { published.add(saved.join()); }
                catch (RuntimeException failure) {
                    unavailable.add(Map.of("name", name, "path", frameRoot.resolve(name + ".png").toString(),
                            "status", "FAILED", "source", "native-mainRenderTarget", "failure", failure.toString()));
                }
            }
        });
        report.put("nativeFrames", published);
        if (!unavailable.isEmpty()) report.put("unpublishedNativeFrames", unavailable);
    }

    private static Object readReceipt(Object owner, String method) { return invoke(owner, method); }

    private static Object invoke(Object owner, String method) {
        try { return owner.getClass().getMethod(method).invoke(owner); }
        catch (NoSuchMethodException | IllegalAccessException failure) { throw new IllegalStateException("Actual public GUI probe is unavailable: " + method, failure); }
        catch (InvocationTargetException failure) { throw new IllegalStateException("Actual GUI callback failed: " + method, failure.getCause()); }
    }

    private void recordAction(String type, String target) {
        actions.add(Map.of("type", type, "target", target, "stage", stage, "at", Instant.now().toString()));
    }

    private void restoreKey() {
        if (nativePrimitiveProbe != null) nativePrimitiveProbe.close();
        dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client), originalGuiScale);
        MinecraftClientWindow.setWindowed(client, originalWindowWidth, originalWindowHeight);
        report.put("windowRestorationRequested", Map.of("width", originalWindowWidth, "height", originalWindowHeight,
                "guiScale", dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client)), "originalGuiScaleRestored", dev.openallay.platform.minecraft.MinecraftOptions.guiScale(dev.openallay.client.context.MinecraftClientContextFacts.options(client)) == originalGuiScale));
        restorePttKey.run();
        report.put("pttKeyRestored", originalPttBinding.equals(GuideProbeKeyBindings.description(OpenAllayKeyMappings.VOICE_PTT)));
        restoreInteractKey.run();
        GuideProbeKeyBindings.refresh();
        report.put("interactKeyAfter", GuideProbeKeyBindings.description(OpenAllayKeyMappings.INTERACT_HUD));
        report.put("interactKeyRestored", originalInteractBinding.equals(GuideProbeKeyBindings.description(OpenAllayKeyMappings.INTERACT_HUD)));
    }

    /** Retain nested native causes within fixed report-size bounds. */
    private static List<Map<String, Object>> nativeFailureCauseChain(Throwable failure) {
        List<Map<String, Object>> causes = new ArrayList<>();
        var seen = new java.util.IdentityHashMap<Throwable, Boolean>();
        Throwable cause = failure;
        while (cause != null && causes.size() < 16 && seen.put(cause, Boolean.TRUE) == null) {
            Map<String, Object> entry = new LinkedHashMap<>();
            String description = cause.toString();
            entry.put("exception", description.length() <= 4096 ? description : description.substring(0, 4096));
            entry.put("exceptionTruncated", description.length() > 4096);
            var stack = cause.getStackTrace();
            entry.put("stack", java.util.Arrays.stream(stack).limit(64).map(Object::toString).toList());
            entry.put("stackTruncated", stack.length > 64);
            Throwable next = cause.getCause();
            entry.put("causeChainTruncated", next != null && (causes.size() == 15 || seen.containsKey(next)));
            causes.add(Map.copyOf(entry));
            cause = next;
        }
        return List.copyOf(causes);
    }

    private void fail(RuntimeException failure) {
        done = true;
        report.put("outcome", "HARNESS_FAILED");
        report.put("failureCode", "native_graphical_stage_failed");
        report.put("failureStage", stage);
        report.put("failureMessage", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        report.put("failureException", failure.toString());
        report.put("failureStack", java.util.Arrays.stream(failure.getStackTrace()).map(Object::toString).toList());
        if (failure.getCause() != null) report.put("failureCause", failure.getCause().toString());
        report.put("failureCauseChain", nativeFailureCauseChain(failure));
        report.put("elapsedMillis", Duration.between(started, Instant.now()).toMillis());
        if ("ui-live-ux-regressions".equals(config.scenario()) && (stage == 27 || stage == 127)) {
            try { report.put("readerSendAtFailure", readerSendDiagnostic()); }
            catch (RuntimeException diagnosticFailure) { report.put("readerSendDiagnosticFailure", diagnosticFailure.toString()); }
        }
        if ("ui-live-ux-regressions".equals(config.scenario()) && (stage == 9 || stage == 10)) {
            try { recordNativeHoverDiagnostic("failure-before-native-frame-capture"); }
            catch (RuntimeException diagnosticFailure) { report.put("nativeHoverDiagnosticFailure", diagnosticFailure.toString()); }
        }
        try { captureFrame(String.format("failure-stage-%02d", stage)); }
        catch (RuntimeException captureFailure) { report.put("failureFrameCaptureFailure", captureFailure.toString()); }
        restoreKey();
        CompletableFuture<Void> pending = displayFault == null ? CompletableFuture.completedFuture(null) : displayFault;
        CompletableFuture<Void> cleanup = pending.handle((ignored, cleanupFailure) -> null)
                .thenRunAsync(this::restoreDisplayFile);
        // Do not block a native render tick waiting for GPU readback or disk IO. If the GPU cannot
        // finish after a failure, keep the actual finished receipts and identify unfinished frames.
        CompletableFuture<Void> framePublication = CompletableFuture.allOf(
                frames.values().toArray(CompletableFuture[]::new)).handle((ignored, frameFailure) -> (Void) null)
                .completeOnTimeout(null, 15, java.util.concurrent.TimeUnit.SECONDS);
        cleanup.handle((ignored, cleanupFailure) -> cleanupFailure)
                .thenCombine(framePublication, (cleanupFailure, ignored) -> cleanupFailure)
                .whenComplete((cleanupFailure, publicationFailure) -> dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> {
                    if (cleanupFailure != null) report.put("cleanupFailure", cleanupFailure.toString());
                    if (publicationFailure != null) report.put("failurePublicationFailure", publicationFailure.toString());
                    retainFailureFrameReceipts();
                    completed.accept(report);
                }));
    }

    private String theme() { return settings.snapshot().display().ui().fullscreen().theme().name(); }
    private void advance() { stage++; stageWait = 0; }
    private void waitFor(String condition) {
        if (++stageWait > 100) throw new IllegalStateException("Timed out waiting for " + condition);
    }
    private static long number(JsonObject value, String key) { return value.get(key).getAsLong(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
