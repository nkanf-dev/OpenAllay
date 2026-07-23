package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.tool.ToolAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AgentSkillManagerTest {
    @TempDir
    Path root;

    @Test
    void createsUpdatesAndDeletesOnlyValidatedManagedPackages() throws Exception {
        SkillRepository repository =
                new SkillRepository(new SkillParser(), Set.of("openallay:run_javascript"));
        repository.reload(java.util.List.of(), Set.of());
        AgentSkillManager manager = new AgentSkillManager(
                root,
                repository,
                new SkillParser(),
                java.util.List.of(),
                Set.of(),
                Set.of("openallay:run_javascript"));

        AgentSkillManager.Result created = manager.create(
                "rank-weapons",
                markdown("rank-weapons", "first instructions"),
                Map.of("references/examples.md", "example"));

        assertEquals(AgentSkillManager.Operation.CREATE, created.operation());
        assertEquals(SkillSource.Origin.LOCAL, created.origin());
        assertEquals("first instructions", repository.find("rank-weapons").orElseThrow().instructions());
        assertTrue(Files.isRegularFile(root.resolve("rank-weapons/SKILL.md")));

        AgentSkillManager.Result updated = manager.update(
                "rank-weapons",
                markdown("rank-weapons", "updated instructions"),
                Map.of());

        assertEquals(AgentSkillManager.Operation.UPDATE, updated.operation());
        assertEquals("updated instructions", repository.find("rank-weapons").orElseThrow().instructions());
        assertFalse(Files.exists(root.resolve("rank-weapons/references/examples.md")));

        AgentSkillManager.Result deleted = manager.delete("rank-weapons");
        assertEquals(AgentSkillManager.Operation.DELETE, deleted.operation());
        assertTrue(repository.find("rank-weapons").isEmpty());
        assertFalse(Files.exists(root.resolve("rank-weapons")));
    }

    @Test
    void rejectsEscapingReferencesAndUnavailableToolDependencies() {
        SkillRepository repository =
                new SkillRepository(new SkillParser(), Set.of("openallay:run_javascript"));
        AgentSkillManager manager = new AgentSkillManager(
                root,
                repository,
                new SkillParser(),
                java.util.List.of(),
                Set.of(),
                Set.of("openallay:run_javascript"));

        SkillManagementException escape = assertThrows(
                SkillManagementException.class,
                () -> manager.create(
                        "unsafe",
                        markdown("unsafe", "instructions"),
                        Map.of("references/../../secret.md", "no")));
        assertEquals("skill_reference_invalid", escape.code());

        SkillManagementException dependency = assertThrows(
                SkillManagementException.class,
                () -> manager.create(
                        "unknown-tool",
                        markdown("unknown-tool", "instructions").replace(
                                "openallay:run_javascript", "other:missing"),
                        Map.of()));
        assertEquals("skill_dependency_unavailable", dependency.code());
        assertFalse(Files.exists(root.resolve("unknown-tool")));
    }

    @Test
    void toolDeclaresManagedWriteInsteadOfGeneralFilesystemAuthority() {
        SkillRepository repository =
                new SkillRepository(new SkillParser(), Set.of("openallay:run_javascript"));
        ManageSkillTool tool = new ManageSkillTool(new AgentSkillManager(
                root,
                repository,
                new SkillParser(),
                java.util.List.of(),
                Set.of(),
                Set.of("openallay:run_javascript")));

        assertEquals(ToolAccess.MANAGED_WRITE, tool.descriptor().access());
        assertTrue(tool.descriptor().description().contains("cannot access arbitrary paths"));
    }

    private static String markdown(String name, String body) {
        return """
                ---
                name: %s
                description: Rank captured weapons.
                allowed-tools: "openallay:run_javascript"
                ---
                %s
                """.formatted(name, body);
    }
}
