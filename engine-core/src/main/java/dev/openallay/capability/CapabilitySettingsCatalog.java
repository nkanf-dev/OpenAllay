package dev.openallay.capability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Trusted registration authority for presentation-only capability descriptors. */
public final class CapabilitySettingsCatalog {
    private static final Comparator<Registration> ORDER = Comparator
            .comparing((Registration value) -> value.descriptor().kind())
            .thenComparing(value -> value.descriptor().id());

    private final Map<String, Registration> registrations = new TreeMap<>();

    public synchronized void register(
            String ownerId, Collection<CapabilitySettingsDescriptor> descriptors) {
        String owner = CapabilitySettingsDescriptor.requireIdentity(ownerId, "owner id");
        List<CapabilitySettingsDescriptor> candidate = dev.openallay.util.Java8Collections.listCopyOf(descriptors);
        Set<String> batchIds = new HashSet<>();
        for (CapabilitySettingsDescriptor descriptor : candidate) {
            Objects.requireNonNull(descriptor, "descriptor");
            if (!batchIds.add(descriptor.id()) || registrations.containsKey(descriptor.id())) {
                throw new IllegalStateException(
                        "Duplicate capability settings id " + descriptor.id());
            }
        }
        candidate.forEach(descriptor ->
                registrations.put(descriptor.id(), new Registration(owner, descriptor)));
    }

    public synchronized CapabilityCatalogSnapshot snapshot(CapabilityCatalogState state) {
        Objects.requireNonNull(state, "state");
        List<Registration> ordered = dev.openallay.util.Java8Collections.toList(registrations.values().stream().sorted(ORDER));
        List<CapabilitySettingsEntry> entries = new ArrayList<>(ordered.size());
        for (Registration registration : ordered) {
            CapabilitySettingsDescriptor descriptor = registration.descriptor();
            entries.add(new CapabilitySettingsEntry(
                    registration.ownerId(),
                    descriptor.id(),
                    descriptor.kind(),
                    descriptor.titleKey(),
                    descriptor.descriptionKey(),
                    descriptor.childPage(),
                    !state.unavailableIds().contains(descriptor.id()),
                    !state.disabledIds().contains(descriptor.id())));
        }
        return new CapabilityCatalogSnapshot(entries);
    }

    @dev.openallay.value.ValueType(Registration.ValueSchemaProvider.class)
private static final class Registration {
    private final String ownerId;
    private final CapabilitySettingsDescriptor descriptor;
    private Registration(String ownerId, CapabilitySettingsDescriptor descriptor) {
        this.ownerId = ownerId;
        this.descriptor = descriptor;
    }
    public String ownerId() { return ownerId; }
    public CapabilitySettingsDescriptor descriptor() { return descriptor; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Registration)) return false;
        Registration that = (Registration) other;
        return java.util.Objects.equals(ownerId, that.ownerId) && java.util.Objects.equals(descriptor, that.descriptor);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(ownerId);
        hash = 31 * hash + java.util.Objects.hashCode(descriptor);
        return hash;
    }
    @Override public String toString() { return "Registration[ownerId=" + ownerId + ", descriptor=" + descriptor + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Registration> schema() {
            return new dev.openallay.value.ValueSchema<>(Registration.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Registration>>asList(new dev.openallay.value.ValueSchema.Component<>(Registration.class, "ownerId", Registration::ownerId), new dev.openallay.value.ValueSchema.Component<>(Registration.class, "descriptor", Registration::descriptor)), arguments -> new Registration((String) arguments[0], (CapabilitySettingsDescriptor) arguments[1]));
        }
    }
}
}
