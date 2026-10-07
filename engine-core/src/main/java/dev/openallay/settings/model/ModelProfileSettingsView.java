package dev.openallay.settings.model;

import dev.openallay.guide.GuideFailure;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ordered, credential-free model profile projection for native settings. */
@dev.openallay.value.ValueType(ModelProfileSettingsView.ValueSchemaProvider.class)
public final class ModelProfileSettingsView {
    private final ModelProfilesConfig config;
    private final List<Profile> profiles;
    private final GuideFailure metadataFailure;
    private final ModelConnectionResult connectionResult;
    public ModelProfileSettingsView(ModelProfilesConfig config, List<Profile> profiles, GuideFailure metadataFailure, ModelConnectionResult connectionResult) {

        Objects.requireNonNull(config, "config");
        profiles = dev.openallay.util.Java8Collections.listCopyOf(profiles);
        if (profiles.size() != config.profiles().size()) {
            throw new IllegalArgumentException("every configured model profile needs a view");
        }
        for (int index = 0; index < profiles.size(); index++) {
            if (!profiles.get(index).definition().id().equals(config.profiles().get(index).id())) {
                throw new IllegalArgumentException("model profile view order must match configuration");
            }
        }

        ModelConnectionResult.requireKnown(connectionResult);
        this.config = config;
        this.profiles = profiles;
        this.metadataFailure = metadataFailure;
        this.connectionResult = connectionResult;
    }
    public ModelProfilesConfig config() { return config; }
    public List<Profile> profiles() { return profiles; }
    public GuideFailure metadataFailure() { return metadataFailure; }
    public ModelConnectionResult connectionResult() { return connectionResult; }
public static ModelProfileSettingsView from(
            ModelProfilesConfig config,
            List<Resolution> resolved,
            java.util.Set<String> ignoredPresentEnvironmentNames,
            GuideFailure metadataFailure,
            ModelConnectionResult connectionResult) {
        Objects.requireNonNull(ignoredPresentEnvironmentNames, "presentEnvironmentNames");
        Map<String, Resolution> byId = new HashMap<>();
        for (Resolution profile : resolved) {
            if (byId.put(profile.definition().id(), profile) != null) {
                throw new IllegalArgumentException("duplicate resolved model profile");
            }
        }
        List<Profile> views = new ArrayList<>();
        for (ModelProfileDefinition definition : config.profiles()) {
            Resolution profile = Objects.requireNonNull(
                    byId.get(definition.id()), "missing resolved model profile");
            views.add(new Profile(
                    definition,
                    profile.available(),
                    profile.credentialPresent(),
                    profile.effectiveContextWindowTokens(),
                    profile.effectiveMaxOutputTokens(),
                    profile.failure(),
                    profile.imageCapability()));
        }
        return new ModelProfileSettingsView(
                config, views, metadataFailure, connectionResult);
    }
private static ModelImageCapabilityResolution automaticImageCapability(ModelProfileDefinition definition) {
        return ModelImageCapabilityResolution.resolve(definition.baseUri(), definition.model(),
                definition.imageInputCapabilityOverride(), dev.openallay.util.Java8Collections.mapOf(), BuiltinModelCatalog.bundled().catalog());
    }
@dev.openallay.value.ValueType(Resolution.ValueSchemaProvider.class)
public static final class Resolution {
    private final ModelProfileDefinition definition;
    private final boolean available;
    private final boolean credentialPresent;
    private final Integer effectiveContextWindowTokens;
    private final Integer effectiveMaxOutputTokens;
    private final GuideFailure failure;
    private final ModelImageCapabilityResolution imageCapability;
    public Resolution(ModelProfileDefinition definition, boolean available, boolean credentialPresent, Integer effectiveContextWindowTokens, Integer effectiveMaxOutputTokens, GuideFailure failure, ModelImageCapabilityResolution imageCapability) {

            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(imageCapability, "imageCapability");
            if (available == (failure != null)) {
                throw new IllegalArgumentException(
                        "available model profiles have no failure and unavailable profiles require one");
            }

        this.definition = definition;
        this.available = available;
        this.credentialPresent = credentialPresent;
        this.effectiveContextWindowTokens = effectiveContextWindowTokens;
        this.effectiveMaxOutputTokens = effectiveMaxOutputTokens;
        this.failure = failure;
        this.imageCapability = imageCapability;
    }
    public ModelProfileDefinition definition() { return definition; }
    public boolean available() { return available; }
    public boolean credentialPresent() { return credentialPresent; }
    public Integer effectiveContextWindowTokens() { return effectiveContextWindowTokens; }
    public Integer effectiveMaxOutputTokens() { return effectiveMaxOutputTokens; }
    public GuideFailure failure() { return failure; }
    public ModelImageCapabilityResolution imageCapability() { return imageCapability; }
public Resolution(
                ModelProfileDefinition definition, boolean available, boolean credentialPresent,
                Integer effectiveContextWindowTokens, Integer effectiveMaxOutputTokens,
                GuideFailure failure) {
            this(definition, available, credentialPresent, effectiveContextWindowTokens,
                    effectiveMaxOutputTokens, failure, automaticImageCapability(definition));
        }
public Resolution(
                ModelProfileDefinition definition,
                boolean available,
                boolean credentialPresent,
                Integer effectiveContextWindowTokens,
                GuideFailure failure) {
            this(definition, available, credentialPresent, effectiveContextWindowTokens,
                    definition.maxOutputTokens(), failure);
        }
public Resolution(
                ModelProfileDefinition definition,
                boolean available,
                Integer effectiveContextWindowTokens,
                GuideFailure failure) {
            this(definition, available, available, effectiveContextWindowTokens,
                    definition.maxOutputTokens(), failure);
        }
public static Resolution from(ResolvedModelProfile profile) {
            return from(profile, profile.available());
        }
public static Resolution from(
                ResolvedModelProfile profile, boolean credentialPresent) {
            Objects.requireNonNull(profile, "profile");
            return new Resolution(
                    profile.definition(),
                    profile.available(),
                    credentialPresent,
                    profile.runtimeConfig() == null
                            ? profile.definition().contextWindowTokens()
                            : Integer.valueOf(profile.runtimeConfig().contextWindowTokens()),
                    profile.runtimeConfig() == null
                            ? profile.definition().maxOutputTokens()
                            : Integer.valueOf(profile.runtimeConfig().maxOutputTokens()),
                    profile.failure(),
                    profile.imageCapability());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Resolution)) return false;
        Resolution that = (Resolution) other;
        return java.util.Objects.equals(definition, that.definition) && available == that.available && credentialPresent == that.credentialPresent && java.util.Objects.equals(effectiveContextWindowTokens, that.effectiveContextWindowTokens) && java.util.Objects.equals(effectiveMaxOutputTokens, that.effectiveMaxOutputTokens) && java.util.Objects.equals(failure, that.failure) && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(definition);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(credentialPresent);
        hash = 31 * hash + java.util.Objects.hashCode(effectiveContextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(effectiveMaxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "Resolution[definition=" + definition + ", available=" + available + ", credentialPresent=" + credentialPresent + ", effectiveContextWindowTokens=" + effectiveContextWindowTokens + ", effectiveMaxOutputTokens=" + effectiveMaxOutputTokens + ", failure=" + failure + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Resolution> schema() {
            return new dev.openallay.value.ValueSchema<>(Resolution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Resolution>>asList(new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "definition", Resolution::definition), new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "available", Resolution::available), new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "credentialPresent", Resolution::credentialPresent), new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "effectiveContextWindowTokens", Resolution::effectiveContextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "effectiveMaxOutputTokens", Resolution::effectiveMaxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "failure", Resolution::failure), new dev.openallay.value.ValueSchema.Component<>(Resolution.class, "imageCapability", Resolution::imageCapability)), arguments -> new Resolution((ModelProfileDefinition) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (GuideFailure) arguments[5], (ModelImageCapabilityResolution) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(Profile.ValueSchemaProvider.class)
public static final class Profile {
    private final ModelProfileDefinition definition;
    private final boolean available;
    private final boolean credentialPresent;
    private final Integer effectiveContextWindowTokens;
    private final Integer effectiveMaxOutputTokens;
    private final GuideFailure failure;
    private final ModelImageCapabilityResolution imageCapability;
    public Profile(ModelProfileDefinition definition, boolean available, boolean credentialPresent, Integer effectiveContextWindowTokens, Integer effectiveMaxOutputTokens, GuideFailure failure, ModelImageCapabilityResolution imageCapability) {

            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(imageCapability, "imageCapability");
            if (available == (failure != null)) {
                throw new IllegalArgumentException(
                        "available model profiles have no failure and unavailable profiles require one");
            }

        this.definition = definition;
        this.available = available;
        this.credentialPresent = credentialPresent;
        this.effectiveContextWindowTokens = effectiveContextWindowTokens;
        this.effectiveMaxOutputTokens = effectiveMaxOutputTokens;
        this.failure = failure;
        this.imageCapability = imageCapability;
    }
    public ModelProfileDefinition definition() { return definition; }
    public boolean available() { return available; }
    public boolean credentialPresent() { return credentialPresent; }
    public Integer effectiveContextWindowTokens() { return effectiveContextWindowTokens; }
    public Integer effectiveMaxOutputTokens() { return effectiveMaxOutputTokens; }
    public GuideFailure failure() { return failure; }
    public ModelImageCapabilityResolution imageCapability() { return imageCapability; }
public Profile(
                ModelProfileDefinition definition, boolean available, boolean credentialPresent,
                Integer effectiveContextWindowTokens, Integer effectiveMaxOutputTokens,
                GuideFailure failure) {
            this(definition, available, credentialPresent, effectiveContextWindowTokens,
                    effectiveMaxOutputTokens, failure, automaticImageCapability(definition));
        }
public Profile(
                ModelProfileDefinition definition,
                boolean available,
                boolean credentialPresent,
                Integer effectiveContextWindowTokens,
                GuideFailure failure) {
            this(definition, available, credentialPresent, effectiveContextWindowTokens,
                    definition.maxOutputTokens(), failure);
        }
public boolean credentialStoredLocally() {
            try {
                return credentialPresent
                        && CredentialReference.parse(definition.credentialRef()).kind()
                                == CredentialReference.Kind.LOCAL;
            } catch (RuntimeException failure) {
                return false;
            }
        }
public boolean credentialFromEnvironment() {
            try {
                return credentialPresent
                        && CredentialReference.parse(definition.credentialRef()).kind()
                                == CredentialReference.Kind.ENVIRONMENT;
            } catch (RuntimeException failure) {
                return false;
            }
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Profile)) return false;
        Profile that = (Profile) other;
        return java.util.Objects.equals(definition, that.definition) && available == that.available && credentialPresent == that.credentialPresent && java.util.Objects.equals(effectiveContextWindowTokens, that.effectiveContextWindowTokens) && java.util.Objects.equals(effectiveMaxOutputTokens, that.effectiveMaxOutputTokens) && java.util.Objects.equals(failure, that.failure) && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(definition);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + Boolean.hashCode(credentialPresent);
        hash = 31 * hash + java.util.Objects.hashCode(effectiveContextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(effectiveMaxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "Profile[definition=" + definition + ", available=" + available + ", credentialPresent=" + credentialPresent + ", effectiveContextWindowTokens=" + effectiveContextWindowTokens + ", effectiveMaxOutputTokens=" + effectiveMaxOutputTokens + ", failure=" + failure + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Profile> schema() {
            return new dev.openallay.value.ValueSchema<>(Profile.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Profile>>asList(new dev.openallay.value.ValueSchema.Component<>(Profile.class, "definition", Profile::definition), new dev.openallay.value.ValueSchema.Component<>(Profile.class, "available", Profile::available), new dev.openallay.value.ValueSchema.Component<>(Profile.class, "credentialPresent", Profile::credentialPresent), new dev.openallay.value.ValueSchema.Component<>(Profile.class, "effectiveContextWindowTokens", Profile::effectiveContextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(Profile.class, "effectiveMaxOutputTokens", Profile::effectiveMaxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(Profile.class, "failure", Profile::failure), new dev.openallay.value.ValueSchema.Component<>(Profile.class, "imageCapability", Profile::imageCapability)), arguments -> new Profile((ModelProfileDefinition) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (GuideFailure) arguments[5], (ModelImageCapabilityResolution) arguments[6]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelProfileSettingsView)) return false;
        ModelProfileSettingsView that = (ModelProfileSettingsView) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(profiles, that.profiles) && java.util.Objects.equals(metadataFailure, that.metadataFailure) && java.util.Objects.equals(connectionResult, that.connectionResult);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(profiles);
        hash = 31 * hash + java.util.Objects.hashCode(metadataFailure);
        hash = 31 * hash + java.util.Objects.hashCode(connectionResult);
        return hash;
    }
    @Override public String toString() { return "ModelProfileSettingsView[config=" + config + ", profiles=" + profiles + ", metadataFailure=" + metadataFailure + ", connectionResult=" + connectionResult + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelProfileSettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelProfileSettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelProfileSettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelProfileSettingsView.class, "config", ModelProfileSettingsView::config), new dev.openallay.value.ValueSchema.Component<>(ModelProfileSettingsView.class, "profiles", ModelProfileSettingsView::profiles), new dev.openallay.value.ValueSchema.Component<>(ModelProfileSettingsView.class, "metadataFailure", ModelProfileSettingsView::metadataFailure), new dev.openallay.value.ValueSchema.Component<>(ModelProfileSettingsView.class, "connectionResult", ModelProfileSettingsView::connectionResult)), arguments -> new ModelProfileSettingsView((ModelProfilesConfig) arguments[0], (List) arguments[1], (GuideFailure) arguments[2], (ModelConnectionResult) arguments[3]));
        }
    }
}
