package dev.openallay.client.observation;

import dev.openallay.client.gui.GuideClientUiCoordinator;
import dev.openallay.guide.GuideService;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.platform.PlatformService;
import dev.openallay.world.WorldObservationRuntime;


/** Shared real bindings for both loaders. No UI-only stand-in for a captured reference. */
public final class ObservationUiBindings {
    private ObservationUiBindings() {}

    public static MinecraftObservationInputActions bind(net.minecraft.client.Minecraft client, PlatformService platform,
            WorldObservationRuntime observations, GuideClientUiCoordinator ui,
            dev.openallay.guide.GuideServiceManager services) {
        MinecraftObservationInputActions input = new MinecraftObservationInputActions(client, platform, observations);
        ui.withObservationInput(input)
                .withObservationImages(service -> service::observationImages)
                .withObservationSubmission((service, route, pending, message, capture) -> {
                    ModelMessage associated = ModelMessage.requireUserInput(new ModelMessage(
                            message.role(), message.content(),
                            capture == null ? message.inputObservation() : capture.anchor()));
                    {
java.util.concurrent.CompletableFuture<? extends dev.openallay.tool.ToolResult<?>> $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((route)) {
case ASK:
{
$oaSwitch0_exit_result = service.ask(associated); break $oaSwitch0_exit;
}
case FOLLOW_UP:
{
$oaSwitch0_exit_result = service.followUp(associated); break $oaSwitch0_exit;
}
case STEER:
{
$oaSwitch0_exit_result = service.steer(associated); break $oaSwitch0_exit;
}
case EDIT_PENDING:
{
$oaSwitch0_exit_result = service.editPending(pending, associated); break $oaSwitch0_exit;
}
case EDIT_INVALID:
{
throw new IllegalStateException("Invalid edit cannot be submitted");
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
                });
        ObservationMenuKeyHandler.configure(current -> {
            if (current.player != null) ui.openGuide(services.forActor(dev.openallay.client.context.MinecraftClientContextFacts.uuid(current.player)));
        });
        return input;
    }
}
