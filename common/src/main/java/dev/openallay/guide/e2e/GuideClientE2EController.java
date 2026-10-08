package dev.openallay.guide.e2e;

import dev.openallay.client.gui.MinecraftClientWindow;

import com.google.gson.Gson;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideSubscription;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.ui.SemanticLayoutCache;
import dev.openallay.client.gui.OpenAllayScreen;
import dev.openallay.client.gui.OpenAllaySettingsScreen;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.tool.ToolResult;
import dev.openallay.recipe.RecipeProviderReadiness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Development-only real-client probe. Loader tick adapters call {@link #tick(UUID)};
 * the in-game Agent never receives its report-writing or shutdown capabilities.
 */
public final class GuideClientE2EController {
    private final GuideClientE2EConfig config;
    private final String loader;
    private final String gameVersion;
    private final String modVersion;
    private final GuideServiceManager services;
    private final Gson gson;
    private final Runnable shutdown;
    private final Supplier<RecipeProviderReadiness> recipeReadiness;
    private final ClientSettingsService clientSettings;
    private final BiFunction<String, UUID, java.util.Optional<String>> traceLookup;
    private final List<GuideRequestStatus> transitions = new ArrayList<>();
    private Instant startedAt;
    private UUID requestId;
    private GuideSubscription subscription;
    private RecipeProviderReadiness lastRecipeReadiness;
    private int remainingHistorySeeds;
    private boolean seedingHistory;
    private boolean started;
    private boolean finished;
    private int screenshotStage = -1;
    private int screenshotTicks;
    private int originalWindowWidth;
    private int originalWindowHeight;
    private int activeScreenshotTicks;
    private int activeProgressVisibleTicks;
    private boolean activeScreenshotCaptured;
    private String pendingReport;
    private String pendingTraceProfile;
    private int traceWaitTicks;
    private Instant harnessStartedAt;
    private String startupPhase;
    private Instant startupPhaseAt;
    private Map<String, Object> startupDiagnostic = dev.openallay.util.Java8Collections.mapOf();
    private int startupDiagnosticChanges;
    private boolean worldLaunchStarted;
    private int builderWarmupTicks;
    private GuideBuilderE2EProbe.Anchor builderAnchor;
    private GuideBuilderE2EProbe.Anchor currentPlayerAnchor;
    private boolean unrestrictedAtStart;
    private boolean nativeProbePending;
    private boolean builderAnchorPending;
    private Instant builderAnchorSubmittedAt;
    private volatile String builderAnchorPhase = "not_submitted";
    private dev.openallay.OpenAllayRuntime nativeCommandRuntime;
    private dev.openallay.client.MinecraftGuideContextProvider nativeCommandContexts;
    private dev.openallay.model.CancellationSignal nativeCommandCancellation;
    private com.google.gson.JsonObject nativeCommandWarmup;
    private Boolean nativeCommandOriginalSetting;
    private java.util.concurrent.CompletableFuture<ToolResult<Boolean>> nativeCommandEnable;
    private java.util.concurrent.CompletableFuture<Boolean> nativeCommandRestore;
    private String nativeCommandPendingFinish;
    private UUID screenshotActor;
    private dev.openallay.guide.ui.GuideDisplayConfig screenshotOriginalDisplay;
    private boolean screenshotActionPending;
    private int screenshotWaitTicks;
    private String screenshotReviewFailure;
    private boolean screenshotSourceAvailable;
    private boolean cancelOnToolStartRequested;
    private boolean cancelOnToolStartPending;
    private boolean cancelOnToolStartAccepted;
    private final boolean developmentProbeEnabled = Boolean.getBoolean(GuideClientE2EConfig.ENABLED);
    private GuideGraphicalRegressionProbe graphicalProbe;
    private java.util.function.Consumer<GuideService> graphicalOpenGuide;
    private Supplier<Object> graphicalHudReceipt;
    private Supplier<Object> graphicalToastReceipt;
    private Supplier<dev.openallay.client.voice.VoiceSettingsActions> graphicalVoiceSettings;
    private String graphicalFreshWorldName;
    private net.minecraft.client.server.IntegratedServer graphicalSeedServer;
    private boolean graphicalRecipeSeedAdmitted;
    private boolean graphicalRecipeSeedReady;
    private Set<String> graphicalSeedRecipes = dev.openallay.util.Java8Collections.setOf();
    private com.google.gson.JsonObject graphicalRecipeSeedReceipt;

    public GuideClientE2EController(
            GuideClientE2EConfig config,
            String loader,
            String gameVersion,
            String modVersion,
            GuideServiceManager services,
            Gson gson,
            Runnable shutdown) {
        this(config, loader, gameVersion, modVersion, services, gson, shutdown,
                RecipeProviderReadiness::ready, null, null);
    }

    public GuideClientE2EController(
            GuideClientE2EConfig config,
            String loader,
            String gameVersion,
            String modVersion,
            GuideServiceManager services,
            Gson gson,
            Runnable shutdown,
            Supplier<RecipeProviderReadiness> recipeReadiness) {
        this(config, loader, gameVersion, modVersion, services, gson, shutdown,
                recipeReadiness, null, null);
    }

    public GuideClientE2EController(
            GuideClientE2EConfig config,
            String loader,
            String gameVersion,
            String modVersion,
            GuideServiceManager services,
            Gson gson,
            Runnable shutdown,
            Supplier<RecipeProviderReadiness> recipeReadiness,
            ClientSettingsService clientSettings) {
        this(
                config,
                loader,
                gameVersion,
                modVersion,
                services,
                gson,
                shutdown,
                recipeReadiness,
                clientSettings,
                null);
    }

    public GuideClientE2EController(
            GuideClientE2EConfig config,
            String loader,
            String gameVersion,
            String modVersion,
            GuideServiceManager services,
            Gson gson,
            Runnable shutdown,
            Supplier<RecipeProviderReadiness> recipeReadiness,
            ClientSettingsService clientSettings,
            BiFunction<String, UUID, java.util.Optional<String>> traceLookup) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.loader = require(loader, "loader");
        this.gameVersion = require(gameVersion, "gameVersion");
        this.modVersion = require(modVersion, "modVersion");
        this.services = java.util.Objects.requireNonNull(services, "services");
        this.gson = java.util.Objects.requireNonNull(gson, "gson");
        this.shutdown = java.util.Objects.requireNonNull(shutdown, "shutdown");
        this.recipeReadiness = java.util.Objects.requireNonNull(recipeReadiness, "recipeReadiness");
        this.clientSettings = clientSettings;
        this.traceLookup = traceLookup;
    }

    /** Loader-owned real UI capabilities; inert unless the explicit graphical scenario is selected. */
    public void attachGraphicalProbe(
            java.util.function.Consumer<GuideService> openGuide,
            Supplier<Object> hudReceipt,
            Supplier<dev.openallay.client.voice.VoiceSettingsActions> voiceSettings) {
        if (!developmentProbeEnabled) throw new IllegalStateException("development probe was disabled at construction");
        if (started) throw new IllegalStateException("Graphical probe must attach before startup");
        graphicalOpenGuide = java.util.Objects.requireNonNull(openGuide, "openGuide");
        graphicalHudReceipt = java.util.Objects.requireNonNull(hudReceipt, "hudReceipt");
        graphicalVoiceSettings = java.util.Objects.requireNonNull(voiceSettings, "voiceSettings");
    }

    /** Read-only native owner receipt; never shows a test notification or creates a card. */
    public void attachGraphicalToastReceipt(Supplier<Object> toastReceipt) {
        if (!developmentProbeEnabled) throw new IllegalStateException("development probe was disabled at construction");
        if (started) throw new IllegalStateException("Graphical toast receipt must attach before startup");
        graphicalToastReceipt = java.util.Objects.requireNonNull(toastReceipt, "toastReceipt");
    }

    /** Actual product owners for the separate development-only native command warmup. */
    public void attachNativeCommandProbe(dev.openallay.OpenAllayRuntime runtime,
            dev.openallay.client.MinecraftGuideContextProvider contexts) {
        if (!developmentProbeEnabled || started)
            throw new IllegalStateException("Native command probe must attach before development startup");
        nativeCommandRuntime = java.util.Objects.requireNonNull(runtime, "runtime");
        nativeCommandContexts = java.util.Objects.requireNonNull(contexts, "contexts");
    }

    static boolean graphicalScenario(String scenario) {
        return "ui-manual-regressions".equals(scenario) || "ui-live-ux-regressions".equals(scenario);
    }

    /** Runs opt-in startup lifecycle and starts the request once a real client player exists. */
    public void tick(UUID actor) {
        GuideProbeWorldReload.tick(dev.openallay.client.gui.MinecraftClientWindow.instance());
        if (finished) {
            if (!screenshotActionPending) {
                try { tickScreenshotProbe(); }
                catch (RuntimeException failure) {
                    System.err.println("OpenAllay E2E screenshot failed: " + failure.getClass().getSimpleName());
                    finishScreenshotProbe();
                }
            }
            return;
        }
        if (started) {
            if (builderAnchorPending) {
                startupGate(builderAnchorPhase, actor);
                if (finished) return;
                if ((Duration.between(builderAnchorSubmittedAt, Instant.now())).getSeconds()
                        > Math.max(1L, Long.getLong("openallay.e2e.anchorTimeoutSeconds", 30L))) {
                    builderAnchorPending = false;
                    failWithoutRequest("native_anchor_timeout", "Builder anchor did not advance from " + builderAnchorPhase);
                }
                return;
            }
            if (graphicalProbe != null) {
                graphicalProbe.tick();
                return;
            }
            long timeoutSeconds = Long.getLong("openallay.e2e.timeoutSeconds", 300L);
            if ((Duration.between(startedAt, Instant.now())).getSeconds() > timeoutSeconds) {
                failWithoutRequest("harness_timeout", "Real-client acceptance exceeded its elapsed timeout");
                return;
            }
            if (nativeProbePending) return;
            if (pendingReport != null) {
                finishWithTrace();
                return;
            }
            tickActiveScreenshotProbe();
            return;
        }
        if (harnessStartedAt == null) harnessStartedAt = Instant.now();
        long timeoutSeconds = Long.getLong("openallay.e2e.timeoutSeconds", 300L);
        if ((Duration.between(harnessStartedAt, Instant.now())).getSeconds() > timeoutSeconds) {
            failWithoutRequest("harness_timeout", "Real-client acceptance exceeded its elapsed timeout");
            return;
        }
        if (actor == null) {
            try { tickWorldLaunch(); }
            catch (RuntimeException failure) { failWithoutRequest("world_startup_failed", failure.toString()); }
            startupGate("awaiting_player", actor);
            return;
        }
        if ("native-world-sdk".equals(config.scenario())) {
            if (++builderWarmupTicks < 60 || nativeProbePending) return;
            nativeProbePending = true;
            started = true;
            startedAt = Instant.now();
            GuideNativeWorldAccessProbe.run(actor, System.getProperty("openallay.e2e.createWorld", System.getProperty("openallay.e2e.resumeWorld", "")),
                    report -> finish(gson.toJson(report)));
            return;
        }
        if (GuideBuilderE2EProbe.enabled(config.scenario()) && ++builderWarmupTicks < 40) return;
        if (!dev.openallay.util.Java8Strings.isBlank(System.getProperty("openallay.e2e.screenshotRoot", ""))) {
            net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
            if (MinecraftClientWindow.overlayPresent(client) || MinecraftClientWindow.screen(client) != null) {
                startupGate("awaiting_gameplay_screen", actor);
                return;
            }
        }
        GuideService service = services.forActor(actor);
        if (service.snapshot().persistence().state()
                == dev.openallay.guide.GuidePersistenceSnapshot.State.LOADING) {
            startupGate("awaiting_history_load", actor);
            return;
        }
        RecipeProviderReadiness readiness = recipeReadiness.get();
        if (!readiness.equals(lastRecipeReadiness)) {
            lastRecipeReadiness = readiness;
            System.out.println("OpenAllay E2E recipe readiness: "
                    + readiness.state() + " " + readiness.code() + " " + readiness.message());
        }
        if (readiness.state() == RecipeProviderReadiness.State.WAITING) {
            startupGate("awaiting_recipe_provider", actor);
            return;
        }
        if (readiness.state() == RecipeProviderReadiness.State.FAILED) {
            failWithoutRequest(readiness.code(), readiness.message());
            return;
        }
        if (!tickGraphicalRecipePrecondition(actor)) {
            if (!finished) startupGate("awaiting_recipe_synchronization", actor);
            return;
        }
        startupGate("startup_ready", actor);
        started = true;
        startedAt = Instant.now();
        subscription = service.subscribe(this::observe);
        unrestrictedAtStart = clientSettings != null && clientSettings.snapshot().unrestrictedJavascript().enabled();
        if (GuideBuilderE2EProbe.enabled(config.scenario())) {
            if (!developmentProbeEnabled || !worldLaunchStarted) {
                failWithoutRequest("unsafe_builder_fixture", "Builder acceptance requires this controller's disposable world launch");
                return;
            }
            builderAnchorPending = true;
            builderAnchorSubmittedAt = Instant.now();
            GuideBuilderE2EProbe.captureAnchor(config.scenario(), actor, anchor -> {
                builderAnchorPending = false;
                if (finished) return;
                currentPlayerAnchor = anchor;
                try {
                    builderAnchor = selectBuilderAnchor(anchor);
                } catch (IOException | RuntimeException failure) {
                    failWithoutRequest("native_anchor_failed", failure.toString());
                    return;
                }
                if (config.scenario().equals("builder-acceptance")
                        && ("1.18.2".equals(gameVersion) || "1.19.2".equals(gameVersion))
                        && "forge".equals(loader)) {
                    if (nativeCommandRuntime == null || nativeCommandContexts == null) {
                        failWithoutRequest("native_command_owner_unavailable", "Actual product command owners are unavailable");
                        return;
                    }
                    startNativeCommandWarmup(actor, service);
                } else selectSession(service);
            }, failure -> {
                builderAnchorPending = false;
                failWithoutRequest("native_capture_failed", failure);
            }, phase -> {
                builderAnchorPhase = phase;
                System.out.println("OpenAllay E2E Builder anchor: " + phase);
            });
        } else {
            selectSession(service);
        }
    }

    /** Change only this isolated development profile, then restore before Builder admission. */
    private void startNativeCommandWarmup(UUID actor, GuideService service) {
        if (clientSettings == null) {
            failWithoutRequest("native_command_settings_unavailable", "Actual client settings are unavailable");
            return;
        }
        nativeProbePending = true;
        nativeCommandOriginalSetting = clientSettings.snapshot().unrestrictedJavascript().enabled();
        nativeCommandWarmup = new com.google.gson.JsonObject();
        nativeCommandWarmup.addProperty("outcome", "WAITING");
        nativeCommandWarmup.addProperty("initialUnrestrictedSetting", nativeCommandOriginalSetting);
        nativeCommandWarmup.addProperty("javascriptSettingTemporarilyEnabled", !nativeCommandOriginalSetting);
        nativeCommandWarmup.addProperty("worldAuthorityChanged", false);
        // Use the same typed async settings action as the real settings UI; never block a tick.
        nativeCommandEnable = nativeCommandOriginalSetting
                ? java.util.concurrent.CompletableFuture.completedFuture(new ToolResult.Success<>(true))
                : clientSettings.saveUnrestrictedJavascript(true);
        nativeCommandEnable.whenComplete((enabled, failure) ->
                dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                    if (finished || nativeCommandPendingFinish != null) return;
                    if (failure != null || !(enabled instanceof ToolResult.Success<Boolean>)
                            || !clientSettings.snapshot().unrestrictedJavascript().enabled()) {
                        nativeCommandWarmup.addProperty("outcome", "FAILED");
                        nativeCommandWarmup.addProperty("failure", "Actual unrestricted settings action failed");
                        finishNativeCommandWarmup(service);
                        return;
                    }
                    try {
                        nativeCommandCancellation = GuideNativeCommandE2EProbe.start(
                                nativeCommandRuntime, nativeCommandContexts, actor, receipt -> {
                                    if (finished || nativeCommandPendingFinish != null) return;
                                    receipt.addProperty("initialUnrestrictedSetting", nativeCommandOriginalSetting);
                                    receipt.addProperty("javascriptSettingTemporarilyEnabled", !nativeCommandOriginalSetting);
                                    nativeCommandWarmup = receipt;
                                    finishNativeCommandWarmup(service);
                                });
                    } catch (RuntimeException error) {
                        nativeCommandWarmup.addProperty("outcome", "FAILED");
                        nativeCommandWarmup.addProperty("failure", error.toString());
                        finishNativeCommandWarmup(service);
                    }
                }));
    }

    private void finishNativeCommandWarmup(GuideService service) {
        restoreNativeCommandSetting().whenComplete((restored, failure) ->
                dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                    nativeProbePending = false;
                    if (finished || nativeCommandPendingFinish != null) return;
                    if (failure != null || !Boolean.TRUE.equals(restored)
                            || !"PASSED".equals(nativeCommandWarmup.get("outcome").getAsString())) {
                        failWithoutRequest("native_command_warmup_failed", "Native command warmup failed; inspect its receipt");
                        return;
                    }
                    selectSession(service);
                }));
    }

    /** One serialized restoration, including harness timeout while enable is still pending. */
    private java.util.concurrent.CompletableFuture<Boolean> restoreNativeCommandSetting() {
        if (nativeCommandRestore != null) return nativeCommandRestore;
        nativeCommandRestore = new java.util.concurrent.CompletableFuture<>();
        nativeCommandEnable.whenComplete((ignored, enableFailure) ->
                dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                    boolean original = nativeCommandOriginalSetting;
                    java.util.concurrent.CompletableFuture<dev.openallay.tool.ToolResult<java.lang.Boolean>> restoration = clientSettings.snapshot().unrestrictedJavascript().enabled() == original
                            ? java.util.concurrent.CompletableFuture.<ToolResult<Boolean>>completedFuture(
                                    new ToolResult.Success<>(true))
                            : clientSettings.saveUnrestrictedJavascript(original);
                    restoration.whenComplete((result, failure) ->
                            dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                                boolean actual = clientSettings.snapshot().unrestrictedJavascript().enabled();
                                boolean restored = failure == null && result instanceof ToolResult.Success<Boolean>
                                        && actual == original;
                                nativeCommandWarmup.addProperty("restoredUnrestrictedSetting", actual);
                                nativeCommandWarmup.addProperty("javascriptSettingRestored", restored);
                                if (!restored) {
                                    nativeCommandWarmup.addProperty("outcome", "FAILED");
                                    nativeCommandWarmup.addProperty("restorationFailure", "Actual unrestricted settings restore failed");
                                }
                                nativeCommandRestore.complete(restored);
                            }));
                }));
        return nativeCommandRestore;
    }

    /** Setup-only native recipe unlock; never a model Tool, inventory grant, or accepted UI action. */
    private boolean tickGraphicalRecipePrecondition(UUID actor) {
        if (!graphicalScenario(config.scenario())) return true;
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        if (graphicalRecipeSeedReceipt == null) {
            graphicalRecipeSeedReceipt = new com.google.gson.JsonObject();
            graphicalRecipeSeedReceipt.addProperty("purpose", "isolated-fresh-world-test-bootstrap-only");
            graphicalRecipeSeedReceipt.addProperty("modelToolAction", false);
            graphicalRecipeSeedReceipt.addProperty("acceptedUiAction", false);
            graphicalRecipeSeedReceipt.addProperty("inventoryGranted", false);
            graphicalRecipeSeedReceipt.addProperty("commandsUsed", false);
            graphicalRecipeSeedReceipt.addProperty("outcome", "WAITING");
        }
        try {
            net.minecraft.client.server.IntegratedServer server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
            requireGraphicalSeedWorld(server);
            if (client.player == null || dev.openallay.client.gui.MinecraftClientWindow.world(client) == null || !actor.equals(dev.openallay.client.gui.MinecraftClientWindow.actor(client)))
                throw new IllegalStateException("Fresh-world client player differs from the tested actor");
            if (!graphicalRecipeSeedAdmitted) {
                graphicalSeedServer = server;
                graphicalRecipeSeedReceipt.addProperty("worldName", graphicalFreshWorldName);
                graphicalRecipeSeedReceipt.addProperty("actorId", actor.toString());
                graphicalRecipeSeedReceipt.addProperty("commandsAllowedBefore", false);
                graphicalRecipeSeedReceipt.add("clientBefore", graphicalRecipeBookReceipt(client));
                graphicalRecipeSeedReceipt.add("captureBefore", graphicalRecipeCaptureReceipt(client));
                graphicalRecipeSeedAdmitted = true;
                graphicalRecipeSeedReceipt.addProperty("admissions", 1);
                dev.openallay.server.NativeServerOwner.execute(server, () -> awardGraphicalSeedRecipe(actor, client, server));
                return false;
            }
            if (graphicalSeedRecipes.isEmpty()) return false;
            boolean synchronizedOutput = dev.openallay.context.minecraft.MinecraftRecipeCapture
                    .clientRecipes(client.player, client).stream()
                    .anyMatch(input -> graphicalSeedRecipes.contains(input.id())
                            && input.outputs().stream().anyMatch(GuideClientE2EController::positiveIronBlock));
            if (!synchronizedOutput) return false;
            if (!graphicalRecipeSeedReady) {
                com.google.gson.JsonObject capture = graphicalRecipeCaptureReceipt(client);
                if ((capture.getAsJsonArray("selectedRecipes").size() == 0)) return false;
                graphicalRecipeSeedReceipt.add("clientAfter", graphicalRecipeBookReceipt(client));
                graphicalRecipeSeedReceipt.add("captureAfter", capture);
                graphicalRecipeSeedReceipt.addProperty("commandsAllowedAfter", false);
                graphicalRecipeSeedReceipt.addProperty("synchronizedAt", Instant.now().toString());
                graphicalRecipeSeedReceipt.addProperty("outcome", "READY");
                graphicalRecipeSeedReady = true;
            }
            return true;
        } catch (RuntimeException failure) {
            failGraphicalRecipePrecondition(failure);
            return false;
        }
    }

    private void requireGraphicalSeedWorld(net.minecraft.client.server.IntegratedServer server) {
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        String create = System.getProperty("openallay.e2e.createWorld", "");
        if (!developmentProbeEnabled
                || !graphicalScenario(config.scenario())
                || !worldLaunchStarted || graphicalFreshWorldName == null
                || !graphicalFreshWorldName.equals(create)
                || !dev.openallay.util.Java8Strings.isBlank(System.getProperty("openallay.e2e.resumeWorld", ""))
                || server == null || server != dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client)
                || (graphicalSeedServer != null && server != graphicalSeedServer)
                || dev.openallay.server.NativeServerOwner.published(server)
                || !graphicalFreshWorldName.equals(dev.openallay.server.NativeServerOwner.worldName(server))
                || GuideProbeWorldSettings.commandsAllowed(server)
                || !dev.openallay.server.NativeServerOwner.survival(server)
                || !GuideProbeWorldSettings.isFlat(server)
                || !dev.openallay.platform.minecraft.MinecraftWorldSavePath.root(server).toAbsolutePath().normalize()
                        .equals(dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("saves").resolve(graphicalFreshWorldName)
                                .toAbsolutePath().normalize()))
            throw new IllegalStateException("Recipe bootstrap requires this controller's fresh isolated commands-off survival world");
    }

    private void awardGraphicalSeedRecipe(UUID actor, net.minecraft.client.Minecraft client,
            net.minecraft.client.server.IntegratedServer server) {
        if (finished) return;
        try {
            requireGraphicalSeedWorld(server);
            if (!dev.openallay.server.NativeServerOwner.isOwner(server)) throw new IllegalStateException("Recipe bootstrap is not on the server owner thread");
            net.minecraft.server.level.ServerPlayer player = dev.openallay.server.NativeServerOwner.player(server, actor);
            if (player == null || dev.openallay.context.minecraft.MinecraftPlayerFacts.gameMode(player) != net.minecraft.world.level.GameType.SURVIVAL
                    || dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player).getSeed() != 17L)
                throw new IllegalStateException("Native bootstrap player or fresh-world seed differs from setup");
            dev.openallay.context.minecraft.MinecraftRecipeSeed seed = dev.openallay.context.minecraft.MinecraftRecipeCapture.seed(player, "minecraft:iron_block");
            java.util.List<dev.openallay.context.minecraft.MinecraftRecipeInput> positiveInputs = dev.openallay.util.Java8Collections.toList(seed.inputs().stream()
                    .filter(input -> input.outputs().stream().anyMatch(GuideClientE2EController::positiveIronBlock)));
            if (positiveInputs.isEmpty()) throw new IllegalStateException("Exact native holder has no positive iron-block output");
            com.google.gson.JsonObject receipt = new com.google.gson.JsonObject();
            receipt.addProperty("api", "ServerPlayer.awardRecipes");
            receipt.addProperty("ownerThread", dev.openallay.server.NativeServerOwner.isOwner(server));
            receipt.addProperty("recipeHolderId", seed.holderId());
            receipt.addProperty("nativeRecipeClass", seed.nativeRecipeClass());
            receipt.addProperty("knownBefore", seed.known());
            receipt.add("recipeIds", gson.toJsonTree(dev.openallay.util.Java8Collections.toList(positiveInputs.stream().map(input -> input.id()))));
            receipt.add("positiveOutputs", gson.toJsonTree(dev.openallay.util.Java8Collections.toList(positiveInputs.stream()
                    .flatMap(input -> input.outputs().stream())
                    .filter(GuideClientE2EController::positiveIronBlock)
                    .map(stack -> dev.openallay.util.Java8Collections.mapOf("itemId", "minecraft:iron_block", "count", stack.getCount())))));
            receipt.addProperty("awardedRecipeCount", seed.award());
            receipt.addProperty("knownAfter", seed.known());
            receipt.addProperty("commandsAllowedAfter", GuideProbeWorldSettings.commandsAllowed(server));
            if (!receipt.get("knownAfter").getAsBoolean() || GuideProbeWorldSettings.commandsAllowed(server))
                throw new IllegalStateException("Native recipe award did not preserve bootstrap preconditions");
            java.util.Set<java.lang.String> ids = dev.openallay.util.Java8Collections.setCopyOf(dev.openallay.util.Java8Collections.toList(positiveInputs.stream().map(input -> input.id())));
            dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> {
                if (finished) return;
                graphicalRecipeSeedReceipt.add("serverAward", receipt);
                graphicalSeedRecipes = ids;
            });
        } catch (RuntimeException failure) {
            dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> { if (!finished) failGraphicalRecipePrecondition(failure); });
        }
    }

    private static boolean positiveIronBlock(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 0
                && "minecraft:iron_block".equals(dev.openallay.platform.minecraft.MinecraftNativeRegistries.ITEM
                        .getKey(stack.getItem()).toString());
    }

    private com.google.gson.JsonObject graphicalRecipeBookReceipt(net.minecraft.client.Minecraft client) {
        java.util.Map<java.lang.String, dev.openallay.context.minecraft.MinecraftRecipeInput> entries = dev.openallay.context.minecraft.MinecraftRecipeCapture.clientRecipes(client.player, client)
                .stream().collect(java.util.stream.Collectors.toMap(input -> input.id(), input -> input,
                        (first, ignored) -> first));
        com.google.gson.JsonObject receipt = new com.google.gson.JsonObject();
        receipt.addProperty("collectionCount", dev.openallay.context.minecraft.MinecraftRecipeCapture
                .clientCollectionCount(client.player));
        receipt.addProperty("recipeCount", entries.size());
        receipt.addProperty("positiveIronRecipeCount", entries.values().stream()
                .filter(input -> input.outputs().stream().anyMatch(GuideClientE2EController::positiveIronBlock)).count());
        receipt.addProperty("positiveIronResultCount", entries.values().stream()
                .flatMap(input -> input.outputs().stream()).filter(GuideClientE2EController::positiveIronBlock).count());
        return receipt;
    }

    private com.google.gson.JsonObject graphicalRecipeCaptureReceipt(net.minecraft.client.Minecraft client) {
        if (clientSettings == null) throw new IllegalStateException("Actual recipe capture settings are unavailable");
        dev.openallay.recipe.config.RecipeClientRuntime runtime = dev.openallay.recipe.config.RecipeClientRuntime.defaults();
        runtime.replace(clientSettings.snapshot().recipes().config());
        dev.openallay.context.ToolInvocationContext capture = new dev.openallay.client.context.ClientContextCapture(gson,
                dev.openallay.platform.PlatformServices.load(), runtime).capture(client,
                dev.openallay.util.Java8Collections.setOf(dev.openallay.context.ContextCapability.RECIPES), "e2e-native-recipe-bootstrap");
        java.util.List<dev.openallay.context.RecipeEntrySnapshot> recipes = dev.openallay.util.Java8ApiSupport.orElseThrow(capture.recipes()).recipes();
        java.util.List<dev.openallay.context.RecipeEntrySnapshot> positive = dev.openallay.util.Java8Collections.toList(recipes.stream()
                .filter(recipe -> "minecraft:client_recipe_book".equals(recipe.reference().sourceId())
                        && recipe.unlockState() == dev.openallay.recipe.RecipeUnlockState.UNLOCKED
                        && recipe.evidence().authority() == dev.openallay.context.DataAuthority.CLIENT_VISIBLE
                        && recipe.outputs().stream().anyMatch(output -> output.stack().count() > 0
                                && "minecraft:iron_block".equals(output.stack().itemId()))));
        com.google.gson.JsonObject receipt = new com.google.gson.JsonObject();
        receipt.addProperty("capturedAt", capture.capturedAt().toString());
        receipt.addProperty("recipeCount", recipes.size());
        receipt.addProperty("positiveUnlockedIronRecipeCount", positive.size());
        receipt.add("selectedRecipes", gson.toJsonTree(dev.openallay.util.Java8Collections.toList(positive.stream()
                .filter(recipe -> graphicalSeedRecipes.contains(recipe.id()))
                .map(recipe -> dev.openallay.util.Java8Collections.mapOf("reference", recipe.reference(), "unlockState", recipe.unlockState(), "evidence", recipe.evidence(), "positiveOutputs", dev.openallay.util.Java8Collections.toList(recipe.outputs().stream()
                                .filter(output -> output.stack().count() > 0
                                        && "minecraft:iron_block".equals(output.stack().itemId()))))))));
        return receipt;
    }

    private void failGraphicalRecipePrecondition(RuntimeException failure) {
        graphicalRecipeSeedReceipt.addProperty("outcome", "FAILED");
        graphicalRecipeSeedReceipt.addProperty("failure", failure.toString());
        failWithoutRequest("native_recipe_precondition_failed", failure.toString());
    }

    private GuideBuilderE2EProbe.Anchor selectBuilderAnchor(GuideBuilderE2EProbe.Anchor anchor) throws IOException {
        if (!dev.openallay.util.Java8Collections.listOf("builder-acceptance", "builder-reload", "builder-live-copy", "builder-live-undo").contains(config.scenario())) return anchor;
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        net.minecraft.client.server.IntegratedServer server = dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client);
        if (server == null) throw new IllegalStateException("Integrated server is unavailable");
        String world = dev.openallay.server.NativeServerOwner.worldName(server);
        if (!world.matches("openallay-builder-[a-zA-Z0-9_.-]+")) throw new IllegalStateException("Not a disposable acceptance world");
        java.nio.file.Path retained = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay/e2e")
                .resolve(world + ".anchor.json");
        if (config.scenario().equals("builder-reload") || config.scenario().equals("builder-live-undo")) {
            dev.openallay.guide.e2e.GuideBuilderE2EProbe.Anchor persisted = gson.fromJson(dev.openallay.util.Java8Files.readString(retained), GuideBuilderE2EProbe.Anchor.class);
            String suffix = config.scenario().equals("builder-reload") ? ".acceptance.json" : ".live-copy.json";
            com.google.gson.JsonObject proof = dev.openallay.json.JsonTrees.parse(dev.openallay.util.Java8Files.readString(retained.resolveSibling(world + suffix))).getAsJsonObject();
            return GuideBuilderE2EProbe.retainedOrigin(anchor, persisted, proof, world);
        }
        writeAtomically(retained, gson.toJson(anchor));
        return anchor;
    }

    private java.nio.file.Path builderProofPath(String suffix) {
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        String world = dev.openallay.server.NativeServerOwner.worldName(dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client));
        return dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay/e2e").resolve(world + suffix);
    }

    private static com.google.gson.JsonObject builderReceipt(GuideRequestSnapshot request) {
        return GuideBuilderE2EProbe.builderReceipt(request);
    }

    private void retainAcceptancePersistence(GuideRequestSnapshot request, com.google.gson.JsonObject probe) throws IOException {
        if (!config.scenario().equals("builder-acceptance") || !"PASSED".equals(probe.get("outcome").getAsString())) return;
        com.google.gson.JsonObject preview = builderReceipt(request);
        com.google.gson.JsonObject receipt = new com.google.gson.JsonObject();
        receipt.addProperty("outcome", "PASSED");
        receipt.addProperty("requestId", request.requestId().toString());
        receipt.add("worldName", probe.get("worldName"));
        receipt.add("nativeAnchor", probe.get("independentAnchor"));
        receipt.add("operations", preview.get("operations"));
        receipt.add("skipped", preview.get("skipped"));
        receipt.add("lifecycle", preview.get("lifecycle"));
        receipt.add("templates", preview.get("templates"));
        writeAtomically(builderProofPath(".acceptance.json"), gson.toJson(receipt));
    }

    private void verifyReloadPersistence(GuideRequestSnapshot request, com.google.gson.JsonObject probe) throws IOException {
        com.google.gson.JsonObject retained = dev.openallay.json.JsonTrees.parse(dev.openallay.util.Java8Files.readString(builderProofPath(".acceptance.json"))).getAsJsonObject();
        if (!"PASSED".equals(retained.get("outcome").getAsString())) throw new IllegalStateException("Reload lacks prior independently passed acceptance receipt");
        boolean matched = GuideBuilderE2EProbe.persistedOperationsMatch(retained, builderReceipt(request));
        probe.addProperty("exactPersistencePassed", matched);
        if (!matched) probe.addProperty("outcome", "FAILED");
    }

    private void retainLiveCopyProof(com.google.gson.JsonObject probe) throws IOException {
        if (!config.scenario().equals("builder-live-copy")) return;
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        String world = dev.openallay.server.NativeServerOwner.worldName(dev.openallay.client.gui.MinecraftClientWindow.integratedServer(client));
        java.nio.file.Path path = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("config/openallay/e2e").resolve(world + ".live-copy.json");
        writeAtomically(path, gson.toJson(probe));
    }

    private void selectSession(GuideService service) {
        service.selectSession(config.sessionId()).thenAccept(selected -> {
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<java.lang.String> value; ToolResult.Failure<String> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = selected) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<String>) $oaPattern0_holder.value) != null))) {
                failWithoutRequest($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
            } else {
                selectMode(service);
            }
        });
    }

    private void tickWorldLaunch() {
        String create = System.getProperty("openallay.e2e.createWorld", "");
        String resume = System.getProperty("openallay.e2e.resumeWorld", "");
        String name = dev.openallay.util.Java8Strings.isBlank(create) ? resume : create;
        if (dev.openallay.util.Java8Strings.isBlank(name) || worldLaunchStarted) return;
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        if (MinecraftClientWindow.overlayPresent(client)
                || !(MinecraftClientWindow.screen(client) instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
        worldLaunchStarted = true;
        if (graphicalScenario(config.scenario())) {
            if (dev.openallay.util.Java8Strings.isBlank(create) || !dev.openallay.util.Java8Strings.isBlank(resume)) {
                failWithoutRequest("fresh_world_required", "Graphical acceptance requires a new disposable world");
                return;
            }
            java.nio.file.Path saves = dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("saves");
            try (java.util.stream.Stream<java.nio.file.Path> savedWorlds = Files.isDirectory(saves) ? Files.list(saves) : java.util.stream.Stream.<java.nio.file.Path>empty()) {
                if (savedWorlds.findAny().isPresent()) {
                    failWithoutRequest("fresh_world_required", "Graphical acceptance requires an empty isolated saves directory");
                    return;
                }
            } catch (IOException failure) {
                failWithoutRequest("fresh_world_required", "Unable to verify the isolated saves directory");
                return;
            }
        }
        boolean existing = Files.isDirectory(dev.openallay.client.gui.MinecraftClientWindow.gameDirectory(client).resolve("saves").resolve(name));
        if (!name.matches("openallay-builder-[a-zA-Z0-9_.-]+")
                || (!dev.openallay.util.Java8Strings.isBlank(create) && (!dev.openallay.util.Java8Strings.isBlank(resume) || existing))
                || (dev.openallay.util.Java8Strings.isBlank(create) && (!(dev.openallay.util.Java8Collections.listOf("builder-reload", "builder-live-undo").contains(config.scenario())
                        || "native-world-sdk".equals(config.scenario()) && "reload".equals(System.getProperty("openallay.e2e.worldPhase", ""))) || !existing))) {
            failWithoutRequest("unsafe_world_name", "Acceptance requires a new disposable world or an explicitly resumed Builder reload world");
            return;
        }
        if (dev.openallay.util.Java8Strings.isBlank(create)) {
            GuideProbeWorldSettings.open(client, name, () -> failWithoutRequest(
                    "world_reload_cancelled", "The native world reload did not complete"));
            return;
        }
        GuideProbeWorldSettings.createFresh(client, name);
        if (graphicalScenario(config.scenario())) graphicalFreshWorldName = name;
    }

    public boolean finished() {
        return finished;
    }

    private void selectMode(GuideService service) {
        service.setModelMode(config.modelMode()).thenAccept(mode -> {
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.GuideModelMode> value; ToolResult.Failure<?> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = mode) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<?>) $oaPattern1_holder.value) != null))) {
                failWithoutRequest($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
            } else {
                if (graphicalScenario(config.scenario())) {
                    dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                        if (!developmentProbeEnabled || clientSettings == null || graphicalOpenGuide == null
                                || ("ui-live-ux-regressions".equals(config.scenario()) && graphicalToastReceipt == null)) {
                            failWithoutRequest("graphical_probe_unattached", "The actual client UI is unavailable");
                            return;
                        }
                        try {
                            graphicalProbe = new GuideGraphicalRegressionProbe(config, loader, gameVersion, modVersion,
                                    service, clientSettings, gson, graphicalOpenGuide, graphicalHudReceipt,
                                    graphicalVoiceSettings, graphicalToastReceipt, traceLookup, report -> {
                                        finish(gson.toJson(report));
                                        if (!config.shutdownAfterReport()
                                                && Boolean.getBoolean("openallay.e2e.shutdownAfterScreenshots")) shutdown.run();
                                    });
                        } catch (RuntimeException failure) {
                            failWithoutRequest("graphical_probe_start_failed", failure.toString());
                        }
                    });
                    return;
                }
                remainingHistorySeeds = config.historySeedRequests();
                if (remainingHistorySeeds > 0) {
                    seedingHistory = true;
                    seedHistory(service);
                } else {
                    ask(service);
                }
            }
        });
    }

    private void seedHistory(GuideService service) {
        service.ask("OpenAllay E2E 历史分页种子 " + remainingHistorySeeds).thenAccept(asked -> {
            final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<java.util.UUID> value; ToolResult.Failure<UUID> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = asked) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern2_holder.bound = (ToolResult.Failure<UUID>) $oaPattern2_holder.value) != null))) {
                failWithoutRequest($oaPattern2_holder.bound.code(), $oaPattern2_holder.bound.message());
                return;
            }
            requestId = ((ToolResult.Success<UUID>) asked).value();
            observe(service.snapshot());
        });
    }

    private void ask(GuideService service) {
        openScreenForScreenshotProbe(service);
        String question = config.question();
        if (config.scenario().equals("builder-reload") || config.scenario().equals("builder-live-undo")) {
            question += "\n" + GuideBuilderE2EProbe.retainedOriginLine(builderAnchor);
        }
        service.ask(question).thenAccept(asked -> {
            final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<java.util.UUID> value; ToolResult.Failure<UUID> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = asked) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern3_holder.bound = (ToolResult.Failure<UUID>) $oaPattern3_holder.value) != null))) {
                failWithoutRequest($oaPattern3_holder.bound.code(), $oaPattern3_holder.bound.message());
                return;
            }
            requestId = ((ToolResult.Success<UUID>) asked).value();
            observe(service.snapshot());
        });
    }

    private void observe(GuideSnapshot snapshot) {
        if (finished || requestId == null) return;
        GuideRequestSnapshot request = snapshot.sessions().stream()
                .flatMap(value -> value.requests().stream())
                .filter(value -> value.requestId().equals(requestId))
                .findFirst().orElse(null);
        if (request == null) return;
        if (transitions.isEmpty() || transitions.get(transitions.size() - 1) != request.status()) {
            transitions.add(request.status());
        }
        if (Boolean.getBoolean("openallay.e2e.cancelOnToolStart") && !cancelOnToolStartRequested
                && !request.terminal() && request.tools().stream().anyMatch(value ->
                        value.toolId().equals("openallay:run_javascript")
                                && value.status() == dev.openallay.guide.GuideToolStatus.RUNNING)) {
            cancelOnToolStartRequested = true;
            cancelOnToolStartPending = true;
            GuideService service = services.forActor(snapshot.actorId());
            service.cancel().thenAccept(cancelled -> {
                cancelOnToolStartPending = false;
                final class $oaPattern4_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Success<Boolean> bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
cancelOnToolStartAccepted = (($oaPattern4_holder.value = cancelled) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern4_holder.bound = (ToolResult.Success<Boolean>) $oaPattern4_holder.value) != null)) && $oaPattern4_holder.bound.value();
                if (!cancelOnToolStartAccepted) {
                    failWithoutRequest("stop_request_failed", "The actual guide cancellation was not accepted");
                } else observe(service.snapshot());
            });
        }
        if (!request.terminal() || nativeProbePending || pendingReport != null || cancelOnToolStartPending) return;
        if (seedingHistory) {
            requestId = null;
            remainingHistorySeeds--;
            if (remainingHistorySeeds > 0) {
                seedHistory(services.forActor(snapshot.actorId()));
            } else {
                seedingHistory = false;
                transitions.clear();
                startedAt = Instant.now();
                ask(services.forActor(snapshot.actorId()));
            }
            return;
        }
        LinkedHashMap<String, Long> timings = new LinkedHashMap<>();
        timings.put("total", Duration.between(startedAt, request.terminalAt()).toMillis());
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("assistantTextSha256", sha256(request.assistantText()));
        hashes.put("userMessageSha256", sha256(request.userMessage()));
        GuideSessionSnapshot session = dev.openallay.util.Java8ApiSupport.orElseThrow(snapshot.sessions().stream()
                .filter(value -> value.sessionId().equals(request.sessionId()))
                .findFirst());
        SemanticSummary semantic = summarize(request);
        SemanticLayoutCache.Stats cache = SemanticLayoutCache.globalStats();
        LinkedHashMap<String, Long> historyMetrics = new LinkedHashMap<>();
        historyMetrics.put("loadedRequests", (long) session.requests().size());
        historyMetrics.put("totalRequests", session.historyWindow().totalRequests());
        historyMetrics.put("hasEarlier", session.historyWindow().hasEarlier() ? 1L : 0L);
        historyMetrics.put("hasLater", session.historyWindow().hasLater() ? 1L : 0L);
        historyMetrics.put("cacheHits", cache.hits());
        historyMetrics.put("cacheMisses", cache.misses());
        GuideE2EReport report = new GuideE2EReport(
                loader,
                gameVersion,
                modVersion,
                config.scenario(),
                request.topology(),
                request.requestId(),
                request.sessionId(),
                transitions,
                dev.openallay.util.Java8Collections.toList(request.tools().stream().map(value -> value.toolId())),
                dev.openallay.util.Java8Collections.toList(request.tools().stream().map(GuideClientE2EController::toolProbe)),
                dev.openallay.util.Java8Collections.toList(request.sources().stream().map(value -> value.evidence())),
                dev.openallay.util.Java8Collections.toList(request.timeline().stream().map(value -> {
                    java.util.Objects.requireNonNull(value);
                    final class $oaPattern5_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.User bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = value) instanceof dev.openallay.guide.GuideTimelineEntry.User && (($oaPattern5_holder.bound = (GuideTimelineEntry.User) $oaPattern5_holder.value) != null))) {
                        return "user";
                    } else {
final class $oaPattern6_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = value) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern6_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern6_holder.value) != null))) {
                        return "assistant";
                    } else {
final class $oaPattern7_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Tool bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = value) instanceof dev.openallay.guide.GuideTimelineEntry.Tool && (($oaPattern7_holder.bound = (GuideTimelineEntry.Tool) $oaPattern7_holder.value) != null))) {
                        return "tool";
                    }
}
}
                    throw new IncompatibleClassChangeError();
                })),
                semantic.metrics(),
                semantic.diagnosticCodes(),
                semantic.componentTypes(),
                session.historyWindow().state().name(),
                historyMetrics,
                request.status(),
                request.failure() == null ? null : request.failure().code(),
                request.failure() == null ? null : request.failure().message(),
                timings,
                hashes);
        if (!config.shutdownAfterReport()) {
            screenshotActor = snapshot.actorId();
            if (professionalScreenshots() && clientSettings != null)
                screenshotOriginalDisplay = clientSettings.snapshot().display();
            dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
                originalWindowWidth = dev.openallay.client.gui.MinecraftClientWindow.framebufferWidth(client);
                originalWindowHeight = dev.openallay.client.gui.MinecraftClientWindow.framebufferHeight(client);
                MinecraftClientWindow.setScreen(client, screenshotGuide(services.forActor(snapshot.actorId())));
                if (!dev.openallay.util.Java8Strings.isBlank(System.getProperty("openallay.e2e.screenshotRoot", ""))) {
                    screenshotStage = 0;
                    screenshotTicks = 0;
                }
            });
        }
        pendingReport = new GuideE2EReportJson(gson).encode(report);
        if (Boolean.getBoolean("openallay.e2e.cancelOnToolStart")) {
            com.google.gson.JsonObject stop = new com.google.gson.JsonObject();
            stop.addProperty("requested", cancelOnToolStartRequested);
            stop.addProperty("accepted", cancelOnToolStartAccepted);
            stop.addProperty("terminalCancelled", request.status() == GuideRequestStatus.CANCELLED);
            stop.addProperty("pendingToolHasNoNormalizedResult", request.tools().stream().anyMatch(value ->
                    value.toolId().equals("openallay:run_javascript")
                            && value.status() == dev.openallay.guide.GuideToolStatus.RUNNING
                            && value.normalized() == null));
            com.google.gson.JsonObject retained = dev.openallay.json.JsonTrees.parse(pendingReport).getAsJsonObject();
            retained.add("actualStop", stop);
            pendingReport = gson.toJson(retained);
        }
        pendingTraceProfile = request.modelSelection().profileId();
        if (GuideBuilderE2EProbe.enabled(config.scenario())) {
            nativeProbePending = true;
            GuideBuilderE2EProbe.verify(config.scenario(), snapshot.actorId(), builderAnchor,
                    request, clientSettings, unrestrictedAtStart, probe -> {
                nativeProbePending = false;
                if (nativeCommandWarmup != null) {
                    probe.add("nativeCommandWarmup", nativeCommandWarmup);
                    if (!"PASSED".equals(nativeCommandWarmup.get("outcome").getAsString()))
                        probe.addProperty("outcome", "FAILED");
                }
                if (currentPlayerAnchor != null) probe.add("currentPlayerAnchor", gson.toJsonTree(currentPlayerAnchor));
                if (config.scenario().equals("builder-reload") || config.scenario().equals("builder-live-undo"))
                    probe.addProperty("originSource", "prior-passed-independent-native-receipt");
                if (config.scenario().equals("builder-reload")) {
                    try { verifyReloadPersistence(request, probe); }
                    catch (IOException | RuntimeException failure) { probe.addProperty("outcome", "FAILED"); probe.addProperty("persistenceFailure", failure.toString()); }
                }
                try { retainAcceptancePersistence(request, probe); retainLiveCopyProof(probe); }
                catch (IOException | RuntimeException failure) { probe.addProperty("outcome", "FAILED"); probe.addProperty("proofFailure", failure.toString()); }
                com.google.gson.JsonObject encoded = dev.openallay.json.JsonTrees.parse(pendingReport).getAsJsonObject();
                encoded.add("nativeAcceptance", probe);
                pendingReport = gson.toJson(encoded);
                if (traceLookup == null || request.modelSelection().modelMode() != GuideModelMode.CLIENT) finish(pendingReport);
            });
        } else if (traceLookup == null || request.modelSelection().modelMode() != GuideModelMode.CLIENT) {
            finish(pendingReport);
        }
    }

    private void finishWithTrace() {
        java.util.Optional<String> trace = traceLookup.apply(pendingTraceProfile, requestId);
        if (trace.isPresent()) {
            try {
                writeAtomically(config.tracePath(), dev.openallay.util.Java8ApiSupport.orElseThrow(trace));
            } catch (IOException failure) {
                failWithoutRequest(
                        "trace_write_failed",
                        "Unable to retain the complete Agent trace");
                return;
            }
            finish(pendingReport);
            return;
        }
        if (++traceWaitTicks > 200) {
            failWithoutRequest(
                    "trace_unavailable",
                    "The complete Agent trace was not published");
        }
    }

    private void tickScreenshotProbe() {
        if (screenshotStage < 0 || ++screenshotTicks < 8) return;
        screenshotTicks = 0;
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        if (screenshotStage <= 7
                && !(MinecraftClientWindow.screen(client) instanceof OpenAllayScreen)) return;
        final class $oaPattern8_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllayScreen bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
OpenAllayScreen screen = (($oaPattern8_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllayScreen && (($oaPattern8_holder.bound = (OpenAllayScreen) $oaPattern8_holder.value) != null))
                ? $oaPattern8_holder.bound : null;
        switch ((screenshotStage++)) {
case 0:
{
screen.positionForDevelopmentProbe(0.0D);
break;
}
case 1:
{
{
                screenshot(client, "01-wide-top.png");
                screen.positionForDevelopmentProbe(0.5D);
            }
break;
}
case 2:
{
{
                screenshot(client, "02-wide-middle.png");
                screen.positionForDevelopmentProbe(1.0D);
            }
break;
}
case 3:
{
{
                screenshot(client, "03-wide-final.png");
                int tools = screen.toolCountForDevelopmentProbe();
                if (professionalScreenshots()) {
                    screen.selectLatestJavascriptForDevelopmentProbe();
                } else if (tools > 0) {
                    // Durable E2E history may contain older interrupted requests. Select a
                    // terminal card near the end of the current chronology so the retained
                    // screenshot demonstrates a populated result rather than stale progress.
                    screen.selectToolForDevelopmentProbe(Math.max(0, tools - 2));
                }
            }
break;
}
case 4:
{
{
                screenshot(client, "04-wide-tool-detail.png");
                screen.openModelSelectorForDevelopmentProbe();
            }
break;
}
case 5:
{
{
                screenshot(client, "05-wide-model-selector.png");
                screen.closeModelSelectorForDevelopmentProbe();
                MinecraftClientWindow.setWindowed(client, 640, 480);
            }
break;
}
case 6:
{
screenshot(client, "06-narrow-tool-detail.png");
break;
}
case 7:
{
{
                MinecraftClientWindow.setWindowed(client, originalWindowWidth, originalWindowHeight);
                if (clientSettings == null) {
                    finishScreenshotProbe();
                    break;
                }
                OpenAllaySettingsScreen settings = new OpenAllaySettingsScreen(
                        clientSettings, () -> {});
                MinecraftClientWindow.setScreen(client, settings);
                if (professionalScreenshots()) settings.e2eSelectExtension("openallay:builder");
                else settings.e2eOpenExtensions();
            }
break;
}
case 8:
{
{
                screenshot(client, professionalScreenshots() ? "07-wide-builder-extension.png" : "07-wide-tool-settings.png");
                final class $oaPattern9_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllaySettingsScreen bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllaySettingsScreen && (($oaPattern9_holder.bound = (OpenAllaySettingsScreen) $oaPattern9_holder.value) != null))) {
                    $oaPattern9_holder.bound.e2eScrollExtensionDetails(320);
                }
            }
break;
}
case 9:
{
{
                screenshot(client, professionalScreenshots() ? "08-wide-builder-extension-lower.png" : "08-wide-tool-settings-lower.png");
                final class $oaPattern10_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllaySettingsScreen bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllaySettingsScreen && (($oaPattern10_holder.bound = (OpenAllaySettingsScreen) $oaPattern10_holder.value) != null))) {
                    $oaPattern10_holder.bound.e2eOpenGeneral(professionalScreenshots()
                            ? clientSettings.snapshot().display().assistantName() : "小羽");
                }
            }
break;
}
case 10:
{
{
                screenshot(client, "09-wide-general-settings.png");
                final class $oaPattern11_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllaySettingsScreen bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllaySettingsScreen && (($oaPattern11_holder.bound = (OpenAllaySettingsScreen) $oaPattern11_holder.value) != null))) {
                    $oaPattern11_holder.bound.e2eOpenAbout();
                }
            }
break;
}
case 11:
{
{
                screenshot(client, "10-wide-about.png");
                if (professionalScreenshots()) {
                    final class $oaPattern12_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllaySettingsScreen bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllaySettingsScreen && (($oaPattern12_holder.bound = (OpenAllaySettingsScreen) $oaPattern12_holder.value) != null))) $oaPattern12_holder.bound.e2eScrollPageBottom();
                } else if (!GuideBuilderE2EProbe.enabled(config.scenario())) {
                    finishScreenshotProbe();
                } else {
                    MinecraftClientWindow.setScreen(client, null);
                    if (client.player != null) {
                        dev.openallay.client.MinecraftPlayerRotation.yaw(client.player, -45.0F);
                        dev.openallay.client.MinecraftPlayerRotation.pitch(client.player, -12.0F);
                    }
                }
            }
break;
}
case 12:
{
{
                if (!professionalScreenshots()) {
                    screenshot(client, "11-native-world-builds.png");
                    finishScreenshotProbe();
                } else {
                    screenshot(client, "11-about-bottom.png");
                    OpenAllaySettingsScreen settings = screenshotSettings(client);
                    String profile = requiredScreenshotProperty("openallay.e2e.screenshotManualProfile");
                    requireScreenshotProfile(profile, false);
                    settings.e2eOpenModels(profile);
                }
            }
break;
}
case 13:
{
{
                screenshot(client, "12-models-manual-context.png");
                String profile = requiredScreenshotProperty("openallay.e2e.screenshotAutomaticProfile");
                requireScreenshotProfile(profile, true);
                ((OpenAllaySettingsScreen) MinecraftClientWindow.screen(client)).e2eOpenModels(profile);
            }
break;
}
case 14:
{
{
                screenshot(client, "13-models-automatic-reference.png");
                ((OpenAllaySettingsScreen) MinecraftClientWindow.screen(client)).e2eScrollPageBottom();
            }
break;
}
case 15:
{
{
                screenshot(client, "14-models-automatic-reference-bottom.png");
                dev.openallay.client.gui.OpenAllaySettingsScreen settings = (OpenAllaySettingsScreen) MinecraftClientWindow.screen(client);
                settings.e2eOpenGeneral(clientSettings.snapshot().display().assistantName());
            }
break;
}
case 16:
{
{
                ((OpenAllaySettingsScreen) MinecraftClientWindow.screen(client)).e2eScrollPageBottom();
            }
break;
}
case 17:
{
{
                screenshot(client, "15-general-bottom.png");
                ((OpenAllaySettingsScreen) MinecraftClientWindow.screen(client)).e2eOpenAbout();
            }
break;
}
case 18:
{
((OpenAllaySettingsScreen) MinecraftClientWindow.screen(client)).e2eScrollPageBottom();
break;
}
case 19:
{
{
                screenshot(client, "16-about-bottom.png");
                screenshotDebug(false, () -> {
                    dev.openallay.client.gui.OpenAllayScreen guide = screenshotGuide(services.forActor(screenshotActor));
                    MinecraftClientWindow.setScreen(client, guide);
                    guide.selectLatestJavascriptForDevelopmentProbe();
                });
            }
break;
}
case 20:
{
{
                screenshot(client, "17-normal-javascript-intent-detail.png");
                final class $oaPattern13_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllayScreen bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
screenshotSourceAvailable = (($oaPattern13_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllayScreen && (($oaPattern13_holder.bound = (OpenAllayScreen) $oaPattern13_holder.value) != null))
                        && $oaPattern13_holder.bound.selectLatestSourceForDevelopmentProbe();
                if (!screenshotSourceAvailable)
                    System.out.println("OpenAllay E2E source detail: no actual source in this request");
            }
break;
}
case 21:
{
{
                if (!screenshotSourceAvailable) {
                    System.out.println("OpenAllay E2E normal source screenshot skipped: no actual source");
                } else screenshot(client, "18-normal-source-detail.png");
                screenshotDebug(true, () -> {
                    dev.openallay.client.gui.OpenAllayScreen guide = screenshotGuide(services.forActor(screenshotActor));
                    MinecraftClientWindow.setScreen(client, guide);
                    guide.selectLatestJavascriptForDevelopmentProbe();
                });
            }
break;
}
case 22:
{
{
                screenshot(client, "19-debug-javascript-detail.png");
                ((OpenAllayScreen) MinecraftClientWindow.screen(client)).scrollDetailToBottomForDevelopmentProbe();
            }
break;
}
case 23:
{
{
                screenshot(client, "20-debug-javascript-detail-bottom.png");
                screenshotSourceAvailable = ((OpenAllayScreen) MinecraftClientWindow.screen(client)).selectLatestSourceForDevelopmentProbe();
                if (!screenshotSourceAvailable)
                    System.out.println("OpenAllay E2E debug source screenshot skipped: no actual source");
            }
break;
}
case 24:
{
{
                if (screenshotSourceAvailable)
                    screenshot(client, "21-debug-source-detail.png");
                screenshotSettings(client).e2eSelectExtension("openallay:builder");
            }
break;
}
case 25:
{
{
                screenshot(client, "22-builder-installed-detail.png");
                String jar = System.getProperty("openallay.e2e.reviewPackage", "");
                if (dev.openallay.util.Java8Strings.isBlank(jar)) { screenshotStage = 28; break; }
                screenshotActionPending = true;
                clientSettings.importLocalExtensionPackage(java.nio.file.Paths.get(jar)).thenAccept(prepared ->
                        dev.openallay.client.gui.MinecraftClientWindow.execute(client, () -> {
                            screenshotActionPending = false;
                            final class $oaPattern14_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = prepared) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern14_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern14_holder.value) != null)))
                                screenshotReviewFailure = $oaPattern14_holder.bound.code();
                        }));
            }
break;
}
case 26:
{
{
                if (screenshotReviewFailure != null) {
                    System.out.println("OpenAllay E2E package review failed: " + screenshotReviewFailure);
                    screenshot(client, "23-builder-review-failed.png");
                    screenshotStage = 28;
                } else if (MinecraftClientWindow.screen(client) instanceof dev.openallay.client.gui.RequirementReviewScreen) {
                    screenshot(client, "23-builder-advisory-review.png");
                    screenshotWaitTicks = 0;
                } else if (++screenshotWaitTicks > 100) {
                    throw new IllegalStateException("Actual validated package review did not open");
                } else screenshotStage = 26;
            }
break;
}
case 27:
{
{
                final class $oaPattern15_Holder { net.minecraft.client.gui.screens.Screen value; dev.openallay.client.gui.RequirementReviewScreen bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.RequirementReviewScreen && (($oaPattern15_holder.bound = (dev.openallay.client.gui.RequirementReviewScreen) $oaPattern15_holder.value) != null))) $oaPattern15_holder.bound.onClose();
                else throw new IllegalStateException("Actual package review is unavailable for cancellation");
            }
break;
}
case 28:
{
{
                screenshot(client, "24-builder-review-cancelled.png");
                MinecraftClientWindow.setScreen(client, null);
                if (client.player != null) {
                    dev.openallay.client.MinecraftPlayerRotation.yaw(client.player, -45.0F);
                    dev.openallay.client.MinecraftPlayerRotation.pitch(client.player, -12.0F);
                }
            }
break;
}
case 29:
{
{
                screenshot(client, "25-native-world-final.png");
                finishScreenshotProbe();
            }
break;
}
default:
{
finishScreenshotProbe();
break;
}
}

    }

    static boolean professionalScreenshots() {
        return Boolean.getBoolean(GuideClientE2EConfig.ENABLED)
                && "professional".equals(System.getProperty("openallay.e2e.screenshotMatrix", ""));
    }

    private static String requiredScreenshotProperty(String key) {
        String value = System.getProperty(key, "");
        if (dev.openallay.util.Java8Strings.isBlank(value)) throw new IllegalStateException("An explicit screenshot profile is required");
        return value;
    }

    private void requireScreenshotProfile(String id, boolean automatic) {
        dev.openallay.model.config.ModelProfileDefinition profile = dev.openallay.util.Java8ApiSupport.orElseThrow(clientSettings.snapshot().models().config().profiles().stream()
                .filter(value -> value.id().equals(id)).findFirst());
        if (automatic) {
            if (profile.enabled() || profile.contextWindowTokens() != null
                    || dev.openallay.util.Java8ApiSupport.isEmpty(dev.openallay.model.metadata.BuiltinModelCatalog.bundled().catalog().match(profile.model())))
                throw new IllegalStateException("Automatic screenshot profile must be a disabled known public model without a manual context value");
        } else if (!Integer.valueOf(1_000_000).equals(profile.contextWindowTokens())) {
            throw new IllegalStateException("Manual screenshot profile must retain the user's explicit one-million-token context");
        }
    }

    private OpenAllayScreen screenshotGuide(GuideService service) {
        return clientSettings == null ? new OpenAllayScreen(service)
                : new OpenAllayScreen(service, dev.openallay.recipe.config.RecipeClientRuntime.defaults(),
                        clientSettings.snapshot().display());
    }

    private OpenAllaySettingsScreen screenshotSettings(net.minecraft.client.Minecraft client) {
        OpenAllaySettingsScreen settings = new OpenAllaySettingsScreen(clientSettings, () -> {});
        MinecraftClientWindow.setScreen(client, settings);
        return settings;
    }

    private void screenshotDebug(boolean enabled, Runnable afterSave) {
        if (screenshotOriginalDisplay == null || clientSettings == null)
            throw new IllegalStateException("Actual display settings are unavailable");
        screenshotActionPending = true;
        dev.openallay.guide.ui.GuideDisplayConfig current = clientSettings.snapshot().display();
        dev.openallay.guide.ui.GuideDisplayConfig replacement = new dev.openallay.guide.ui.GuideDisplayConfig(enabled,
                current.animationsEnabled(), current.assistantName());
        clientSettings.saveDisplay(replacement).thenAccept(saved -> dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
            screenshotActionPending = false;
            if (saved instanceof ToolResult.Failure<Boolean>) {
                System.err.println("OpenAllay E2E screenshot display save failed");
                finishScreenshotProbe();
            } else {
                try { afterSave.run(); }
                catch (RuntimeException failure) {
                    System.err.println("OpenAllay E2E screenshot navigation failed");
                    finishScreenshotProbe();
                }
            }
        }));
    }

    private void finishScreenshotProbe() {
        screenshotStage = -1;
        if (clientSettings != null && screenshotOriginalDisplay != null
                && !clientSettings.snapshot().display().equals(screenshotOriginalDisplay)) {
            screenshotActionPending = true;
            clientSettings.saveDisplay(screenshotOriginalDisplay).thenAccept(saved ->
                    dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                        screenshotActionPending = false;
                        if (saved instanceof ToolResult.Failure<Boolean>)
                            System.err.println("OpenAllay E2E original display restoration failed");
                        else System.out.println("OpenAllay E2E original display restored");
                        screenshotOriginalDisplay = null;
                        if (Boolean.getBoolean("openallay.e2e.shutdownAfterScreenshots")) shutdown.run();
                    }));
            return;
        }
        if (Boolean.getBoolean("openallay.e2e.shutdownAfterScreenshots")) shutdown.run();
    }

    private void tickActiveScreenshotProbe() {
        if (activeScreenshotCaptured
                || requestId == null
                || dev.openallay.util.Java8Strings.isBlank(System.getProperty("openallay.e2e.screenshotRoot", ""))
                || ++activeScreenshotTicks < 4) {
            return;
        }
        net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
        final class $oaPattern16_Holder { net.minecraft.client.gui.screens.Screen value; OpenAllayScreen bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if (!MinecraftClientWindow.overlayPresent(client)
                && (($oaPattern16_holder.value = MinecraftClientWindow.screen(client)) instanceof dev.openallay.client.gui.OpenAllayScreen && (($oaPattern16_holder.bound = (OpenAllayScreen) $oaPattern16_holder.value) != null))
                && $oaPattern16_holder.bound.hasRenderedActiveProgressForDevelopmentProbe()) {
            // A tick projection becomes visible in the framebuffer only after a later render.
            // Retained evidence must show the strip, not the frame immediately before it.
            if (++activeProgressVisibleTicks >= 3) {
                activeScreenshotCaptured = true;
                screenshot(client, "00-active-progress.png");
            }
        } else {
            activeProgressVisibleTicks = 0;
        }
    }

    private void openScreenForScreenshotProbe(GuideService service) {
        if (config.shutdownAfterReport()
                || dev.openallay.util.Java8Strings.isBlank(System.getProperty("openallay.e2e.screenshotRoot", ""))) {
            return;
        }
        dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
            net.minecraft.client.Minecraft client = dev.openallay.client.gui.MinecraftClientWindow.instance();
            originalWindowWidth = dev.openallay.client.gui.MinecraftClientWindow.framebufferWidth(client);
            originalWindowHeight = dev.openallay.client.gui.MinecraftClientWindow.framebufferHeight(client);
            MinecraftClientWindow.setScreen(client, screenshotGuide(service));
        });
    }

    private static void screenshot(net.minecraft.client.Minecraft client, String name) {
        java.io.File root = new java.io.File(
                System.getProperty("openallay.e2e.screenshotRoot"));
        root.mkdirs();
        GuideNativeScreenshot.grab(
                root,
                name,
                MinecraftClientWindow.mainRenderTarget(client),
                component -> System.out.println("OpenAllay E2E screenshot: "
                        + dev.openallay.platform.minecraft.MinecraftComponents.getString(component)));
    }

    private static SemanticSummary summarize(GuideRequestSnapshot request) {
        long assistants = 0;
        long blocks = 0;
        long components = 0;
        long fallbacks = 0;
        TreeSet<String> diagnostics = new TreeSet<>();
        TreeSet<String> componentTypes = new TreeSet<>();
        for (GuideTimelineEntry entry : request.timeline()) {
            final class $oaPattern17_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if (!((($oaPattern17_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern17_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern17_holder.value) != null)))) continue;
            assistants++;
            fallbacks += $oaPattern17_holder.bound.semantic().diagnostics().size();
            $oaPattern17_holder.bound.semantic().diagnostics().forEach(value -> diagnostics.add(value.code()));
            Counter counter = new Counter();
            for (SemanticBlock block : $oaPattern17_holder.bound.semantic().blocks()) {
                collect(block, counter, componentTypes);
            }
            blocks += counter.blocks;
            components += counter.components;
        }
        LinkedHashMap<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("assistantSegments", assistants);
        metrics.put("toolInvocations", (long) request.tools().size());
        metrics.put("semanticBlocks", blocks);
        metrics.put("controlledComponents", components);
        metrics.put("semanticFallbacks", fallbacks);
        return new SemanticSummary(
                metrics, dev.openallay.util.Java8Collections.listCopyOf(diagnostics), dev.openallay.util.Java8Collections.listCopyOf(componentTypes));
    }

    private static GuideE2EReport.ToolProbe toolProbe(dev.openallay.guide.GuideToolActivity activity) {
        com.google.gson.JsonObject normalized = activity.normalized();
        String section = null;
        String failureCode = null;
        if (normalized != null) {
            if (normalized.has("value") && normalized.get("value").isJsonObject()) {
                com.google.gson.JsonObject value = normalized.getAsJsonObject("value");
                if (value.has("section") && value.get("section").isJsonPrimitive()) {
                    section = value.get("section").getAsString();
                }
            }
            if (normalized.has("code") && normalized.get("code").isJsonPrimitive()) {
                failureCode = normalized.get("code").getAsString();
            }
        }
        return new GuideE2EReport.ToolProbe(
                activity.toolId(), activity.status(), section, failureCode);
    }

    private static void collect(
            SemanticBlock block, Counter counter, Set<String> componentTypes) {
        counter.blocks++;
        java.util.Objects.requireNonNull(block);
        final class $oaPattern18_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern18_holder.bound = (SemanticBlock.ListBlock) $oaPattern18_holder.value) != null))) {
            $oaPattern18_holder.bound.items().forEach(
                    item -> item.forEach(child -> collect(child, counter, componentTypes)));
        } else {
final class $oaPattern19_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern19_holder.bound = (SemanticBlock.Quote) $oaPattern19_holder.value) != null))) {
            $oaPattern19_holder.bound.content().forEach(
                    child -> collect(child, counter, componentTypes));
        } else {
final class $oaPattern20_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern20_holder.bound = (SemanticBlock.Component) $oaPattern20_holder.value) != null))) {
            counter.components++;
            componentTypes.add(componentType($oaPattern20_holder.bound.component()));
        }
}
}
    }

    private static String componentType(RichComponent component) {
        java.util.Objects.requireNonNull(component);
        final class $oaPattern21_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern21_holder.bound = (RichComponent.ItemRow) $oaPattern21_holder.value) != null))) {
            return "item_row";
        } else {
final class $oaPattern22_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern22_Holder $oaPattern22_holder = new $oaPattern22_Holder();
if ((($oaPattern22_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern22_holder.bound = (RichComponent.RecipeGrid) $oaPattern22_holder.value) != null))) {
            return "recipe_grid";
        } else {
final class $oaPattern23_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern23_Holder $oaPattern23_holder = new $oaPattern23_Holder();
if ((($oaPattern23_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern23_holder.bound = (RichComponent.IngredientCheck) $oaPattern23_holder.value) != null))) {
            return "ingredient_check";
        } else {
final class $oaPattern24_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern24_Holder $oaPattern24_holder = new $oaPattern24_Holder();
if ((($oaPattern24_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern24_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern24_holder.value) != null))) {
            return "craftability_summary";
        } else {
final class $oaPattern25_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern25_Holder $oaPattern25_holder = new $oaPattern25_Holder();
if ((($oaPattern25_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern25_holder.bound = (RichComponent.ProgressSteps) $oaPattern25_holder.value) != null))) {
            return "progress_steps";
        } else {
final class $oaPattern26_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern26_Holder $oaPattern26_holder = new $oaPattern26_Holder();
if ((($oaPattern26_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern26_holder.bound = (RichComponent.SourceSummary) $oaPattern26_holder.value) != null))) {
            return "source_summary";
        } else {
final class $oaPattern27_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern27_Holder $oaPattern27_holder = new $oaPattern27_Holder();
if ((($oaPattern27_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern27_holder.bound = (RichComponent.StatusBadge) $oaPattern27_holder.value) != null))) {
            return "status_badge";
        } else {
final class $oaPattern28_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern28_Holder $oaPattern28_holder = new $oaPattern28_Holder();
if ((($oaPattern28_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern28_holder.bound = (RichComponent.ChoiceGroup) $oaPattern28_holder.value) != null))) {
            return "choice_group";
        }
}
}
}
}
}
}
}
        throw new IncompatibleClassChangeError();
    }

    private static final class Counter {
        private long blocks;
        private long components;
    }

    @dev.openallay.value.ValueType(SemanticSummary.ValueSchemaProvider.class)
private static final class SemanticSummary {
    private final Map<String, Long> metrics;
    private final List<String> diagnosticCodes;
    private final List<String> componentTypes;
    private SemanticSummary(Map<String, Long> metrics, List<String> diagnosticCodes, List<String> componentTypes) {
        this.metrics = metrics;
        this.diagnosticCodes = diagnosticCodes;
        this.componentTypes = componentTypes;
    }
    public Map<String, Long> metrics() { return metrics; }
    public List<String> diagnosticCodes() { return diagnosticCodes; }
    public List<String> componentTypes() { return componentTypes; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SemanticSummary)) return false;
        SemanticSummary that = (SemanticSummary) other;
        return java.util.Objects.equals(metrics, that.metrics) && java.util.Objects.equals(diagnosticCodes, that.diagnosticCodes) && java.util.Objects.equals(componentTypes, that.componentTypes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(metrics);
        hash = 31 * hash + java.util.Objects.hashCode(diagnosticCodes);
        hash = 31 * hash + java.util.Objects.hashCode(componentTypes);
        return hash;
    }
    @Override public String toString() { return "SemanticSummary[metrics=" + metrics + ", diagnosticCodes=" + diagnosticCodes + ", componentTypes=" + componentTypes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SemanticSummary> schema() {
            return new dev.openallay.value.ValueSchema<>(SemanticSummary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SemanticSummary>>asList(new dev.openallay.value.ValueSchema.Component<>(SemanticSummary.class, "metrics", SemanticSummary::metrics), new dev.openallay.value.ValueSchema.Component<>(SemanticSummary.class, "diagnosticCodes", SemanticSummary::diagnosticCodes), new dev.openallay.value.ValueSchema.Component<>(SemanticSummary.class, "componentTypes", SemanticSummary::componentTypes)), arguments -> new SemanticSummary((Map) arguments[0], (List) arguments[1], (List) arguments[2]));
        }
    }
}

    /** Bounded opt-in startup observations. No screen changes or readiness are manufactured. */
    private void startupGate(String phase, UUID actor) {
        if (!graphicalScenario(config.scenario()) && !GuideBuilderE2EProbe.enabled(config.scenario())) return;
        Instant now = Instant.now();
        if (!phase.equals(startupPhase)) {
            startupPhase = phase;
            startupPhaseAt = now;
        }
        net.minecraft.client.Minecraft client = MinecraftClientWindow.instance();
        Map<String, Object> facts = new LinkedHashMap<>(MinecraftClientWindow.screenFacts(client));
        facts.put("phase", phase);
        facts.put("actor", actor == null ? "" : actor.toString());
        facts.put("worldLaunchStarted", worldLaunchStarted);
        net.minecraft.client.server.IntegratedServer server = MinecraftClientWindow.integratedServer(client);
        facts.put("integratedServerPresent", server != null);
        if (server != null) facts.put("worldName", dev.openallay.server.NativeServerOwner.worldName(server));
        facts.put("recipeSeedAdmitted", graphicalRecipeSeedAdmitted);
        facts.put("recipeSeedReady", graphicalRecipeSeedReady);
        if (GuideBuilderE2EProbe.enabled(config.scenario())) {
            facts.put("builderAnchorPhase", builderAnchorPhase);
            facts.put("builderAnchorPending", builderAnchorPending);
        }
        if (!facts.equals(startupDiagnostic)) {
            startupDiagnostic = dev.openallay.util.Java8Collections.mapCopyOf(facts);
            if (startupDiagnosticChanges++ < 32) {
                System.out.println("OpenAllay E2E startup: " + gson.toJson(startupDiagnostic));
            }
        }
        long budget = Math.max(1L, Long.getLong("openallay.e2e.startupPhaseTimeoutSeconds", 90L));
        if (!"startup_ready".equals(phase)
                && (Duration.between(startupPhaseAt, now)).getSeconds() > budget) {
            failWithoutRequest("startup_phase_timeout", "Native startup did not advance from " + phase);
        }
    }

    private void failWithoutRequest(String code, String message) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("elapsedMillis", harnessStartedAt == null ? 0L : Duration.between(harnessStartedAt, Instant.now()).toMillis());
        report.put("loader", loader);
        report.put("gameVersion", gameVersion);
        report.put("modVersion", modVersion);
        report.put("scenario", config.scenario());
        report.put("outcome", "HARNESS_FAILED");
        report.put("failureCode", code);
        report.put("failureMessage", message);
        if (!startupDiagnostic.isEmpty()) {
            report.put("startup", startupDiagnostic);
            report.put("startupPhaseMillis", Duration.between(startupPhaseAt, Instant.now()).toMillis());
        }
        String encoded = gson.toJson(report);
        finish(encoded);
        if (!config.shutdownAfterReport()
                && Boolean.getBoolean("openallay.e2e.shutdownAfterScreenshots")) shutdown.run();
    }

    private void finish(String report) {
        if (finished) return;
        if (nativeCommandOriginalSetting != null
                && (nativeCommandRestore == null || !nativeCommandRestore.isDone())) {
            if (nativeCommandPendingFinish != null) return;
            nativeCommandPendingFinish = report;
            if (nativeCommandCancellation != null) nativeCommandCancellation.cancel();
            restoreNativeCommandSetting().whenComplete((restored, failure) ->
                    dev.openallay.client.gui.MinecraftClientWindow.execute(dev.openallay.client.gui.MinecraftClientWindow.instance(), () -> {
                        String pending = nativeCommandPendingFinish;
                        nativeCommandPendingFinish = null;
                        if (failure != null || !Boolean.TRUE.equals(restored)) {
                            com.google.gson.JsonObject retained = dev.openallay.json.JsonTrees.parse(pending).getAsJsonObject();
                            retained.addProperty("outcome", "HARNESS_FAILED");
                            retained.addProperty("failureCode", "native_command_setting_restore_failed");
                            retained.addProperty("failureMessage", "Actual client setting restoration failed");
                            pending = gson.toJson(retained);
                        }
                        finish(pending);
                    }));
            return;
        }
        finished = true;
        if (nativeCommandCancellation != null) nativeCommandCancellation.cancel();
        if (nativeCommandWarmup != null) {
            com.google.gson.JsonObject retained = dev.openallay.json.JsonTrees.parse(report).getAsJsonObject();
            retained.add("nativeCommandWarmup", nativeCommandWarmup);
            report = gson.toJson(retained);
        }
        if (subscription != null) subscription.close();
        if (graphicalRecipeSeedReceipt != null) {
            com.google.gson.JsonObject retained = dev.openallay.json.JsonTrees.parse(report).getAsJsonObject();
            retained.add("testBootstrapSeed", graphicalRecipeSeedReceipt);
            report = gson.toJson(retained);
        }
        try {
            writeAtomically(config.reportPath(), report + System.lineSeparator());
        } catch (IOException exception) {
            System.err.println("Unable to write OpenAllay E2E report: " + exception.getMessage());
        }
        if (config.shutdownAfterReport()) shutdown.run();
    }

    private static void writeAtomically(java.nio.file.Path target, String value)
            throws IOException {
        java.nio.file.Path absolute = target.toAbsolutePath();
        if (absolute.getParent() != null) {
            Files.createDirectories(absolute.getParent());
        }
        java.nio.file.Path directory = absolute.getParent() == null
                ? java.nio.file.Paths.get(".").toAbsolutePath()
                : absolute.getParent();
        java.nio.file.Path temporary = Files.createTempFile(
                directory, ".openallay-e2e-", ".tmp");
        try {
            dev.openallay.util.Java8Files.writeString(temporary, value, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(String value) {
        try {
            return dev.openallay.util.Java8Hex.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
