package dev.openallay.script.schema;

import static org.junit.jupiter.api.Assertions.*;

import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.script.fixture.ExplicitHostValue;
import dev.openallay.script.host.HostAccessException;
import dev.openallay.script.host.RhinoHostAdapter;
import dev.openallay.tool.query.QueryOperation;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class ExplicitHostSchemaTest {
    @Test
    void derivesCanonicalQueryOperationAndNestedGenericValueSchemas() {
        HostSchema.RecordValue query = assertInstanceOf(HostSchema.RecordValue.class,
                RhinoHostAdapter.declaredSchema(QueryOperation.class));
        assertEquals(QueryOperation.class.getName(), query.javaType());
        assertEquals(9, query.fields().size());
        assertInstanceOf(HostSchema.Enumeration.class, query.fields().get("op"));
        HostSchema.Sequence fields = assertInstanceOf(HostSchema.Sequence.class, query.fields().get("fields"));
        assertEquals(new HostSchema.Scalar("string"), fields.elements());
        RhinoTypeSchema.validateValue(QueryOperation.class, new QueryOperation(QueryOperation.Op.SELECT,
                null, null, null, List.of("/id"), null, null, null, null));

        HostSchema.RecordValue value = assertInstanceOf(HostSchema.RecordValue.class,
                RhinoTypeSchema.require(TypeInfo.of(ExplicitHostValue.class)));
        assertEquals(4, value.fields().size());
        assertFalse(value.fields().containsKey("privateAuthority"));
        assertFalse(value.fields().containsKey("secretGetter"));
        HostSchema.Sequence rows = assertInstanceOf(HostSchema.Sequence.class, value.fields().get("rows"));
        HostSchema.RecordValue leaf = assertInstanceOf(HostSchema.RecordValue.class, rows.elements());
        assertEquals(ExplicitHostValue.Leaf.class.getName(), leaf.javaType());
        assertEquals(Map.of("id", new HostSchema.Scalar("string")), leaf.fields());
        HostSchema.Dictionary properties = assertInstanceOf(HostSchema.Dictionary.class, value.fields().get("properties"));
        assertEquals(rows, properties.values());
        HostSchema.OptionalValue note = assertInstanceOf(HostSchema.OptionalValue.class, value.fields().get("note"));
        assertEquals(new HostSchema.Scalar("string"), note.value());
        RhinoTypeSchema.validateValue(ExplicitHostValue.class, fixture());
    }

    @Test
    void ownerConstructorNormalizationAndFieldOnlyMetadataStayCanonical() {
        ValueSchema<ExplicitHostValue> schema = ValueSchemas.of(ExplicitHostValue.class);
        var component = schema.components().get(0);
        assertEquals("Field metadata only", component.annotation(ToolDescription.class).value());
        assertNotNull(component.annotation(ToolOptional.class));
        assertFalse(component.fieldMetadata().canAccess(fixture()));
        assertEquals("Getter metadata must not override the field",
                assertDoesNotThrow(() -> ExplicitHostValue.class.getMethod("name"))
                        .getAnnotation(ToolDescription.class).value());
        assertEquals("normalized", fixture().name());
        assertEquals("nested", fixture().rows().get(0).id());
        assertThrows(UnsupportedOperationException.class, () -> fixture().rows().clear());
        assertThrows(IllegalArgumentException.class, () -> schema.construct(new Object[] {"wrong count"}));
        assertThrows(NullPointerException.class, () -> schema.construct(new Object[] {
                null, List.of(), Map.of(), Optional.empty()}));
        assertThrows(UnsupportedOperationException.class, () -> schema.components().clear());
        // Run the same dependency-free metadata/constructor fixture used by the actual Java8 subset.
        ExplicitHostValue.main(new String[0]);
    }

    @Test
    void catalogsDeclareExplicitValuesWithoutResolvingSuppliers() {
        AtomicInteger reads = new AtomicInteger();
        HostRootDescriptor root = new HostRootDescriptor("value", ExplicitHostValue.class, true,
                "test:provider", "Explicit root", "test", () -> { reads.incrementAndGet(); return fixture(); });
        HostSchemaCatalog catalog = new HostSchemaCatalog(List.of(root));
        assertEquals("record", catalog.describe("value").orElseThrow().schema().kind());
        assertEquals("list", catalog.describe("value.rows").orElseThrow().schema().kind());
        assertTrue(catalog.describe("value.secretGetter").isEmpty());
        assertEquals(0, reads.get());
        assertEquals("normalized", ((ExplicitHostValue) root.resolve()).name());
        assertEquals(1, reads.get());
    }

    @Test
    void invalidProvidersAndUnsupportedComponentTypesFailClosed() {
        for (Class<?> owner : List.of(NoPublicProvider.class, ForeignSchema.class, UnsupportedValue.class, PlainPojo.class)) {
            var failure = assertThrows(HostAccessException.class, () -> RhinoTypeSchema.require(owner));
            assertEquals("javascript_host_type_unsupported", failure.code(), owner.getName());
            assertFalse(failure.getMessage().contains("private-provider-marker"));
        }
    }

    @Test
    void validatesNestedValuesAndRetainsPreciseMismatchCodeWithoutJavaReflectionFallback() {
        var wrongOwner = assertThrows(HostAccessException.class,
                () -> RhinoTypeSchema.validateValue(ExplicitHostValue.class, new PlainPojo()));
        assertEquals("javascript_host_type_mismatch", wrongOwner.code());
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<ExplicitHostValue.Leaf> wrongRows = (List) List.of("not a leaf");
        var invalid = new ExplicitHostValue("name", wrongRows, Map.of(), Optional.empty());
        var nested = assertThrows(HostAccessException.class,
                () -> RhinoTypeSchema.validateValue(ExplicitHostValue.class, invalid));
        assertEquals("javascript_host_type_mismatch", nested.code());
        var broken = assertThrows(HostAccessException.class,
                () -> RhinoTypeSchema.validateValue(BrokenValue.class, new BrokenValue("ignored")));
        assertEquals("javascript_host_access_failed", broken.code());
        assertFalse(broken.getMessage().contains("private-native-marker"));
    }

    @ValueType(NoPublicProvider.Schema.class)
    public static final class NoPublicProvider {
        public static final class Schema implements ValueSchema.Provider {
            private Schema() {}
            @Override public ValueSchema<?> schema() { throw new AssertionError("private-provider-marker"); }
        }
    }

    @ValueType(ForeignSchema.Schema.class)
    public static final class ForeignSchema {
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<?> schema() { return ValueSchemas.of(ExplicitHostValue.class); }
        }
    }

    @ValueType(UnsupportedValue.Schema.class)
    public static final class UnsupportedValue {
        private final Thread thread = new Thread();
        public Thread thread() { return thread; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<UnsupportedValue> schema() {
                return new ValueSchema<>(UnsupportedValue.class,
                        Collections.singletonList(new ValueSchema.Component<>(UnsupportedValue.class, "thread", UnsupportedValue::thread)),
                        arguments -> new UnsupportedValue());
            }
        }
    }

    private static final class PlainPojo { public String name() { return "not authorized"; } }

    @ValueType(BrokenValue.Schema.class)
    public static final class BrokenValue {
        private final String value;
        public BrokenValue(String value) { this.value = value; }
        public String value() { throw new IllegalStateException("private-native-marker"); }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<BrokenValue> schema() {
                return new ValueSchema<>(BrokenValue.class,
                        Collections.singletonList(new ValueSchema.Component<>(BrokenValue.class, "value", BrokenValue::value)),
                        arguments -> new BrokenValue((String) arguments[0]));
            }
        }
    }

    private static ExplicitHostValue fixture() {
        var rows = new ArrayList<>(List.of(new ExplicitHostValue.Leaf(" nested ")));
        var value = ValueSchemas.of(ExplicitHostValue.class).construct(new Object[] {
                " normalized ", rows, Map.of("nested", rows), Optional.empty()});
        rows.clear();
        return value;
    }
}
