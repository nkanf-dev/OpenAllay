package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ModelProfileDraftTest {
    @Test
    void roundTripsValidDefinitionAndTracksDirtyState() {
        ModelProfileDefinition original = definition();
        ModelProfileDraft draft = ModelProfileDraft.from(original);

        assertFalse(draft.dirtyComparedTo(original));
        assertEquals(original, success(draft.validate()));

        ModelProfileDraft changed = draft.withModel("vendor/new-model");
        assertTrue(changed.dirtyComparedTo(original));
        assertEquals("vendor/new-model", success(changed.validate()).model());
    }

    @Test
    void invalidFieldsReturnStableFailureWithoutThrowing() {
        ModelProfileDraft invalid = new ModelProfileDraft(
                "bad id",
                "",
                true,
                ModelProtocol.OPENAI_CHAT,
                "http://remote.example/v1",
                "",
                "BAD KEY",
                "not-a-number",
                "0",
                "-1",
                "none");

        ToolResult<ModelProfileDefinition> result = invalid.validate();
        if (!(result instanceof ToolResult.Failure<ModelProfileDefinition> failure)) {
            throw new AssertionError("expected an invalid model profile draft");
        }
        assertEquals("invalid_model_profile", failure.code());
        assertFalse(failure.message().contains("http://remote.example/v1"));
    }

    @Test
    void catalogValidationDoesNotRequireAModelId() {
        ModelProfileDraft draft = ModelProfileDraft.create("new-profile");
        draft = new ModelProfileDraft(
                draft.id(), draft.displayName(), draft.enabled(), draft.protocol(),
                "https://provider.example/v1/", "", draft.credentialRef(),
                draft.contextWindowTokens(), draft.maxOutputTokens(),
                draft.connectTimeoutSeconds(), draft.requestTimeoutSeconds(), draft.metadata());

        var request = (ToolResult.Success<dev.openallay.model.catalog.ModelCatalogRequest>)
                assertInstanceOf(ToolResult.Success.class, draft.catalogRequest());

        assertEquals("https://provider.example/v1/", request.value().baseUri().toString());
        assertEquals("new-profile", request.value().profileId());
    }

    @Test
    void automaticContextRemainsOmittedAcrossSaveAndReopenUntilActuallyEdited() {
        ModelProfileDraft automatic = ModelProfileDraft.create("main").withModel("gpt-6-luna");
        automatic = new ModelProfileDraft(automatic.id(), automatic.displayName(), automatic.enabled(),
                automatic.protocol(), "https://arbitrary.example/v1/", automatic.model(),
                automatic.credentialRef(), automatic.contextWindowTokens(), automatic.maxOutputTokens(),
                automatic.connectTimeoutSeconds(), automatic.requestTimeoutSeconds(), automatic.metadata(),
                automatic.automaticContextWindowTokens());
        assertEquals("1050000", automatic.contextWindowTokens());
        var saved = success(automatic.validate());
        assertNull(saved.contextWindowTokens());
        assertEquals("1050000", ModelProfileDraft.from(saved).contextWindowTokens());
        assertFalse(automatic.dirtyComparedTo(saved));
        var trustedDisplay = automatic.withAutomaticContext(600_000);
        assertEquals("600000", trustedDisplay.contextWindowTokens());
        assertNull(success(trustedDisplay.validate()).contextWindowTokens());
        var manual = automatic.withContextWindow("1000000");
        assertEquals(1_000_000, success(manual.validate()).contextWindowTokens());
        assertEquals("1000000", manual.withAutomaticContext(2_000_000).contextWindowTokens());
        assertEquals("1000000", manual.withModel("unknown-new-model").contextWindowTokens());
    }

    @Test
    void modelEditReplacesOnlyAutomaticContextAndUnknownClearsIt() {
        var automatic = ModelProfileDraft.create("main").withModel("gpt-6-luna");
        assertEquals("1050000", automatic.contextWindowTokens());
        var unknown = automatic.withModel("unpublished-unrelated");
        assertEquals("", unknown.contextWindowTokens());
        assertNull(unknown.automaticContextWindowTokens());
        var other = automatic.withModel("claude-sonnet-4-5");
        assertFalse(other.contextWindowTokens().isBlank());
        assertEquals(other.contextWindowTokens(), other.automaticContextWindowTokens());
    }

    private static ModelProfileDefinition definition() {
        return new ModelProfileDefinition(
                "main",
                "Main",
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                "vendor/model",
                "MODEL_KEY",
                256_000,
                4_096,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
    }

    private static ModelProfileDefinition success(
            ToolResult<ModelProfileDefinition> result) {
        if (result instanceof ToolResult.Success<ModelProfileDefinition> value) {
            return value.value();
        }
        throw new AssertionError("expected a successful model profile draft");
    }
}
