package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkillRepositoryTest {
    @Test
    void progressivelyLoadsBodyAndDeclaredReferences() {
        SkillRepository repository = repository();
        assertTrue(repository.reload(java.util.List.of(valid("answer-guide", "Secret body")), Set.of()));

        assertFalse(repository.metadataPrompt().contains("Secret body"));
        SkillDocument loaded = repository.find("answer-guide").orElseThrow();
        assertEquals("Secret body", loaded.instructions());
        assertEquals("Ground every claim.", loaded.references().get("references/policy.md"));
    }

    @Test
    void failedReloadRetainsLastGoodSnapshot() {
        SkillRepository repository = repository();
        assertTrue(repository.reload(java.util.List.of(valid("answer-guide", "first")), Set.of()));
        SkillSource invalid = new SkillSource(
                "bad-pack",
                "bad/SKILL.md",
                Map.of("bad/SKILL.md", frontmatter("answer-guide", "second", "[unknown:tool]")));

        assertFalse(repository.reload(java.util.List.of(invalid), Set.of()));
        assertEquals("first", repository.find("answer-guide").orElseThrow().instructions());
        assertEquals("skill_validation_failed", repository.diagnostics().getFirst().code());
    }

    @Test
    void rejectsScriptsUrlsAndMissingReferences() {
        SkillRepository repository = repository();
        SkillSource scripted = new SkillSource(
                "scripted",
                "s/skill.md",
                Map.of(
                        "s/skill.md", frontmatter("scripted", "body", "[openallay:find_recipes]"),
                        "s/scripts/run.sh", "danger"));
        assertFalse(repository.reload(java.util.List.of(scripted), Set.of()));

        String remote = frontmatter("remote", "body", "[openallay:find_recipes]")
                .replace("references/policy.md", "https://example.invalid/policy.md");
        assertFalse(repository.reload(java.util.List.of(new SkillSource(
                "remote", "r/SKILL.md", Map.of("r/SKILL.md", remote))), Set.of()));
    }

    @Test
    void filtersSkillsWhoseRequiredModsAreAbsent() {
        SkillRepository repository = repository();
        SkillSource source = valid("quest-guide", "body");
        String entry = source.files().get(source.entryPath()).replace(
                "allowed-tools:", "metadata:\n  openallay/required-mods: \"ftbquests\"\nallowed-tools:");
        source = new SkillSource(source.provenance(), source.entryPath(), Map.of(
                source.entryPath(), entry,
                "quest-guide/references/policy.md", "Ground every claim."));

        assertTrue(repository.reload(java.util.List.of(source), Set.of()));
        assertTrue(repository.metadata().isEmpty());
        assertEquals("required_mod_unavailable", repository.diagnostics().getFirst().code());
    }

    @Test
    void snapshotFiltersDisabledSkillsAndDoesNotObserveLaterReload() {
        SkillRepository repository = repository();
        assertTrue(repository.reload(java.util.List.of(
                valid("original-skill", "original"),
                valid("disabled-skill", "disabled")), Set.of()));

        SkillCatalogSnapshot snapshot = repository.snapshot(Set.of("disabled-skill"));
        assertTrue(repository.reload(
                java.util.List.of(valid("replacement-skill", "replacement")), Set.of()));

        assertTrue(snapshot.find("original-skill").isPresent());
        assertTrue(snapshot.find("disabled-skill").isEmpty());
        assertTrue(snapshot.find("replacement-skill").isEmpty());
        assertFalse(snapshot.metadataPrompt().contains("disabled-skill"));
        assertTrue(repository.find("replacement-skill").isPresent());
    }

    @Test
    void bothReloadPathsRetainRegisteredExternalSourcesReferencesAndDenyState() {
        SkillRepository repository = repository();
        SkillSource external = valid("extension-guide", "extension body");
        repository.registerExternal(List.of(external), Set.of());
        SkillCatalogSnapshot original = repository.snapshot(Set.of());
        SkillCatalogSnapshot denied = repository.snapshot(Set.of("extension-guide"));
        repository.setRuntimeDisabledSkills(Set.of("extension-guide"));

        for (int generation = 0; generation < 3; generation++) {
            assertTrue(repository.reload(List.of(valid("bundled-guide", "bundled body")), Set.of()));
            assertEquals(2, repository.metadata().size());
            assertTrue(repository.reload(List.of(valid("bundled-guide", "bundled body")),
                    new FilesystemSkillLoader.LoadResult(List.of(), List.of()), Set.of()));
            assertEquals(2, repository.metadata().size());
            assertEquals("extension body", repository.find("extension-guide").orElseThrow().instructions());
            assertEquals("Ground every claim.", repository.find("extension-guide").orElseThrow()
                    .references().get("references/policy.md"));
            assertTrue(repository.snapshot(Set.of()).find("extension-guide").isEmpty());
            assertTrue(repository.snapshotIncludingRuntimeDisabled(Set.of("extension-guide"))
                    .find("extension-guide").isEmpty());
        }
        assertEquals(List.of(external), repository.externalSources());
        assertThrows(UnsupportedOperationException.class, () -> repository.externalSources().clear());
        assertTrue(original.find("extension-guide").isPresent());
        assertTrue(denied.find("extension-guide").isEmpty());
    }

    @Test
    void localOverridesExternalAndRemovalRestoresRegisteredOriginalWithReferences() {
        SkillRepository repository = repository();
        SkillSource external = valid("extension-guide", "extension body");
        repository.registerExternal(List.of(external), Set.of());
        SkillSource local = withOrigin(valid("extension-guide", "local body"), SkillSource.Origin.LOCAL);
        assertTrue(repository.reload(List.of(),
                new FilesystemSkillLoader.LoadResult(List.of(local), List.of()), Set.of()));
        assertEquals("local body", repository.find("extension-guide").orElseThrow().instructions());
        assertEquals(1, repository.metadata().size());
        assertEquals(List.of(external), repository.externalSources());

        assertTrue(repository.reload(List.of(), new FilesystemSkillLoader.LoadResult(List.of(), List.of()), Set.of()));
        assertEquals("extension body", repository.find("extension-guide").orElseThrow().instructions());
        assertEquals(external.origin(), repository.find("extension-guide").orElseThrow().metadata().origin());
        assertEquals("Ground every claim.", repository.find("extension-guide").orElseThrow()
                .references().get("references/policy.md"));
        assertEquals(1, repository.metadata().size());
    }

    @Test
    void registrationAfterLocalLoadingPreservesOverrideAndStillReservesExternalIdentity() {
        SkillRepository repository = repository();
        SkillSource local = withOrigin(valid("extension-guide", "local body"), SkillSource.Origin.LOCAL);
        assertTrue(repository.reload(List.of(),
                new FilesystemSkillLoader.LoadResult(List.of(local), List.of()), Set.of()));
        SkillSource external = valid("extension-guide", "extension body");
        repository.registerExternal(List.of(external), Set.of());

        assertEquals("local body", repository.find("extension-guide").orElseThrow().instructions());
        assertEquals(List.of(external), repository.externalSources());
        assertThrows(IllegalArgumentException.class,
                () -> repository.registerExternal(List.of(external), Set.of()));
        assertTrue(repository.reload(List.of(), Set.of()));
        assertEquals("extension body", repository.find("extension-guide").orElseThrow().instructions());
        assertEquals(1, repository.metadata().size());
    }

    @Test
    void duplicateExternalOrBundledIdentityRejectsAtomicallyEvenBehindLocalOverride() {
        SkillRepository repository = repository();
        SkillSource bundled = withOrigin(valid("bundled-guide", "bundled"), SkillSource.Origin.BUNDLED);
        SkillSource local = withOrigin(valid("bundled-guide", "local"), SkillSource.Origin.LOCAL);
        assertTrue(repository.reload(List.of(bundled),
                new FilesystemSkillLoader.LoadResult(List.of(local), List.of()), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.registerExternal(List.of(valid("bundled-guide", "external")), Set.of()));
        SkillSource external = valid("extension-guide", "extension");
        assertThrows(IllegalArgumentException.class,
                () -> repository.registerExternal(List.of(external, external), Set.of()));
        assertTrue(repository.externalSources().isEmpty());
        assertEquals("local", repository.find("bundled-guide").orElseThrow().instructions());
        repository.registerExternal(List.of(external), Set.of());
        assertFalse(repository.reload(List.of(valid("extension-guide", "collision")), Set.of()));
        assertEquals("extension", repository.find("extension-guide").orElseThrow().instructions());
        assertEquals(List.of(external), repository.externalSources());
    }

    private static SkillSource withOrigin(SkillSource source, SkillSource.Origin origin) {
        return new SkillSource(source.provenance(), source.entryPath(), source.files(), origin);
    }

    private static SkillRepository repository() {
        return new SkillRepository(new SkillParser(), Set.of("openallay:find_recipes"));
    }

    private static SkillSource valid(String name, String body) {
        return new SkillSource(
                "test-pack",
                name + "/SKILL.md",
                Map.of(
                        name + "/SKILL.md", frontmatter(name, body, "[openallay:find_recipes]"),
                        name + "/references/policy.md", "Ground every claim."));
    }

    private static String frontmatter(String name, String body, String tools) {
        return """
                ---
                name: %s
                description: Answer a guide question
                allowed-tools: "%s"
                ---
                %s
                """.formatted(name, tools.substring(1, tools.length() - 1), body);
    }
}
