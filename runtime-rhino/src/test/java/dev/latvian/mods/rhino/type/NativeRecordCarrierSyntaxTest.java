package dev.latvian.mods.rhino.type;
import dev.latvian.mods.rhino.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public final class NativeRecordCarrierSyntaxTest {
    public record Row(String name, int count, Optional<String> note) {}
    private record ComponentOracle(int index, String name, TypeInfo type) {}
    private record DataOracle(RecordTypeInfo.Component[] components, Map<String, RecordTypeInfo.Component> componentMap, Object[] defaultArguments) {}
    @Test void componentAndDataKeepRecordValueContractsAndShallowArrayIdentity() {
        RecordTypeInfo.Component component = new RecordTypeInfo.Component(0, "name", TypeInfo.STRING);
        assertEquals(0, component.index()); assertEquals("name", component.name()); assertSame(TypeInfo.STRING, component.type());
        assertEquals(new ComponentOracle(0, "name", TypeInfo.STRING).hashCode(), component.hashCode());
        assertEquals(component, new RecordTypeInfo.Component(0, "name", TypeInfo.STRING));
        RecordTypeInfo.Component[] components = {component}; Map<String,RecordTypeInfo.Component> map = Map.of("name", component); Object[] defaults = {null};
        RecordTypeInfo.Data data = new RecordTypeInfo.Data(components, map, defaults);
        assertSame(components, data.components()); assertSame(map, data.componentMap()); assertSame(defaults, data.defaultArguments());
        assertEquals(new DataOracle(components, map, defaults).hashCode(), data.hashCode());
        assertEquals(data, new RecordTypeInfo.Data(components, map, defaults));
        assertNotEquals(data, new RecordTypeInfo.Data(components.clone(), map, defaults));
    }
    @Test void genuineModernRecordMetadataAndConstructorMapDefaultsRemainExact() {
        Context cx = new ContextFactory().enter(); TypeInfo type = TypeInfo.of(Row.class);
        assertTrue(type instanceof RecordTypeInfo); RecordTypeInfo record = (RecordTypeInfo) type;
        assertEquals(List.of("name", "count", "note"), Arrays.stream(record.getData().components()).map(RecordTypeInfo.Component::name).toList());
        Row row = (Row) record.createInstance(cx, Map.of("name", "value", "count", 7));
        assertEquals("value", row.name()); assertEquals(7, row.count()); assertEquals(Optional.empty(), row.note());
        cx.factory.registerDefaultRecordProperties(new Row("default", 3, Optional.of("note")));
        Row inherited = (Row) record.createInstance(cx, Map.of("name", "changed"));
        assertEquals("changed", inherited.name()); assertEquals(3, inherited.count()); assertEquals(Optional.of("note"), inherited.note());
    }
}
