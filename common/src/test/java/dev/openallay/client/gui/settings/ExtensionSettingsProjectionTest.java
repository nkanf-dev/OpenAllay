package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.settings.extension.ExtensionSettingsView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ExtensionSettingsProjectionTest {
    @Test
    void descriptorOnlyProjectionIncludesRootsModulesAndDefaultOffCommands() {
        ExtensionSettingsProjection projection = ExtensionSettingsProjection.from(
                ExtensionSettingsView.from(new JavascriptDataModuleRegistry()),
                CommandCapabilityConfig.defaults(),
                true);

        assertEquals("openallay:run_javascript", projection.runtime().id());
        assertEquals(
                java.util.List.of("source", "handles", "title", "description"),
                projection.runtime().parameters());
        assertTrue(projection.roots().stream()
                .anyMatch(root -> root.name().equals("items")
                        && root.availability().equals("REQUEST_SCOPED")
                        && root.schema().startsWith("array<")));
        assertEquals(
                java.util.List.of("openallay:crafting"),
                projection.modules().stream().map(ExtensionSettingsProjection.ModuleCard::id).toList());
        assertTrue(projection.adapters().isEmpty());
        assertEquals(
                java.util.List.of("openallay:core"),
                projection.extensions().stream()
                        .map(ExtensionSettingsProjection.ExtensionCard::id)
                        .toList());
        assertFalse(projection.experimentalCommands());
        assertTrue(projection.debugMode());
        assertEquals(1, projection.installed().size());
        assertTrue(projection.community().isEmpty());
        assertFalse(projection.catalog().configured());
    }

    @Test
    void toggleOnlyChangesExperimentalCommandChoice() {
        ExtensionSettingsProjection original = ExtensionSettingsProjection.from(
                ExtensionSettingsView.defaults(),
                CommandCapabilityConfig.defaults(),
                false);

        ExtensionSettingsProjection toggled = original.toggleExperimentalCommands();

        assertTrue(toggled.experimentalCommands());
        assertEquals(original.runtime(), toggled.runtime());
        assertEquals(original.roots(), toggled.roots());
        assertEquals(original.modules(), toggled.modules());
        assertEquals(original.adapters(), toggled.adapters());
        assertEquals(original.extensions(), toggled.extensions());
    }

    @Test
    void separatesInstalledAndCommunityCardsWithActionAndCatalogState() {
        ExtensionSettingsView base =
                ExtensionSettingsView.from(new JavascriptDataModuleRegistry());
        ExtensionSettingsView.Extension community = new ExtensionSettingsView.Extension(
                "community:sample",
                "Sample",
                "1.2.0",
                "Community",
                "Sample Extension",
                ExtensionSettingsView.State.COMMUNITY,
                List.of("fabric"),
                "[26.2,26.3)",
                "[0.2,0.3)",
                "https://example.test/sample",
                new ExtensionSettingsView.Contributions(
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                "",
                new ExtensionSettingsView.PackageInfo(
                        true,
                        "1.2.0",
                        "https://example.test/sample.jar",
                        "a".repeat(64),
                        false,
                        true));
        ExtensionSettingsView view = new ExtensionSettingsView(
                base.roots(),
                base.bundledModules(),
                base.adapters(),
                List.of(base.extensions().getFirst(), community),
                new ExtensionSettingsView.Catalog(
                        true,
                        true,
                        Optional.of(Instant.parse("2026-07-25T00:00:00Z")),
                        Optional.empty()));

        ExtensionSettingsProjection projection = ExtensionSettingsProjection.from(
                view, CommandCapabilityConfig.defaults(), false);

        assertEquals(
                List.of("openallay:core"),
                projection.installed().stream()
                        .map(ExtensionSettingsProjection.ExtensionCard::id)
                        .toList());
        assertEquals(
                List.of("community:sample"),
                projection.community().stream()
                        .map(ExtensionSettingsProjection.ExtensionCard::id)
                        .toList());
        assertTrue(projection.findCommunity("community:sample").isPresent());
        assertTrue(projection.findInstalled("community:sample").isEmpty());
        assertTrue(projection.findInstalled("openallay:core").isPresent());
        assertTrue(projection.findCommunity("openallay:core").isEmpty());
        assertTrue(projection.community().getFirst().installable());
        assertTrue(projection.catalog().available());
    }

    @Test
    void incompatibleCommunityCardDoesNotExposeAnotherLoaderArtifact() {
        ExtensionSettingsView base =
                ExtensionSettingsView.from(new JavascriptDataModuleRegistry());
        ExtensionSettingsView.Extension incompatible = new ExtensionSettingsView.Extension(
                "community:neoforge",
                "NeoForge package",
                "1.0.0",
                "Community",
                "NeoForge-only Extension",
                ExtensionSettingsView.State.INCOMPATIBLE,
                List.of("neoforge"),
                "[26.2,26.3)",
                "[0.2,0.3)",
                "community",
                new ExtensionSettingsView.Contributions(
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                "incompatible_loader",
                ExtensionSettingsView.PackageInfo.catalogOnly("1.0.0"));
        ExtensionSettingsView view = new ExtensionSettingsView(
                base.roots(),
                base.bundledModules(),
                base.adapters(),
                List.of(base.extensions().getFirst(), incompatible),
                new ExtensionSettingsView.Catalog(
                        true,
                        true,
                        Optional.of(Instant.parse("2026-07-25T00:00:00Z")),
                        Optional.empty()));

        ExtensionSettingsProjection.ExtensionCard card =
                ExtensionSettingsProjection.from(
                                view, CommandCapabilityConfig.defaults(), true)
                        .find("community:neoforge")
                        .orElseThrow();

        assertTrue(card.catalogListed());
        assertEquals("1.0.0", card.availableVersion());
        assertEquals("", card.artifact());
        assertEquals("", card.sha256());
        assertFalse(card.installable());
    }
    @Test
    void unmetRequirementsNeverChangeActiveStateOrInstallableUpdate() {
        var requirements = new dev.openallay.requirement.RequirementSet(
                java.util.Set.of("example:unknown"), java.util.Set.of("example:missing"),
                java.util.Set.of("missing-skill"));
        var extension = new ExtensionSettingsView.Extension(
                "example:active", "Active package", "1.0.0", "Example", "Still available",
                ExtensionSettingsView.State.ACTIVE, List.of("fabric"), "[26.2,26.3)",
                "[0.2,0.3)", "local", new ExtensionSettingsView.Contributions(
                        List.of(), List.of(), List.of(), List.of(), List.of()), "",
                new ExtensionSettingsView.PackageInfo(true, "1.1.0", "https://example.test/update.jar",
                        "b".repeat(64), true, true), requirements);
        var view = new ExtensionSettingsView(List.of(), List.of(), List.of(), List.of(extension),
                ExtensionSettingsView.Catalog.unavailable());
        var card = ExtensionSettingsProjection.from(view, CommandCapabilityConfig.defaults(), false)
                .findInstalled("example:active").orElseThrow();

        assertEquals(ExtensionSettingsView.State.ACTIVE, card.state());
        assertTrue(card.installable());
        assertTrue(card.updateAvailable());
        assertEquals(3, card.requirements().rows().size());
        assertEquals(dev.openallay.requirement.RequirementStatus.UNKNOWN,
                card.requirements().rows().getFirst().status());
        assertTrue(card.requirements().continueEnabled());
    }
}
