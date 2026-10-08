package dev.openallay.model.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict ordered profile document stored without credential values. */
@dev.openallay.value.ValueType(ModelProfilesConfig.ValueSchemaProvider.class)
public final class ModelProfilesConfig {
    private final String defaultProfileId;
    private final List<ModelProfileDefinition> profiles;
    public ModelProfilesConfig(String defaultProfileId, List<ModelProfileDefinition> profiles) {

        if (defaultProfileId == null || !defaultProfileId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid defaultProfileId");
        }
        profiles = dev.openallay.util.Java8Collections.listCopyOf(profiles);
        Set<String> ids = new HashSet<>();
        for (ModelProfileDefinition profile : profiles) {
            if (!ids.add(profile.id())) {
                throw new IllegalArgumentException("duplicate model profile id " + profile.id());
            }
        }
        if (!ids.contains(defaultProfileId)) {
            throw new IllegalArgumentException("defaultProfileId does not name a profile");
        }

        this.defaultProfileId = defaultProfileId;
        this.profiles = profiles;
    }
    public String defaultProfileId() { return defaultProfileId; }
    public List<ModelProfileDefinition> profiles() { return profiles; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelProfilesConfig)) return false;
        ModelProfilesConfig that = (ModelProfilesConfig) other;
        return java.util.Objects.equals(defaultProfileId, that.defaultProfileId) && java.util.Objects.equals(profiles, that.profiles);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(defaultProfileId);
        hash = 31 * hash + java.util.Objects.hashCode(profiles);
        return hash;
    }
    @Override public String toString() { return "ModelProfilesConfig[defaultProfileId=" + defaultProfileId + ", profiles=" + profiles + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelProfilesConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelProfilesConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelProfilesConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelProfilesConfig.class, "defaultProfileId", ModelProfilesConfig::defaultProfileId), new dev.openallay.value.ValueSchema.Component<>(ModelProfilesConfig.class, "profiles", ModelProfilesConfig::profiles)), arguments -> new ModelProfilesConfig((String) arguments[0], (List) arguments[1]));
        }
    }
}
