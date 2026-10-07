package dev.openallay.settings.capability;

import dev.openallay.capability.CapabilityCatalogSnapshot;
import dev.openallay.capability.CapabilityPolicy;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Immutable capability-domain projection for the native settings screen. */
@dev.openallay.value.ValueType(CapabilitySettingsView.ValueSchemaProvider.class)
public final class CapabilitySettingsView {
    private final CapabilityPolicy policy;
    private final CapabilityCatalogSnapshot catalog;
    private final Set<String> unknownDisabledTools;
    private final Set<String> unknownDisabledSkills;
    public CapabilitySettingsView(CapabilityPolicy policy, CapabilityCatalogSnapshot catalog, Set<String> unknownDisabledTools, Set<String> unknownDisabledSkills) {

        java.util.Objects.requireNonNull(policy, "policy");
        java.util.Objects.requireNonNull(catalog, "catalog");
        unknownDisabledTools = sorted(unknownDisabledTools);
        unknownDisabledSkills = sorted(unknownDisabledSkills);

        this.policy = policy;
        this.catalog = catalog;
        this.unknownDisabledTools = unknownDisabledTools;
        this.unknownDisabledSkills = unknownDisabledSkills;
    }
    public CapabilityPolicy policy() { return policy; }
    public CapabilityCatalogSnapshot catalog() { return catalog; }
    public Set<String> unknownDisabledTools() { return unknownDisabledTools; }
    public Set<String> unknownDisabledSkills() { return unknownDisabledSkills; }
public static CapabilitySettingsView defaults() {
        return new CapabilitySettingsView(
                CapabilityPolicy.defaults(),
                new CapabilityCatalogSnapshot(java.util.List.of()),
                Set.of(),
                Set.of());
    }
private static Set<String> sorted(Set<String> values) {
        return Collections.unmodifiableSet(new TreeSet<>(values));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilitySettingsView)) return false;
        CapabilitySettingsView that = (CapabilitySettingsView) other;
        return java.util.Objects.equals(policy, that.policy) && java.util.Objects.equals(catalog, that.catalog) && java.util.Objects.equals(unknownDisabledTools, that.unknownDisabledTools) && java.util.Objects.equals(unknownDisabledSkills, that.unknownDisabledSkills);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(policy);
        hash = 31 * hash + java.util.Objects.hashCode(catalog);
        hash = 31 * hash + java.util.Objects.hashCode(unknownDisabledTools);
        hash = 31 * hash + java.util.Objects.hashCode(unknownDisabledSkills);
        return hash;
    }
    @Override public String toString() { return "CapabilitySettingsView[policy=" + policy + ", catalog=" + catalog + ", unknownDisabledTools=" + unknownDisabledTools + ", unknownDisabledSkills=" + unknownDisabledSkills + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilitySettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilitySettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilitySettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsView.class, "policy", CapabilitySettingsView::policy), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsView.class, "catalog", CapabilitySettingsView::catalog), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsView.class, "unknownDisabledTools", CapabilitySettingsView::unknownDisabledTools), new dev.openallay.value.ValueSchema.Component<>(CapabilitySettingsView.class, "unknownDisabledSkills", CapabilitySettingsView::unknownDisabledSkills)), arguments -> new CapabilitySettingsView((CapabilityPolicy) arguments[0], (CapabilityCatalogSnapshot) arguments[1], (Set) arguments[2], (Set) arguments[3]));
        }
    }
}
