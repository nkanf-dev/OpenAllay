package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import dev.openallay.value.ValueSchema;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;

/** Current modern record metadata bridge; JSON execution is shared with explicit values. */
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
        if (!constructor.trySetAccessible()) throw new JsonIOException("JSON adapter constructor is inaccessible");
        return constructor.newInstance();
    }
    private static <T> ValueSchema<T> metadata(Class<T> owner) {
        RecordComponent[] records = owner.getRecordComponents();
        try {
            Class<?>[] parameters = Arrays.stream(records).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Constructor<T> constructor = owner.getDeclaredConstructor(parameters);
            if (!constructor.trySetAccessible()) throw new JsonIOException("Record constructor is inaccessible: " + owner.getName());
            ArrayList<ValueSchema.Component<T>> components = new ArrayList<>();
            for (RecordComponent component : records) {
                java.lang.reflect.Method accessor = component.getAccessor();
                if (!accessor.trySetAccessible()) throw new JsonIOException("Record accessor is inaccessible: " + accessor);
                components.add(new ValueSchema.Component<>(component.getName(),
                        owner.getDeclaredField(component.getName()), component.getGenericType(), component.getType(), value -> {
                    try { return accessor.invoke(value); }
                    catch (InvocationTargetException failure) { throw new JsonIOException("Cannot read record component " + component.getName(), failure.getCause()); }
                    catch (ReflectiveOperationException failure) { throw new JsonIOException("Cannot read record component " + component.getName(), failure); }
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
