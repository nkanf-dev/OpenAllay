package dev.openallay.script.host;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.script.schema.HostRootDescriptor;
import dev.openallay.script.schema.CoreJavascriptContract;
import dev.openallay.script.schema.HostSchemaCatalog;
import dev.openallay.model.CancellationSignal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class HostSchemaJava8SourceRegressionTest {
    private record PrivateValue(String name, List<Integer> values, Optional<String> optional) {}
    private record Broken(String value) {
        @Override public String value() { throw new IllegalStateException("private-sensitive-marker"); }
    }
    private static final class ForeignSchema implements HostSchema {
        @Override public String kind() { return "foreign"; }
    }
    @Test void privateExternalModernRecordUsesOwnedAccessorOnly() {
        var value = new RhinoJavascriptRuntime().execute("return {name: mc.value.name, filtered: mc.value.values.filter(x => x > 1), getClass: typeof mc.value.getClass};",
                Map.of("value", new PrivateValue("private", List.of(1,2,3), Optional.of("yes"))), Map.of(), new CancellationSignal()).value().getAsJsonObject();
        assertEquals("private",value.get("name").getAsString());
        assertEquals("[2,3]",value.get("filtered").toString());
        assertEquals("undefined",value.get("getClass").getAsString());
    }
    @Test void accessorFailureRemainsStableAndRedacted() {
        var failure=assertThrows(JavascriptExecutionException.class,()->new RhinoJavascriptRuntime().execute("return mc.value.value;",
                Map.of("value",new Broken("ignored")),Map.of(),new CancellationSignal()));
        assertEquals("javascript_host_access_failed",failure.code());
        assertFalse(failure.getMessage().contains("private-sensitive-marker"));
    }
    @Test void componentNullMessagesRemainExact() {
        assertEquals("elements", assertThrows(NullPointerException.class, () -> new HostSchema.Sequence("list", null)).getMessage());
        assertEquals("value", assertThrows(NullPointerException.class, () -> new HostSchema.OptionalValue("optional", null)).getMessage());
        assertEquals("values", assertThrows(NullPointerException.class, () -> new HostSchema.Dictionary("map", null, true)).getMessage());
    }
    @Test void foreignSchemaRejectedBeforeDescriptorAndRenderIngress() {
        assertThrows(IncompatibleClassChangeError.class,()->HostRootDescriptor.requestScopedDynamic("value",new ForeignSchema(),"provider","summary","evidence"));
        assertThrows(IncompatibleClassChangeError.class,()->new HostSchema.Sequence("list",new ForeignSchema()));
        assertThrows(IncompatibleClassChangeError.class,()->new HostSchema.RecordValue("record","test",Map.of("foreign",new ForeignSchema())));
    }
    @Test void declaredDescriptorRemainsLazyAndTypedContractDeterministic() {
        var root=HostRootDescriptor.requestScoped("value",PrivateValue.class,"provider","summary","evidence");
        var catalog=new HostSchemaCatalog(List.of(root));
        assertEquals("record",catalog.describe("value").orElseThrow().schema().kind());
        String contract=CoreJavascriptContract.render(catalog);
        assertTrue(contract.contains("mc.value.name: string"));
        assertTrue(contract.contains("mc.value.values: array<number>"));
        assertTrue(contract.endsWith("when needed."));
    }
}
