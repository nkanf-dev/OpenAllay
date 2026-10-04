package dev.openallay.settings.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.community.CommunityCatalogManifest;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkillCommunityViewTest {
    @Test
    void versionedInstallIsCurrentOnlyWhenPackageVersionMatches() {
        assertFalse(view(true, Optional.of("1.2.0")).updateAvailable());
        assertTrue(view(true, Optional.of("1.1.0")).updateAvailable());
    }

    @Test
    void legacyInstallWithoutVersionOffersOneTrackedCommunityUpdate() {
        assertTrue(view(true, Optional.empty()).updateAvailable());
        assertFalse(view(false, Optional.empty()).updateAvailable());
    }

    private static SkillCommunityView.Package view(
            boolean installed, Optional<String> installedVersion) {
        CommunityCatalogManifest.PackageEntry entry =
                new CommunityCatalogManifest.PackageEntry(
                        "demo",
                        "Demo Skill",
                        "A demo vertical workflow.",
                        "Test Publisher",
                        "1.2.0",
                        URI.create("https://example.test/demo.zip"),
                        "a".repeat(64),
                        new CommunityCatalogManifest.Compatibility("26.2", "0.2"),
                        URI.create("https://example.test/demo"));
        return SkillCommunityView.Package.from(
                entry, installed, installedVersion, true);
    }
}
