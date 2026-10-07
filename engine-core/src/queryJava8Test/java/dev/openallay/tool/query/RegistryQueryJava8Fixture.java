package dev.openallay.tool.query;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.context.RegistrySnapshot;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Same full-owner vectors execute on original modern sources and canonical Java8 sources. */
public final class RegistryQueryJava8Fixture {
    private RegistryQueryJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        boolean actual8 = args.length == 1 && args[0].equals("java8");
        if (actual8) {
            equal("1.8", System.getProperty("java.specification.version"));
            for (Class<?> type : Arrays.<Class<?>>asList(RegistryQueryEngine.class,
                    RegistryEntrySnapshot.class, RegistrySnapshot.class, EvidenceMetadata.class, QueryOperation.class)) classMajor(type);
        }
        List<RegistryEntrySnapshot> entries = Arrays.asList(
                entry("test:alpha", "item", "ＦＯＯ", 3, "a", new String[] {"red", "blue"}),
                entry("test:beta", "item", "bar", 1, "a", new String[] {"green"}),
                entry("other:gamma", "block", "Foo stone", 2, "b", new String[] {"red"}));
        RegistryQueryEngine engine = new RegistryQueryEngine();
        RegistryQueryEngine.Schema schema = engine.describe(entries, RegistryQueryEngine.Dataset.all, null);
        check(schema.fields().stream().anyMatch(field -> field.path().equals("/data/test:slash~1tilde_key")), "RFC6901 describe");
        report("searchNfkc", engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.SEARCH, null, null, "foo", null, null, null, null, null))));
        report("namespace", engine.execute(entries, RegistryQueryEngine.Dataset.all, " test ",
                ops(op(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 9))));
        for (QueryOperation.Operator operator : QueryOperation.Operator.values()) {
            String expected = operator == QueryOperation.Operator.EXISTS ? null
                    : operator == QueryOperation.Operator.CONTAINS ? "a" : "2";
            String field = operator == QueryOperation.Operator.CONTAINS ? "/displayName" : "/data/test:score";
            report("filter" + operator, engine.execute(entries, RegistryQueryEngine.Dataset.all,
                    ops(op(QueryOperation.Op.FILTER, field, operator, expected, null, null, null, null, null))));
        }
        report("selectPointer", engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.SELECT, null, null, null,
                        Arrays.asList("/id", "/data/test:slash~1tilde_key"), null, null, null, null))));
        report("sortAsc", engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.SORT, "/data/test:score", null, null, null, null, null, null, null))));
        report("sortDesc", engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.SORT, "/data/test:score", null, null, null, QueryOperation.Direction.DESC, null, null, null))));
        report("group", engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.GROUP, "/data/test:category", null, null, null, null, null, null, null))));
        for (QueryOperation.Aggregate aggregate : QueryOperation.Aggregate.values()) {
            report("aggregate" + aggregate, engine.execute(entries, RegistryQueryEngine.Dataset.all,
                    ops(op(QueryOperation.Op.AGGREGATE, aggregate == QueryOperation.Aggregate.COUNT ? null : "/data/test:score",
                            null, null, null, null, aggregate, null, null))));
        }
        report("aggregateGroup", engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.AGGREGATE, "/data/test:score", null, null, null, null,
                        QueryOperation.Aggregate.SUM, "/data/test:category", null))));
        report("expandTake", engine.execute(entries, RegistryQueryEngine.Dataset.items,
                ops(op(QueryOperation.Op.EXPAND, "/data/test:labels", null, null, null, null, null, null, null),
                        op(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 2))));
        report("datasetBlocks", engine.execute(entries, RegistryQueryEngine.Dataset.blocks,
                ops(op(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 9))));
        for (RegistryQueryEngine.Dataset dataset : RegistryQueryEngine.Dataset.values()) {
            report("dataset" + dataset, engine.execute(entries, dataset,
                    ops(op(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 9))));
        }
        failure("unknown", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.FILTER, "/missing", QueryOperation.Operator.EXISTS, null, null, null, null, null, null))));
        failure("scalarRequired", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.SORT, "/data/test:labels/*", null, null, null, null, null, null, null))));
        failure("takeRequired", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 0))));
        failure("noStages", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all, Collections.<QueryOperation>emptyList()));
        failure("blankFilter", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.FILTER, "/id", QueryOperation.Operator.EQ, " ", null, null, null, null, null))));
        failure("expandNonArray", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.EXPAND, "/id", null, null, null, null, null, null, null))));
        failure("numericRequired", () -> engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.AGGREGATE, "/id", null, null, null, null, QueryOperation.Aggregate.SUM, null, null))));
        RegistryEntrySnapshot original = entries.get(0);
        Map<String, JsonElement> copy = original.properties();
                copy.get("test:labels").getAsJsonArray().add(new JsonPrimitive("later"));
        equal(2, original.properties().get("test:labels").getAsJsonArray().size());
        RegistryQueryEngine.Result result = engine.execute(entries, RegistryQueryEngine.Dataset.all,
                ops(op(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 9)));
        result.rows().get(0).get("data").getAsJsonObject().addProperty("test:score", 999);
        equal(3, result.rows().get(0).get("data").getAsJsonObject().get("test:score").getAsInt());
        try { original.aliases().add("x"); throw new AssertionError("mutable aliases"); } catch (UnsupportedOperationException expected) {}
        try { result.rows().add(Collections.<String, JsonElement>emptyMap()); throw new AssertionError("mutable rows"); } catch (UnsupportedOperationException expected) {}
        EvidenceMetadata evidence = new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST, DataCompleteness.COMPLETE,
                Instant.EPOCH, "test:source", "test:proof", "1.12.2", "forge", Collections.singletonMap("test:key", "value"));
        RegistrySnapshot snapshot = new RegistrySnapshot(evidence, entries);
        equal(entries, snapshot.entries());
        failure("evidenceNull", () -> new RegistrySnapshot(null, entries));
        failure("entryInvalid", () -> new RegistryEntrySnapshot("invalid", "item", "x", "test", "test:proof"));
        failure("evidenceDetailBlank", () -> new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST,
                DataCompleteness.COMPLETE, Instant.EPOCH, "test:source", "test:proof", "1.12.2", "forge", Collections.singletonMap("test:key", " ")));
        if (actual8) {
            ValueSchema<EvidenceMetadata> evidenceSchema = ValueSchemas.of(EvidenceMetadata.class);
            equal(evidence, evidenceSchema.construct(new Object[] {evidence.authority(), evidence.completeness(),
                    evidence.capturedAt(), evidence.sourceId(), evidence.provenance(), evidence.gameVersion(), evidence.loader(), evidence.details()}));
            equal(8, evidenceSchema.components().size());
        }
        System.out.println("evidence=" + evidence.toString());
        System.out.println("PASS full canonical query owner vectors");
    }
    private static RegistryEntrySnapshot entry(String id, String kind, String display, int score, String category, String[] labels) {
        Map<String, JsonElement> properties = new LinkedHashMap<>();
        properties.put("test:score", new JsonPrimitive(score));
        properties.put("test:category", new JsonPrimitive(category));
        properties.put("test:slash/tilde_key", new JsonPrimitive("escaped"));
        JsonArray array = new JsonArray(); for (String label : labels) array.add(new JsonPrimitive(label));
        properties.put("test:labels", array);
        return new RegistryEntrySnapshot(id, kind, display, id.substring(0, id.indexOf(':')), "test:proof",
                Arrays.asList(display), Collections.singleton("test:tag"), Collections.singleton("test:component"), properties);
    }
    private static QueryOperation op(QueryOperation.Op op, String field, QueryOperation.Operator operator,
            String value, List<String> fields, QueryOperation.Direction direction, QueryOperation.Aggregate aggregate,
            String group, Integer count) {
        return new QueryOperation(op, field, operator, value, fields, direction, aggregate, group, count);
    }
    private static List<QueryOperation> ops(QueryOperation... operations) { return Arrays.asList(operations); }
    private static void report(String name, RegistryQueryEngine.Result result) {
        JsonObject report = new JsonObject(); report.addProperty("dataset", result.dataset().name());
        report.addProperty("sourceRows", result.sourceRows());
        JsonArray columns = new JsonArray(); for (String column : result.columns()) columns.add(new JsonPrimitive(column));
        report.add("columns", columns); JsonArray rows = new JsonArray();
        for (Map<String, JsonElement> row : result.rows()) { JsonObject object = new JsonObject(); row.forEach(object::add); rows.add(object); }
        report.add("rows", rows); JsonArray stages = new JsonArray();
        for (RegistryQueryEngine.Stage stage : result.stages()) {
            JsonObject object = new JsonObject(); object.addProperty("index", stage.index()); object.addProperty("operation", stage.operation().name());
            object.addProperty("inputRows", stage.inputRows()); object.addProperty("outputRows", stage.outputRows()); stages.add(object);
        }
        report.add("stages", stages); System.out.println(name + "=" + report.toString());
    }
    private static void failure(String name, Checked operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void classMajor(Class<?> owner) throws Exception {
        try (InputStream input = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int position = 0;
            while (position < 8) { int count = input.read(header, position, 8 - position); check(count > 0, "header"); position += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
