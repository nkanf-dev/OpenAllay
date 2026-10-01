package dev.openallay.model.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentSystemPrompt;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.benchmark.AgentBenchmarkRecorder;
import dev.openallay.benchmark.BenchmarkCase;
import dev.openallay.benchmark.BenchmarkCorpus;
import dev.openallay.benchmark.BenchmarkCorpusCodec;
import dev.openallay.benchmark.BenchmarkOutcome;
import dev.openallay.benchmark.BenchmarkReport;
import dev.openallay.benchmark.BenchmarkRunner;
import dev.openallay.benchmark.BenchmarkSelector;
import dev.openallay.benchmark.BenchmarkTraceAudit;
import dev.openallay.benchmark.BenchmarkTraceAuditor;
import dev.openallay.benchmark.BenchmarkVerifier;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.IngredientAlternativeSnapshot;
import dev.openallay.context.IngredientRequirementSnapshot;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.PlayerSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeLayoutSnapshot;
import dev.openallay.context.RecipeOutputSnapshot;
import dev.openallay.context.RecipeProcessingSnapshot;
import dev.openallay.context.RecipeReference;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.context.RegistrySnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.context.game.ObservableGameStateSnapshot;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.anthropic.AnthropicMessagesClient;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.openai.OpenAiChatClient;
import dev.openallay.model.scheduling.ModelRequestScheduler;
import dev.openallay.platform.InstalledModMetadata;
import dev.openallay.recipe.RecipeUnlockState;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.command.CommandCatalogSnapshot;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.testing.GroundedTestFixtures;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import dev.openallay.world.BlockObservation;
import dev.openallay.world.EntityObservation;
import dev.openallay.world.WorldBlockSnapshot;
import dev.openallay.world.WorldBounds;
import dev.openallay.world.WorldEntitySnapshot;
import dev.openallay.world.WorldEntitySummary;
import dev.openallay.world.WorldObservationCoordinator;
import dev.openallay.world.WorldObservationCoverage;
import dev.openallay.world.WorldObservationRequest;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.world.WorldPosition;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Explicit, billable corpus benchmark over the production Agent loop and model transports.
 *
 * <p>The fixture contributes data and observed effects only. Expected outcomes stay in the corpus
 * verifier and are never appended to model context.
 */
final class LiveAgentBenchmarkAcceptanceTest {
    private static final String FIXTURE = "javascript-agent";
    private static final Set<String> FIXTURE_CAPABILITIES = Set.of(
            "game",
            "player",
            "registries",
            "recipes",
            "extensions",
            "recipe-viewer",
            "world",
            "content-profile",
            "inventory-rich");

    @Test
    void fixtureSelectionDeclaresEveryDefaultCoverageGap() {
        ToolInvocationContext context = benchmarkContext("fixture-audit");
        BenchmarkSelector.Selection plan = select(
                corpus(),
                Map.of(),
                fixtureCapabilities(false),
                1);
        List<String> selected = plan.selected().stream()
                .map(BenchmarkCase::id)
                .toList();

        assertEquals(List.of(
                "exact-installed-mod",
                "highest-damage-sword",
                "least-material-container",
                "farmers-delight-food-ranking",
                "dynamic-extension-schema",
                "recipe-craftability",
                "recipe-viewer-extension",
                "tree-cross-section",
                "entity-detail"), selected);
        assertEquals(List.of(
                new BenchmarkSelector.SkippedCase(
                        "server-model-routing",
                        "server-model-routing",
                        BenchmarkSelector.SkipReason.FIXTURE_MISMATCH,
                        List.of("server-model")),
                new BenchmarkSelector.SkippedCase(
                        "modern-command-components",
                        FIXTURE,
                        BenchmarkSelector.SkipReason.MISSING_CAPABILITIES,
                        List.of("commands"))),
                plan.skipped());
        assertTrue(context.registries().orElseThrow().entries().stream()
                .anyMatch(entry -> entry.id().equals("farmersdelight:roast_chicken")));
        assertTrue(context.recipes().orElseThrow().recipes().stream()
                .anyMatch(recipe -> recipe.id().equals("farmersdelight:apple_cider")));
        assertTrue(context.player().orElseThrow().inventory().slots().stream()
                .anyMatch(slot -> slot.stack().itemId().equals("minecraft:apple")));
    }

    @Test
    void namedProfileReferenceUsesProductionSchemaAndEnvironmentCredential(
            @TempDir Path directory) throws Exception {
        Path profiles = directory.resolve("models.json");
        Files.writeString(profiles, """
                {
                  "defaultProfileId": "default-profile",
                  "profiles": [{
                    "id": "default-profile",
                    "displayName": "Default",
                    "enabled": true,
                    "protocol": "openai_chat",
                    "baseUrl": "https://provider.example/v1/",
                    "model": "provider/default",
                    "credentialRef": "env:DEFAULT_KEY",
                    "contextWindowTokens": 128000,
                    "maxOutputTokens": 4096,
                    "connectTimeoutSeconds": 30,
                    "requestTimeoutSeconds": 300
                  }, {
                    "id": "benchmark-profile",
                    "displayName": "Benchmark",
                    "enabled": true,
                    "protocol": "anthropic_messages",
                    "baseUrl": "https://benchmark.example/v1/",
                    "model": "provider/benchmark",
                    "credentialRef": "env:BENCHMARK_KEY",
                    "contextWindowTokens": 256000,
                    "maxOutputTokens": 8192,
                    "connectTimeoutSeconds": 20,
                    "requestTimeoutSeconds": 240
                  }]
                }
                """, StandardCharsets.UTF_8);

        BenchmarkModelProfile selected = modelProfile(Map.of(
                "OPENALLAY_BENCHMARK_PROFILE_FILE", profiles.toString(),
                "OPENALLAY_BENCHMARK_PROFILE_ID", "benchmark-profile",
                "DEFAULT_KEY", "unused",
                "BENCHMARK_KEY", "secret"));

        assertEquals("benchmark-profile", selected.profileId());
        assertEquals("provider/benchmark", selected.canonicalModelId());
        assertEquals(ModelProtocol.ANTHROPIC_MESSAGES, selected.config().protocol());
        assertEquals(256_000, selected.config().contextWindowTokens());

        BenchmarkCorpus corpus = corpus();
        BenchmarkSelector.Selection selection = select(
                corpus,
                Map.of(),
                fixtureCapabilities(false),
                1);
        Path retained = retain(
                Map.of(
                        "OPENALLAY_PRODUCT_COMMIT", "test-commit",
                        "OPENALLAY_BENCHMARK_OUTPUT", directory.resolve("reports").toString()),
                selected,
                selection,
                new BenchmarkReport(List.of()),
                List.of(),
                new GsonBuilder().setPrettyPrinting().create());
        String report = Files.readString(retained, StandardCharsets.UTF_8);
        String audit = Files.readString(auditPath(retained), StandardCharsets.UTF_8);
        assertEquals(Set.of("fixture", "productCommit", "provider", "profileId",
                        "canonicalModelId", "selection", "benchmark", "traces"),
                JsonParser.parseString(report).getAsJsonObject().keySet());
        assertEquals(Set.of("attempts"),
                JsonParser.parseString(audit).getAsJsonObject().keySet());
        assertTrue(report.contains("\"profileId\": \"benchmark-profile\""));
        assertTrue(report.contains("\"canonicalModelId\": \"provider/benchmark\""));
        assertTrue(report.contains("\"provider\": \"https://benchmark.example\""));
        assertFalse(report.contains("secret"));
        assertFalse(audit.contains("secret"));
    }

    @Test
    void realProviderRunsEveryApplicableCorpusCaseAndRetainsRedactedTraces()
            throws Exception {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(Boolean.parseBoolean(
                environment.get("OPENALLAY_LIVE_AGENT_BENCHMARK")));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        BenchmarkCorpus corpus = corpus();
        int repeats = positive(environment.getOrDefault(
                "OPENALLAY_BENCHMARK_REPEATS", "3"), "OPENALLAY_BENCHMARK_REPEATS");
        boolean includeCommands = Boolean.parseBoolean(
                environment.getOrDefault("OPENALLAY_BENCHMARK_INCLUDE_COMMANDS", "false"));
        BenchmarkSelector.Selection selection =
                select(corpus, environment, fixtureCapabilities(includeCommands), repeats);
        List<BenchmarkCase> cases = selection.selected();
        assertFalse(cases.isEmpty(), "No applicable benchmark cases were selected");

        BenchmarkModelProfile modelProfile = modelProfile(environment);
        ModelClient rawModel = model(modelProfile.config(), gson);
        JavascriptDataModuleRegistry extensions = extensions();
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
        commands.replace(new CommandCapabilityConfig(
                includeCommands));
        WorldObservationRuntime world = new WorldObservationRuntime();
        RunJavascriptTool javascript = new RunJavascriptTool(
                new RhinoJavascriptRuntime(),
                context -> new MinecraftAgentHostGraph(
                        context,
                        dev.openallay.knowledge.KnowledgeSnapshot::empty,
                        extensions),
                new AgentResultWorkspaceRegistry(),
                new JavascriptResultPresenter(),
                commands,
                world);
        ToolRegistry registry = new ToolRegistry();
        registry.register("openallay:live-benchmark", List.of(javascript));
        SkillRepository skills = new SkillRepository(
                new SkillParser(),
                registry.descriptors().stream().map(value -> value.id()).toList());
        if (!skills.reload(new BundledSkillLoader().load(), Set.of())) {
            throw new IllegalStateException("Bundled Skills failed validation");
        }
        registry.register("openallay:skills", List.of(new LoadSkillTool(skills)));
        GameGuideAgent agent = new GameGuideAgent(
                new ModelRequestScheduler(rawModel),
                new LocalAgentToolExecutor(registry, gson),
                new AgentSessionStore(),
                gson);
        String prompt = AgentSystemPrompt.compose(
                skills.metadataPrompt(),
                dev.openallay.script.schema.CoreJavascriptContract.render(
                        MinecraftAgentHostGraph.describeRequest(
                                benchmarkContext("descriptor"), extensions)));
        boolean stream = Boolean.parseBoolean(
                environment.getOrDefault("OPENALLAY_LIVE_STREAM", "true"));
        List<BenchmarkTraceAuditor.TraceRef> traces = new CopyOnWriteArrayList<>();

        BenchmarkReport report = new BenchmarkRunner(new BenchmarkVerifier()).run(
                cases,
                (testCase, attempt) -> execute(
                        testCase,
                        attempt,
                        agent,
                        prompt,
                        stream,
                        commands,
                        world,
                        includeCommands,
                        traces));

        Path retained = retain(
                environment, modelProfile, selection, report, traces, gson);
        selection.skipped().forEach(value -> System.out.println(
                "OPENALLAY_BENCHMARK_SKIPPED"
                        + " id=" + value.caseId()
                        + " reason=" + value.reason()
                        + " missing_capabilities="
                        + String.join(",", value.missingCapabilities())));
        report.cases().forEach(value -> System.out.println(
                "OPENALLAY_BENCHMARK_CASE"
                        + " id=" + value.caseId()
                        + " successes=" + value.successes() + "/" + value.attempts()
                        + " probability=" + value.successProbability()
                        + " median_model_turns=" + value.medianModelTurns()
                        + " median_tool_calls=" + value.medianToolCalls()));
        System.out.println("OPENALLAY_BENCHMARK_REPORT " + retained.toAbsolutePath());
        System.out.println("OPENALLAY_BENCHMARK_AUDIT "
                + auditPath(retained).toAbsolutePath());
    }

    private static BenchmarkOutcome execute(
            BenchmarkCase testCase,
            int attempt,
            GameGuideAgent agent,
            String prompt,
            boolean stream,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime world,
            boolean includeCommands,
            List<BenchmarkTraceAuditor.TraceRef> traces) {
        String correlationId = "benchmark-" + testCase.id() + "-" + attempt;
        AgentBenchmarkRecorder recorder = new AgentBenchmarkRecorder();
        world.capture(correlationId, new FixtureWorld());
        if (includeCommands) {
            commands.capture(
                    correlationId,
                    GroundedTestFixtures.PLAYER_ID,
                    commandCatalog(),
                    (actor, command, cancellation) -> {
                        recorder.effect("command-submitted:" + command);
                        if (command.startsWith("give ")) {
                            recorder.effect("enchanted-item-created");
                        }
                        commands.acceptFeedback(actor, "Command completed: " + command);
                        return CompletableFuture.completedFuture(null);
                    });
        }
        AgentResult result;
        try {
            result = agent.ask(
                            new AgentRequest(
                                    UUID.randomUUID(),
                                    GroundedTestFixtures.PLAYER_ID,
                                    testCase.id() + "-" + attempt,
                                    testCase.prompt(),
                                    prompt,
                                    benchmarkContext(correlationId),
                                    stream),
                            recorder)
                    .get(6, TimeUnit.MINUTES);
        } catch (Exception failure) {
            result = new AgentResult(
                    dev.openallay.agent.AgentState.FAILED,
                    "",
                    "benchmark_harness_failure",
                    "Benchmark attempt did not reach an Agent terminal result",
                    null);
        }
        traces.add(new BenchmarkTraceAuditor.TraceRef(
                testCase.id(), attempt, result.trace()));
        return recorder.outcome(result);
    }

    private static BenchmarkSelector.Selection select(
            BenchmarkCorpus corpus,
            Map<String, String> environment,
            Set<String> capabilities,
            int repeats) {
        String selection = environment.getOrDefault("OPENALLAY_BENCHMARK_CASES", "").strip();
        Set<String> requested = selection.isEmpty()
                ? Set.of()
                : java.util.Arrays.stream(selection.split(","))
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new BenchmarkSelector().select(
                corpus,
                FIXTURE,
                capabilities,
                requested,
                repeats);
    }

    private static BenchmarkCorpus corpus() {
        var input = LiveAgentBenchmarkAcceptanceTest.class.getClassLoader()
                .getResourceAsStream("data/openallay/benchmarks/core.json");
        if (input == null) {
            throw new IllegalStateException("Bundled benchmark corpus is unavailable");
        }
        return new BenchmarkCorpusCodec().decode(
                new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private static ToolInvocationContext benchmarkContext(String correlationId) {
        ToolInvocationContext base = JavascriptAgentTestFixtures.context(correlationId);
        ArrayList<RegistryEntrySnapshot> registryEntries =
                new ArrayList<>(base.registries().orElseThrow().entries());
        registryEntries.add(food("farmersdelight:vegetable_soup", 8, 0.6D));
        registryEntries.add(food("farmersdelight:bacon_sandwich", 10, 0.8D));
        registryEntries.add(food("farmersdelight:roast_chicken", 16, 1.0D));
        registryEntries.sort(java.util.Comparator.comparing(RegistryEntrySnapshot::id));
        RegistrySnapshot registries =
                new RegistrySnapshot(GroundedTestFixtures.serverEvidence(), registryEntries);

        ArrayList<RecipeEntrySnapshot> recipeEntries =
                new ArrayList<>(base.recipes().orElseThrow().recipes());
        recipeEntries.add(appleCiderRecipe());
        recipeEntries.sort(java.util.Comparator.comparing(RecipeEntrySnapshot::id));
        RecipeSnapshot recipes =
                new RecipeSnapshot(GroundedTestFixtures.serverEvidence(), recipeEntries);

        PlayerSnapshot basePlayer = base.player().orElseThrow();
        PlayerSnapshot player = new PlayerSnapshot(
                basePlayer.uuid(),
                basePlayer.displayName(),
                basePlayer.dimension(),
                basePlayer.position(),
                basePlayer.gameMode(),
                GroundedTestFixtures.inventory(Map.of(
                        "minecraft:apple", 3L,
                        "minecraft:glass_bottle", 1L,
                        "minecraft:sugar", 2L)),
                basePlayer.evidence());

        ObservableGameStateSnapshot state = base.observableGameState().orElseThrow();
        ArrayList<InstalledModMetadata> installed = new ArrayList<>(state.mods().installed());
        installed.add(new InstalledModMetadata(
                "farmersdelight",
                "Farmer's Delight",
                "26.2-fixture",
                "Benchmark content fixture",
                List.of("vectorwing"),
                List.of("MIT"),
                Map.of(),
                "client_and_server",
                List.of()));
        installed.sort(java.util.Comparator.comparing(InstalledModMetadata::id));
        ObservableGameStateSnapshot game = new ObservableGameStateSnapshot(
                state.capturedAt(),
                state.runtime(),
                new ObservableGameStateSnapshot.ModsState(
                        installed, state.mods().evidence(), state.mods().diagnostics()),
                state.options(),
                state.packs(),
                state.shaders(),
                state.diagnostics(),
                new ObservableGameStateSnapshot.PlayerUiState(
                        player,
                        state.player().openScreen(),
                        state.player().openScreenTitle(),
                        state.player().evidence(),
                        state.player().diagnostics()),
                state.worldQueries());
        return new ToolInvocationContext(
                correlationId,
                base.capturedAt(),
                base.caller(),
                java.util.Optional.of(player),
                java.util.Optional.of(registries),
                java.util.Optional.of(recipes),
                java.util.Optional.of(game),
                base.metrics());
    }

    private static Set<String> fixtureCapabilities(boolean includeCommands) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>(FIXTURE_CAPABILITIES);
        if (includeCommands) {
            capabilities.add("commands");
        }
        return Set.copyOf(capabilities);
    }

    private static RegistryEntrySnapshot food(
            String id, int nutrition, double saturationModifier) {
        return new RegistryEntrySnapshot(
                id,
                "item",
                id,
                id.substring(0, id.indexOf(':')),
                "minecraft:registry",
                List.of(),
                Set.of("farmersdelight:foods"),
                Set.of("minecraft:food"),
                Map.of(
                        "minecraft:food",
                        JsonParser.parseString("""
                                {"nutrition":%d,"saturationModifier":%s}
                                """.formatted(nutrition, saturationModifier))));
    }

    private static RecipeEntrySnapshot appleCiderRecipe() {
        return new RecipeEntrySnapshot(
                new RecipeReference(
                        "minecraft:recipe_manager",
                        GroundedTestFixtures.RECIPE_GENERATION,
                        "farmersdelight:apple_cider"),
                "farmersdelight:apple_cider",
                "farmersdelight:cooking",
                new RecipeLayoutSnapshot(3, 1, false),
                "farmersdelight:cooking_pot",
                List.of(
                        ingredient("apples", 2, "minecraft:apple"),
                        ingredient("sugar", 1, "minecraft:sugar"),
                        ingredient("bottle", 1, "minecraft:glass_bottle")),
                List.of(),
                List.of(),
                List.of(new RecipeOutputSnapshot(
                        new ItemStackSnapshot(
                                "farmersdelight:apple_cider", 1, "Apple Cider"),
                        1.0D)),
                List.of(),
                RecipeProcessingSnapshot.unknown(),
                List.of(),
                Map.of(),
                RecipeUnlockState.UNKNOWN,
                GroundedTestFixtures.serverEvidence());
    }

    private static IngredientRequirementSnapshot ingredient(
            String key, int count, String itemId) {
        return new IngredientRequirementSnapshot(
                key,
                count,
                true,
                List.of(new IngredientAlternativeSnapshot(
                        "item", itemId, List.of(itemId))));
    }

    private static JavascriptDataModuleRegistry extensions() {
        JavascriptDataModuleRegistry registry = new JavascriptDataModuleRegistry();
        registry.register("benchmark-fixture", List.of(
                module(
                        "benchmark:machines",
                        "Dynamic machine metadata",
                        """
                        [{"id":"example:crusher","fields":{"energy":"number","speed":"number"}}]
                        """),
                module(
                        "openallay:jei",
                        "Detached recipe-viewer metadata",
                        """
                        {"provider":"jei","categories":["minecraft:crafting"],\
                        "example":{"recipeId":"minecraft:chest"}}
                        """)));
        return registry;
    }

    private static JavascriptDataModule module(String id, String summary, String json) {
        JsonElement value = JsonParser.parseString(json);
        return new JavascriptDataModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public java.lang.reflect.Type valueType() {
                return JsonElement.class;
            }

            @Override
            public String summary() {
                return summary;
            }

            @Override
            public Snapshot capture(ToolInvocationContext context) {
                return new Snapshot(
                        value.deepCopy(), List.of(GroundedTestFixtures.serverEvidence()));
            }
        };
    }

    private static CommandCatalogSnapshot commandCatalog() {
        return new CommandCatalogSnapshot(
                Instant.EPOCH,
                List.of(
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give", "give", "literal", "", false, "",
                                List.of("give <targets> <item> [count]"),
                                List.of("give <targets>")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give <targets>", "targets", "argument",
                                "minecraft:entity", false, "",
                                List.of("give <targets> <item> [count]"),
                                List.of("give <targets> <item>")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give <targets> <item>", "item", "argument",
                                "minecraft:item_stack", true, "",
                                List.of("give <targets> <item> [count]"),
                                List.of("give <targets> <item> <count>")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give <targets> <item> <count>", "count", "argument",
                                "brigadier:integer", true, "",
                                List.of("give <targets> <item> [count]"),
                                List.of())));
    }

    private static Path retain(
            Map<String, String> environment,
            BenchmarkModelProfile modelProfile,
            BenchmarkSelector.Selection selection,
            BenchmarkReport report,
            List<BenchmarkTraceAuditor.TraceRef> traces,
            Gson gson)
            throws Exception {
        ModelConfig config = modelProfile.config();
        URI endpoint = config.baseUri();
        String provider = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort());
        LiveReport retained = new LiveReport(
                FIXTURE,
                environment.getOrDefault("OPENALLAY_PRODUCT_COMMIT", "unknown"),
                provider,
                modelProfile.profileId(),
                modelProfile.canonicalModelId(),
                new SelectionSummary(
                        selection.selected().stream().map(BenchmarkCase::id).toList(),
                        selection.skipped()),
                report,
                List.copyOf(traces));
        Path directory = Path.of(environment.getOrDefault(
                "OPENALLAY_BENCHMARK_OUTPUT",
                "build/reports/openallay/benchmarks"));
        Files.createDirectories(directory);
        Path path = directory.resolve(
                "live-" + Instant.now().toString().replace(':', '-') + ".json");
        Files.writeString(path, gson.toJson(retained), StandardCharsets.UTF_8);
        BenchmarkTraceAudit audit = new BenchmarkTraceAuditor().audit(report, traces);
        Files.writeString(
                auditPath(path),
                gson.toJson(audit),
                StandardCharsets.UTF_8);
        return path;
    }

    private static Path auditPath(Path reportPath) {
        String name = reportPath.getFileName().toString();
        String stem = name.endsWith(".json")
                ? name.substring(0, name.length() - ".json".length())
                : name;
        return reportPath.resolveSibling(stem + "-audit.json");
    }

    private static BenchmarkModelProfile modelProfile(Map<String, String> environment) {
        String profileFile = environment
                .getOrDefault("OPENALLAY_BENCHMARK_PROFILE_FILE", "")
                .strip();
        if (!profileFile.isEmpty()) {
            Path profiles = Path.of(profileFile);
            ToolResult<ModelProfilesConfigLoader.Load> loaded =
                    new ModelProfilesConfigLoader().load(profiles, environment);
            if (loaded instanceof ToolResult.Failure<ModelProfilesConfigLoader.Load> failure) {
                throw new IllegalArgumentException(
                        "Benchmark profile load failed: " + failure.code() + ": "
                                + failure.message());
            }
            ModelProfilesConfigLoader.Load value =
                    ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
            String requested = environment
                    .getOrDefault(
                            "OPENALLAY_BENCHMARK_PROFILE_ID",
                            value.config().defaultProfileId())
                    .strip();
            ResolvedModelProfile profile = value.profiles().stream()
                    .filter(candidate -> candidate.definition().id().equals(requested))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Benchmark profile does not exist: " + requested));
            if (!profile.available()) {
                throw new IllegalArgumentException(
                        "Benchmark profile is unavailable: " + profile.failure().code() + ": "
                                + profile.failure().message());
            }
            return new BenchmarkModelProfile(
                    profile.definition().id(),
                    profile.canonicalModelId(),
                    profile.runtimeConfig());
        }

        ModelProtocol protocol = ModelProtocol.valueOf(environment
                .getOrDefault("OPENALLAY_MODEL_PROTOCOL", "OPENAI_CHAT")
                .toUpperCase());
        ModelConfig config = new ModelConfig(
                true,
                protocol,
                URI.create(required(environment, "OPENALLAY_MODEL_BASE_URL")),
                required(environment, "OPENALLAY_MODEL"),
                SecretValue.of(required(environment, "OPENALLAY_API_KEY")),
                positive(environment.getOrDefault(
                        "OPENALLAY_CONTEXT_WINDOW_TOKENS", "100000"),
                        "OPENALLAY_CONTEXT_WINDOW_TOKENS"),
                positive(environment.getOrDefault(
                        "OPENALLAY_MAX_OUTPUT_TOKENS", "8192"),
                        "OPENALLAY_MAX_OUTPUT_TOKENS"),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5));
        return new BenchmarkModelProfile("environment", config.model(), config);
    }

    private static ModelClient model(ModelConfig config, Gson gson) {
        return switch (config.protocol()) {
            case ANTHROPIC_MESSAGES -> new AnthropicMessagesClient(config, gson);
            case OPENAI_CHAT -> new OpenAiChatClient(config, gson);
        };
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is required");
        return value;
    }

    private static int positive(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private record BenchmarkModelProfile(
            String profileId,
            String canonicalModelId,
            ModelConfig config) {}

    private record SelectionSummary(
            List<String> selectedCaseIds,
            List<BenchmarkSelector.SkippedCase> skipped) {
        private SelectionSummary {
            selectedCaseIds = List.copyOf(selectedCaseIds);
            skipped = List.copyOf(skipped);
        }
    }

    private record LiveReport(
            String fixture,
            String productCommit,
            String provider,
            String profileId,
            String canonicalModelId,
            SelectionSummary selection,
            BenchmarkReport benchmark,
            List<BenchmarkTraceAuditor.TraceRef> traces) {}

    private static final class FixtureWorld implements WorldObservationCoordinator {
        private static final EvidenceMetadata EVIDENCE = new EvidenceMetadata(
                DataAuthority.DETERMINISTIC_TEST,
                DataCompleteness.COMPLETE,
                Instant.EPOCH,
                "openallay:benchmark_world",
                "openallay:live_benchmark_fixture",
                "26.2",
                "common-test",
                Map.of());

        @Override
        public CompletionStage<BlockObservation> inspect(
                WorldObservationRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            WorldPosition position = request.bounds().from();
            return CompletableFuture.completedFuture(new BlockObservation(
                    request.bounds(),
                    List.of(new WorldBlockSnapshot(
                            "minecraft:oak_log",
                            position,
                            new WorldPosition(0, 0, 0),
                            Map.of("axis", "y"),
                            "",
                            false)),
                    coverage(request.bounds()),
                    EVIDENCE));
        }

        @Override
        public CompletionStage<EntityObservation> entities(
                WorldObservationRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new EntityObservation(
                    request.bounds(),
                    List.of(new WorldEntitySummary(
                            "benchmark-cow",
                            "minecraft:cow",
                            "Cow",
                            request.bounds().from(),
                            true)),
                    coverage(request.bounds()),
                    EVIDENCE));
        }

        @Override
        public CompletionStage<WorldEntitySnapshot> entity(
                String observationId, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new WorldEntitySnapshot(
                    observationId,
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "minecraft:cow",
                    "Cow",
                    new WorldPosition(1, 64, 1),
                    Map.of("health", 10.0D, "age", 0),
                    EVIDENCE));
        }

        private static WorldObservationCoverage coverage(WorldBounds bounds) {
            return new WorldObservationCoverage(
                    bounds.volume(), bounds.volume(), true, List.of());
        }
    }
}
