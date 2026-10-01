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
                automatic.automaticContextWindowTokens(), automatic.automaticMaxOutputTokens());
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

    @Test
    void automaticOutputUsesMaximumWithoutAdoptingItOnSaveOrReopen() {
        assertEquals("", ModelProfileDraft.create("main").maxOutputTokens());
        var automatic = validAutomaticDraft();
        assertEquals("128000", automatic.maxOutputTokens());
        assertEquals("128000", automatic.automaticMaxOutputTokens());
        var saved = success(automatic.validate());
        assertNull(saved.maxOutputTokens());
        var reopened = ModelProfileDraft.from(saved);
        assertEquals("128000", reopened.maxOutputTokens());
        assertEquals("128000", reopened.automaticMaxOutputTokens());
        assertFalse(reopened.dirtyComparedTo(saved));
        String encoded = new dev.openallay.model.config.ModelProfilesConfigWriter().encode(
                new dev.openallay.model.config.ModelProfilesConfig("main", java.util.List.of(saved)));
        assertFalse(encoded.contains("maxOutputTokens"));
        var refreshed = automatic.withAutomaticOutput(64_000);
        assertEquals("64000", refreshed.maxOutputTokens());
        assertNull(success(refreshed.validate()).maxOutputTokens());
        assertFalse(refreshed.dirtyComparedTo(saved));
    }

    @Test
    void actualOutputEditWinsEvenAtPublishedMaximumAndClearReturnsToAutomatic() {
        var automatic = validAutomaticDraft().withContextWindow("1000000");
        for (String budget : java.util.List.of("4096", "8192", "128000")) {
            var manual = automatic.withMaxOutput(budget);
            assertNull(manual.automaticMaxOutputTokens());
            assertEquals(Integer.valueOf(budget), success(manual.validate()).maxOutputTokens());
            assertEquals(budget, manual.withAutomaticOutput(64_000).maxOutputTokens());
            assertEquals(budget, manual.withModel("unpublished-model").maxOutputTokens());
            assertEquals("1000000", manual.withModel("gpt-4.1").contextWindowTokens());
            var reset = manual.withMaxOutput("").autoFill(
                    dev.openallay.model.metadata.BuiltinModelCatalog.bundled().catalog());
            assertEquals("128000", reset.maxOutputTokens());
            assertNull(success(reset.validate()).maxOutputTokens());
            assertEquals(1_000_000, success(reset.validate()).contextWindowTokens());
        }
    }

    @Test
    void modelPickerReplacesAutomaticOutputAndUnknownClearsRatherThanGuesses() {
        var automatic = validAutomaticDraft();
        var changed = automatic.withModel("gpt-4.1");
        int published = dev.openallay.model.metadata.BuiltinModelCatalog.bundled().catalog()
                .match("gpt-4.1").orElseThrow().entry().maxOutputTokens();
        assertEquals(Integer.toString(published), changed.maxOutputTokens());
        assertEquals(changed.maxOutputTokens(), changed.automaticMaxOutputTokens());
        assertNull(success(changed.validate()).maxOutputTokens());
        var unknown = automatic.withModel("unpublished-unrelated");
        assertEquals("", unknown.maxOutputTokens());
        assertNull(unknown.automaticMaxOutputTokens());
        assertNull(success(unknown.validate()).maxOutputTokens());
        assertEquals("128000", unknown.withModel("gpt-6-luna").maxOutputTokens());
    }

    @Test
    void malformedOutputEditsFailWithoutLeakingFields() {
        for (String bad : java.util.List.of("true", "1.5", "0", "-1", "2147483648")) {
            var result = validAutomaticDraft().withMaxOutput(bad).validate();
            var failure = (ToolResult.Failure<ModelProfileDefinition>)
                    assertInstanceOf(ToolResult.Failure.class, result);
            assertEquals("invalid_model_profile", failure.code());
            assertEquals("Review the model profile fields", failure.message());
        }
    }

    private static ModelProfileDraft validAutomaticDraft() {
        var automatic = ModelProfileDraft.create("main").withModel("gpt-6-luna");
        return new ModelProfileDraft(automatic.id(), automatic.displayName(), automatic.enabled(),
                automatic.protocol(), "https://arbitrary.example/v1/", automatic.model(),
                automatic.credentialRef(), automatic.contextWindowTokens(), automatic.maxOutputTokens(),
                automatic.connectTimeoutSeconds(), automatic.requestTimeoutSeconds(), automatic.metadata(),
                automatic.automaticContextWindowTokens(), automatic.automaticMaxOutputTokens());
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
