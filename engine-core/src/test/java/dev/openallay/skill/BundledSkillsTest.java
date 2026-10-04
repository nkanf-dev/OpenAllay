package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class BundledSkillsTest {
    @Test
    void everyBundledSkillIsValidGroundedAndProgressivelyLoadable() {
        Set<String> tools = Set.of(
                "openallay:run_javascript",
                "openallay:load_skill");
        SkillRepository repository = new SkillRepository(new SkillParser(), tools);
        assertTrue(repository.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));
        assertEquals(BundledSkillLoader.NAMES.stream().sorted().toList(), repository.metadata().stream()
                .map(SkillMetadata::name).sorted().toList());
        for (SkillMetadata metadata : repository.metadata()) {
            SkillDocument document = repository.find(metadata.name()).orElseThrow();
            assertFalse(document.instructions().isBlank());
            assertTrue(metadata.attributes().get("openallay/version").matches("0\\.2\\.\\d+"));
            assertTrue(metadata.name().equals("analyze-game-data")
                    || metadata.description().startsWith("Use when "));
            assertTrue(metadata.allowedTools().stream()
                    .allMatch(tool -> tool.equals("openallay:run_javascript")));
            assertFalse(repository.metadataPrompt().contains(document.instructions()));
            assertFalse(dev.openallay.agent.AgentSystemPrompt.compose(repository.metadataPrompt())
                    .contains(document.instructions()));
            assertFalse(document.instructions().contains("one JavaScript program"));
        }

        assertFalse(repository.find("analyze-game-data").isPresent());
        assertFalse(repository.metadataPrompt().contains("<name>analyze-game-data</name>"));
        assertFalse(repository.metadataPrompt().contains("ordinary modern JavaScript"));

        assertTrue(repository.find("answer-modded-minecraft-question").isEmpty());
        SkillDocument gameState = repository.find("inspect-game-state").orElseThrow();
        assertTrue(gameState.instructions().contains("`mc.game.diagnostics`"));
        assertFalse(gameState.instructions().contains("roots:"));
        assertTrue(gameState.instructions().contains("core top-level `world`"));
        assertFalse(gameState.instructions().contains("`openallay:inspect_game_state`"));
        SkillDocument commands = repository.find("run-game-commands").orElseThrow();
        assertEquals(Set.of("references/commands.md"), commands.references().keySet());
        assertTrue(commands.instructions().contains("`commands.list()`"));
        assertTrue(commands.instructions().contains("never rolled back"));
        assertTrue(commands.instructions().contains("`commands.run(...)` is synchronous"));
        assertTrue(commands.instructions().contains("return commands.run(command);"));
        String commandReference = commands.references().get("references/commands.md");
        assertTrue(commandReference.contains("<component-id>=<SNBT value>"));
        assertTrue(commandReference.contains("commands.describe(\"give <targets> <item>\")"));
        assertTrue(commandReference.contains("call-count cap"));
        assertTrue(commandReference.contains("player permission"));
        assertTrue(commandReference.contains("no universal command-result packet"));
        assertTrue(commandReference.contains("no_feedback"));
        assertTrue(commandReference.contains("sequence"));
        assertTrue(commandReference.contains("messages"));
        assertFalse(commandReference.contains("enchanted-item-created"));
        assertFalse(commandReference.contains("benchmark artifact"));
        assertFalse(repository.find("diagnose-missing-recipe").orElseThrow().instructions()
                .contains("one materially corrected analysis"));
    }

    @Test
    void nonCommandGuidanceKeepsUsageFactsWithoutGenericStopRules() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of("openallay:run_javascript"));
        assertTrue(repository.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));

        String gameState = repository.find("inspect-game-state").orElseThrow().instructions();
        assertTrue(gameState.contains("`mc.game.diagnostics`"));
        assertTrue(gameState.contains("core top-level `world`"));
        assertFalse(gameState.contains("stop rather than guessing"));
        assertFalse(gameState.contains("remain outside this Skill"));

        SkillDocument java = repository.snapshot(Set.of()).forRequest(true)
                .find("unrestricted-javascript").orElseThrow();
        assertTrue(java.instructions().contains("Java.type(\"fully.qualified.ClassName\")"));
        assertTrue(java.instructions().contains("JSON-friendly"));
        assertTrue(java.instructions().contains("does not change the\nmodel-facing result view"));
        assertFalse(java.instructions().contains("bypasses OpenAllay JavaScript execution and result budgets"));
        assertTrue(java.instructions().contains("owning thread"));
        assertFalse(java.instructions().contains("verify them rather than inventing them"));
        assertFalse(java.references().get("references/java-jvm.md")
                .contains("Do not guess a universal"));
    }

    @Test
    void unrestrictedGuidanceIsCapturedPerRequestAndDeniedForServerCallbacks() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of("openallay:run_javascript"));
        assertTrue(repository.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));
        SkillCatalogSnapshot captured = repository.snapshot(Set.of());
        assertTrue(captured.find("unrestricted-javascript").isEmpty());
        assertFalse(captured.metadataPrompt().contains("unrestricted-javascript"));
        SkillCatalogSnapshot enabled = captured.forRequest(true);
        assertTrue(enabled.find("unrestricted-javascript").isPresent());
        assertEquals(Set.of("references/java-jvm.md"),
                enabled.find("unrestricted-javascript").orElseThrow().references().keySet());
        repository.setRuntimeDisabledSkills(Set.of("unrestricted-javascript"));
        assertTrue(enabled.find("unrestricted-javascript").isPresent());
        assertTrue(repository.snapshot(Set.of()).forRequest(true).find("unrestricted-javascript").isEmpty());
        assertTrue(captured.forRequest(false).find("unrestricted-javascript").isEmpty());
        repository.setRuntimeDisabledSkills(Set.of());
        assertTrue(repository.snapshot(Set.of("unrestricted-javascript")).forRequest(true)
                .find("unrestricted-javascript").isEmpty());
        var disabled = dev.openallay.context.ToolInvocationContext.developmentConsole("server-callback");
        var authorized = new dev.openallay.context.ToolInvocationContext("local-enabled", disabled.capturedAt(),
                disabled.caller(), disabled.player(), disabled.registries(), disabled.recipes(),
                disabled.observableGameState(), disabled.metrics(), true);
        LoadSkillTool tool = new LoadSkillTool(enabled);
        assertTrue(tool.invoke(disabled, new LoadSkillTool.Input("unrestricted-javascript"))
                instanceof dev.openallay.tool.ToolResult.Failure<?>);
        assertTrue(tool.invoke(disabled, new LoadSkillTool.Input("unrestricted-javascript", "references/java-jvm.md"))
                instanceof dev.openallay.tool.ToolResult.Failure<?>);
        assertTrue(tool.invoke(authorized, new LoadSkillTool.Input("unrestricted-javascript"))
                instanceof dev.openallay.tool.ToolResult.Success<?>);
        assertTrue(tool.invoke(authorized, new LoadSkillTool.Input("unrestricted-javascript", "references/java-jvm.md"))
                instanceof dev.openallay.tool.ToolResult.Success<?>);
    }

    @Test
    void experimentalCommandSkillIsAbsentFromCapturedCatalogWhileDisabled() {
        SkillRepository repository = new SkillRepository(
                new SkillParser(), Set.of("openallay:run_javascript"));
        assertTrue(repository.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));
        repository.setRuntimeDisabledSkills(Set.of("run-game-commands"));

        assertFalse(repository.snapshot(Set.of())
                .find("run-game-commands").isPresent());
        repository.setRuntimeDisabledSkills(Set.of());
        assertTrue(repository.snapshot(Set.of())
                .find("run-game-commands").isPresent());
    }
}
