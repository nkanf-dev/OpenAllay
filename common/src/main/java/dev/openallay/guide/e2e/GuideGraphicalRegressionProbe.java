package dev.openallay.guide.e2e;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
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
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Opt-in native GUI scenario. Does not manufacture model results or bypass Done/Export callbacks. */
final class GuideGraphicalRegressionProbe {
    private final GuideClientE2EConfig config;
    private final GuideService service;
    private final ClientSettingsService settings;
    private final Gson gson;
    private final Consumer<GuideService> openGuide;
    private final Supplier<Object> hudReceipt;
    private final Supplier<VoiceSettingsActions> voiceSettings;
    private final BiFunction<String, UUID, java.util.Optional<String>> traceLookup;
    private final Consumer<Map<String, Object>> completed;
    private final Minecraft client = Minecraft.getInstance();
    private final Instant started = Instant.now();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> checkpoints = new ArrayList<>();
    private final List<Map<String, Object>> actions = new ArrayList<>();
    private final Map<String, CompletableFuture<Map<String, Object>>> frames = new LinkedHashMap<>();
    private final InputConstants.Key originalInteractKey;
    private final String originalInteractBinding;
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
            Supplier<VoiceSettingsActions> voiceSettings,
            BiFunction<String, UUID, java.util.Optional<String>> traceLookup,
            Consumer<Map<String, Object>> completed) {
        this.config = config;
        this.service = service;
        this.settings = settings;
        this.gson = gson;
        this.openGuide = openGuide;
        this.hudReceipt = hudReceipt;
        this.voiceSettings = voiceSettings;
        this.traceLookup = traceLookup;
        this.completed = completed;
        String root = System.getProperty("openallay.e2e.screenshotRoot", "");
        if (root.isBlank()) throw new IllegalStateException("Native frame output is required");
        frameRoot = Path.of(root);
        originalWindowWidth = client.getWindow().getWidth();
        originalWindowHeight = client.getWindow().getHeight();
        originalGuiScale = client.options.guiScale().get();
        report.put("windowBefore", Map.of("width", originalWindowWidth, "height", originalWindowHeight, "guiScale", originalGuiScale));
        originalInteractBinding = OpenAllayKeyMappings.INTERACT_HUD.saveString();
        originalInteractKey = InputConstants.getKey(originalInteractBinding);
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
    }

    void tick() {
        if (done) return;
        try {
            if (Duration.between(started, Instant.now()).toSeconds()
                    > Long.getLong("openallay.e2e.timeoutSeconds", 300L)) {
                throw new IllegalStateException("Native graphical scenario exceeded its timeout at stage " + stage);
            }
            // At FPS10 this leaves several actual extraction/render frames between actions.
            if (++ticks < 12 || client.gui.overlay() != null) return;
            ticks = 0;
            runStage();
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    private void runStage() {
        switch (stage) {
            case 0 -> {
                requireLoopbackFixture();
                require("zh_cn".equals(client.options.languageCode), "Chinese language must be prepared before launch");
                client.getWindow().setWindowed(850, 480);
                OpenAllayKeyMappings.INTERACT_HUD.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
                KeyMapping.resetMapping();
                report.put("interactKeyDuring", OpenAllayKeyMappings.INTERACT_HUD.saveString());
                report.put("world", client.getSingleplayerServer().getWorldData().getLevelName());
                report.put("commandsAllowed", client.getSingleplayerServer().getWorldData().isAllowCommands());
                require(!client.getSingleplayerServer().getWorldData().isAllowCommands(), "Disposable world commands must be off");
                openGuide.accept(service);
                advance();
            }
            case 1 -> {
                guide();
                MultiLineEditBox composer = client.gui.screen().children().stream()
                        .filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast)
                        .findFirst().orElseThrow();
                composer.setValue(config.question(), true);
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
                var firstNative = request.tools().getFirst().normalized().getAsJsonObject("value");
                require("RECIPE".equals(firstNative.get("viewKind").getAsString()),
                        "First Tool did not return an actual trusted native recipe card");
                require(request.assistantText().contains("全文末尾：原生图形长回复验收完成"), "The full local test response was not retained");
                report.put("requestId", request.requestId().toString());
                report.put("requestOutcome", request.status().name());
                report.put("actualTools", request.tools().stream().map(value -> Map.of(
                        "toolId", value.toolId(), "status", value.status().name())).toList());
                report.put("assistantTextSha256", sha256(request.assistantText()));
                guide().setFocused(null);
                guide().keyPressed(new KeyEvent(GLFW.GLFW_KEY_HOME, 0, 0));
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
                String label = Component.translatable("screen.openallay.settings.general.assistant_name.label").getString();
                EditBox name = client.gui.screen().children().stream().filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast).filter(value -> value.getMessage().getString().equals(label))
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
                require(!settings.snapshot().display().ui().fullscreen().toolsCollapsed(), "Reset kept Tools collapsed");
                require(expectedTheme.equals(theme()), "Done did not acknowledge Reset");
                checkpoint("03-reset-expanded-tools", true);
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
                require(client.gui.screen() == null, "Passive HUD captured the gameplay screen");
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                require(number(receipt, "extractedFrame") > hudFrameBeforeDone, "HUD receipt is stale after Done");
                if (number(receipt, "recipeNodes") == 0 || number(receipt, "nativeViews") == 0
                        || number(receipt, "toolRows") == 0 || number(receipt, "toolCards") == 0) {
                    waitFor("passive HUD actual recipe and Tool-result page");
                    return;
                }
                checkpoint("05-passive-hud-native-recipe-tool", false);
                report.put("passiveHudNativeRecipeAndTool", receipt);
                passiveRecipeFrame = number(receipt, "extractedFrame");
                stage = 65;
                stageWait = 0;
            }
            case 26 -> {
                Screen lite = client.gui.screen();
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
                boolean handled = lite.mouseScrolled(wheelX, wheelY, 0, -10000);
                actions.add(Map.of("type", "native-wheel", "target", "interactive-hud-bottom", "stage", stage,
                        "at", Instant.now().toString(), "x", wheelX, "y", wheelY,
                        "scrollX", 0, "scrollY", -10000, "handled", handled, "resultViewport", results));
                require(handled, "Native HUD result viewport did not consume the actual wheel callback");
                advance();
            }
            case 27 -> {
                Screen lite = client.gui.screen();
                JsonObject receipt = gson.toJsonTree(readReceipt(lite, "resultReceipt")).getAsJsonObject();
                require(number(receipt, "extractedFrame") > interactiveBeforeWheelFrame,
                        "HUD wheel receipt was not freshly extracted");
                require(number(receipt, "scroll") == number(receipt, "maximumScroll")
                                && number(receipt, "scroll") == number(receipt, "contentHeight") - number(receipt, "viewportHeight"),
                        "HUD wheel did not expose全文 bottom");
                require(receipt.has("renderedNodeIds") && !receipt.getAsJsonArray("renderedNodeIds").isEmpty(),
                        "HUD bottom has no actually extracted semantic text-node identity");
                var finalAssistant = request.timeline().stream()
                        .filter(dev.openallay.guide.GuideTimelineEntry.Assistant.class::isInstance)
                        .map(dev.openallay.guide.GuideTimelineEntry.Assistant.class::cast).toList().getLast();
                String tailNode = finalAssistant.semantic().blocks().getLast().nodeId();
                require(receipt.getAsJsonArray("renderedNodeIds").asList().stream()
                                .anyMatch(value -> tailNode.equals(value.getAsString())),
                        "Actual HUD extracted nodes do not include the full response's final text block");
                String paintedTail = receipt.has("lastRenderedText") ? receipt.get("lastRenderedText").getAsString().strip() : "";
                require(!paintedTail.isEmpty() && "全文末尾：原生图形长回复验收完成。".endsWith(paintedTail),
                        "HUD full response tail was not actually painted after wheel scrolling");
                checkpoint("07-interactive-hud-bottom", false);
                lite.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                recordAction("native-key", "ESC/HUD-to-game");
                advance();
            }
            case 28 -> {
                require(client.gui.screen() == null, "HUD ESC left a Screen or focus behind");
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
                require(client.gui.screen() == null, "Cached HUD failed to return to gameplay");
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
                Path exports = client.gameDirectory.toPath().resolve("openallay/exports").toAbsolutePath().normalize();
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
                settingsScreen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                recordAction("native-key", "ESC/dirty-settings");
                advance();
            }
            case 44 -> {
                if (!doneReturned()) return;
                require(expectedName.equals(settings.snapshot().display().assistantName()), "ESC lost the dirty name");
                exits.add(Map.of("entry", "nativeEscape", "savedName", expectedName, "ackBeforeClose", true));
                checkpoint("exit-escape-saved", true);
                client.getWindow().setWindowed(320, 480);
                press("screen.openallay.settings.short");
                advance();
            }
            case 45 -> { navigate("screen.openallay.settings.general"); advance(); }
            case 46 -> {
                expectedName = "小羽 · 返回保存";
                nameEditor().setValue(expectedName);
                Button back = findButton("screen.openallay.settings.back", false);
                require(back != null, "Native narrow Back button is not visible");
                report.put("narrowBack", Map.of("visible", true, "guiWidth", settingsScreen().width,
                        "guiHeight", settingsScreen().height, "guiScale", client.options.guiScale().get()));
                checkpoint("exit-back-dirty-narrow", false);
                press("screen.openallay.settings.back");
                advance();
            }
            case 47 -> {
                if (!doneReturned()) return;
                require(expectedName.equals(settings.snapshot().display().assistantName()), "Native Back lost the dirty name");
                exits.add(Map.of("entry", "nativeBackButton", "savedName", expectedName, "ackBeforeClose", true));
                checkpoint("exit-back-saved", true);
                client.getWindow().setWindowed(850, 480);
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
                require(client.gui.screen() == null, "Passive item page captured gameplay");
                JsonObject receipt = gson.toJsonTree(hudReceipt.get()).getAsJsonObject();
                if (number(receipt, "extractedFrame") <= passiveRecipeFrame || number(receipt, "nativeItemNodes") == 0) {
                    waitFor("passive HUD actual native item page");
                    return;
                }
                report.put("passiveHudNativeItemPage", receipt);
                checkpoint("05-passive-hud-native-items", false);
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
                recordAction("native-keymapping-click", "INTERACT_HUD/F8");
                stage = 26;
                stageWait = 0;
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
                require(client.isWindowActive(), "HUD editor requires an active native window for dragging");
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
                        "guiScale", client.options.guiScale().get()));
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
                var event = new MouseButtonEvent(hudDragPointerX, hudDragPointerY,
                        new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                boolean handled = editor.mouseClicked(event, false);
                recordHudPointer("mouseClicked", event, 0, 0, handled);
                require(handled, "Native HUD editor did not consume the real pointer press");
                advance();
            }
            case 76 -> {
                GuideHudEditorScreen editor = hudEditor();
                require(client.isWindowActive(), "Native HUD drag lost window focus");
                var event = new MouseButtonEvent(hudDragPointerX + hudDragDx, hudDragPointerY + hudDragDy,
                        new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                boolean handled = editor.mouseDragged(event, hudDragDx, hudDragDy);
                recordHudPointer("mouseDragged", event, hudDragDx, hudDragDy, handled);
                require(handled, "Native HUD editor did not consume the real drag callback");
                require(hudDisplayBeforeDrag.equals(settings.snapshot().display()), "Dragging saved settings before Apply");
                advance();
            }
            case 77 -> {
                GuideHudEditorScreen editor = hudEditor();
                var event = new MouseButtonEvent(hudDragPointerX + hudDragDx, hudDragPointerY + hudDragDy,
                        new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                boolean handled = editor.mouseReleased(event);
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
                require(client.gui.screen() == null, "Applied HUD did not return to actual gameplay");
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
                require(editor.width == client.getWindow().getGuiScaledWidth()
                                && editor.height == client.getWindow().getGuiScaledHeight(),
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

    private GuideHudEditorScreen hudEditor() {
        require(client.gui.screen() instanceof GuideHudEditorScreen, "Actual native HUD layout editor is not open");
        return (GuideHudEditorScreen) client.gui.screen();
    }

    private EditBox hudOffsetEditor(String key) {
        String label = Component.translatable("screen.openallay.settings.ui." + key).getString();
        return settingsScreen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst()
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
        require(screen.mouseScrolled(x, y, 0, -2), "Actual UI HUD page did not consume native scrolling");
        recordAction("native-wheel", "reveal-visible-Edit-HUD-button");
        waitFor("visible enabled native Edit HUD button");
        return false;
    }

    private void clickHudCaseButton(String key) {
        Button button = findButton(key, false);
        require(button != null, "Actual visible enabled HUD case button unavailable: " + key);
        Screen screen = client.gui.screen();
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        require(button.getX() >= 0 && button.getY() >= 0
                        && button.getX() + button.getWidth() <= screen.width
                        && button.getY() + button.getHeight() <= screen.height,
                "Actual HUD case button is outside the native viewport");
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        boolean clicked = screen.mouseClicked(event, false);
        boolean released = screen.mouseReleased(event);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "native-mouse-button");
        action.put("target", key);
        action.put("stage", stage);
        action.put("at", Instant.now().toString());
        action.put("screen", screen.getClass().getName());
        action.put("eventType", MouseButtonEvent.class.getName());
        action.put("x", x);
        action.put("y", y);
        action.put("button", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        action.put("modifiers", 0);
        action.put("doubleClick", false);
        action.put("mouseClickedHandled", clicked);
        action.put("mouseReleasedHandled", released);
        actions.add(action);
        require(clicked, "Native Screen did not route the actual HUD case button click: " + key);
    }

    private void recordHudPointer(String callback, MouseButtonEvent event, double dx, double dy, boolean handled) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "native-hud-pointer");
        action.put("target", "HUD-layout-drag");
        action.put("callback", callback);
        action.put("stage", stage);
        action.put("at", Instant.now().toString());
        action.put("eventType", MouseButtonEvent.class.getName());
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
        Path current = client.gameDirectory.toPath().resolve("config/openallay/display.json").toAbsolutePath().normalize();
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
        var actual = dev.openallay.guide.ui.GuideUiView.from(service.snapshot(), settings.snapshot().display());
        var recipeTool = actual.rows().stream().filter(dev.openallay.guide.ui.GuideUiRow.Tool.class::isInstance)
                .map(dev.openallay.guide.ui.GuideUiRow.Tool.class::cast)
                .filter(tool -> tool.requestId().equals(request.requestId())
                        && tool.activity().invocationId().equals(request.tools().getFirst().invocationId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Actual recipe Tool row is unavailable"));
        String toolIdentity = "tool:" + recipeTool.requestId() + ":" + recipeTool.activity().invocationId();
        var recipeNodes = dev.openallay.guide.ui.hud.GuideHudToolCards.project(recipeTool,
                key -> Component.translatable(key).getString()).recipes().keySet();
        require(!recipeNodes.isEmpty(), "Actual typed Tool projection contains no native recipe card");
        var expected = recipeNodes.stream().map(node -> toolIdentity + ":card:" + node).toList();
        JsonObject nativeReceipt = gson.toJsonTree(readReceipt(guide(), "e2eToolsReceipt")).getAsJsonObject();
        var painted = nativeReceipt.getAsJsonArray("resultCardIds").asList().stream().map(value -> value.getAsString()).toList();
        if (expected.stream().noneMatch(painted::contains)) {
            guide().mouseScrolled(guide().width / 2.0, guide().height / 2.0, 0, -2);
            recordAction("native-wheel", "reveal-actual-typed-recipe-card");
            waitFor("painted native recipe stable ID in the actual fullscreen viewport");
            return false;
        }
        long frame = number(nativeReceipt, "lastNativeFrame");
        require(frame > nativeRecipeFrame, "Fullscreen recipe receipt did not come from a fresh extraction");
        nativeRecipeFrame = frame;
        report.put("fullscreenNativeRecipePaint", Map.of("expectedStableIds", expected,
                "actuallyPaintedStableIds", painted, "nativeExtractedFrame", frame,
                "receiptSource", "native registry render returned painted=true"));
        return true;
    }

    private EditBox nameEditor() {
        String label = Component.translatable("screen.openallay.settings.general.assistant_name.label").getString();
        return settingsScreen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst()
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
        String label = Component.translatable("screen.openallay.settings.ui.reply_lines").getString();
        AbstractSliderButton slider = settingsScreen().children().stream()
                .filter(AbstractSliderButton.class::isInstance).map(AbstractSliderButton.class::cast)
                .filter(value -> value.getMessage().getString().startsWith(label + " · ")).findFirst().orElseThrow();
        if (!slider.visible) {
            settingsScreen().mouseScrolled(settingsScreen().width / 2.0, settingsScreen().height / 2.0, 0, -2);
            recordAction("native-wheel", "reveal-reply-slider");
            waitFor("actual reply-lines slider visibility");
            return false;
        }
        require(slider.active, "Actual reply-lines slider is not enabled");
        double x = slider.getX() + 4 + selected / 80.0 * (slider.getWidth() - 8);
        var mouse = new MouseButtonEvent(x, slider.getY() + slider.getHeight() / 2.0, new MouseButtonInfo(0, 0));
        slider.onClick(mouse, false);
        slider.onRelease(mouse);
        recordAction("native-slider-click", "reply-lines=" + selected);
        return true;
    }

    private void installDisplayFault() {
        displayPath = client.gameDirectory.toPath().resolve("config/openallay/display.json").toAbsolutePath().normalize();
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
        require(!settings.snapshot().display().ui().fullscreen().toolsCollapsed(), "Tools must default to expanded");
        JsonObject tools = gson.toJsonTree(readReceipt(guide(), "e2eToolsReceipt")).getAsJsonObject();
        require(number(tools, "totalToolCount") == number(tools, "expandedToolCount"), "Actual task Tools are not expanded");
    }

    private boolean doneReturned() {
        if (client.gui.screen() instanceof OpenAllaySettingsScreen) { waitFor("Done save acknowledgement and return"); return false; }
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

    private Button findButton(String key, boolean prefix) {
        String text = Component.translatable(key).getString();
        return client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.visible && value.active)
                .filter(value -> value.getMessage().getString().equals(text)
                        || prefix && value.getMessage().getString().startsWith(text + " · "))
                .findFirst().orElse(null);
    }

    private void press(String key) {
        Button button = findButton(key, true);
        require(button != null, "Actual visible enabled native button unavailable: " + key);
        button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        recordAction("native-button", key);
    }

    private OpenAllayScreen guide() {
        require(client.gui.screen() instanceof OpenAllayScreen, "Actual fullscreen guide is not open");
        return (OpenAllayScreen) client.gui.screen();
    }

    private OpenAllaySettingsScreen settingsScreen() {
        require(client.gui.screen() instanceof OpenAllaySettingsScreen, "Actual coordinator settings screen is not open");
        return (OpenAllaySettingsScreen) client.gui.screen();
    }

    private void checkpoint(String name, boolean includeGuide) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("name", name);
        receipt.put("stage", stage);
        receipt.put("capturedAt", Instant.now().toString());
        receipt.put("nativeScreen", client.gui.screen() == null ? "gameplay" : client.gui.screen().getClass().getName());
        receipt.put("theme", theme());
        receipt.put("guiWidth", client.getWindow().getGuiScaledWidth());
        receipt.put("guiHeight", client.getWindow().getGuiScaledHeight());
        receipt.put("guiScale", client.options.guiScale().get());
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
        } else if (client.gui.screen() instanceof OpenAllaySettingsScreen) {
            receipt.put("settingsState", readReceipt(client.gui.screen(), "e2eSettingsState"));
        } else if (client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("GuideChatLiteScreen")) {
            receipt.put("interactiveHud", readReceipt(client.gui.screen(), "resultReceipt"));
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
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                CompletableFuture.runAsync(() -> {
                    try (image) {
                        int width = image.getWidth();
                        int height = image.getHeight();
                        image.writeToFile(path);
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
        client.options.guiScale().set(originalGuiScale);
        client.getWindow().setWindowed(originalWindowWidth, originalWindowHeight);
        report.put("windowRestorationRequested", Map.of("width", originalWindowWidth, "height", originalWindowHeight,
                "guiScale", client.options.guiScale().get(), "originalGuiScaleRestored", client.options.guiScale().get() == originalGuiScale));
        OpenAllayKeyMappings.INTERACT_HUD.setKey(originalInteractKey);
        KeyMapping.resetMapping();
        report.put("interactKeyAfter", OpenAllayKeyMappings.INTERACT_HUD.saveString());
        report.put("interactKeyRestored", originalInteractBinding.equals(OpenAllayKeyMappings.INTERACT_HUD.saveString()));
    }

    private void fail(RuntimeException failure) {
        done = true;
        report.put("outcome", "HARNESS_FAILED");
        report.put("failureCode", "native_graphical_stage_failed");
        report.put("failureStage", stage);
        report.put("failureMessage", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        report.put("failureException", failure.toString());
        if (failure.getCause() != null) report.put("failureCause", failure.getCause().toString());
        report.put("elapsedMillis", Duration.between(started, Instant.now()).toMillis());
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
                .whenComplete((cleanupFailure, publicationFailure) -> client.execute(() -> {
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
