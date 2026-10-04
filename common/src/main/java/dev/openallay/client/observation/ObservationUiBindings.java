package dev.openallay.client.observation;

import dev.openallay.client.gui.GuideClientUiCoordinator;
import dev.openallay.guide.GuideService;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.platform.PlatformService;
import dev.openallay.world.WorldObservationRuntime;
import net.minecraft.client.Minecraft;

/** Shared real bindings for both loaders. No UI-only stand-in for a captured reference. */
public final class ObservationUiBindings {
    private ObservationUiBindings() {}

    public static MinecraftObservationInputActions bind(Minecraft client, PlatformService platform,
            WorldObservationRuntime observations, GuideClientUiCoordinator ui,
            dev.openallay.guide.GuideServiceManager services) {
        MinecraftObservationInputActions input = new MinecraftObservationInputActions(client, platform, observations);
        ui.withObservationInput(input)
                .withObservationImages(service -> service::observationImages)
                .withObservationSubmission((service, route, pending, message, capture) -> {
                    ModelMessage associated = ModelMessage.requireUserInput(new ModelMessage(
                            message.role(), message.content(),
                            capture == null ? message.inputObservation() : capture.anchor()));
                    return switch (route) {
                        case ASK -> service.ask(associated);
                        case FOLLOW_UP -> service.followUp(associated);
                        case STEER -> service.steer(associated);
                        case EDIT_PENDING -> service.editPending(pending, associated);
                        case EDIT_INVALID -> throw new IllegalStateException("Invalid edit cannot be submitted");
                    };
                });
        ObservationMenuKeyHandler.configure(current -> {
            if (current.player != null) ui.openGuide(services.forActor(current.player.getUUID()));
        });
        return input;
    }
}
