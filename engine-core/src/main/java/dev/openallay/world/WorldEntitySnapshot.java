package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@dev.openallay.value.ValueType(WorldEntitySnapshot.ValueSchemaProvider.class)
public final class WorldEntitySnapshot {
    private final String observationId;
    private final UUID uuid;
    private final String type;
    private final String name;
    private final WorldPosition position;
    private final Map<String, Object> data;
    private final EvidenceMetadata evidence;
    public WorldEntitySnapshot(String observationId, UUID uuid, String type, String name, WorldPosition position, Map<String, Object> data, EvidenceMetadata evidence) {

        observationId = require(observationId, "observationId");
        Objects.requireNonNull(uuid, "uuid");
        type = require(type, "type");
        name = name == null ? "" : name;
        Objects.requireNonNull(position, "position");
        data = dev.openallay.util.Java8Collections.mapCopyOf(new TreeMap<>(Objects.requireNonNull(data, "data")));
        Objects.requireNonNull(evidence, "evidence");

        this.observationId = observationId;
        this.uuid = uuid;
        this.type = type;
        this.name = name;
        this.position = position;
        this.data = data;
        this.evidence = evidence;
    }
    public String observationId() { return observationId; }
    public UUID uuid() { return uuid; }
    public String type() { return type; }
    public String name() { return name; }
    public WorldPosition position() { return position; }
    public Map<String, Object> data() { return data; }
    public EvidenceMetadata evidence() { return evidence; }
private static String require(String value, String field) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldEntitySnapshot)) return false;
        WorldEntitySnapshot that = (WorldEntitySnapshot) other;
        return java.util.Objects.equals(observationId, that.observationId) && java.util.Objects.equals(uuid, that.uuid) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(position, that.position) && java.util.Objects.equals(data, that.data) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(observationId);
        hash = 31 * hash + java.util.Objects.hashCode(uuid);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(position);
        hash = 31 * hash + java.util.Objects.hashCode(data);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "WorldEntitySnapshot[observationId=" + observationId + ", uuid=" + uuid + ", type=" + type + ", name=" + name + ", position=" + position + ", data=" + data + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldEntitySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldEntitySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldEntitySnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "observationId", WorldEntitySnapshot::observationId), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "uuid", WorldEntitySnapshot::uuid), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "type", WorldEntitySnapshot::type), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "name", WorldEntitySnapshot::name), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "position", WorldEntitySnapshot::position), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "data", WorldEntitySnapshot::data), new dev.openallay.value.ValueSchema.Component<>(WorldEntitySnapshot.class, "evidence", WorldEntitySnapshot::evidence)), arguments -> new WorldEntitySnapshot((String) arguments[0], (UUID) arguments[1], (String) arguments[2], (String) arguments[3], (WorldPosition) arguments[4], (Map) arguments[5], (EvidenceMetadata) arguments[6]));
        }
    }
}
