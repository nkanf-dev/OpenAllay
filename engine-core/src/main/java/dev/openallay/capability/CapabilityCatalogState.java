package dev.openallay.capability;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Detached availability and deny state projected onto trusted descriptors. */
@dev.openallay.value.ValueType(CapabilityCatalogState.ValueSchemaProvider.class)
public final class CapabilityCatalogState {
    private final Set<String> unavailableIds;
    private final Set<String> disabledIds;
    public CapabilityCatalogState(Set<String> unavailableIds, Set<String> disabledIds) {

        unavailableIds = canonical(unavailableIds, "unavailable capability id");
        disabledIds = canonical(disabledIds, "disabled capability id");

        this.unavailableIds = unavailableIds;
        this.disabledIds = disabledIds;
    }
    public Set<String> unavailableIds() { return unavailableIds; }
    public Set<String> disabledIds() { return disabledIds; }
public static CapabilityCatalogState defaults() {
        return new CapabilityCatalogState(dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf());
    }
private static Set<String> canonical(Set<String> values, String name) {
        if (values == null) {
            throw new NullPointerException(name);
        }
        TreeSet<String> result = new TreeSet<>();
        for (String value : values) {
            result.add(CapabilitySettingsDescriptor.requireIdentity(value, name));
        }
        return Collections.unmodifiableSet(result);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilityCatalogState)) return false;
        CapabilityCatalogState that = (CapabilityCatalogState) other;
        return java.util.Objects.equals(unavailableIds, that.unavailableIds) && java.util.Objects.equals(disabledIds, that.disabledIds);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(unavailableIds);
        hash = 31 * hash + java.util.Objects.hashCode(disabledIds);
        return hash;
    }
    @Override public String toString() { return "CapabilityCatalogState[unavailableIds=" + unavailableIds + ", disabledIds=" + disabledIds + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilityCatalogState> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilityCatalogState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilityCatalogState>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilityCatalogState.class, "unavailableIds", CapabilityCatalogState::unavailableIds), new dev.openallay.value.ValueSchema.Component<>(CapabilityCatalogState.class, "disabledIds", CapabilityCatalogState::disabledIds)), arguments -> new CapabilityCatalogState((Set) arguments[0], (Set) arguments[1]));
        }
    }
}
