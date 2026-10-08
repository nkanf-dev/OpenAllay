package dev.openallay.capability;

/** Typed settings route; it carries no callback, path, URL, or permission. */
@dev.openallay.value.ValueType(CapabilityChildPage.ValueSchemaProvider.class)
public final class CapabilityChildPage {
    private final String routeId;
    public CapabilityChildPage(String routeId) {

        routeId = CapabilityPolicy.requireToolId(routeId);

        this.routeId = routeId;
    }
    public String routeId() { return routeId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilityChildPage)) return false;
        CapabilityChildPage that = (CapabilityChildPage) other;
        return java.util.Objects.equals(routeId, that.routeId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(routeId);
        return hash;
    }
    @Override public String toString() { return "CapabilityChildPage[routeId=" + routeId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilityChildPage> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilityChildPage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilityChildPage>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilityChildPage.class, "routeId", CapabilityChildPage::routeId)), arguments -> new CapabilityChildPage((String) arguments[0]));
        }
    }
}
