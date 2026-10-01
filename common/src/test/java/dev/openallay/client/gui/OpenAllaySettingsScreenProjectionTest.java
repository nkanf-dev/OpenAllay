package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.client.gui.settings.SettingsSection;
import dev.openallay.client.gui.settings.ExtensionSettingsProjection;
import dev.openallay.client.gui.settings.SkillSettingsProjection;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.settings.ClientSettingsSnapshot;
import dev.openallay.settings.SettingsOperation;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.settings.skill.SkillSettingsView;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OpenAllaySettingsScreenProjectionTest {
    @Test
    void modelProjectionPreservesOrderAndNeverContainsCredentialValue() {
        ModelProfileDefinition alpha = profile("alpha");
        ModelProfileDefinition beta = profile("beta");
        ModelProfilesConfig config = new ModelProfilesConfig(
                ModelProfilesConfig.SCHEMA_VERSION, "alpha", List.of(alpha, beta));
        ModelProfileSettingsView models = ModelProfileSettingsView.from(
                config,
                List.of(
                        new ModelProfileSettingsView.Resolution(alpha, true, 256_000, null),
                        new ModelProfileSettingsView.Resolution(beta, true, 256_000, null)),
                java.util.Set.of("ALPHA_KEY", "BETA_KEY"),
                null,
                null);
        ClientSettingsSnapshot snapshot = new ClientSettingsSnapshot(
                0,
                GuideDisplayConfig.defaults(),
                models,
                SettingsOperation.idle(),
                null);

        OpenAllaySettingsScreen.Projection projection =
                OpenAllaySettingsScreen.project(snapshot);

        assertEquals(List.of("alpha", "beta"), projection.models().stream()
                .map(dev.openallay.client.gui.settings.ModelSettingsProjection.ModelCard::profileId)
                .toList());
        assertFalse(projection.toString().contains("ALPHA_KEY"));
        assertFalse(projection.toString().contains("secret-value"));
        assertTrue(projection.sections().contains(SettingsSection.EXTENSIONS));
        assertTrue(projection.sections().contains(SettingsSection.SKILLS));
        assertTrue(projection.sections().contains(SettingsSection.ABOUT));
        assertFalse(projection.sections().stream()
                .anyMatch(section -> section.name().equals("RECIPES")));
        assertEquals(0, projection.recipes().sources().size());
        assertEquals(0, projection.skills().skills().size());
        assertEquals("openallay:run_javascript", projection.extensions().runtime().id());
        assertTrue(projection.extensions().roots().stream()
                .anyMatch(root -> root.name().equals("items")
                        && root.availability().equals("REQUEST_SCOPED")));
        assertEquals(
                List.of("openallay:crafting"),
                projection.extensions().modules().stream()
                        .map(dev.openallay.client.gui.settings.ExtensionSettingsProjection.ModuleCard::id)
                        .toList());
        assertFalse(projection.extensions().experimentalCommands());
        assertFalse(projection.general().debugMode());
        assertTrue(projection.general().animationsEnabled());
        assertTrue(projection.history().actions().stream()
                .noneMatch(dev.openallay.client.gui.settings.HistorySettingsProjection.ActionRow::enabled));
        assertTrue(projection.diagnostics().debug().isEmpty());
        assertTrue(dev.openallay.client.gui.settings.SettingsLayout
                .calculate(960, 600).wide());
        assertTrue(dev.openallay.client.gui.settings.SettingsLayout
                .calculate(480, 320).showBack());
    }

    @Test
    void automaticOutputProjectionKeepsExplicitMillionContextAndUnknownDisabledOutput() {
        var definition = new ModelProfileDefinition("luna", "Luna", true,
                ModelProtocol.OPENAI_CHAT, URI.create("https://provider.example/v1/"),
                "gpt-6-luna", "env:LUNA_KEY", 1_000_000, null,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var models = ModelProfileSettingsView.from(
                new ModelProfilesConfig(ModelProfilesConfig.SCHEMA_VERSION, "luna", List.of(definition)),
                List.of(new ModelProfileSettingsView.Resolution(
                        definition, true, true, 1_000_000, 128_000, null)),
                java.util.Set.of("LUNA_KEY"), null, null);
        var snapshot = new ClientSettingsSnapshot(0, GuideDisplayConfig.defaults(), models,
                SettingsOperation.idle(), null);
        var card = OpenAllaySettingsScreen.project(snapshot).models().getFirst();
        assertEquals(1_000_000, card.contextWindowTokens());
        assertEquals(128_000, card.maxOutputTokens());
        assertEquals(null, definition.maxOutputTokens());

        var disabled = new ModelProfileDefinition("off", "Off", false,
                ModelProtocol.OPENAI_CHAT, URI.create("https://provider.example/v1/"),
                "unpublished-model", "env:OFF_KEY", null, null,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var unavailable = ModelProfileSettingsView.from(
                new ModelProfilesConfig(ModelProfilesConfig.SCHEMA_VERSION, "off", List.of(disabled)),
                List.of(new ModelProfileSettingsView.Resolution(disabled, false, false, null, null,
                        new dev.openallay.guide.GuideFailure("model_disabled", "Disabled"))),
                java.util.Set.of(), null, null);
        var unknown = OpenAllaySettingsScreen.project(new ClientSettingsSnapshot(
                0, GuideDisplayConfig.defaults(), unavailable, SettingsOperation.idle(), null))
                .models().getFirst();
        assertEquals(null, unknown.contextWindowTokens());
        assertEquals(null, unknown.maxOutputTokens());
    }

    @Test
    void screenshotControlsAreInertUnlessTheExplicitE2ePropertyIsEnabled() {
        String key = dev.openallay.guide.e2e.GuideClientE2EConfig.ENABLED;
        String prior = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertFalse(OpenAllaySettingsScreen.e2eControlsEnabled());
            System.setProperty(key, "true");
            assertTrue(OpenAllaySettingsScreen.e2eControlsEnabled());
        } finally {
            if (prior == null) System.clearProperty(key);
            else System.setProperty(key, prior);
        }
    }

    @Test
    void communityTabsRefreshOnceWhileKeepingCachedCatalogsVisible() {
        SkillSettingsProjection.Community cachedSkills = SkillSettingsProjection.from(
                        SkillSettingsView.empty(),
                        new SkillCommunityView(
                                true,
                                Optional.of(Instant.EPOCH),
                                List.of(),
                                Optional.empty()),
                        false)
                .community();
        ExtensionSettingsProjection.CatalogCard cachedExtensions =
                new ExtensionSettingsProjection.CatalogCard(
                        true, true, Instant.EPOCH.toString(), "", "");

        assertTrue(OpenAllaySettingsScreen.shouldRefreshSkillCommunity(
                cachedSkills, false));
        assertFalse(OpenAllaySettingsScreen.shouldRefreshSkillCommunity(
                cachedSkills, true));
        assertTrue(OpenAllaySettingsScreen.shouldRefreshExtensionCommunity(
                cachedExtensions, false));
        assertFalse(OpenAllaySettingsScreen.shouldRefreshExtensionCommunity(
                cachedExtensions, true));
        assertFalse(OpenAllaySettingsScreen.shouldRefreshExtensionCommunity(
                new ExtensionSettingsProjection.CatalogCard(
                        false, false, "", "", ""),
                false));
        SkillSettingsProjection.Community failedSkills = SkillSettingsProjection.from(
                        SkillSettingsView.empty(),
                        new SkillCommunityView(
                                false,
                                Optional.empty(),
                                List.of(),
                                Optional.of(new SkillCommunityView.Notice(
                                        "catalog_refresh_failed", "offline"))),
                        false)
                .community();
        assertFalse(OpenAllaySettingsScreen.shouldRefreshSkillCommunity(
                failedSkills, false));
        assertFalse(OpenAllaySettingsScreen.shouldRefreshExtensionCommunity(
                new ExtensionSettingsProjection.CatalogCard(
                        true,
                        false,
                        "",
                        "catalog_refresh_failed",
                        "offline"),
                false));
    }

    private static ModelProfileDefinition profile(String id) {
        return new ModelProfileDefinition(
                id,
                id.toUpperCase(),
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                "vendor/" + id,
                id.toUpperCase() + "_KEY",
                256_000,
                4_096,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
    }
}
