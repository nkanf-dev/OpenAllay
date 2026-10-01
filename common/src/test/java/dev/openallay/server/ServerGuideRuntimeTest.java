package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ServerGuideRuntimeTest {
    @Test
    void remoteSkillPromptDescribesOnlyTheActualClientFrozenCatalog() {
        SkillRepository client = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(client.reload(java.util.List.of(new dev.openallay.skill.SkillSource(
                "client-extension", "client-only/SKILL.md", java.util.Map.of("client-only/SKILL.md",
                        "---\nname: client-only\ndescription: Use <client> facts & workflows\n---\n"
                                + "Private client Skill instructions."))), Set.of()));
        var manifest = new dev.openallay.skill.LoadSkillTool(client.snapshot(Set.of()), "client")
                .catalogManifest();
        String prompt = ServerGuideRuntime.systemPrompt(manifest);
        assertTrue(prompt.contains("<name>client-only</name>"));
        assertTrue(prompt.contains("Use &lt;client&gt; facts &amp; workflows"));
        assertFalse(prompt.contains("Private client Skill instructions."));
        assertFalse(prompt.contains("<name>unrestricted-javascript</name>"));
    }

    @Test
    void commandSkillIsAdvertisedOnlyForRequestsWithTheCapturedCapabilityMarker() {
        SkillRepository skills = new SkillRepository(
                new SkillParser(), Set.of("openallay:run_javascript"));
        assertTrue(skills.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));
        skills.setRuntimeDisabledSkills(Set.of("run-game-commands", "inspect-game-state"));

        String ordinaryPrompt = ServerGuideRuntime.systemPrompt(skills, false);
        String commandPrompt = ServerGuideRuntime.systemPrompt(skills, true);

        assertFalse(ordinaryPrompt.contains("<name>run-game-commands</name>"));
        assertTrue(commandPrompt.contains("<name>run-game-commands</name>"));
        assertFalse(commandPrompt.contains("<name>inspect-game-state</name>"));
        assertFalse(commandPrompt.contains("<name>unrestricted-javascript</name>"));
        assertFalse(ordinaryPrompt.contains("<name>unrestricted-javascript</name>"));
        assertTrue(commandPrompt.contains("JavaScript uses the default isolated mode"));
        assertTrue(commandPrompt.contains("commands.run(text) are available as top-level"));
        assertTrue(ordinaryPrompt.contains("The commands binding is not present for this request"));
    }
}
