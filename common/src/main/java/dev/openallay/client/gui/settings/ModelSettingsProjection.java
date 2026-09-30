package dev.openallay.client.gui.settings;

import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.model.ServerModelSettingsView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mixed local/server model list with explicit mutation rights for the settings screen. */
public record ModelSettingsProjection(List<ModelCard> models) {
    public static final String SERVER_SELECTION_ID = "server";

    public ModelSettingsProjection {
        models = List.copyOf(models);
    }

    public static ModelSettingsProjection from(
            ModelProfileSettingsView local,
            ServerModelSettingsView server) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(server, "server");
        List<ModelCard> cards = new ArrayList<>();
        for (ModelProfileSettingsView.Profile profile : local.profiles()) {
            cards.add(new ModelCard(
                    "client:" + profile.definition().id(),
                    profile.definition().id(),
                    profile.definition().displayName(),
                    profile.definition().model(),
                    Origin.CLIENT,
                    true,
                    true,
                    true,
                    profile.credentialPresent(),
                    profile.available(),
                    profile.definition().id().equals(local.config().defaultProfileId()),
                    profile.failure() == null ? null : profile.failure().code(),
                    profile.effectiveContextWindowTokens(),
                    profile.definition().maxOutputTokens()));
        }
        if (server.available()) {
            cards.add(new ModelCard(
                    SERVER_SELECTION_ID,
                    "",
                    server.canonicalModelId(),
                    server.canonicalModelId(),
                    Origin.SERVER,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    null,
                    server.contextWindowTokens(),
                    server.maxOutputTokens()));
        }
        return new ModelSettingsProjection(cards);
    }

    public enum Origin {
        CLIENT,
        SERVER
    }

    public record ModelCard(
            String selectionId,
            String profileId,
            String displayName,
            String model,
            Origin origin,
            boolean editable,
            boolean testable,
            boolean deletable,
            boolean credentialPresent,
            boolean available,
            boolean defaultProfile,
            String failureCode,
            Integer contextWindowTokens,
            int maxOutputTokens) {
        public ModelCard {
            Objects.requireNonNull(selectionId, "selectionId");
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(origin, "origin");
            if (displayName.isBlank() || model.isBlank()) {
                throw new IllegalArgumentException("model identity must not be blank");
            }
            if (origin == Origin.SERVER && (editable || testable || deletable)) {
                throw new IllegalArgumentException("server model projection must be read-only");
            }
        }
    }
}
