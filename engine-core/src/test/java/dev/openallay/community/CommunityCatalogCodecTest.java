package dev.openallay.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class CommunityCatalogCodecTest {
    private final CommunityCatalogCodec codec = new CommunityCatalogCodec();

    @Test
    void decodesStrictSchemaTwoSkillCatalogInDeterministicOrder() {
        CommunityCatalogManifest manifest = codec.decode("""
                {
                  "schemaVersion": 2,
                  "kind": "skill",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "packages": [
                    {
                      "id": "zeta",
                      "displayName": "Zeta Workflow",
                      "description": "Explains the Zeta workflow.",
                      "publisher": "Zeta Team",
                      "version": "1.0.0",
                      "archive": "https://example.test/zeta.zip",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "compatibility": {"minecraft": "26.2", "openallayApi": "0.2"},
                      "source": "https://example.test/zeta"
                    },
                    {
                      "id": "alpha",
                      "displayName": "Alpha Workflow",
                      "description": "Explains the Alpha workflow.",
                      "publisher": "Alpha Team",
                      "version": "2.0.0",
                      "archive": "https://example.test/alpha.zip",
                      "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "compatibility": {"minecraft": "26.2", "openallayApi": "0.2"},
                      "source": "https://example.test/alpha"
                    }
                  ]
                }
                """);

        assertEquals(Instant.parse("2026-07-25T00:00:00Z"), manifest.generatedAt());
        assertEquals(CommunityCatalogManifest.SCHEMA_VERSION, manifest.schemaVersion());
        assertEquals(java.util.List.of("alpha", "zeta"),
                manifest.packages().stream().map(CommunityCatalogManifest.PackageEntry::id).toList());
        assertEquals("Alpha Workflow", manifest.packages().getFirst().displayName());
        assertEquals("Explains the Alpha workflow.",
                manifest.packages().getFirst().description());
        assertEquals("Alpha Team", manifest.packages().getFirst().publisher());
        assertEquals(URI.create("https://example.test/alpha.zip"),
                manifest.packages().getFirst().archive());
    }

    @Test
    void rejectsUnknownFieldsVersionsDuplicateIdsAndUnsafeUris() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"packages\"", "\"unknown\":true,\"packages\"")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(validSchemaOne()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"schemaVersion\":2", "\"schemaVersion\":3")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("https://example.test/a.zip", "http://example.test/a.zip")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("]", "," + validEntry("alpha") + "]")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"publisher\":\"Publisher\",", "")));
    }

    @Test
    void minecraftCompatibilityKeepsExactShapeAndUsesExistingVersionRanges() {
        for (String range : java.util.List.of("26.2", "[26.2]", "[1.12.2,26.2]")) {
            CommunityCatalogManifest manifest = codec.decode(valid()
                    .replace("\"minecraft\":\"26.2\"", "\"minecraft\":\"" + range + "\""));
            assertEquals(range, manifest.packages().getFirst().compatibility().minecraft());
            assertEquals(manifest, codec.decode(codec.encode(manifest)));
        }
        var compatibility = codec.decode(valid().replace("26.2", "[1.12.2,26.2]"))
                .packages().getFirst().compatibility();
        for (String target : java.util.List.of("1.12.2", "1.16.5", "26.2")) {
            assertTrue(compatibility.supports(target, "0.2"));
            assertFalse(compatibility.supports(target, "0.4.0"));
        }
        assertFalse(compatibility.supports("1.12.1", "0.2"));
        assertFalse(compatibility.supports("26.3", "0.2"));
        for (String malformed : java.util.List.of("[]", "(1.12.2)", "[26.2,1.12.2]", "[1.12.2,26.2")) {
            assertThrows(IllegalArgumentException.class,
                    () -> codec.decode(valid().replace("26.2", malformed)));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"minecraft\":\"26.2\"", "\"minecraft\":[\"26.2\"]")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"minecraft\":\"26.2\"", "\"minecraftRange\":\"26.2\"")));
    }

    private static String valid() {
        return """
                {"schemaVersion":2,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[%s]}
                """.formatted(validEntry("alpha"));
    }

    private static String validEntry(String id) {
        return """
                {"id":"%s","displayName":"Alpha Skill",
                 "description":"A player-facing workflow.","publisher":"Publisher",
                 "version":"1.0.0","archive":"https://example.test/a.zip",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/a"}
                """.formatted(id);
    }

    private static String validSchemaOne() {
        return """
                {"schemaVersion":1,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[{"id":"alpha","version":"1.0.0",
                 "archive":"https://example.test/a.zip",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/a"}]}
                """;
    }
}
