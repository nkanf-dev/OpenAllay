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
    }
}
