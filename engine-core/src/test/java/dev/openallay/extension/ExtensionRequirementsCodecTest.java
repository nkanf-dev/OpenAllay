package dev.openallay.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import dev.openallay.extension.install.ExtensionPackageManifestCodec;
import dev.openallay.requirement.RequirementCodec;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ExtensionRequirementsCodecTest {
    private static final String PACKAGE = """
            {"schemaVersion":1,"id":"sample:one","name":"Sample","version":"1.0.0",
             "provider":"Provider","summary":"Sample extension","loaders":["fabric"],
             "minecraftVersionRange":"[26.2,26.3)","openAllayApiVersionRange":"[0.2,0.3)",
             "modIds":["sample_one"],"source":"community"}
            """;
    private static final String CATALOG = """
            {"schemaVersion":2,"kind":"extension","generatedAt":"2026-09-30T00:00:00Z",
             "extensions":[{"id":"sample:one","name":"Sample","version":"1.0.0",
             "provider":"Provider","summary":"Sample extension",
             "minecraftVersionRange":"[26.2,26.3)","openAllayApiVersionRange":"[0.2,0.3)",
             "artifacts":[{"loader":"fabric","artifact":"https://example.test/sample.jar",
             "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "modIds":["sample_one"]}],"source":"community"}]}
            """;
    private static final RequirementSet DECLARATIONS = new RequirementSet(
            Set.of("future:unknown"), Set.of("sample:missing"), Set.of("missing-guide"));

    @Test
    void packageAndCatalogRoundTripAndLoaderDescriptorsPreserveDeclarations() {
        JsonObject packageJson = JsonParser.parseString(PACKAGE).getAsJsonObject();
        packageJson.add("requirements", RequirementCodec.encode(DECLARATIONS));
        var packages = new ExtensionPackageManifestCodec();
        var manifest = packages.decode(packageJson.toString());
        assertEquals(DECLARATIONS, manifest.descriptor().requirements());
        assertEquals(manifest, packages.decode(packages.encode(manifest)));

        JsonObject catalogJson = JsonParser.parseString(CATALOG).getAsJsonObject();
        catalogEntry(catalogJson).add("requirements", RequirementCodec.encode(DECLARATIONS));
        var catalogs = new ExtensionCatalogCodec();
        var catalog = catalogs.decode(catalogJson.toString());
        ExtensionCatalogEntry entry = catalog.extensions().getFirst();
        assertEquals(DECLARATIONS, entry.requirements());
        assertEquals(DECLARATIONS, entry.descriptor().requirements());
        assertEquals(DECLARATIONS, entry.descriptorFor("fabric").requirements());
        assertEquals(manifest.descriptor(), entry.descriptorFor("fabric"));
        assertEquals(catalog, catalogs.decode(catalogs.encode(catalog)));
    }

    @Test
    void oldSchemasAndConstructorsRetainEmptyRequirementsAndOmitOptionalOutput() throws Exception {
        var packages = new ExtensionPackageManifestCodec();
        var packageManifest = packages.decode(PACKAGE);
        assertEquals(RequirementSet.EMPTY, packageManifest.descriptor().requirements());
        assertFalse(packages.encode(packageManifest).contains("requirements"));
        var catalogs = new ExtensionCatalogCodec();
        var catalog = catalogs.decode(CATALOG);
        assertEquals(RequirementSet.EMPTY, catalog.extensions().getFirst().requirements());
        assertFalse(catalogs.encode(catalog).contains("requirements"));
        assertNotNull(OpenAllayExtensionDescriptor.class.getConstructor(String.class, String.class,
                String.class, String.class, String.class, Set.class, String.class, String.class, String.class));
        var oldDescriptor = new OpenAllayExtensionDescriptor("sample:old", "Old", "1", "Provider",
                "Summary", Set.of("fabric"), "[26.2,26.3)", "[0.2,0.3)", "source");
        assertEquals(RequirementSet.EMPTY, oldDescriptor.requirements());
        var oldEntry = new ExtensionCatalogEntry("sample:old", "Old", "1", "Provider", "Summary",
                "[26.2,26.3)", "[0.2,0.3)", catalog.extensions().getFirst().artifacts(), "source");
        assertEquals(RequirementSet.EMPTY, oldEntry.requirements());
    }

    @Test
    void optionalExpansionDoesNotAdmitUnknownTopLevelFieldsVersionsOrMalformedRequirements() {
        var packages = new ExtensionPackageManifestCodec();
        var catalogs = new ExtensionCatalogCodec();
        for (String invalid : new String[] {"null", "{\"unknown\":[]}",
                "{\"capabilities\":[\"future:unknown\",\"future:unknown\"]}"}) {
            JsonObject packageJson = JsonParser.parseString(PACKAGE).getAsJsonObject();
            packageJson.add("requirements", JsonParser.parseString(invalid));
            assertThrows(IllegalArgumentException.class, () -> packages.decode(packageJson.toString()));
            JsonObject catalogJson = JsonParser.parseString(CATALOG).getAsJsonObject();
            catalogEntry(catalogJson).add("requirements", JsonParser.parseString(invalid));
            assertThrows(IllegalArgumentException.class, () -> catalogs.decode(catalogJson.toString()));
        }
        assertThrows(IllegalArgumentException.class, () -> packages.decode(
                PACKAGE.replace("\"schemaVersion\":1", "\"schemaVersion\":99")));
        assertThrows(IllegalArgumentException.class, () -> catalogs.decode(
                CATALOG.replace("\"schemaVersion\":2", "\"schemaVersion\":99")));
        assertThrows(IllegalArgumentException.class, () -> packages.decode(
                PACKAGE.replace("\"schemaVersion\":1", "\"unknown\":[],\"schemaVersion\":1")));
        assertThrows(IllegalArgumentException.class, () -> catalogs.decode(
                CATALOG.replace("\"summary\":", "\"unknown\":[],\"summary\":")));
    }

    @Test
    void advisoryDeclarationsDoNotPreventExtensionActivationOrPublishAuthority() {
        var modules = new JavascriptModuleCatalog(Map.of());
        var data = new JavascriptDataModuleRegistry();
        var skills = new SkillRepository(new SkillParser(), Set.of());
        var registry = new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"), data, modules, skills, Set.of());
        JsonObject packageJson = JsonParser.parseString(PACKAGE).getAsJsonObject();
        packageJson.add("requirements", RequirementCodec.encode(DECLARATIONS));
        var descriptor = new ExtensionPackageManifestCodec().decode(packageJson.toString()).descriptor();
        var result = registry.register(new OpenAllayExtension() {
            public OpenAllayExtensionDescriptor descriptor() { return descriptor; }
            public OpenAllayExtensionContribution contribution() { return OpenAllayExtensionContribution.empty(); }
        });
        assertEquals(OpenAllayExtensionState.ACTIVE, result.state());
        assertTrue(modules.ids().isEmpty());
        assertTrue(skills.metadata().isEmpty());
        assertEquals(DECLARATIONS, registry.snapshot().extensions().getFirst().descriptor().requirements());
    }

    private static JsonObject catalogEntry(JsonObject catalog) {
        return catalog.getAsJsonArray("extensions").get(0).getAsJsonObject();
    }
}
