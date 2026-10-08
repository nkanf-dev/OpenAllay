package dev.openallay.script;
import com.google.gson.*;
import dev.openallay.json.EngineJson;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.schema.RhinoTypeSchema;
import dev.openallay.tool.query.QueryOperation;
import dev.openallay.value.RecordMetadata;
import dev.openallay.value.ValueSchemas;
import java.time.Instant;
import java.util.*;

/** Whole actual engine classes on the genuine Java8 VM; no copied DTO or facade stubs. */
public final class WholeEngineJava8RuntimeFixture {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check=" + checks); }
    public static void main(String[] args) throws Exception {
        check("1.8".equals(System.getProperty("java.specification.version")));
        UnrestrictedJava8FactsFixture.main(new String[0]);
        Gson json = EngineJson.create();
        QueryOperation operation = new QueryOperation(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, 3);
        String encoded = json.toJson(operation);
        check(json.fromJson(encoded, QueryOperation.class).equals(operation));
        check(ValueSchemas.supports(QueryOperation.class)); check(!RecordMetadata.isRecord(QueryOperation.class));
        check(RhinoTypeSchema.require(QueryOperation.class) != null);
        Instant timestamp = Instant.ofEpochSecond(17L, 123);
        check(json.fromJson(json.toJson(timestamp), Instant.class).equals(timestamp));
        Map<String,Object> roots = new LinkedHashMap<String,Object>(); roots.put("operation", operation);
        JavascriptExecution execution = new RhinoJavascriptRuntime().execute(
            "return {count:mc.operation.count,op:mc.operation.op,total:[1,2,3].reduce(function(a,b){return a+b;},0)};",
            roots, Collections.<String,JsonElement>emptyMap(), new CancellationSignal());
        JsonObject output = execution.value().getAsJsonObject();
        check(output.get("count").getAsInt() == 3); check(output.get("op").getAsString().equals("TAKE")); check(output.get("total").getAsInt() == 6);
        CancellationSignal cancelled = new CancellationSignal(); cancelled.cancel();
        try {
            new RhinoJavascriptRuntime().execute("return 1;", Collections.<String,Object>emptyMap(), Collections.<String,JsonElement>emptyMap(), cancelled);
            throw new AssertionError("Cancelled execution was admitted");
        } catch (ModelClientException expected) { checks++; }
        check(EngineJson.create().fromJson("{\"count\":4,\"op\":\"TAKE\"}", QueryOperation.class).count() == 4);
        System.out.println("PASS whole actual engine Java8 runtime=" + System.getProperty("java.version") + " checks=" + checks);
    }
}
