package dev.openallay.script.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import dev.openallay.script.host.HostAccessException;
import dev.openallay.script.host.RhinoHostAdapter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class HostSchemaCatalogTest {
    @Test
    void derivesClosedRecordsCollectionsOptionalsEnumsAndDynamicJson() {
        HostSchema.RecordValue record = assertInstanceOf(
                HostSchema.RecordValue.class,
                RhinoHostAdapter.declaredSchema(Sample.class));

        assertInstanceOf(HostSchema.Sequence.class, record.fields().get("rows"));
        assertInstanceOf(HostSchema.Dictionary.class, record.fields().get("properties"));
        assertInstanceOf(HostSchema.OptionalValue.class, record.fields().get("note"));
        assertInstanceOf(HostSchema.Enumeration.class, record.fields().get("mode"));
        assertInstanceOf(HostSchema.DynamicJson.class, record.fields().get("dynamic"));
    }

    @Test
    void rejectsExactlyTheTypesTheClosedHostAdapterCannotDeclare() {
        Type unsupported = componentType(Unsupported.class, "threads");

        HostAccessException failure = assertThrows(
                HostAccessException.class,
                () -> RhinoHostAdapter.declaredSchema(unsupported));

        assertEquals("javascript_host_type_unsupported", failure.code());
    }

    @Test
    void listsAndDescribesRootsWithoutResolvingTheirValues() {
        AtomicInteger resolutions = new AtomicInteger();
        HostRootDescriptor root = new HostRootDescriptor(
                "sample",
                Sample.class,
                true,
                "test:provider",
                "Sample root",
                "test",
                () -> {
                    resolutions.incrementAndGet();
                    return new Sample(List.of(), Map.of(), Optional.empty(), Mode.ACTIVE, null);
                });
        HostSchemaCatalog catalog = new HostSchemaCatalog(List.of(root));

        assertEquals("sample", catalog.list().getFirst().name());
        assertEquals(
                "list",
                catalog.describe("sample.rows").orElseThrow().schema().kind());
        assertEquals(0, resolutions.get());
    }

    private static Type componentType(Class<?> owner, String name) {
        for (RecordComponent component : owner.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component.getGenericType();
            }
        }
        throw new IllegalArgumentException(name);
    }

    private enum Mode {
        ACTIVE,
        DISABLED
    }

    private record Row(String id, int count) {}

    private record Sample(
            List<Row> rows,
            Map<String, JsonElement> properties,
            Optional<String> note,
            Mode mode,
            JsonElement dynamic) {}

    private record Unsupported(List<Thread> threads) {}
}
