package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelMessageImageTest {
    private static final ImageReference IMAGE =
            new ImageReference("a".repeat(64), "image/png", 32, 24, 100);

    @Test
    void explicitAssociationKeepsOrdinaryAttachmentsIndependentAndAllowsOnlyRealImageBlankInput() {
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(IMAGE);
        var associated = ModelMessage.userText("this item").withInputObservation(anchor);
        assertEquals(anchor, associated.inputObservation().orElseThrow());
        assertEquals(List.of(new ModelContent.Text("this item")), associated.content());
        assertTrue(ModelMessage.userText("plain").inputObservation().isEmpty());
        var imageOnly = ModelMessage.userInput("", List.of(), java.util.Optional.of(anchor));
        assertEquals(List.of(new ModelContent.Text("")), imageOnly.content());
        assertSame(imageOnly, ModelMessage.requireUserInput(imageOnly));
        assertDoesNotThrow(() -> dev.openallay.agent.AgentRequest.validateUserInput(imageOnly));
        assertEquals("[Image]", dev.openallay.agent.AgentRequest.displayText(imageOnly));
        assertEquals(List.of(IMAGE), dev.openallay.model.image.ModelImages.occurrences(List.of(imageOnly)));
        var independent = ModelMessage.userInput("", List.of(IMAGE), java.util.Optional.of(anchor));
        assertEquals(List.of(IMAGE, IMAGE), dev.openallay.model.image.ModelImages.occurrences(List.of(independent)));
        assertTrue(dev.openallay.model.image.ModelImages.hasImages(List.of(imageOnly)));
        assertEquals(List.of(IMAGE), dev.openallay.model.image.ModelImages.uniqueReferences(List.of(independent)));
        var conflict = ModelMessage.userInput("", List.of(new ImageReference(IMAGE.sha256(), IMAGE.mimeType(),
                IMAGE.width() + 1, IMAGE.height(), IMAGE.byteSize())), java.util.Optional.of(anchor));
        assertThrows(IllegalArgumentException.class, () -> dev.openallay.model.image.ModelImages.uniqueReferences(List.of(conflict)));
        assertThrows(IllegalArgumentException.class, () -> ModelMessage.userInput("", List.of(),
                java.util.Optional.of(dev.openallay.world.InputObservationFixtures.anchor(null))));
        assertThrows(IllegalArgumentException.class, () -> new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.Text("answer")), java.util.Optional.of(anchor)));
        assertThrows(IllegalArgumentException.class, () -> new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.ToolResult("tool", new JsonPrimitive("result"), false)),
                java.util.Optional.of(anchor)));
        assertThrows(IllegalArgumentException.class, () -> new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.Image(IMAGE, "tool-origin")), java.util.Optional.of(anchor)));
        var foreignFocus = dev.openallay.world.InputObservationFixtures.focus(java.util.UUID.randomUUID());
        var mismatched = new dev.openallay.world.ClientObservationAnchor(anchor.associationId(), anchor.capturedAt(),
                foreignFocus, anchor.image());
        assertThrows(IllegalArgumentException.class, () -> ModelMessage.userText("look").withInputObservation(mismatched));
    }

    @Test
    void imageOnlyAndMixedPlayerInputAreTypedAndDetached() {
        var images = new ArrayList<>(List.of(IMAGE));
        var input = ModelMessage.userInput("Look at this", images);
        images.clear();
        assertEquals(ModelRole.USER, input.role());
        assertEquals(List.of(new ModelContent.Text("Look at this"), new ModelContent.Image(IMAGE)),
                input.content());
        assertEquals(List.of(new ModelContent.Image(IMAGE)),
                ModelMessage.userInput(null, List.of(IMAGE)).content());
        assertEquals(List.of(new ModelContent.Image(IMAGE)),
                ModelMessage.userInput("   ", List.of(IMAGE)).content());
        assertSame(input, ModelMessage.requireUserInput(input));
        assertThrows(UnsupportedOperationException.class, () -> input.content().clear());
        assertThrows(NullPointerException.class, () -> new ModelContent.Image(null));
    }

    @Test
    void imageBlocksCannotBecomeAssistantContentOrToolResultInput() {
        assertThrows(IllegalArgumentException.class, () -> new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.Image(IMAGE))));
        for (ModelContent other : List.of(new ModelContent.Reasoning("reasoning", null),
                new ModelContent.ToolUse("call", "lookup", new JsonObject()),
                new ModelContent.ToolResult("call", new JsonPrimitive("ok"), false))) {
            assertThrows(IllegalArgumentException.class, () -> new ModelMessage(ModelRole.USER,
                    List.of(new ModelContent.Image(IMAGE), other)));
        }
        assertThrows(IllegalArgumentException.class, () -> ModelMessage.requireUserInput(
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult("call", new JsonPrimitive("ok"), false)))));
        assertThrows(IllegalArgumentException.class, () -> ModelMessage.requireUserInput(
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("ok")))));
        assertThrows(IllegalArgumentException.class,
                () -> ModelMessage.userInput("   ", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ModelMessage.requireUserInput(ModelMessage.userText("")));
    }

    @Test
    void typedToolOriginIsAllowedInInternalSummaryButRejectedAsPlayerInput() {
        var carried = new ModelContent.Image(IMAGE, "original-call");
        var summary = new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text("derived memory"), carried));
        assertEquals("original-call", ((ModelContent.Image) summary.content().getLast()).originToolUseId());
        assertThrows(IllegalArgumentException.class, () -> ModelMessage.requireUserInput(summary));
        assertThrows(IllegalArgumentException.class, () -> dev.openallay.agent.AgentRequest.validateUserInput(summary));
        assertThrows(IllegalArgumentException.class, () -> new ModelContent.Image(IMAGE, " "));
        var player = ModelMessage.userInput("look", List.of(IMAGE));
        assertNull(((ModelContent.Image) player.content().getLast()).originToolUseId());
        assertSame(player, ModelMessage.requireUserInput(player));
    }


    @Test
    void optionalResolverLeaseCleanupKeepsLambdaResolversFunctional() throws IOException {
        ImagePayloadResolver resolver = reference -> new byte[(int) reference.byteSize()];
        assertDoesNotThrow(resolver::close);
        try (resolver) {
            assertEquals(IMAGE.byteSize(), resolver.read(IMAGE).length);
        }
        assertDoesNotThrow(() -> ImagePayloadResolver.unavailable().close());
    }

    @Test
    void oldRequestConstructorsDoNotAuthorizeAnyImageRead() {
        var message = ModelMessage.userInput(null, List.of(IMAGE));
        for (ModelRequest request : List.of(
                new ModelRequest("System", List.of(message), List.of(), false),
                new ModelRequest("System", List.of(message), List.of(), false, "session"),
                new ModelRequest("System", List.of(message), List.of(), false, "session", 256))) {
            assertThrows(IOException.class, () -> request.images().read(IMAGE));
        }
        var request = new ModelRequest("System", List.of(message), List.of(), false,
                "session", 256, reference -> new byte[(int) reference.byteSize()]);
        assertEquals(256, request.effectiveMaxOutputTokens(512));
        assertThrows(NullPointerException.class, () -> new ModelRequest("System", List.of(message),
                List.of(), false, "session", null, null));
    }
}
