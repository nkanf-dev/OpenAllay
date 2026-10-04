package dev.openallay.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CapabilityPolicyLoaderTest {
    @TempDir Path temporary;

    private final CapabilityPolicyLoader loader = new CapabilityPolicyLoader();
    private final CapabilityPolicyWriter writer = new CapabilityPolicyWriter();

    @Test
    void retainsUnknownDisabledIdentitiesAndCanonicalizesOrder() {
        CapabilityPolicy policy = success(loader.load(new StringReader("""
                {
                  "disabledTools": ["openallay:get_recipe", "future:tool"],
                  "disabledSkills": ["future-skill", "recipe-helper"]
                }
                """))).value();

        assertEquals(Set.of("future:tool", "openallay:get_recipe"), policy.disabledTools());
        assertEquals(Set.of("future-skill", "recipe-helper"), policy.disabledSkills());
        String encoded = writer.encode(policy);
        assertEquals(policy, success(loader.load(new StringReader(encoded))).value());
        assertFalse(encoded.indexOf("future:tool") > encoded.indexOf("openallay:get_recipe"));
        assertFalse(encoded.indexOf("future-skill") > encoded.indexOf("recipe-helper"));
    }

    @Test
    void missingFileReturnsDefaultsWithoutWriting() {
        Path missing = temporary.resolve("capabilities.json");

        assertEquals(CapabilityPolicy.defaults(), success(loader.load(missing)).value());
        assertFalse(java.nio.file.Files.exists(missing));
    }

    @Test
    void rejectsMissingAndExtraFields() {
        assertFailure("""
                {"disabledTools":[]}
                """);
        assertFailure("""
                {"disabledTools":[],"disabledSkills":[],"extra":true}
                """);
    }

    @Test
    void rejectsDuplicatesAndInvalidToolIdentities() {
        assertFailure("""
                {"disabledTools":["future:tool","future:tool"],"disabledSkills":[]}
                """);
        assertFailure("""
                {"disabledTools":["missing_namespace"],"disabledSkills":[]}
                """);
        assertFailure("""
                {"disabledTools":["Future:tool"],"disabledSkills":[]}
                """);
        assertFailure("""
                {"disabledTools":["future:","other:ok"],"disabledSkills":[]}
                """);
    }

    @Test
    void rejectsDuplicatesAndInvalidSkillNames() {
        assertFailure("""
                {"disabledTools":[],"disabledSkills":["future-skill","future-skill"]}
                """);
        assertFailure("""
                {"disabledTools":[],"disabledSkills":[""]}
                """);
        assertFailure("""
                {"disabledTools":[],"disabledSkills":["Future-Skill"]}
                """);
        assertFailure("""
                {"disabledTools":[],"disabledSkills":["future_skill"]}
                """);
    }

    @Test
    void rejectsNonStringArrays() {
        assertFailure("""
                {"disabledTools":[1],"disabledSkills":[]}
                """);
        assertFailure("""
                {"disabledTools":[],"disabledSkills":{}}
                """);
    }

    private void assertFailure(String json) {
        ToolResult.Failure<CapabilityPolicy> failure = failure(loader.load(new StringReader(json)));
        assertEquals("invalid_capability_config", failure.code());
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<CapabilityPolicy> success(ToolResult<CapabilityPolicy> result) {
        return (ToolResult.Success<CapabilityPolicy>)
                assertInstanceOf(ToolResult.Success.class, result);
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<CapabilityPolicy> failure(ToolResult<CapabilityPolicy> result) {
        return (ToolResult.Failure<CapabilityPolicy>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }
}
