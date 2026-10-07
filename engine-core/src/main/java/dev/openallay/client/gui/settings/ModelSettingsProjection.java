package dev.openallay.client.gui.settings;

import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.model.ServerModelSettingsView;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mixed local/server model list with explicit mutation rights for the settings screen. */
@dev.openallay.value.ValueType(ModelSettingsProjection.ValueSchemaProvider.class)
public final class ModelSettingsProjection {
    private final List<ModelCard> models;
    public ModelSettingsProjection(List<ModelCard> models) {

        models = List.copyOf(models);

        this.models = models;
    }
    public List<ModelCard> models() { return models; }
public static final String SERVER_SELECTION_ID = "server";
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
                    profile.effectiveMaxOutputTokens(), profile.imageCapability()));
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
                    server.maxOutputTokens(), server.imageCapability()));
        }
        return new ModelSettingsProjection(cards);
    }
public enum Origin {
        CLIENT,
        SERVER
    }
@dev.openallay.value.ValueType(ModelCard.ValueSchemaProvider.class)
public static final class ModelCard {
    private final String selectionId;
    private final String profileId;
    private final String displayName;
    private final String model;
    private final Origin origin;
    private final boolean editable;
    private final boolean testable;
    private final boolean deletable;
    private final boolean credentialPresent;
    private final boolean available;
    private final boolean defaultProfile;
    private final String failureCode;
    private final Integer contextWindowTokens;
    private final Integer maxOutputTokens;
    private final ModelImageCapabilityResolution imageCapability;
    public ModelCard(String selectionId, String profileId, String displayName, String model, Origin origin, boolean editable, boolean testable, boolean deletable, boolean credentialPresent, boolean available, boolean defaultProfile, String failureCode, Integer contextWindowTokens, Integer maxOutputTokens, ModelImageCapabilityResolution imageCapability) {

            Objects.requireNonNull(selectionId, "selectionId");
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(imageCapability, "imageCapability");
            if (displayName.isBlank() || model.isBlank()) {
                throw new IllegalArgumentException("model identity must not be blank");
            }
            if (origin == Origin.SERVER && (editable || testable || deletable)) {
                throw new IllegalArgumentException("server model projection must be read-only");
            }

        this.selectionId = selectionId;
        this.profileId = profileId;
        this.displayName = displayName;
        this.model = model;
        this.origin = origin;
        this.editable = editable;
        this.testable = testable;
        this.deletable = deletable;
        this.credentialPresent = credentialPresent;
        this.available = available;
        this.defaultProfile = defaultProfile;
        this.failureCode = failureCode;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.imageCapability = imageCapability;
    }
    public String selectionId() { return selectionId; }
    public String profileId() { return profileId; }
    public String displayName() { return displayName; }
    public String model() { return model; }
    public Origin origin() { return origin; }
    public boolean editable() { return editable; }
    public boolean testable() { return testable; }
    public boolean deletable() { return deletable; }
    public boolean credentialPresent() { return credentialPresent; }
    public boolean available() { return available; }
    public boolean defaultProfile() { return defaultProfile; }
    public String failureCode() { return failureCode; }
    public Integer contextWindowTokens() { return contextWindowTokens; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public ModelImageCapabilityResolution imageCapability() { return imageCapability; }
public ModelCard(
                String selectionId, String profileId, String displayName, String model,
                Origin origin, boolean editable, boolean testable, boolean deletable,
                boolean credentialPresent, boolean available, boolean defaultProfile,
                String failureCode, Integer contextWindowTokens, Integer maxOutputTokens) {
            this(selectionId, profileId, displayName, model, origin, editable, testable, deletable,
                    credentialPresent, available, defaultProfile, failureCode, contextWindowTokens,
                    maxOutputTokens, ModelImageCapabilityResolution.unknown());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelCard)) return false;
        ModelCard that = (ModelCard) other;
        return java.util.Objects.equals(selectionId, that.selectionId) && java.util.Objects.equals(profileId, that.profileId) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(origin, that.origin) && editable == that.editable && testable == that.testable && deletable == that.deletable && credentialPresent == that.credentialPresent && available == that.available && defaultProfile == that.defaultProfile && java.util.Objects.equals(failureCode, that.failureCode) && java.util.Objects.equals(contextWindowTokens, that.contextWindowTokens) && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selectionId);
        hash = 31 * hash + java.util.Objects.hashCode(profileId);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + Boolean.hashCode(editable);
        hash = 31 * hash + Boolean.hashCode(testable);
        hash = 31 * hash + Boolean.hashCode(deletable);
        hash = 31 * hash + Boolean.hashCode(credentialPresent);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(defaultProfile);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        hash = 31 * hash + java.util.Objects.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "ModelCard[selectionId=" + selectionId + ", profileId=" + profileId + ", displayName=" + displayName + ", model=" + model + ", origin=" + origin + ", editable=" + editable + ", testable=" + testable + ", deletable=" + deletable + ", credentialPresent=" + credentialPresent + ", available=" + available + ", defaultProfile=" + defaultProfile + ", failureCode=" + failureCode + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelCard> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelCard.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelCard>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "selectionId", ModelCard::selectionId), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "profileId", ModelCard::profileId), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "displayName", ModelCard::displayName), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "model", ModelCard::model), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "origin", ModelCard::origin), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "editable", ModelCard::editable), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "testable", ModelCard::testable), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "deletable", ModelCard::deletable), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "credentialPresent", ModelCard::credentialPresent), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "available", ModelCard::available), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "defaultProfile", ModelCard::defaultProfile), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "failureCode", ModelCard::failureCode), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "contextWindowTokens", ModelCard::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "maxOutputTokens", ModelCard::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelCard.class, "imageCapability", ModelCard::imageCapability)), arguments -> new ModelCard((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (Origin) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (Boolean) arguments[8], (Boolean) arguments[9], (Boolean) arguments[10], (String) arguments[11], (Integer) arguments[12], (Integer) arguments[13], (ModelImageCapabilityResolution) arguments[14]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelSettingsProjection)) return false;
        ModelSettingsProjection that = (ModelSettingsProjection) other;
        return java.util.Objects.equals(models, that.models);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(models);
        return hash;
    }
    @Override public String toString() { return "ModelSettingsProjection[models=" + models + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelSettingsProjection.class, "models", ModelSettingsProjection::models)), arguments -> new ModelSettingsProjection((List) arguments[0]));
        }
    }
}
