package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkillRequirementsTest {
    @Test
    void parsesScalarDeclarationsAndPreservesOtherMetadataAndLegacyDependencies() {
        SkillMetadata metadata = new SkillParser().parse(source("""
                  openallay/requires-capabilities: "future:unknown openallay:unrestricted_javascript"
                  openallay/requires-extensions: "sample:missing"
                  openallay/requires-skills: "other-guide"
                  openallay/required-mods: "legacy_mod, another_mod"
                  arbitrary/key: "retained"
                """, "openallay:run_javascript")).metadata();

        assertEquals(new RequirementSet(Set.of("future:unknown", "openallay:unrestricted_javascript"),
                Set.of("sample:missing"), Set.of("other-guide")), metadata.requirements());
        assertEquals(Set.of("legacy_mod", "another_mod"), metadata.requiredMods());
        assertEquals(Set.of("openallay:run_javascript"), metadata.allowedTools());
        assertEquals("retained", metadata.attributes().get("arbitrary/key"));
    }

    @Test
    void unmetRequirementsDoNotFilterCatalogLoadOrExplicitRead() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        assertTrue(repository.reload(List.of(source("""
                  openallay/requires-capabilities: "future:unknown openallay:unrestricted_javascript"
                  openallay/requires-extensions: "sample:missing"
                  openallay/requires-skills: "missing-guide"
                """, "")), Set.of()));
        assertEquals(1, repository.metadata().size());
        assertTrue(repository.snapshot(Set.of()).find("guide").isPresent());
        assertInstanceOf(ToolResult.Success.class, new LoadSkillTool(repository.snapshot(Set.of()))
                .invoke(ToolInvocationContext.developmentConsole("test"), new LoadSkillTool.Input("guide")));
        assertTrue(repository.diagnostics().isEmpty());
    }

    @Test
    void emptyMetadataDefaultsAndMalformedKnownValuesRejectAtParseTime() {
        assertEquals(RequirementSet.EMPTY, new SkillParser().parse(source("""
                  openallay/requires-capabilities: ""
                  openallay/requires-extensions: " "
                  openallay/requires-skills: ""
                """, "")).metadata().requirements());
        for (String value : List.of("a,b", "[a]", "Upper", "a a")) {
            assertThrows(IllegalArgumentException.class, () -> new SkillParser().parse(source(
                    "  openallay/requires-capabilities: \"" + value + "\"\n", "")));
        }
    }

    private static SkillSource source(String metadata, String tools) {
        String markdown = "---\nname: guide\ndescription: Guide\nmetadata:\n" + metadata
                + "allowed-tools: \"" + tools + "\"\n---\nRead this guide.\n";
        return new SkillSource("test", "guide/SKILL.md", Map.of("guide/SKILL.md", markdown));
    }
}
