package dev.openallay.tool.result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Permission-independent, loss-labelled model view of a canonical result. */
public final class JsonResultProjection {
    /** A transport envelope, not a model context limit. The agent supplies the actual remainder. */
    public static final int DEFAULT_MAXIMUM_UTF8_BYTES = 32 * 1024;
    public static final int MINIMUM_PROJECTION_BYTES = 256;
    private static final String OMITTED = "…";

    private JsonResultProjection() {}

    /** Budget includes JSON-string escaping, exactly as the context estimator counts it. */
    public static Projection project(JsonElement value, String handle, String suffix, int maximumUtf8Bytes) {
        if (maximumUtf8Bytes < MINIMUM_PROJECTION_BYTES) {
            throw new IllegalArgumentException("Model result budget must be at least " + MINIMUM_PROJECTION_BYTES + " encoded UTF-8 bytes");
        }
        value = value == null ? JsonNull.INSTANCE : value;
        return project(value, new dev.openallay.tool.ModelResultView(handle == null ? "" : handle,
                type(value), cardinality(value), serializedBytes(value), true,
                handle == null || dev.openallay.util.Java8Strings.isBlank(handle) ? "original tool result" : "current request only"),
                suffix, maximumUtf8Bytes);
    }

    /** Re-projects a prior view without claiming that its partial value is the complete result. */
    public static Projection project(JsonElement value, dev.openallay.tool.ModelResultView source,
            String suffix, int maximumUtf8Bytes) {
        return project(value, source, suffix, maximumUtf8Bytes, value);
    }

    public static Projection project(JsonElement value, dev.openallay.tool.ModelResultView source,
            String suffix, int maximumUtf8Bytes, JsonElement schemaSource) {
        if (maximumUtf8Bytes < MINIMUM_PROJECTION_BYTES)
            throw new IllegalArgumentException("Model result budget is too small for a structural receipt");
        value = value == null ? JsonNull.INSTANCE : value;
        String handle = "current request only".equals(source.lifetime()) ? source.handle() : "";
        suffix = suffix == null ? "" : dev.openallay.util.Java8Strings.strip(suffix);
        suffix = clipText(suffix, Math.max(128, maximumUtf8Bytes / 8));
        long bytes = source.canonicalUtf8Bytes();
        String type = source.type();
        long cardinality = source.cardinality();
        String receipt = (handle == null || dev.openallay.util.Java8Strings.isBlank(handle) ? "" : "result: " + handle + " (current request only)\n")
                + "type: " + type + "\ncardinality: " + cardinality + "\nsize: " + bytes + " UTF-8 byte(s)\n";
        String next = handle == null || dev.openallay.util.Java8Strings.isBlank(handle)
                ? "omitted data remains in the original tool result; this view creates no workspace handle"
                : "use handles: [\"" + handle + "\"] with workspace.open(\"" + handle + "\") in this request to filter, aggregate, or project required data";
        String schema = schema(schemaSource == null ? value : schemaSource,
                Math.max(0, maximumUtf8Bytes / 3));
        int available = maximumUtf8Bytes - encodedBytes(receipt + "scope: preview\nschema:\n" + schema
                + "\npreview:\n\nomitted: " + next + (suffix.isEmpty() ? "" : "\n" + suffix)) - 32;
        while (available > 0) {
            PreviewBudget budget = new PreviewBudget(available);
            JsonElement preview = preview(value, 0, budget);
            boolean complete = source.complete() && !budget.truncated;
            String ending = complete
                    ? "next: answer from this complete result; do not call run_javascript again only to verify it"
                    : "omitted: " + budget.omittedRows + " row(s), " + budget.omittedFields + " field(s) or nested value(s); " + next;
            String text = receipt + "scope: " + (complete ? "complete" : "preview") + "\nschema:\n" + schema
                    + "\npreview:\n" + render(preview) + "\n" + ending + (suffix.isEmpty() ? "" : "\n" + suffix);
            int encoded = encodedBytes(text);
            if (encoded <= maximumUtf8Bytes) {
                return new Projection(type, cardinality, bytes, fields(preview), preview, text,
                        complete, budget.omittedRows, budget.omittedFields);
            }
            available -= Math.max(32, encoded - maximumUtf8Bytes);
        }
        // Metadata and a real recovery route have priority over a partial value. Never clip a
        // rendered result mid-field and accidentally turn an omitted value into a complete one.
        String provenance = suffix.isEmpty() ? "" : "\n" + suffix;
        String text = receipt + "scope: preview\npreview: (omitted by model output budget)\nnext: " + next + provenance;
        if (encodedBytes(text) > maximumUtf8Bytes) {
            text = receipt + "scope: preview\npreview: (omitted by model output budget)" + provenance;
        }
        if (encodedBytes(text) > maximumUtf8Bytes) {
            text = (handle == null || dev.openallay.util.Java8Strings.isBlank(handle) ? "" : "result: " + handle + " (current request only)\n")
                    + "scope: preview\ntype: " + type + "\nsize: " + bytes + " UTF-8 byte(s)" + provenance;
        }
        if (encodedBytes(text) > maximumUtf8Bytes)
            throw new IllegalArgumentException("Model result budget is too small for provenance and a structural receipt");
        return new Projection(type, cardinality, bytes, dev.openallay.util.Java8Collections.listOf(), new JsonPrimitive(OMITTED), text,
                false, value.isJsonArray() ? value.getAsJsonArray().size() : 0,
                value.isJsonObject() ? value.getAsJsonObject().size() : 0);
    }

    private static JsonElement preview(JsonElement value, int depth, PreviewBudget budget) {
        // This is a display traversal bound, not an execution, storage, or domain-data limit.
        // Each displayed level must pay its indentation and framing from the output budget.
        if (depth > 64 || !budget.take(2 + depth * 2)) {
            budget.truncated = true;
            return new JsonPrimitive(OMITTED);
        }
        if (value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isString() && value.getAsString().length() >= budget.remaining) {
                String fragment = clipText(value.getAsString(), budget.remaining);
                budget.remaining = 0;
                budget.truncated = true;
                return new JsonPrimitive(fragment);
            }
            if (budget.take((int) Math.min(Integer.MAX_VALUE, primitiveBytes(value)))) return dev.openallay.json.JsonTrees.copy(value);
            return new JsonPrimitive(OMITTED);
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            JsonArray array = value.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                if (budget.remaining < 8 + depth * 2) {
                    budget.truncated = true;
                    budget.omittedRows += array.size() - index;
                    break;
                }
                result.add(preview(array.get(index), depth + 1, budget));
            }
            return result;
        }
        JsonObject result = new JsonObject();
        Comparator<Field> order = Comparator.comparingLong(Field::cost).thenComparingInt(Field::ordinal);
        int retainedFields = Math.max(1, budget.remaining / 8);
        java.util.PriorityQueue<Field> candidates = new java.util.PriorityQueue<>(order.reversed());
        int ordinal = 0;
        for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
            Field candidate = new Field(field.getKey(), field.getValue(),
                    Math.min(budget.maximum, field.getKey().length()
                            + cappedSize(field.getValue(), budget.maximum)), ordinal++);
            if (candidates.size() < retainedFields) candidates.add(candidate);
            else if (order.compare(candidate, candidates.peek()) < 0) {
                candidates.remove();
                candidates.add(candidate);
            }
        }
        List<Field> fields = new ArrayList<>(candidates);
        int discarded = value.getAsJsonObject().size() - fields.size();
        if (discarded > 0) { budget.truncated = true; budget.omittedFields += discarded; }
        // Compact findings and small metadata must not disappear just because a large array
        // appeared first. Order by structural cost, never by domain-specific field names.
        fields.sort(order);
        for (int index = 0; index < fields.size(); index++) {
            Field field = fields.get(index);
            long keyCost = quotedBytes(field.name()) + 2 + depth * 2L;
            if (keyCost + 8 > budget.remaining) {
                budget.truncated = true;
                budget.omittedFields += fields.size() - index;
                break;
            }
            budget.take((int) keyCost);
            result.add(field.name(), preview(field.value(), depth + 1, budget));
        }
        return result;
    }

    private static String schema(JsonElement value, int maximumBytes) {
        StringBuilder result = new StringBuilder();
        PreviewBudget budget = new PreviewBudget(maximumBytes);
        ArrayDeque<SchemaNode> pending = new ArrayDeque<>();
        ArrayList<SchemaNode> containers = new ArrayList<>();
        pending.add(new SchemaNode("$", value, 0));
        // Discover structural branches, not all array rows. Samples are explicitly labelled.
        while (!pending.isEmpty() && containers.size() < maximumBytes / 8) {
            SchemaNode node = pending.removeFirst();
            containers.add(node);
            if (node.depth() >= 8) continue;
            if (node.value().isJsonObject()) {
                for (Map.Entry<String, JsonElement> field : node.value().getAsJsonObject().entrySet()) {
                    if (field.getValue().isJsonArray() || field.getValue().isJsonObject()) {
                        if (pending.size() + containers.size() >= maximumBytes / 8) break;
                        pending.add(new SchemaNode(node.path().equals("$") ? field.getKey()
                                : node.path() + "." + field.getKey(), field.getValue(), node.depth() + 1));
                    }
                }
            }
        }
        // Cardinalities and sample shape of bulk branches come before verbose object details.
        // This is structural prioritisation, not knowledge of any tool or game-data field.
        containers.sort(Comparator.comparingInt(node -> node.value().isJsonArray() ? 0 : 1));
        for (SchemaNode node : containers) {
            JsonElement child = node.value();
            StringBuilder line = new StringBuilder(node.path()).append(": ").append(type(child));
            if (child.isJsonArray()) {
                JsonArray array = child.getAsJsonArray();
                line.append('[').append(array.size()).append(']');
                if (!(array.size() == 0)) {
                    JsonElement sample = array.get(0);
                    line.append("; first element: ").append(type(sample));
                    if (sample.isJsonObject()) appendFieldNames(line, sample.getAsJsonObject(), budget.remaining);
                }
            } else {
                JsonObject object = child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
                if (child.isJsonObject()) line.append('{').append(object.size()).append('}');
                appendFieldNames(line, object, budget.remaining);
            }
            if (!budget.take(encodedBytes(line.toString()) + 1)) break;
            if (result.length() != 0) result.append('\n');
            result.append(line);
        }
        return result.length() == 0 ? type(value) : result.toString();
    }

    private static void appendFieldNames(StringBuilder line, JsonObject value, int maximumBytes) {
        if (value.size() == 0) return;
        line.append(" fields[");
        boolean first = true;
        for (String field : dev.openallay.json.JsonTrees.keys(value)) {
            String descriptor = (first ? "" : ",") + field;
            // Keep room for the closing bracket and any explicitly omitted fields.
            if (encodedBytes(line.toString() + descriptor + "…]") + 1 > maximumBytes) {
                line.append("…");
                break;
            }
            line.append(descriptor);
            first = false;
        }
        line.append(']');
    }

    /** Clips only a scalar fragment and labels the omitted tail; complete values are never relabelled. */
    public static String clipText(String value, int maximumUtf8Bytes) {
        if (value.length() < maximumUtf8Bytes && encodedBytes(value) <= maximumUtf8Bytes) return value;
        if (maximumUtf8Bytes < 5) return "";
        int low = 0;
        int high = Math.min(value.length(), maximumUtf8Bytes);
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = safeEnd(value, middle);
            if (encodedBytes(value.substring(0, end) + "…") <= maximumUtf8Bytes) low = middle;
            else high = middle - 1;
        }
        return value.substring(0, safeEnd(value, low)) + "…";
    }

    private static int safeEnd(String value, int end) {
        return end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end)) ? end - 1 : end;
    }

    @dev.openallay.value.ValueType(SchemaNode.ValueSchemaProvider.class)
private static final class SchemaNode {
    private final String path;
    private final JsonElement value;
    private final int depth;
    private SchemaNode(String path, JsonElement value, int depth) {
        this.path = path;
        this.value = value;
        this.depth = depth;
    }
    public String path() { return path; }
    public JsonElement value() { return value; }
    public int depth() { return depth; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SchemaNode)) return false;
        SchemaNode that = (SchemaNode) other;
        return java.util.Objects.equals(path, that.path) && java.util.Objects.equals(value, that.value) && depth == that.depth;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(path);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + Integer.hashCode(depth);
        return hash;
    }
    @Override public String toString() { return "SchemaNode[path=" + path + ", value=" + value + ", depth=" + depth + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SchemaNode> schema() {
            return new dev.openallay.value.ValueSchema<>(SchemaNode.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SchemaNode>>asList(new dev.openallay.value.ValueSchema.Component<>(SchemaNode.class, "path", SchemaNode::path), new dev.openallay.value.ValueSchema.Component<>(SchemaNode.class, "value", SchemaNode::value), new dev.openallay.value.ValueSchema.Component<>(SchemaNode.class, "depth", SchemaNode::depth)), arguments -> new SchemaNode((String) arguments[0], (JsonElement) arguments[1], (Integer) arguments[2]));
        }
    }
}

    public static String render(JsonElement value) {
        StringBuilder result = new StringBuilder();
        appendValue(result, value, 0, null);
        return result.toString();
    }

    private static void appendValue(StringBuilder output, JsonElement value, int indent, String listPrefix) {
        String padding = dev.openallay.util.Java8Strings.repeat(" ", indent);
        if (value.isJsonNull() || value.isJsonPrimitive()) {
            output.append(padding).append(listPrefix == null ? "" : listPrefix).append(scalar(value));
        } else if (value.isJsonArray()) {
            if ((value.getAsJsonArray().size() == 0)) output.append(padding).append(listPrefix == null ? "" : listPrefix).append("(empty)");
            boolean first = true;
            for (JsonElement row : value.getAsJsonArray()) {
                if (!first) output.append('\n');
                appendValue(output, row, indent, "- ");
                first = false;
            }
        } else {
            if (value.getAsJsonObject().size() == 0) {
                output.append(padding).append(listPrefix == null ? "" : listPrefix).append("(empty object)");
                return;
            }
            if (listPrefix != null) output.append(padding).append(listPrefix);
            boolean first = true;
            for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
                if (!first) output.append('\n');
                output.append(first && listPrefix == null ? padding : dev.openallay.util.Java8Strings.repeat(" ", indent + (listPrefix == null ? 0 : 2)))
                        .append(fieldLabel(field.getKey())).append(':');
                JsonElement child = field.getValue();
                if (child.isJsonNull() || child.isJsonPrimitive()) output.append(' ').append(scalar(child));
                else {
                    output.append('\n');
                    appendValue(output, child, indent + (listPrefix == null ? 2 : 4), null);
                }
                first = false;
            }
        }
    }

    public static String fieldLabel(String field) {
        return field.chars().allMatch(character -> Character.isJavaIdentifierPart(character)
                || character == ':' || character == '.' || character == '-')
                ? field : new JsonPrimitive(field).toString();
    }

    public static String scalar(JsonElement value) {
        return value == null || value.isJsonNull() ? "null" : value.toString();
    }

    /** Conservative rendering ceiling; callers must still verify each whole provider request. */
    public static int projectionSizeUpperBound(dev.openallay.tool.ModelResultView source) {
        long bytes = source.canonicalUtf8Bytes();
        long ceiling = bytes > (Integer.MAX_VALUE - 4096L) / 16
                ? Integer.MAX_VALUE : bytes * 16 + 4096;
        return (int) Math.max(MINIMUM_PROJECTION_BYTES, ceiling);
    }

    public static int encodedBytes(String text) {
        long bytes = quotedBytes(text);
        return bytes >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    public static long serializedBytes(JsonElement value) {
        return countEncodedJson(value == null ? JsonNull.INSTANCE : value);
    }

    private static long cappedSize(JsonElement root, long cap) {
        ArrayDeque<JsonElement> pending = new ArrayDeque<>();
        pending.add(root);
        long bytes = 0;
        while (!pending.isEmpty() && bytes < cap) {
            JsonElement value = pending.removeLast();
            if (value.isJsonNull() || value.isJsonPrimitive()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && value.getAsString().length() >= cap - bytes) return cap;
                bytes = add(bytes, primitiveBytes(value));
            }
            else if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                bytes = add(bytes, 2L + Math.max(0, array.size() - 1));
                int remainingSlots = (int) Math.min(array.size(), Math.max(0, cap - bytes));
                for (int index = 0; index < remainingSlots; index++) pending.add(array.get(index));
                if (remainingSlots < array.size()) return cap;
            } else {
                JsonObject object = value.getAsJsonObject();
                bytes = add(bytes, 2L + Math.max(0, object.size() - 1));
                for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                    bytes = add(bytes, quotedBytes(field.getKey()) + 1);
                    if (pending.size() < cap - bytes) pending.add(field.getValue());
                    else return cap;
                }
            }
        }
        return Math.min(bytes, cap);
    }

    private static long primitiveBytes(JsonElement value) {
        if (value.isJsonNull()) return 4;
        return value.getAsJsonPrimitive().isString() ? quotedBytes(value.getAsString())
                : value.getAsString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static long quotedBytes(String text) {
        return serializedBytes(new JsonPrimitive(text));
    }

    private static final com.google.gson.Gson JSON = dev.openallay.json.EngineJson.create();

    private static long countEncodedJson(JsonElement value) {
        CountingOutputStream counter = new CountingOutputStream();
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(counter, StandardCharsets.UTF_8);
                com.google.gson.stream.JsonWriter json = new com.google.gson.stream.JsonWriter(writer)) {
            // JsonElement.toString() uses Gson's normal, non-HTML-safe tree encoding.
            json.setHtmlSafe(false);
            json.setLenient(true);
            json.setSerializeNulls(true);
            JSON.getAdapter(JsonElement.class).write(json, value);
        } catch (java.io.IOException impossible) {
            throw new java.io.UncheckedIOException(impossible);
        }
        return counter.count;
    }

    private static final class CountingOutputStream extends java.io.OutputStream {
        private long count;
        @Override public void write(int value) { count = add(count, 1); }
        @Override public void write(byte[] bytes, int offset, int length) { count = add(count, length); }
    }

    private static long add(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public static String type(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonArray()) return "array";
        if (value.isJsonObject()) return "object";
        if (value.getAsJsonPrimitive().isBoolean()) return "boolean";
        if (value.getAsJsonPrimitive().isNumber()) return "number";
        return "string";
    }

    public static long cardinality(JsonElement value) {
        if (value == null || value.isJsonNull()) return 0;
        if (value.isJsonArray()) return value.getAsJsonArray().size();
        if (value.isJsonObject()) return value.getAsJsonObject().size();
        return 1;
    }

    private static List<String> fields(JsonElement preview) {
        if (preview.isJsonObject()) return dev.openallay.util.Java8Collections.listCopyOf(dev.openallay.json.JsonTrees.keys(preview.getAsJsonObject()));
        if (preview.isJsonArray() && !(preview.getAsJsonArray().size() == 0) && preview.getAsJsonArray().get(0).isJsonObject())
            return dev.openallay.util.Java8Collections.listCopyOf(dev.openallay.json.JsonTrees.keys(preview.getAsJsonArray().get(0).getAsJsonObject()));
        return dev.openallay.util.Java8Collections.listOf();
    }

    @dev.openallay.value.ValueType(Projection.ValueSchemaProvider.class)
public static final class Projection {
    private final String type;
    private final long cardinality;
    private final long serializedBytes;
    private final List<String> fields;
    private final JsonElement preview;
    private final String modelText;
    private final boolean complete;
    private final int omittedRows;
    private final int omittedFields;
    public Projection(String type, long cardinality, long serializedBytes, List<String> fields, JsonElement preview, String modelText, boolean complete, int omittedRows, int omittedFields) {
 fields = dev.openallay.util.Java8Collections.listCopyOf(fields); preview = dev.openallay.json.JsonTrees.copy(preview);
        this.type = type;
        this.cardinality = cardinality;
        this.serializedBytes = serializedBytes;
        this.fields = fields;
        this.preview = preview;
        this.modelText = modelText;
        this.complete = complete;
        this.omittedRows = omittedRows;
        this.omittedFields = omittedFields;
    }
    public String type() { return type; }
    public long cardinality() { return cardinality; }
    public long serializedBytes() { return serializedBytes; }
    public List<String> fields() { return fields; }
    public String modelText() { return modelText; }
    public boolean complete() { return complete; }
    public int omittedRows() { return omittedRows; }
    public int omittedFields() { return omittedFields; }
 public JsonElement preview() { return dev.openallay.json.JsonTrees.copy(preview); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Projection)) return false;
        Projection that = (Projection) other;
        return java.util.Objects.equals(type, that.type) && cardinality == that.cardinality && serializedBytes == that.serializedBytes && java.util.Objects.equals(fields, that.fields) && java.util.Objects.equals(preview, that.preview) && java.util.Objects.equals(modelText, that.modelText) && complete == that.complete && omittedRows == that.omittedRows && omittedFields == that.omittedFields;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + Long.hashCode(cardinality);
        hash = 31 * hash + Long.hashCode(serializedBytes);
        hash = 31 * hash + java.util.Objects.hashCode(fields);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        hash = 31 * hash + java.util.Objects.hashCode(modelText);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedRows);
        hash = 31 * hash + Integer.hashCode(omittedFields);
        return hash;
    }
    @Override public String toString() { return "Projection[type=" + type + ", cardinality=" + cardinality + ", serializedBytes=" + serializedBytes + ", fields=" + fields + ", preview=" + preview + ", modelText=" + modelText + ", complete=" + complete + ", omittedRows=" + omittedRows + ", omittedFields=" + omittedFields + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Projection> schema() {
            return new dev.openallay.value.ValueSchema<>(Projection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Projection>>asList(new dev.openallay.value.ValueSchema.Component<>(Projection.class, "type", Projection::type), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "cardinality", Projection::cardinality), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "serializedBytes", Projection::serializedBytes), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "fields", Projection::fields), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "preview", Projection::preview), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "modelText", Projection::modelText), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "complete", Projection::complete), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "omittedRows", Projection::omittedRows), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "omittedFields", Projection::omittedFields)), arguments -> new Projection((String) arguments[0], (Long) arguments[1], (Long) arguments[2], (List) arguments[3], (JsonElement) arguments[4], (String) arguments[5], (Boolean) arguments[6], (Integer) arguments[7], (Integer) arguments[8]));
        }
    }
}
    @dev.openallay.value.ValueType(Field.ValueSchemaProvider.class)
private static final class Field {
    private final String name;
    private final JsonElement value;
    private final long cost;
    private final int ordinal;
    private Field(String name, JsonElement value, long cost, int ordinal) {
        this.name = name;
        this.value = value;
        this.cost = cost;
        this.ordinal = ordinal;
    }
    public String name() { return name; }
    public JsonElement value() { return value; }
    public long cost() { return cost; }
    public int ordinal() { return ordinal; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Field)) return false;
        Field that = (Field) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(value, that.value) && cost == that.cost && ordinal == that.ordinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + Long.hashCode(cost);
        hash = 31 * hash + Integer.hashCode(ordinal);
        return hash;
    }
    @Override public String toString() { return "Field[name=" + name + ", value=" + value + ", cost=" + cost + ", ordinal=" + ordinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Field> schema() {
            return new dev.openallay.value.ValueSchema<>(Field.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Field>>asList(new dev.openallay.value.ValueSchema.Component<>(Field.class, "name", Field::name), new dev.openallay.value.ValueSchema.Component<>(Field.class, "value", Field::value), new dev.openallay.value.ValueSchema.Component<>(Field.class, "cost", Field::cost), new dev.openallay.value.ValueSchema.Component<>(Field.class, "ordinal", Field::ordinal)), arguments -> new Field((String) arguments[0], (JsonElement) arguments[1], (Long) arguments[2], (Integer) arguments[3]));
        }
    }
}
    private static final class PreviewBudget {
        private final int maximum;
        private int remaining;
        private boolean truncated;
        private int omittedRows;
        private int omittedFields;
        private PreviewBudget(int maximum) { this.maximum = maximum; this.remaining = maximum; }
        private boolean take(int amount) {
            if (amount <= remaining) { remaining -= amount; return true; }
            remaining = 0; truncated = true; return false;
        }
    }
}
