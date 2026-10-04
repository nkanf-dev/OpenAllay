package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import dev.openallay.world.InputObservationFixtures;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideInputObservationValidationTest {
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 1, 1, 4);

    @Test
    void realAssociatedImageNeedsConfirmedCapabilityEvenWithoutOrdinaryImageBlocks() {
        var input = ModelMessage.userInput("", List.of(), java.util.Optional.of(InputObservationFixtures.anchor(IMAGE)));
        for (var capability : List.of(ImageInputCapability.UNKNOWN, ImageInputCapability.UNSUPPORTED)) {
            var service = service(InputObservationFixtures.ACTOR, capability);
            var result = service.validateUserInput(input, GuideModelSelection.server());
            var failure = assertInstanceOf(ToolResult.Failure.class, result);
            assertEquals(capability == ImageInputCapability.UNKNOWN ? "image_model_unknown" : "image_model_unsupported",
                    failure.code());
        }
    }

    @Test
    void focusOnlyContextDoesNotRequireVisionButForeignSourceIsRejected() {
        var input = ModelMessage.userText("this item").withInputObservation(InputObservationFixtures.anchor(null));
        assertInstanceOf(ToolResult.Success.class, service(InputObservationFixtures.ACTOR,
                ImageInputCapability.UNKNOWN).validateUserInput(input, GuideModelSelection.server()));
        var result = service(UUID.randomUUID(), ImageInputCapability.SUPPORTED)
                .validateUserInput(input, GuideModelSelection.server());
        assertEquals("input_source_actor_mismatch", assertInstanceOf(ToolResult.Failure.class, result).code());
    }

    private static GuideService service(UUID actor, ImageInputCapability capability) {
        GuideRemoteEndpoint remote = new GuideRemoteEndpoint() {
            @Override public boolean serverModelAvailable() { return true; }
            @Override public boolean serverToolsAvailable() { return false; }
            @Override public ImageInputCapability imageInputCapability() { return capability; }
            @Override public boolean ask(UUID requestId, String sessionId, String question,
                    java.util.function.Consumer<dev.openallay.agent.AgentEvent> events) {
                throw new AssertionError("Validation must not dispatch a model request");
            }
            @Override public boolean cancel(UUID requestId) { return false; }
            @Override public void disconnect() {}
        };
        return new GuideService(actor, null, remote, (capabilities, correlation) ->
                new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.systemUTC(), new Gson());
    }
}
