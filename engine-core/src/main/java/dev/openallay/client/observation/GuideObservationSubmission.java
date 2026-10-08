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
        return (owner, route, pendingId, message, observation) -> {
java.util.concurrent.CompletableFuture<? extends dev.openallay.tool.ToolResult<?>> $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((route)) {
case ASK:
{
$oaSwitch0_exit_result = service.ask(message); break $oaSwitch0_exit;
}
case FOLLOW_UP:
{
$oaSwitch0_exit_result = service.followUp(message); break $oaSwitch0_exit;
}
case STEER:
{
$oaSwitch0_exit_result = service.steer(message); break $oaSwitch0_exit;
}
case EDIT_PENDING:
{
$oaSwitch0_exit_result = service.editPending(pendingId, message); break $oaSwitch0_exit;
}
case EDIT_INVALID:
{
throw new IllegalStateException("Invalid edit cannot be submitted");
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
};
    }
}
