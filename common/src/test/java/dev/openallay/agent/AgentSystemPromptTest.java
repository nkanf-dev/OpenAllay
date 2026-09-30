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
    void unrestrictedClientRequestPromptDescribesJavaAuthorityAndSideEffects() {
        String prompt = AgentSystemPrompt.compose(" ",
                CoreJavascriptContract.render(MinecraftAgentHostGraph.declaredOnlyCatalog()), true);
        assertTrue(prompt.contains("Java.type(...)` gives scripts Java/JVM access"));
        assertTrue(prompt.contains("irreversible side effects"));
        assertTrue(prompt.contains("credentials available to the JVM"));
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
        assertTrue(prompt.contains("Only registered operations are authorized"));
        assertTrue(prompt.contains("JavaScript uses the default isolated mode"));
    }
}
