package dev.openallay.script.host;

import com.google.gson.JsonObject;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.openallay.context.ItemStackSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Lazy component/key-only view over one detached Java value, map, or Gson object. */
public final class HostObjectView extends ScriptableObject implements Map<String, Object> {
    @FunctionalInterface
    interface Reader {
        Object read(String name);
    }

    private final RhinoHostAdapter adapter;
    private final List<String> keys;
    private final java.util.Set<String> keySet;
    private final Reader reader;
    private final JavascriptResultShape resultShape;
    // Rhino's NativeGSON understands Map/Iterable, not arbitrary ScriptableObject views.
    // Keep its mature JSON writer while projecting only detached, adapted components.
    private final Map<String, Object> jsonView;

    private HostObjectView(
            Context context,
            Scriptable scope,
            RhinoHostAdapter adapter,
            List<String> keys,
            Reader reader,
            JavascriptResultShape resultShape) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.keys = dev.openallay.util.Java8Collections.listCopyOf(keys);
        this.keySet = dev.openallay.util.Java8Collections.setCopyOf(keys);
        this.reader = Objects.requireNonNull(reader, "reader");
        this.resultShape = Objects.requireNonNull(resultShape, "resultShape");
        jsonView = Collections.unmodifiableMap(new AbstractMap<String, Object>() {
            @Override
            public Set<Entry<String, Object>> entrySet() {
                return new AbstractSet<Entry<String, Object>>() {
                    @Override public int size() { return HostObjectView.this.keys.size(); }
                    @Override public Iterator<Entry<String, Object>> iterator() {
                        Iterator<String> names = HostObjectView.this.keys.iterator();
                        return new Iterator<Entry<String, Object>>() {
                            @Override public boolean hasNext() { return names.hasNext(); }
                            @Override public Entry<String, Object> next() {
                                String name = names.next();
                                return new SimpleImmutableEntry<>(name, jsonValue(name));
                            }
                            @Override public void remove() { throw HostAccessException.readOnly(); }
                        };
                    }
                };
            }
        });
        setParentScope(scope);
        setPrototype(ScriptableObject.getObjectPrototype(scope, context));
        preventExtensions();
    }

    static HostObjectView value(
            Context context, Scriptable scope, RhinoHostAdapter adapter, Object value) {
        HostRecordSchema schema = HostRecordSchema.of(value.getClass());
        return new HostObjectView(
                context,
                scope,
                adapter,
                schema.names(),
                name -> {
                    Object result = schema.read(value, name);
                    return result == HostRecordSchema.Missing.INSTANCE
                            ? Scriptable.NOT_FOUND : result;
                },
                trustedShape(value));
    }

    static HostObjectView map(
            Context context, Scriptable scope, RhinoHostAdapter adapter, Map<?, ?> value) {
        ArrayList<String> keys = new ArrayList<>(value.size());
        for (Object key : value.keySet()) {
            if (!(key instanceof String)) {
                throw HostAccessException.unsupportedMapKey(key);
            }
            keys.add((String) key);
        }
        return new HostObjectView(
                context,
                scope,
                adapter,
                keys,
                name -> value.containsKey(name) ? value.get(name) : Scriptable.NOT_FOUND,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.KEY_VALUE));
    }

    static HostObjectView json(
            Context context, Scriptable scope, RhinoHostAdapter adapter, JsonObject value) {
        return json(
                context,
                scope,
                adapter,
                value,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.KEY_VALUE));
    }

    static HostObjectView json(
            Context context,
            Scriptable scope,
            RhinoHostAdapter adapter,
            JsonObject value,
            JavascriptResultShape resultShape) {
        return new HostObjectView(
                context,
                scope,
                adapter,
                dev.openallay.util.Java8Collections.listCopyOf(dev.openallay.json.JsonTrees.keys(value)),
                name -> value.has(name) ? value.get(name) : Scriptable.NOT_FOUND,
                resultShape);
    }

    public JavascriptResultShape resultShape() {
        return resultShape;
    }

    private static JavascriptResultShape trustedShape(Object value) {
        if (value instanceof RecipeEntrySnapshot) {
            return JavascriptResultShape.trusted(
                    JavascriptSemanticKind.RECIPE, RecipeEntrySnapshot.class);
        }
        if (value instanceof RegistryEntrySnapshot && "item".equals(((RegistryEntrySnapshot) value).kind())) {
            return JavascriptResultShape.trusted(
                    JavascriptSemanticKind.ITEM, RegistryEntrySnapshot.class);
        }
        if (value instanceof ItemStackSnapshot && !dev.openallay.util.Java8Strings.isBlank(((ItemStackSnapshot) value).itemId())) {
            return JavascriptResultShape.trusted(
                    JavascriptSemanticKind.ITEM, ItemStackSnapshot.class);
        }
        return JavascriptResultShape.ordinary(JavascriptSemanticKind.KEY_VALUE);
    }

    /** Java-side serialization contract only; these methods are never generic host bindings. */
    private Object jsonValue(String name) {
        Object value = reader.read(name);
        return value == Scriptable.NOT_FOUND ? value : adapter.adapt(value);
    }

    @Override public int size() { return keys.size(); }
    @Override public boolean isEmpty() { return keys.isEmpty(); }
    @Override public boolean containsKey(Object key) { return keySet.contains(key); }
    @Override public boolean containsValue(Object value) { return jsonView.containsValue(value); }
    @Override public Object get(Object key) {
        return key instanceof String && keySet.contains((String) key) ? jsonValue((String) key) : null;
    }
    @Override public Set<String> keySet() { return jsonView.keySet(); }
    @Override public Collection<Object> values() { return jsonView.values(); }
    @Override public Set<Entry<String, Object>> entrySet() { return jsonView.entrySet(); }

    @Override public Object put(String key, Object value) { throw HostAccessException.readOnly(); }
    @Override public Object remove(Object key) { throw HostAccessException.readOnly(); }
    @Override public void putAll(Map<? extends String, ?> values) { throw HostAccessException.readOnly(); }
    @Override public void clear() { throw HostAccessException.readOnly(); }
    @Override public Object putIfAbsent(String key, Object value) { throw HostAccessException.readOnly(); }
    @Override public boolean remove(Object key, Object value) { throw HostAccessException.readOnly(); }
    @Override public boolean replace(String key, Object oldValue, Object newValue) { throw HostAccessException.readOnly(); }
    @Override public Object replace(String key, Object value) { throw HostAccessException.readOnly(); }
    @Override public void replaceAll(BiFunction<? super String, ? super Object, ?> function) { throw HostAccessException.readOnly(); }
    @Override public Object computeIfAbsent(String key, Function<? super String, ?> function) { throw HostAccessException.readOnly(); }
    @Override public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ?> function) { throw HostAccessException.readOnly(); }
    @Override public Object compute(String key, BiFunction<? super String, ? super Object, ?> function) { throw HostAccessException.readOnly(); }
    @Override public Object merge(String key, Object value, BiFunction<? super Object, ? super Object, ?> function) { throw HostAccessException.readOnly(); }

    @Override
    public String getClassName() {
        return "Object";
    }

    @Override
    public boolean has(Context context, String name, Scriptable start) {
        return keySet.contains(name) || super.has(context, name, start);
    }

    @Override
    public Object get(Context context, String name, Scriptable start) {
        if (!keySet.contains(name)) {
            return super.get(context, name, start);
        }
        Object value = reader.read(name);
        return value == Scriptable.NOT_FOUND ? value : adapter.adapt(value);
    }

    @Override
    public boolean has(Context context, int index, Scriptable start) {
        return keySet.contains(Integer.toString(index));
    }

    @Override
    public Object get(Context context, int index, Scriptable start) {
        String name = Integer.toString(index);
        if (!keySet.contains(name)) {
            return Scriptable.NOT_FOUND;
        }
        Object value = reader.read(name);
        return value == Scriptable.NOT_FOUND ? value : adapter.adapt(value);
    }

    @Override
    public Object[] getIds(Context context) {
        return keys.toArray();
    }

    @Override
    public void put(Context context, String name, Scriptable start, Object value) {
        throw HostAccessException.readOnly();
    }

    @Override
    public void put(Context context, int index, Scriptable start, Object value) {
        throw HostAccessException.readOnly();
    }

    @Override
    public void delete(Context context, String name) {
        throw HostAccessException.readOnly();
    }

    @Override
    public void delete(Context context, int index) {
        throw HostAccessException.readOnly();
    }
}
