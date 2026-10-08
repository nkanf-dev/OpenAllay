package dev.openallay.script;
import dev.openallay.model.CancellationSignal;
import java.util.*;

/** Run against actual compiled engine/Rhino sources, never facade stubs. */
public final class UnrestrictedJava8FactsFixture {
    public static final class Host {
        private String value = "original";
        private Host() {}
    }
    public static void main(String[] args) {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("Require actual Java8");
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        String source = "const Host=Java.type('dev.openallay.script.UnrestrictedJava8FactsFixture$Host');"
            + "const object=Java.construct(Host,[],[]);Java.set(object,'value','changed');"
            + "const facts=Java.inspect(Java.type('java.lang.String'));"
            + "return {name:facts.module.name,named:facts.module.named,automatic:facts.module.automatic,"
            + "open:facts.module.packageOpenToBridge,pkg:facts.module.packageName,value:Java.get(object,'value'),"
            + "array:Java.inspect(Java.type('int[][]')).name};";
        JavascriptExecution result = runtime.execute(source, Collections.<String,Object>emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), ignored -> {}, new CancellationSignal(), null, null, true, null);
        com.google.gson.JsonObject json = result.value().getAsJsonObject();
        if (!json.get("name").isJsonNull() || json.get("named").getAsBoolean() || json.get("automatic").getAsBoolean()
            || !json.get("open").isJsonNull() || !json.get("pkg").getAsString().equals("java.lang")
            || !json.get("value").getAsString().equals("changed") || !json.get("array").getAsString().equals("[[I")) {
            throw new AssertionError(json.toString());
        }
        System.out.println("PASS trueJava8 actual unrestricted facts/access runtime=" + System.getProperty("java.version"));
    }
}
