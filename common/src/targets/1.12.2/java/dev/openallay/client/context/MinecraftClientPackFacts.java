package dev.openallay.client.context;
import java.util.List;
import net.minecraft.client.Minecraft;
/** Real legacy repository entry identity and metadata, without a fabricated modern pack API. */
public final class MinecraftClientPackFacts {
    private MinecraftClientPackFacts() {}
    @dev.openallay.value.ValueType(Pack.ValueSchemaProvider.class)
public static final class Pack {
    private final String id;
    private final String title;
    private final String description;
    private final boolean required;
    private final String compatibility;
    private final String source;
    public Pack(String id, String title, String description, boolean required, String compatibility, String source) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.required = required;
        this.compatibility = compatibility;
        this.source = source;
    }
    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public boolean required() { return required; }
    public String compatibility() { return compatibility; }
    public String source() { return source; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Pack)) return false;
        Pack that = (Pack) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && required == that.required && java.util.Objects.equals(compatibility, that.compatibility) && java.util.Objects.equals(source, that.source);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + Boolean.hashCode(required);
        hash = 31 * hash + java.util.Objects.hashCode(compatibility);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        return hash;
    }
    @Override public String toString() { return "Pack[id=" + id + ", title=" + title + ", description=" + description + ", required=" + required + ", compatibility=" + compatibility + ", source=" + source + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Pack> schema() {
            return new dev.openallay.value.ValueSchema<>(Pack.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Pack>>asList(new dev.openallay.value.ValueSchema.Component<>(Pack.class, "id", Pack::id), new dev.openallay.value.ValueSchema.Component<>(Pack.class, "title", Pack::title), new dev.openallay.value.ValueSchema.Component<>(Pack.class, "description", Pack::description), new dev.openallay.value.ValueSchema.Component<>(Pack.class, "required", Pack::required), new dev.openallay.value.ValueSchema.Component<>(Pack.class, "compatibility", Pack::compatibility), new dev.openallay.value.ValueSchema.Component<>(Pack.class, "source", Pack::source)), arguments -> new Pack((String) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}
    public static List<String> selectedIds(Minecraft client) {
        return client.getResourcePackRepository().getRepositoryEntries().stream().map(entry -> entry.toString()).toList();
    }
    public static List<Pack> available(Minecraft client) {
        return client.getResourcePackRepository().getRepositoryEntriesAll().stream()
                .map(entry -> new Pack(entry.toString(), entry.getResourcePackName(), entry.getTexturePackDescription(),
                        false, "native_pack_format:" + entry.getPackFormat(), "native_resource_pack_repository_entry")).toList();
    }
}
