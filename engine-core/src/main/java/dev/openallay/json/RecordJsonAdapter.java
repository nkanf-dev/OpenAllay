package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.RecordMetadata;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Optional public modern-JVM record metadata; JSON execution is shared with explicit values. */
final class RecordJsonAdapter<T> extends ConstructorValueJsonAdapter<T> {
    static <T> RecordJsonAdapter<T> create(Gson gson, TypeToken<T> type, Fields fields) {
        return new RecordJsonAdapter<>(gson, type, fields);
    }
    @SuppressWarnings("unchecked")
    private RecordJsonAdapter(Gson gson, TypeToken<T> type, Fields fields) {
        super(gson, type, fields, metadata((Class<T>) type.getRawType()));
    }
    @Override protected Object annotationAdapter(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        accessible(constructor, "JSON adapter constructor is inaccessible");
        return constructor.newInstance();
    }

    /** Preserve existing record constructor/accessor and annotation-constructor authorization.
     * This never opens fields. Explicit value providers keep their public-only policy.
     */
    private static void accessible(AccessibleObject member, String message) {
        Member metadata = (Member) member;
        if (Modifier.isPublic(metadata.getModifiers())
                && Modifier.isPublic(metadata.getDeclaringClass().getModifiers())) return;
        try { member.setAccessible(true); }
        catch (SecurityException failure) { throw new JsonIOException(message, failure); }
        catch (RuntimeException failure) {
            // The modern module-access exception is optional, not a Java 8 linkage.
            if (failure.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
                throw new JsonIOException(message, failure);
            }
            throw failure;
        }
    }

    private static <T> ValueSchema<T> metadata(Class<T> owner) {
        List<RecordMetadata.Component> records;
        try {
            // The shared public bridge supplies metadata only, never access authorization.
            records = RecordMetadata.components(owner);
        } catch (IllegalArgumentException failure) {
            throw new JsonIOException("Not a record: " + owner.getName(), failure);
        } catch (IllegalStateException failure) {
            // Keep the JSON facade and unwrap the shared metadata reflection cause.
            throw new JsonIOException("Cannot bind record " + owner.getName(),
                    failure.getCause() == null ? failure : failure.getCause());
        }
        try {
            Class<?>[] parameters = new Class<?>[records.size()];
            for (int i = 0; i < records.size(); i++) parameters[i] = records.get(i).rawType();
            Constructor<T> constructor = owner.getDeclaredConstructor(parameters);
            accessible(constructor, "Record constructor is inaccessible: " + owner.getName());
            ArrayList<ValueSchema.Component<T>> components = new ArrayList<>();
            for (RecordMetadata.Component component : records) {
                String name = component.name();
                Method accessor = component.accessorMetadata();
                accessible(accessor, "Record accessor is inaccessible: " + accessor);
                components.add(new ValueSchema.Component<>(name,
                        component.fieldMetadata(), component.genericType(), component.rawType(), value -> {
                    try { return accessor.invoke(value); }
                    catch (InvocationTargetException failure) { throw new JsonIOException("Cannot read record component " + name, failure.getCause()); }
                    catch (ReflectiveOperationException failure) { throw new JsonIOException("Cannot read record component " + name, failure); }
                }));
            }
            return new ValueSchema<>(owner, components, arguments -> {
                try { return constructor.newInstance(arguments); }
                catch (InvocationTargetException failure) { throw new com.google.gson.JsonParseException("Invalid record " + owner.getName(), failure.getCause()); }
                catch (ReflectiveOperationException failure) { throw new JsonIOException("Cannot construct record " + owner.getName(), failure); }
            });
        } catch (ReflectiveOperationException failure) {
            throw new JsonIOException("Cannot bind record " + owner.getName(), failure);
        }
    }
}
