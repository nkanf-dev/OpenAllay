package dev.openallay.guide.e2e;

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
    private final Set<String> secrets;
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
    private boolean worldLaunchStarted;
    private int builderWarmupTicks;
    private GuideBuilderE2EProbe.Anchor builderAnchor;
    private GuideBuilderE2EProbe.Anchor currentPlayerAnchor;
    private boolean unrestrictedAtStart;
    private boolean revocationStarted;
    private boolean revocationCompleted;
    private boolean nativeProbePending;
    private Instant revocationCompletedAt;
    private Instant firstJavascriptObservedAt;
    private boolean javascriptObservedBeforeRevocation;
    private UUID screenshotActor;
    private dev.openallay.guide.ui.GuideDisplayConfig screenshotOriginalDisplay;
    private boolean screenshotActionPending;
    private int screenshotWaitTicks;
    private String screenshotReviewFailure;
    private boolean screenshotSourceAvailable;
    private boolean cancelOnToolStartRequested;
    private boolean cancelOnToolStartPending;
    private boolean cancelOnToolStartAccepted;

    public GuideClientE2EController(
            GuideClientE2EConfig config,
            String loader,
            String gameVersion,
            String modVersion,
            GuideServiceManager services,
            Gson gson,
            Runnable shutdown,
            Set<String> secrets) {
        this(config, loader, gameVersion, modVersion, services, gson, shutdown, secrets,
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
            Set<String> secrets,
            Supplier<RecipeProviderReadiness> recipeReadiness) {
        this(config, loader, gameVersion, modVersion, services, gson, shutdown, secrets,
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
            Set<String> secrets,
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
                secrets,
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
            Set<String> secrets,
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
        this.secrets = configuredSecrets(secrets, clientSettings);
        this.recipeReadiness = java.util.Objects.requireNonNull(recipeReadiness, "recipeReadiness");
        this.clientSettings = clientSettings;
        this.traceLookup = traceLookup;
    }

    /** Runs opt-in startup lifecycle and starts the request once a real client player exists. */
    public void tick(UUID actor) {
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
            long timeoutSeconds = Long.getLong("openallay.e2e.timeoutSeconds", 300L);
            if (Duration.between(startedAt, Instant.now()).toSeconds() > timeoutSeconds) {
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
        if (Duration.between(harnessStartedAt, Instant.now()).toSeconds() > timeoutSeconds) {
            failWithoutRequest("harness_timeout", "Real-client acceptance exceeded its elapsed timeout");
            return;
        }
        if (actor == null) {
            try { tickWorldLaunch(); }
            catch (RuntimeException failure) { failWithoutRequest("world_startup_failed", failure.toString()); }
            return;
        }
        if (GuideBuilderE2EProbe.enabled(config.scenario()) && ++builderWarmupTicks < 40) return;
        if (!System.getProperty("openallay.e2e.screenshotRoot", "").isBlank()) {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.gui.overlay() != null || client.gui.screen() != null) return;
        }
        GuideService service = services.forActor(actor);
        if (service.snapshot().persistence().state()
                == dev.openallay.guide.GuidePersistenceSnapshot.State.LOADING) {
            return;
        }
        RecipeProviderReadiness readiness = recipeReadiness.get();
        if (!readiness.equals(lastRecipeReadiness)) {
            lastRecipeReadiness = readiness;
            System.out.println("OpenAllay E2E recipe readiness: "
                    + readiness.state() + " " + readiness.code() + " " + readiness.message());
        }
        if (readiness.state() == RecipeProviderReadiness.State.WAITING) {
            return;
        }
        if (readiness.state() == RecipeProviderReadiness.State.FAILED) {
            failWithoutRequest(readiness.code(), readiness.message());
            return;
        }
        started = true;
        startedAt = Instant.now();
        subscription = service.subscribe(this::observe);
        unrestrictedAtStart = clientSettings != null && clientSettings.snapshot().unrestrictedJavascript().enabled();
        if (GuideBuilderE2EProbe.enabled(config.scenario())) {
            GuideBuilderE2EProbe.captureAnchor(actor, anchor -> {
                if (finished) return;
                currentPlayerAnchor = anchor;
                try {
                    builderAnchor = selectBuilderAnchor(anchor);
                } catch (IOException | RuntimeException failure) {
                    failWithoutRequest("native_anchor_failed", failure.toString());
                    return;
                }
                selectSession(service);
            }, failure -> failWithoutRequest("native_capture_failed", failure));
        } else {
            selectSession(service);
        }
    }

    private GuideBuilderE2EProbe.Anchor selectBuilderAnchor(GuideBuilderE2EProbe.Anchor anchor) throws IOException {
        if (!List.of("builder-acceptance", "builder-reload", "builder-live-copy", "builder-live-undo").contains(config.scenario())) return anchor;
        var client = net.minecraft.client.Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Integrated server is unavailable");
        String world = server.getWorldData().getLevelName();
        if (!world.matches("openallay-builder-[a-zA-Z0-9_.-]+")) throw new IllegalStateException("Not a disposable acceptance world");
        java.nio.file.Path retained = client.gameDirectory.toPath().resolve("config/openallay/e2e")
                .resolve(world + ".anchor.json");
        if (config.scenario().equals("builder-reload") || config.scenario().equals("builder-live-undo")) {
            var persisted = gson.fromJson(Files.readString(retained), GuideBuilderE2EProbe.Anchor.class);
            String suffix = config.scenario().equals("builder-reload") ? ".acceptance.json" : ".live-copy.json";
            var proof = com.google.gson.JsonParser.parseString(Files.readString(retained.resolveSibling(world + suffix))).getAsJsonObject();
            return GuideBuilderE2EProbe.retainedOrigin(anchor, persisted, proof, world);
        }
        writeAtomically(retained, gson.toJson(anchor));
        return anchor;
    }

    private java.nio.file.Path builderProofPath(String suffix) {
        var client = net.minecraft.client.Minecraft.getInstance();
        String world = client.getSingleplayerServer().getWorldData().getLevelName();
        return client.gameDirectory.toPath().resolve("config/openallay/e2e").resolve(world + suffix);
    }

    private static com.google.gson.JsonObject builderPreview(GuideRequestSnapshot request) {
        var tool = request.tools().stream().filter(value -> value.toolId().equals("openallay:run_javascript")).toList().getLast();
        return tool.normalized().getAsJsonObject("value").getAsJsonObject("preview");
    }

    private void retainAcceptancePersistence(GuideRequestSnapshot request, com.google.gson.JsonObject probe) throws IOException {
        if (!config.scenario().equals("builder-acceptance") || !"PASSED".equals(probe.get("outcome").getAsString())) return;
        var preview = builderPreview(request);
        com.google.gson.JsonObject receipt = new com.google.gson.JsonObject();
        receipt.addProperty("outcome", "PASSED");
        receipt.addProperty("requestId", request.requestId().toString());
        receipt.add("worldName", probe.get("worldName"));
        receipt.add("nativeAnchor", probe.get("independentAnchor"));
        receipt.add("operations", preview.get("operations"));
        receipt.add("lifecycle", preview.get("lifecycle"));
        receipt.add("templates", preview.get("templates"));
        writeAtomically(builderProofPath(".acceptance.json"), gson.toJson(receipt));
    }

    private void verifyReloadPersistence(GuideRequestSnapshot request, com.google.gson.JsonObject probe) throws IOException {
        var retained = com.google.gson.JsonParser.parseString(Files.readString(builderProofPath(".acceptance.json"))).getAsJsonObject();
        if (!"PASSED".equals(retained.get("outcome").getAsString())) throw new IllegalStateException("Reload lacks prior independently passed acceptance receipt");
        boolean matched = GuideBuilderE2EProbe.persistedOperationsMatch(retained, builderPreview(request));
        probe.addProperty("exactPersistencePassed", matched);
        if (!matched) probe.addProperty("outcome", "FAILED");
    }

    private void retainLiveCopyProof(com.google.gson.JsonObject probe) throws IOException {
        if (!config.scenario().equals("builder-live-copy")) return;
        var client = net.minecraft.client.Minecraft.getInstance();
        String world = client.getSingleplayerServer().getWorldData().getLevelName();
        var path = client.gameDirectory.toPath().resolve("config/openallay/e2e").resolve(world + ".live-copy.json");
        writeAtomically(path, gson.toJson(probe));
    }

    private void selectSession(GuideService service) {
        service.selectSession(config.sessionId()).thenAccept(selected -> {
            if (selected instanceof ToolResult.Failure<String> failure) {
                failWithoutRequest(failure.code(), failure.message());
            } else {
                selectMode(service);
            }
        });
    }

    private void tickWorldLaunch() {
        String create = System.getProperty("openallay.e2e.createWorld", "");
        String resume = System.getProperty("openallay.e2e.resumeWorld", "");
        String name = create.isBlank() ? resume : create;
        if (name.isBlank() || worldLaunchStarted) return;
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.gui.overlay() != null
                || !(client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
        worldLaunchStarted = true;
        boolean existing = Files.isDirectory(client.gameDirectory.toPath().resolve("saves").resolve(name));
        if (!name.matches("openallay-builder-[a-zA-Z0-9_.-]+")
                || (!create.isBlank() && (!resume.isBlank() || existing))
                || (create.isBlank() && (!List.of("builder-reload", "builder-live-undo").contains(config.scenario()) || !existing))) {
            failWithoutRequest("unsafe_world_name", "Acceptance requires a new disposable world or an explicitly resumed Builder reload world");
            return;
        }
        if (create.isBlank()) {
            client.createWorldOpenFlows().openWorld(name, () -> failWithoutRequest(
                    "world_reload_cancelled", "The native world reload did not complete"));
            return;
        }
        var settings = new net.minecraft.world.level.LevelSettings(name,
                net.minecraft.world.level.GameType.SURVIVAL,
                new net.minecraft.world.level.LevelSettings.DifficultySettings(
                        net.minecraft.world.Difficulty.PEACEFUL, false, false),
                false, net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(name, settings,
                new net.minecraft.world.level.levelgen.WorldOptions(17L, false, false),
                registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                        .value().createWorldDimensions(), client.gui.screen());
    }

    public boolean finished() {
        return finished;
    }

    private void selectMode(GuideService service) {
        service.setModelMode(config.modelMode()).thenAccept(mode -> {
            if (mode instanceof ToolResult.Failure<?> failure) {
                failWithoutRequest(failure.code(), failure.message());
            } else {
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
            if (asked instanceof ToolResult.Failure<UUID> failure) {
                failWithoutRequest(failure.code(), failure.message());
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
            if (asked instanceof ToolResult.Failure<UUID> failure) {
                failWithoutRequest(failure.code(), failure.message());
                return;
            }
            requestId = ((ToolResult.Success<UUID>) asked).value();
            if (GuideBuilderE2EProbe.enabled(config.scenario())
                    && Boolean.getBoolean("openallay.e2e.revokeUnrestrictedAfterCapture")
                    && !revocationStarted) {
                revocationStarted = true;
                if (clientSettings == null || !unrestrictedAtStart) {
                    failWithoutRequest("revocation_precondition_failed", "Explicit enabled settings are required before the frozen-authority test");
                    return;
                }
                clientSettings.saveUnrestrictedJavascript(false).thenAccept(saved -> {
                    if (saved instanceof ToolResult.Failure<Boolean> failure) {
                        failWithoutRequest(failure.code(), failure.message());
                    } else {
                        revocationCompleted = !clientSettings.snapshot().unrestrictedJavascript().enabled();
                        revocationCompletedAt = Instant.now();
                        observe(service.snapshot());
                    }
                });
            }
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
        if (firstJavascriptObservedAt == null && request.tools().stream()
                .anyMatch(value -> value.toolId().equals("openallay:run_javascript"))) {
            firstJavascriptObservedAt = Instant.now();
            javascriptObservedBeforeRevocation = Boolean.getBoolean("openallay.e2e.revokeUnrestrictedAfterCapture")
                    && !revocationCompleted;
        }
        if (transitions.isEmpty() || transitions.getLast() != request.status()) {
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
                cancelOnToolStartAccepted = cancelled instanceof ToolResult.Success<Boolean> success && success.value();
                if (!cancelOnToolStartAccepted) {
                    failWithoutRequest("stop_request_failed", "The actual guide cancellation was not accepted");
                } else observe(service.snapshot());
            });
        }
        if (!request.terminal() || nativeProbePending || pendingReport != null || cancelOnToolStartPending) return;
        if (revocationStarted && !revocationCompleted) return;
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
        GuideSessionSnapshot session = snapshot.sessions().stream()
                .filter(value -> value.sessionId().equals(request.sessionId()))
                .findFirst().orElseThrow();
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
                request.tools().stream().map(value -> value.toolId()).toList(),
                request.tools().stream().map(GuideClientE2EController::toolProbe).toList(),
                request.sources().stream().map(value -> value.evidence()).toList(),
                request.timeline().stream().map(value -> switch (value) {
                    case GuideTimelineEntry.Assistant ignored -> "assistant";
                    case GuideTimelineEntry.Tool ignored -> "tool";
                }).toList(),
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
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                var client = net.minecraft.client.Minecraft.getInstance();
                originalWindowWidth = client.getWindow().getWidth();
                originalWindowHeight = client.getWindow().getHeight();
                client.gui.setScreen(screenshotGuide(services.forActor(snapshot.actorId())));
                if (!System.getProperty("openallay.e2e.screenshotRoot", "").isBlank()) {
                    screenshotStage = 0;
                    screenshotTicks = 0;
                }
            });
        }
        pendingReport = new GuideE2EReportJson(gson).encode(report, secrets);
        if (Boolean.getBoolean("openallay.e2e.cancelOnToolStart")) {
            var stop = new com.google.gson.JsonObject();
            stop.addProperty("requested", cancelOnToolStartRequested);
            stop.addProperty("accepted", cancelOnToolStartAccepted);
            stop.addProperty("terminalCancelled", request.status() == GuideRequestStatus.CANCELLED);
            stop.addProperty("pendingToolHasNoNormalizedResult", request.tools().stream().anyMatch(value ->
                    value.toolId().equals("openallay:run_javascript")
                            && value.status() == dev.openallay.guide.GuideToolStatus.RUNNING
                            && value.normalized() == null));
            var retained = com.google.gson.JsonParser.parseString(pendingReport).getAsJsonObject();
            retained.add("actualStop", stop);
            pendingReport = gson.toJson(retained);
        }
        pendingTraceProfile = request.modelSelection().profileId();
        if (GuideBuilderE2EProbe.enabled(config.scenario())) {
            nativeProbePending = true;
            GuideBuilderE2EProbe.verify(config.scenario(), snapshot.actorId(), builderAnchor,
                    request, clientSettings, unrestrictedAtStart, revocationCompleted, probe -> {
                nativeProbePending = false;
                if (currentPlayerAnchor != null) probe.add("currentPlayerAnchor", gson.toJsonTree(currentPlayerAnchor));
                if (config.scenario().equals("builder-reload") || config.scenario().equals("builder-live-undo"))
                    probe.addProperty("originSource", "prior-passed-independent-native-receipt");
                if (Boolean.getBoolean("openallay.e2e.revokeUnrestrictedAfterCapture")) {
                    Instant firstNative = request.tools().stream().flatMap(value -> value.sources().stream())
                            .map(value -> value.evidence())
                            .filter(value -> value.sourceId().startsWith("openallay_builder:")
                                    && value.authority() == dev.openallay.context.DataAuthority.SERVER_AUTHORITATIVE)
                            .map(value -> value.capturedAt()).min(Instant::compareTo).orElse(null);
                    boolean ordered = GuideBuilderE2EProbe.frozenNativeTiming(
                            revocationCompletedAt, firstNative, firstJavascriptObservedAt,
                            javascriptObservedBeforeRevocation);
                    probe.addProperty("frozenAuthorityTimingPassed", ordered);
                    probe.addProperty("javascriptObservedBeforeRevocation", javascriptObservedBeforeRevocation);
                    if (revocationCompletedAt != null) probe.addProperty("revocationCompletedAt", revocationCompletedAt.toString());
                    if (firstJavascriptObservedAt != null) probe.addProperty("firstJavascriptObservedAt", firstJavascriptObservedAt.toString());
                    if (firstNative != null) probe.addProperty("firstTrustedNativeEvidenceAt", firstNative.toString());
                    if (!ordered) probe.addProperty("outcome", "FAILED");
                }
                if (config.scenario().equals("builder-reload")) {
                    try { verifyReloadPersistence(request, probe); }
                    catch (IOException | RuntimeException failure) { probe.addProperty("outcome", "FAILED"); probe.addProperty("persistenceFailure", failure.toString()); }
                }
                try { retainAcceptancePersistence(request, probe); retainLiveCopyProof(probe); }
                catch (IOException | RuntimeException failure) { probe.addProperty("outcome", "FAILED"); probe.addProperty("proofFailure", failure.toString()); }
                var encoded = com.google.gson.JsonParser.parseString(pendingReport).getAsJsonObject();
                encoded.add("nativeAcceptance", probe);
                pendingReport = gson.toJson(encoded);
                for (String secret : secrets) {
                    if (secret != null && !secret.isBlank()) pendingReport = pendingReport.replace(secret, "[REDACTED]");
                }
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
                writeAtomically(config.tracePath(), trace.orElseThrow());
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
        var client = net.minecraft.client.Minecraft.getInstance();
        if (screenshotStage <= 7
                && !(client.gui.screen() instanceof OpenAllayScreen)) return;
        OpenAllayScreen screen = client.gui.screen() instanceof OpenAllayScreen value
                ? value : null;
        switch (screenshotStage++) {
            case 0 -> screen.positionForDevelopmentProbe(0.0D);
            case 1 -> {
                screenshot(client, "01-wide-top.png");
                screen.positionForDevelopmentProbe(0.5D);
            }
            case 2 -> {
                screenshot(client, "02-wide-middle.png");
                screen.positionForDevelopmentProbe(1.0D);
            }
            case 3 -> {
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
            case 4 -> {
                screenshot(client, "04-wide-tool-detail.png");
                screen.openModelSelectorForDevelopmentProbe();
            }
            case 5 -> {
                screenshot(client, "05-wide-model-selector.png");
                screen.closeModelSelectorForDevelopmentProbe();
                client.getWindow().setWindowed(640, 480);
            }
            case 6 -> screenshot(client, "06-narrow-tool-detail.png");
            case 7 -> {
                client.getWindow().setWindowed(originalWindowWidth, originalWindowHeight);
                if (clientSettings == null) {
                    finishScreenshotProbe();
                    break;
                }
                OpenAllaySettingsScreen settings = new OpenAllaySettingsScreen(
                        clientSettings, () -> {});
                client.gui.setScreen(settings);
                if (professionalScreenshots()) settings.e2eSelectExtension("openallay:builder");
                else settings.e2eOpenExtensions();
            }
            case 8 -> {
                screenshot(client, professionalScreenshots() ? "07-wide-builder-extension.png" : "07-wide-tool-settings.png");
                if (client.gui.screen() instanceof OpenAllaySettingsScreen settings) {
                    settings.e2eScrollExtensionDetails(320);
                }
            }
            case 9 -> {
                screenshot(client, professionalScreenshots() ? "08-wide-builder-extension-lower.png" : "08-wide-tool-settings-lower.png");
                if (client.gui.screen() instanceof OpenAllaySettingsScreen settings) {
                    settings.e2eOpenGeneral(professionalScreenshots()
                            ? clientSettings.snapshot().display().assistantName() : "小羽");
                }
            }
            case 10 -> {
                screenshot(client, "09-wide-general-settings.png");
                if (client.gui.screen() instanceof OpenAllaySettingsScreen settings) {
                    settings.e2eOpenAbout();
                }
            }
            case 11 -> {
                screenshot(client, "10-wide-about.png");
                if (professionalScreenshots()) {
                    if (client.gui.screen() instanceof OpenAllaySettingsScreen settings) settings.e2eScrollPageBottom();
                } else if (!GuideBuilderE2EProbe.enabled(config.scenario())) {
                    finishScreenshotProbe();
                } else {
                    client.gui.setScreen(null);
                    if (client.player != null) {
                        client.player.setYRot(-45.0F);
                        client.player.setXRot(-12.0F);
                    }
                }
            }
            case 12 -> {
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
            case 13 -> {
                screenshot(client, "12-models-manual-context.png");
                String profile = requiredScreenshotProperty("openallay.e2e.screenshotAutomaticProfile");
                requireScreenshotProfile(profile, true);
                ((OpenAllaySettingsScreen) client.gui.screen()).e2eOpenModels(profile);
            }
            case 14 -> {
                screenshot(client, "13-models-automatic-reference.png");
                ((OpenAllaySettingsScreen) client.gui.screen()).e2eScrollPageBottom();
            }
            case 15 -> {
                screenshot(client, "14-models-automatic-reference-bottom.png");
                var settings = (OpenAllaySettingsScreen) client.gui.screen();
                settings.e2eOpenGeneral(clientSettings.snapshot().display().assistantName());
            }
            case 16 -> {
                ((OpenAllaySettingsScreen) client.gui.screen()).e2eScrollPageBottom();
            }
            case 17 -> {
                screenshot(client, "15-general-bottom.png");
                ((OpenAllaySettingsScreen) client.gui.screen()).e2eOpenAbout();
            }
            case 18 -> ((OpenAllaySettingsScreen) client.gui.screen()).e2eScrollPageBottom();
            case 19 -> {
                screenshot(client, "16-about-bottom.png");
                screenshotDebug(false, () -> {
                    var guide = screenshotGuide(services.forActor(screenshotActor));
                    client.gui.setScreen(guide);
                    guide.selectLatestJavascriptForDevelopmentProbe();
                });
            }
            case 20 -> {
                screenshot(client, "17-normal-javascript-intent-detail.png");
                screenshotSourceAvailable = client.gui.screen() instanceof OpenAllayScreen guide
                        && guide.selectLatestSourceForDevelopmentProbe();
                if (!screenshotSourceAvailable)
                    System.out.println("OpenAllay E2E source detail: no actual source in this request");
            }
            case 21 -> {
                if (!screenshotSourceAvailable) {
                    System.out.println("OpenAllay E2E normal source screenshot skipped: no actual source");
                } else screenshot(client, "18-normal-source-detail.png");
                screenshotDebug(true, () -> {
                    var guide = screenshotGuide(services.forActor(screenshotActor));
                    client.gui.setScreen(guide);
                    guide.selectLatestJavascriptForDevelopmentProbe();
                });
            }
            case 22 -> {
                screenshot(client, "19-debug-javascript-detail.png");
                ((OpenAllayScreen) client.gui.screen()).scrollDetailToBottomForDevelopmentProbe();
            }
            case 23 -> {
                screenshot(client, "20-debug-javascript-detail-bottom.png");
                screenshotSourceAvailable = ((OpenAllayScreen) client.gui.screen()).selectLatestSourceForDevelopmentProbe();
                if (!screenshotSourceAvailable)
                    System.out.println("OpenAllay E2E debug source screenshot skipped: no actual source");
            }
            case 24 -> {
                if (screenshotSourceAvailable)
                    screenshot(client, "21-debug-source-detail.png");
                screenshotSettings(client).e2eSelectExtension("openallay:builder");
            }
            case 25 -> {
                screenshot(client, "22-builder-installed-detail.png");
                String jar = System.getProperty("openallay.e2e.reviewPackage", "");
                if (jar.isBlank()) { screenshotStage = 28; break; }
                screenshotActionPending = true;
                clientSettings.importLocalExtensionPackage(java.nio.file.Path.of(jar)).thenAccept(prepared ->
                        client.execute(() -> {
                            screenshotActionPending = false;
                            if (prepared instanceof ToolResult.Failure<Boolean> failure)
                                screenshotReviewFailure = failure.code();
                        }));
            }
            case 26 -> {
                if (screenshotReviewFailure != null) {
                    System.out.println("OpenAllay E2E package review failed: " + screenshotReviewFailure);
                    screenshot(client, "23-builder-review-failed.png");
                    screenshotStage = 28;
                } else if (client.gui.screen() instanceof dev.openallay.client.gui.RequirementReviewScreen) {
                    screenshot(client, "23-builder-advisory-review.png");
                    screenshotWaitTicks = 0;
                } else if (++screenshotWaitTicks > 100) {
                    throw new IllegalStateException("Actual validated package review did not open");
                } else screenshotStage = 26;
            }
            case 27 -> {
                if (client.gui.screen() instanceof dev.openallay.client.gui.RequirementReviewScreen review) review.onClose();
                else throw new IllegalStateException("Actual package review is unavailable for cancellation");
            }
            case 28 -> {
                screenshot(client, "24-builder-review-cancelled.png");
                client.gui.setScreen(null);
                if (client.player != null) {
                    client.player.setYRot(-45.0F);
                    client.player.setXRot(-12.0F);
                }
            }
            case 29 -> {
                screenshot(client, "25-native-world-final.png");
                finishScreenshotProbe();
            }
            default -> finishScreenshotProbe();
        }
    }

    static boolean professionalScreenshots() {
        return Boolean.getBoolean(GuideClientE2EConfig.ENABLED)
                && "professional".equals(System.getProperty("openallay.e2e.screenshotMatrix", ""));
    }

    private static String requiredScreenshotProperty(String key) {
        String value = System.getProperty(key, "");
        if (value.isBlank()) throw new IllegalStateException("An explicit screenshot profile is required");
        return value;
    }

    private void requireScreenshotProfile(String id, boolean automatic) {
        var profile = clientSettings.snapshot().models().config().profiles().stream()
                .filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        if (automatic) {
            if (profile.enabled() || profile.contextWindowTokens() != null
                    || dev.openallay.model.metadata.BuiltinModelCatalog.bundled().catalog().match(profile.model()).isEmpty())
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
        client.gui.setScreen(settings);
        return settings;
    }

    private void screenshotDebug(boolean enabled, Runnable afterSave) {
        if (screenshotOriginalDisplay == null || clientSettings == null)
            throw new IllegalStateException("Actual display settings are unavailable");
        screenshotActionPending = true;
        var current = clientSettings.snapshot().display();
        var replacement = new dev.openallay.guide.ui.GuideDisplayConfig(current.schemaVersion(), enabled,
                current.animationsEnabled(), current.assistantName());
        clientSettings.saveDisplay(replacement).thenAccept(saved -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
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
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
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
                || System.getProperty("openallay.e2e.screenshotRoot", "").isBlank()
                || ++activeScreenshotTicks < 4) {
            return;
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.gui.overlay() == null
                && client.gui.screen() instanceof OpenAllayScreen screen
                && screen.hasRenderedActiveProgressForDevelopmentProbe()) {
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
                || System.getProperty("openallay.e2e.screenshotRoot", "").isBlank()) {
            return;
        }
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            originalWindowWidth = client.getWindow().getWidth();
            originalWindowHeight = client.getWindow().getHeight();
            client.gui.setScreen(screenshotGuide(service));
        });
    }

    private static void screenshot(net.minecraft.client.Minecraft client, String name) {
        java.io.File root = new java.io.File(
                System.getProperty("openallay.e2e.screenshotRoot"));
        root.mkdirs();
        net.minecraft.client.Screenshot.grab(
                root,
                name,
                client.gameRenderer.mainRenderTarget(),
                1,
                component -> System.out.println("OpenAllay E2E screenshot: "
                        + component.getString()));
    }

    private static SemanticSummary summarize(GuideRequestSnapshot request) {
        long assistants = 0;
        long blocks = 0;
        long components = 0;
        long fallbacks = 0;
        TreeSet<String> diagnostics = new TreeSet<>();
        TreeSet<String> componentTypes = new TreeSet<>();
        for (GuideTimelineEntry entry : request.timeline()) {
            if (!(entry instanceof GuideTimelineEntry.Assistant assistant)) continue;
            assistants++;
            fallbacks += assistant.semantic().diagnostics().size();
            assistant.semantic().diagnostics().forEach(value -> diagnostics.add(value.code()));
            Counter counter = new Counter();
            for (SemanticBlock block : assistant.semantic().blocks()) {
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
                metrics, List.copyOf(diagnostics), List.copyOf(componentTypes));
    }

    private static GuideE2EReport.ToolProbe toolProbe(dev.openallay.guide.GuideToolActivity activity) {
        var normalized = activity.normalized();
        String section = null;
        String failureCode = null;
        if (normalized != null) {
            if (normalized.has("value") && normalized.get("value").isJsonObject()) {
                var value = normalized.getAsJsonObject("value");
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
        switch (block) {
            case SemanticBlock.ListBlock value -> value.items().forEach(
                    item -> item.forEach(child -> collect(child, counter, componentTypes)));
            case SemanticBlock.Quote value -> value.content().forEach(
                    child -> collect(child, counter, componentTypes));
            case SemanticBlock.Component value -> {
                counter.components++;
                componentTypes.add(componentType(value.component()));
            }
            default -> { }
        }
    }

    private static String componentType(RichComponent component) {
        return switch (component) {
            case RichComponent.ItemRow ignored -> "item_row";
            case RichComponent.RecipeGrid ignored -> "recipe_grid";
            case RichComponent.IngredientCheck ignored -> "ingredient_check";
            case RichComponent.CraftabilitySummary ignored -> "craftability_summary";
            case RichComponent.ProgressSteps ignored -> "progress_steps";
            case RichComponent.SourceSummary ignored -> "source_summary";
            case RichComponent.StatusBadge ignored -> "status_badge";
            case RichComponent.ChoiceGroup ignored -> "choice_group";
        };
    }

    private static final class Counter {
        private long blocks;
        private long components;
    }

    private record SemanticSummary(
            Map<String, Long> metrics,
            List<String> diagnosticCodes,
            List<String> componentTypes) {}

    private void failWithoutRequest(String code, String message) {
        String encoded = gson.toJson(java.util.Map.of(
                "elapsedMillis", harnessStartedAt == null ? 0L : Duration.between(harnessStartedAt, Instant.now()).toMillis(),
                "loader", loader,
                "gameVersion", gameVersion,
                "modVersion", modVersion,
                "scenario", config.scenario(),
                "outcome", "HARNESS_FAILED",
                "failureCode", code,
                "failureMessage", message));
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) encoded = encoded.replace(secret, "[REDACTED]");
        }
        finish(encoded);
        if (!config.shutdownAfterReport()
                && Boolean.getBoolean("openallay.e2e.shutdownAfterScreenshots")) shutdown.run();
    }

    private void finish(String report) {
        if (finished) return;
        finished = true;
        if (subscription != null) subscription.close();
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
                ? java.nio.file.Path.of(".").toAbsolutePath()
                : absolute.getParent();
        java.nio.file.Path temporary = Files.createTempFile(
                directory, ".openallay-e2e-", ".tmp");
        try {
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
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
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static Set<String> referencedEnvironmentSecrets(
            Set<String> initial,
            List<dev.openallay.model.config.ModelProfileDefinition> profiles,
            java.util.function.Function<String, String> environment) {
        Set<String> values = new java.util.HashSet<>(initial);
        for (var profile : profiles) {
            var reference = dev.openallay.model.config.CredentialReference.parse(profile.credentialRef());
            if (reference.kind() == dev.openallay.model.config.CredentialReference.Kind.ENVIRONMENT) {
                String value = environment.apply(reference.value());
                if (value != null && !value.isBlank()) values.add(value);
            }
        }
        return Set.copyOf(values);
    }

    private static Set<String> configuredSecrets(Set<String> initial, ClientSettingsService settings) {
        return settings == null ? Set.copyOf(initial) : referencedEnvironmentSecrets(initial,
                settings.snapshot().models().config().profiles(), System::getenv);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
