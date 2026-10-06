package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class UniversalExtensionManifestTest {
    @Test void decodesExactExternalShapeAndOptionalAdvisoryRequirements() {
        var json = UniversalExtensionFixtures.manifest("test:extension", "community.Entry");
        var manifest = UniversalExtensionManifest.decode(json);
        assertEquals("community.Entry", manifest.entrypoint());
        assertEquals(UniversalExtensionFixtures.descriptor("test:extension"), manifest.descriptor());
        var object = dev.openallay.json.JsonTrees.parse(json).getAsJsonObject();
        object.add("requirements", dev.openallay.json.JsonTrees.parse(
                "{\"capabilities\":[\"test:read\"],\"extensions\":[\"test:other\"],\"skills\":[\"test-skill\"]}"));
        assertEquals(java.util.Set.of("test:read"), UniversalExtensionManifest.decode(object.toString())
                .descriptor().requirements().capabilities());
    }
    @Test void rejectsLegacyShapeUnknownMissingNullAndDuplicateMembers() {
        String good = UniversalExtensionFixtures.manifest("test:extension", "community.Entry");
        for (String invalid : List.of(good.replace("\"schemaVersion\":2", "\"schemaVersion\":1"),
                good.replace("\"schemaVersion\":2", "\"schemaVersion\":2.5"),
                good.replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"modIds\":[]"),
                good.replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"schemaVersion\":2"),
                good.replace("\"entrypoint\":\"community.Entry\",", ""),
                good.replace("\"requiredHostFeatures\":[]", "\"requiredHostFeatures\":null"),
                good.replace("\"requiredHostFeatures\":[]", "\"requiredHostFeatures\":[\"a\",\"a\"]"),
                good.replace("\"summary\":\"Test Extension\"", "\"summary\":null"),
                good.replace("\"support\":{", "\"requirements\":null,\"support\":{") )) {
            assertThrows(IllegalArgumentException.class, () -> UniversalExtensionManifest.decode(invalid));
        }
    }
    @Test void rejectsMalformedNonFiniteTrailingAndBadEntrypoint() {
        var good = UniversalExtensionFixtures.manifest("test:extension", "community.Entry");
        for (String invalid : List.of(good + "{}", good.replace("8", "NaN"),
                good.replace("community.Entry", "../Escape"), good.replace("8", "1e999"),
                good.replace("\"schemaVersion\"", "schemaVersion"))) {
            assertThrows(IllegalArgumentException.class, () -> UniversalExtensionManifest.decode(invalid));
        }
    }
}
