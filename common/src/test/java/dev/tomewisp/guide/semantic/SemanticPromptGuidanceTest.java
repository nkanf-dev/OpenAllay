package dev.tomewisp.guide.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SemanticPromptGuidanceTest {
    @Test
    void describesOnlyClosedPresentationSyntaxAndItsAuthorityBoundary() {
        String guidance = SemanticPromptGuidance.text();

        assertTrue(guidance.contains("[[tw:<kind>|<target>|<optional label>]]"));
        assertTrue(guidance.contains("tomewisp-component"));
        assertTrue(guidance.contains("do not create evidence"));
        assertTrue(guidance.contains("Do not emit HTML"));
        assertFalse(guidance.contains("apiKey"));
        assertFalse(guidance.contains("reasoning"));
    }
}
