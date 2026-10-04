package dev.openallay.client.observation;

import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.guide.GuideService;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** UI admission boundary. Root binds typed GuideService input with this frozen observation capture. */
@FunctionalInterface
public interface GuideObservationSubmission {
    CompletableFuture<? extends ToolResult<?>> send(GuideService service, GuideClientUiState.SubmissionRoute route,
            UUID pendingId, ModelMessage message, GuideClientUiState.ObservationCapture observation);

    /** Existing transport for constructors used outside the coordinated observation entry path. */
    static GuideObservationSubmission existing(GuideService service) {
        Objects.requireNonNull(service, "service");
        return (owner, route, pendingId, message, observation) -> switch (route) {
            case ASK -> service.ask(message);
            case FOLLOW_UP -> service.followUp(message);
            case STEER -> service.steer(message);
            case EDIT_PENDING -> service.editPending(pendingId, message);
            case EDIT_INVALID -> throw new IllegalStateException("Invalid edit cannot be submitted");
        };
    }
}
