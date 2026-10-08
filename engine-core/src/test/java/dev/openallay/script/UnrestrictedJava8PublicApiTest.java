package dev.openallay.script;
import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.model.CancellationSignal;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class UnrestrictedJava8PublicApiTest {
    @Test void modernModuleAndLoaderFactsExactlyMatchPublicJdkQueries() throws Exception {
        Method moduleView = UnrestrictedJavaAccess.class.getDeclaredMethod("moduleView", Class.class);
        moduleView.setAccessible(true);
        for (Class<?> type : List.of(String.class, int.class, String[].class, UnrestrictedJava8PublicApiTest.class)) {
            Map<?,?> actual = (Map<?,?>) moduleView.invoke(null, type);
            Module module = type.getModule(); String pkg = type.isPrimitive() || type.isArray() ? null : type.getPackageName();
            assertEquals(module.getName(), actual.get("name")); assertEquals(module.isNamed(), actual.get("named"));
            assertEquals(module.getDescriptor() != null && module.getDescriptor().isAutomatic(), actual.get("automatic"));
            assertEquals(pkg, actual.get("packageName"));
            assertEquals(pkg != null && module.isOpen(pkg, UnrestrictedJavaAccess.class.getModule()), actual.get("packageOpenToBridge"));
        }
    }
    @Test void closedModernJdkMemberRetainsOriginalInaccessibleCauseAndCode() {
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        JavascriptExecutionException failure = assertThrows(JavascriptExecutionException.class, () ->
            runtime.execute("return Java.get('text', 'value');", Map.of(), Map.of(), Map.of(), Map.of(),
                ignored -> {}, new CancellationSignal(), null, null, true, null));
        assertEquals("javascript_java_inaccessible", failure.code());
        assertInstanceOf(InaccessibleObjectException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("java.lang"));
    }
    @Test void canonicalArrayResolutionRetainsPrimitiveNestedReferenceAndVoidErrors() throws Exception {
        // Exercise the real public Java facade rather than substituting a reflection adapter.
        RhinoJavascriptRuntime runtime = new RhinoJavascriptRuntime();
        var result = runtime.execute("return [Java.inspect(Java.type('int[]')).name,Java.inspect(Java.type('java.lang.String[][]')).name];",
            Map.of(), Map.of(), Map.of(), Map.of(), ignored -> {}, new CancellationSignal(), null, null, true, null);
        assertEquals("[I", result.value().getAsJsonArray().get(0).getAsString());
        assertEquals("[[Ljava.lang.String;", result.value().getAsJsonArray().get(1).getAsString());
        assertThrows(JavascriptExecutionException.class, () -> runtime.execute("return Java.type('void[]');",
            Map.of(), Map.of(), Map.of(), Map.of(), ignored -> {}, new CancellationSignal(), null, null, true, null));
    }
}
