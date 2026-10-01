package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.schema.CoreJavascriptContract;
import org.junit.jupiter.api.Test;

final class AgentSystemPromptTest {
    @Test
    void givesTaskAdaptiveGuidanceWithoutReplacingCapabilityBoundaries() {
        String prompt = AgentSystemPrompt.compose("""
                  <skill>
                    <name>inspect-game-state</name>
                    <description>Correlate game settings</description>
                  </skill>
                """);

        assertTrue(prompt.contains("<name>inspect-game-state</name>"));
        assertFalse(prompt.contains("single most-specific matching Skill"));
        assertFalse(prompt.contains("at most one up front"));
        assertFalse(prompt.contains("at most one focused discovery call"));
        assertFalse(prompt.contains("KubeJS Rhino fork"));
        assertFalse(prompt.contains("var origin = mc.player.position"));
        assertFalse(prompt.contains("retry at most once"));
        assertFalse(prompt.contains("Do not poll"));
        assertFalse(prompt.contains("server-hosted"));
        assertTrue(prompt.indexOf("## SKILL GUIDANCE") < prompt.indexOf("## AVAILABLE SKILLS"));
        assertTrue(prompt.indexOf("## AVAILABLE SKILLS") < prompt.indexOf("## EXECUTION"));
    }

    @Test
    void encouragesTaskCompletionAndRecoveryWithoutRepeatingRestrictions() {
        String prompt = AgentSystemPrompt.compose("");
        assertTrue(prompt.contains("Use available capabilities to complete the player's task"));
        assertTrue(prompt.contains("Correct recoverable errors and continue"));
        assertTrue(prompt.contains("Report observed outcomes and relevant uncertainty"));
        assertFalse(prompt.contains("Never treat unavailable, partial, empty, stale, or conflicting data"));
        assertFalse(prompt.contains("never invent arbitrary URLs or paths"));
        assertFalse(prompt.contains("Distinguish execution status from data coverage"));
    }

    @Test
    void keepsProgramInputTypedDataAndRequestScopedWorkspaceContracts() {
        String prompt = AgentSystemPrompt.compose("");
        assertTrue(prompt.contains("source is the JavaScript program text, not source attribution"));
        assertTrue(prompt.contains("Ordinary computations need no Minecraft read"));
        assertTrue(prompt.contains("Declared mc schema:"));
        assertTrue(prompt.contains("mc.items: array<record>"));
        assertTrue(prompt.contains("Copy a host array before sort"));
        assertTrue(prompt.contains("Workspace handles belong only to the active request"));
    }

    @Test
    void everyExecutionModeRequestsBothPlayerLanguageIntentFields() {
        String contract = CoreJavascriptContract.render(MinecraftAgentHostGraph.declaredOnlyCatalog());
        for (boolean unrestricted : new boolean[] {false, true}) {
            String prompt = AgentSystemPrompt.compose("", contract, unrestricted);
            assertTrue(prompt.contains("Include title and description on every run_javascript call"));
            assertTrue(prompt.contains("player's language explaining the intended work"));
            assertTrue(prompt.contains("Return explicit JSON-friendly results"));
        }
    }

    @Test
    void unrestrictedClientRequestPromptDescribesJavaAuthorityWithoutSandboxRestrictions() {
        String prompt = AgentSystemPrompt.compose(" ",
                CoreJavascriptContract.render(MinecraftAgentHostGraph.declaredOnlyCatalog()), true);
        assertTrue(prompt.contains("Java.type(...)` gives scripts Java/JVM access"));
        assertTrue(prompt.contains("including files, network, processes, and live Minecraft objects"));
        assertTrue(prompt.contains("Use Java APIs as needed for the player's task"));
        assertTrue(prompt.contains("Do not disclose credentials or secret-bearing payloads"));
    }

    @Test
    void enabledPromptIncludesCoreGuidanceWithoutContradictingJavaAuthority() {
        String contract = CoreJavascriptContract.render(MinecraftAgentHostGraph.declaredOnlyCatalog());
        String enabled = AgentSystemPrompt.compose("", contract, true, "Use Java.type and new StringBuilder.");
        assertTrue(enabled.contains("## UNRESTRICTED JAVASCRIPT GUIDANCE"));
        assertTrue(enabled.contains("Use Java.type and new StringBuilder."));
        assertTrue(enabled.contains("mc: immutable captured Minecraft data"));
        assertFalse(enabled.contains("never invent arbitrary URLs or paths"));
        assertFalse(enabled.contains("run_javascript analyzes detached Minecraft data."));
        String disabled = AgentSystemPrompt.compose("", contract, false, "Use Java.type and new StringBuilder.");
        assertFalse(disabled.contains("UNRESTRICTED JAVASCRIPT GUIDANCE"));
        assertFalse(disabled.contains("Use Java.type and new StringBuilder."));
        assertTrue(disabled.contains("JavaScript uses the default isolated mode"));
    }

    @Test
    void commandBindingAvailabilityDoesNotChangeJavaAuthority() {
        String contract = CoreJavascriptContract.render(MinecraftAgentHostGraph.declaredOnlyCatalog());
        for (boolean unrestricted : new boolean[] {false, true}) {
            String available = AgentSystemPrompt.compose("", contract, unrestricted, "", true);
            assertTrue(available.contains("commands.list(), commands.describe(path), and commands.run(text)"));
            assertTrue(available.contains("top-level JavaScript methods for this request"));
            assertFalse(available.contains("The commands binding is not present"));

            String absent = AgentSystemPrompt.compose("", contract, unrestricted, "", false);
            assertTrue(absent.contains("The commands binding is not present for this request"));
            assertTrue(absent.contains("Use available capabilities to complete the player's task"));
            String mode = unrestricted ? "Use Java APIs as needed for the player's task"
                    : "JavaScript uses the default isolated mode";
            assertTrue(available.contains(mode));
            assertTrue(absent.contains(mode));
        }
        assertFalse(AgentSystemPrompt.compose("", contract, true, "")
                .contains("The commands binding is not present"));
    }

    @Test
    void acceptsTheDescriptorDerivedCoreContractExplicitly() {
        String contract = CoreJavascriptContract.render(
                MinecraftAgentHostGraph.declaredOnlyCatalog());

        String prompt = AgentSystemPrompt.compose(" ", contract);

        assertTrue(prompt.contains(contract));
        assertTrue(prompt.indexOf("## CORE JAVASCRIPT") < prompt.indexOf("## AVAILABLE SKILLS"));
    }

    @Test
    void suppliesAnExplicitEmptySkillCatalogWithoutChangingAuthority() {
        String prompt = AgentSystemPrompt.compose("  ");
        assertTrue(prompt.contains("<none/>"));
        assertTrue(prompt.contains("Use the current Tool definitions' exact names and schemas"));
        assertTrue(prompt.contains("JavaScript uses the default isolated mode"));
    }
}
