package dev.openallay.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionCapabilityPolicyStoreTest {
    @TempDir Path temporary;

    @Test
    void policyIsDefaultOffPerOwnerAndDeeplyImmutable() {
        Set<String> scopes = new HashSet<>(Set.of("sample:world_actions"));
        Map<String, Set<String>> grants = new HashMap<>();
        grants.put("sample:extension", scopes);
        ExtensionCapabilityPolicy policy = new ExtensionCapabilityPolicy(grants);
        scopes.clear();
        grants.clear();

        assertTrue(policy.allows("sample:extension", "sample:world_actions"));
        assertFalse(policy.allows("other:extension", "sample:world_actions"));
        assertFalse(policy.allows("sample:extension", "sample:read"));
        assertEquals(Map.of(), ExtensionCapabilityPolicy.defaults().grants());
        assertThrows(UnsupportedOperationException.class, () -> policy.grants().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> policy.grants().get("sample:extension").clear());
        assertFalse(policy.withGrant("sample:extension", "sample:world_actions", false)
                .allows("sample:extension", "sample:world_actions"));
        assertTrue(policy.allows("sample:extension", "sample:world_actions"));
    }

    @Test
    void policyRejectsBlankNonNamespacedWhitespaceAndWildcardIds() {
        for (String invalid : List.of("", "sample", "Sample:scope", "sample:*", " sample:scope")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ExtensionCapabilityPolicy(Map.of(invalid, Set.of())));
            assertThrows(IllegalArgumentException.class,
                    () -> new ExtensionCapabilityPolicy(Map.of("sample:extension", Set.of(invalid))));
        }
    }

    @Test
    void missingFileLoadsEmptyWithoutWriting() {
        Path path = temporary.resolve("extension-capabilities.json");
        ExtensionCapabilityPolicyStore store = new ExtensionCapabilityPolicyStore(path);
        assertEquals(ExtensionCapabilityPolicy.defaults(), success(store.load()));
        assertFalse(Files.exists(path));
    }

    @Test
    void strictDecoderRejectsShapeTypesDuplicateNamesAndDuplicateGrants() {
        for (String invalid : List.of(
                "{}", "[]", "null", "{\"schemaVersion\":1,\"grants\":{}}",
                "{\"grants\":null}", "{\"grants\":[]}",
                "{\"grants\":{\"sample:extension\":null}}",
                "{\"grants\":{\"sample:extension\":[true]}}",
                "{\"grants\":{\"sample:extension\":[42]}}",
                "{\"grants\":{\"sample:extension\":[{}]}}",
                "{\"grants\":{\"sample:extension\":[\"sample:*\"]}}",
                "{\"grants\":{\"sample:extension\":[\"sample:write\",\"sample:write\"]}}",
                "{\"grants\":{},\"grants\":{}}",
                "{\"grants\":{\"sample:extension\":[],\"sample:extension\":[]}}",
                "{grants:{}}", "{\"grants\":{}} {}", "{\"grants\":{/*comment*/}}")) {
            ToolResult<?> result = ExtensionCapabilityPolicyStore.decode(new StringReader(invalid));
            assertEquals("invalid_extension_capability_config",
                    assertInstanceOf(ToolResult.Failure.class, result).code(), invalid);
        }
        assertEquals(ExtensionCapabilityPolicy.defaults(), success(
                ExtensionCapabilityPolicyStore.decode(new StringReader("{\"grants\":{}}"))));
    }

    @Test
    void savesCanonicalDocumentBeforePublishingAndRoundTrips() throws Exception {
        Path path = temporary.resolve("extension-capabilities.json");
        ExtensionCapabilityPolicy candidate = new ExtensionCapabilityPolicy(Map.of(
                "zeta:extension", Set.of("zeta:write", "zeta:read"),
                "alpha:extension", Set.of("alpha:write")));
        AtomicReference<ExtensionCapabilityPolicyStore> reference = new AtomicReference<>();
        ExtensionCapabilityPolicyStore store = new ExtensionCapabilityPolicyStore(path, (target, contents) -> {
            assertEquals(ExtensionCapabilityPolicy.defaults(), reference.get().current());
            new dev.openallay.settings.AtomicSettingsFile().replace(target, contents);
        });
        reference.set(store);
        assertEquals(candidate, success(store.save(candidate)));
        assertEquals(candidate, store.current());
        assertEquals(candidate, success(new ExtensionCapabilityPolicyStore(path).load()));
        String encoded = Files.readString(path);
        assertTrue(encoded.indexOf("alpha:extension") < encoded.indexOf("zeta:extension"));
        assertTrue(encoded.indexOf("zeta:read") < encoded.indexOf("zeta:write"));
        assertFalse(encoded.contains("Version"));
    }

    @Test
    void failedSaveRetainsPriorBytesAndAuthorityWhileFailedLoadClearsAuthority() throws Exception {
        Path path = temporary.resolve("extension-capabilities.json");
        ExtensionCapabilityPolicy prior = new ExtensionCapabilityPolicy(
                Map.of("sample:extension", Set.of("sample:write")));
        success(new ExtensionCapabilityPolicyStore(path).save(prior));
        String priorBytes = Files.readString(path);
        ExtensionCapabilityPolicyStore store = new ExtensionCapabilityPolicyStore(path,
                (target, contents) -> { throw new SettingsWriteException(); });
        assertEquals(prior, success(store.load()));
        assertEquals("settings_write_failed", assertInstanceOf(ToolResult.Failure.class,
                store.save(ExtensionCapabilityPolicy.defaults())).code());
        assertEquals(priorBytes, Files.readString(path));
        assertEquals(prior, store.current());
        Files.writeString(path, "{}");
        assertInstanceOf(ToolResult.Failure.class, store.load());
        assertEquals(ExtensionCapabilityPolicy.defaults(), store.current());
    }

    @SuppressWarnings("unchecked")
    private static ExtensionCapabilityPolicy success(ToolResult<ExtensionCapabilityPolicy> result) {
        return ((ToolResult.Success<ExtensionCapabilityPolicy>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }
}
