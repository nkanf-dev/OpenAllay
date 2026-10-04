package dev.openallay.client.observation;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GuideObservationSubmissionTest {
    @Test void senderReceivesExactFrozenCaptureWithNoImageCopiedOutOfTheAnchor() {
        var f = new ObservationUiFixture(); var anchor = ObservationUiFixture.anchor(0, true, true);
        f.state.seedObservation("one", anchor); var capture = f.state.captureObservation("one");
        var message = ModelMessage.userInput("What is in this slot?", List.of(ObservationUiFixture.PASTE));
        UUID pending = UUID.randomUUID();
        GuideObservationSubmission sender = (service, route, id, input, observation) -> {
            assertEquals(GuideClientUiState.SubmissionRoute.EDIT_PENDING, route);
            assertEquals(pending, id); assertSame(message, input); assertSame(capture, observation);
            assertSame(anchor, observation.anchor().orElseThrow());
            assertEquals(List.of(ObservationUiFixture.PASTE), input.content().stream()
                    .filter(ModelContent.Image.class::isInstance).map(ModelContent.Image.class::cast)
                    .map(ModelContent.Image::reference).toList());
            return CompletableFuture.completedFuture(new ToolResult.Success<>(true));
        };
        assertTrue(sender.send(null, GuideClientUiState.SubmissionRoute.EDIT_PENDING, pending, message, capture)
                .join() instanceof ToolResult.Success<?>);
    }
}
