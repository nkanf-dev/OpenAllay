package dev.openallay.client.resource;

@dev.openallay.value.ValueType(ClientResource.ValueSchemaProvider.class)
public final class ClientResource {
    private final String resourceId;
    private final String packId;
    private final int priority;
    private final boolean selected;
    private final String content;
    public ClientResource(String resourceId, String packId, int priority, boolean selected, String content) {

        if (resourceId == null || !resourceId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid client resource ID: " + resourceId);
        }
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("Pack ID must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("Resource content must not be null");
        }

        this.resourceId = resourceId;
        this.packId = packId;
        this.priority = priority;
        this.selected = selected;
        this.content = content;
    }
    public String resourceId() { return resourceId; }
    public String packId() { return packId; }
    public int priority() { return priority; }
    public boolean selected() { return selected; }
    public String content() { return content; }
public String namespace() {
        return resourceId.substring(0, resourceId.indexOf(':'));
    }
public String path() {
        return resourceId.substring(resourceId.indexOf(':') + 1);
    }
public String provenance() {
        return "pack=" + packId + ",resource=" + resourceId + ",priority=" + priority;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientResource)) return false;
        ClientResource that = (ClientResource) other;
        return java.util.Objects.equals(resourceId, that.resourceId) && java.util.Objects.equals(packId, that.packId) && priority == that.priority && selected == that.selected && java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(resourceId);
        hash = 31 * hash + java.util.Objects.hashCode(packId);
        hash = 31 * hash + Integer.hashCode(priority);
        hash = 31 * hash + Boolean.hashCode(selected);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "ClientResource[resourceId=" + resourceId + ", packId=" + packId + ", priority=" + priority + ", selected=" + selected + ", content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientResource> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientResource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientResource>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientResource.class, "resourceId", ClientResource::resourceId), new dev.openallay.value.ValueSchema.Component<>(ClientResource.class, "packId", ClientResource::packId), new dev.openallay.value.ValueSchema.Component<>(ClientResource.class, "priority", ClientResource::priority), new dev.openallay.value.ValueSchema.Component<>(ClientResource.class, "selected", ClientResource::selected), new dev.openallay.value.ValueSchema.Component<>(ClientResource.class, "content", ClientResource::content)), arguments -> new ClientResource((String) arguments[0], (String) arguments[1], (Integer) arguments[2], (Boolean) arguments[3], (String) arguments[4]));
        }
    }
}
