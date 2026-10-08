package dev.openallay.script.schema;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
/** Whole contract bytes, including all literal text, catalog ordering and nested type rendering. */
public final class CoreContractExactOracle {
    public static void main(String[] args) throws Exception {
        Map<String,HostSchema> fields=new LinkedHashMap<>();
        fields.put("zeta",new HostSchema.Sequence("list",new HostSchema.RecordValue("record","FixtureRow",java.util.Collections.singletonMap("count",new HostSchema.Scalar("number")))));
        fields.put("alpha",new HostSchema.OptionalValue("optional",new HostSchema.Enumeration("enum",Arrays.asList("FIRST","SECOND"))));
        fields.put("dynamic",new HostSchema.Dictionary("map",new HostSchema.DynamicJson("json"),true));
        HostSchemaCatalog catalog=new HostSchemaCatalog(Arrays.asList(
                HostRootDescriptor.requestScopedDynamic("zRoot",new HostSchema.RecordValue("record","Fixture",fields),"provider-z","Z root","evidence-z"),
                HostRootDescriptor.declaredDynamic("aRoot",new HostSchema.DynamicDetached("extension"),false,"provider-a","A root","evidence-a",null)));
        System.out.write(CoreJavascriptContract.render(catalog).getBytes(StandardCharsets.UTF_8));
    }
}
