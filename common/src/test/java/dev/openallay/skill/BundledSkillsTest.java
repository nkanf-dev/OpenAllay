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
                "openallay:calculate_craftability",
                "openallay:load_skill");
        SkillRepository repository = new SkillRepository(new SkillParser(), tools);
        assertTrue(repository.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));
        assertEquals(BundledSkillLoader.NAMES.stream().sorted().toList(), repository.metadata().stream()
                .map(SkillMetadata::name).sorted().toList());
        for (SkillMetadata metadata : repository.metadata()) {
            SkillDocument document = repository.find(metadata.name()).orElseThrow();
            assertFalse(document.instructions().isBlank());
            assertTrue(metadata.allowedTools().stream()
                    .allMatch(tool -> tool.equals("openallay:run_javascript")
                            || tool.equals("openallay:calculate_craftability")));
            assertFalse(repository.metadataPrompt().contains(document.instructions()));
        }

        SkillDocument analysis = repository.find("analyze-game-data").orElseThrow();
        assertEquals(Set.of(
                "references/datasets.md",
                "references/examples.md",
                "references/pipelines.md"), analysis.references().keySet());
        assertTrue(repository.metadataPrompt().contains("<name>analyze-game-data</name>"));
        assertFalse(repository.metadataPrompt().contains("ordinary modern JavaScript"));

        SkillDocument fallback = repository.find("answer-modded-minecraft-question")
                .orElseThrow();
        assertTrue(fallback.instructions().contains("Choose one"));
        assertTrue(fallback.instructions().contains("one `run_javascript` program"));
        SkillDocument gameState = repository.find("inspect-game-state").orElseThrow();
        assertTrue(gameState.instructions().contains("`mc.game.diagnostics`"));
        assertFalse(gameState.instructions().contains("`openallay:inspect_game_state`"));
    }
}
