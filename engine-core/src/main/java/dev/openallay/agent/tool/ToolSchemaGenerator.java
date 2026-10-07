package dev.openallay.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import dev.openallay.value.RecordMetadata;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;
import java.util.Set;

public final class ToolSchemaGenerator {
    public JsonObject generate(Class<?> inputType) {
        return schema(inputType, new HashSet<>(), true);
    }

    /** Generates the serialized result shape without claiming every nullable result field is present. */
    public JsonObject generateOutput(Class<?> outputType) {
        return schema(outputType, new HashSet<>(), false);
    }

    private JsonObject schema(Type type, Set<Type> visiting, boolean inputContract) {
        if (type instanceof Class<?>) {
            return classSchema((Class<?>) type, visiting, inputContract);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] arguments = parameterized.getActualTypeArguments();
            if (raw == Optional.class) {
                return schema(arguments[0], visiting, inputContract);
            }
            if (Collection.class.isAssignableFrom(raw)) {
                JsonObject result = typed("array");
                result.add("items", schema(arguments[0], visiting, inputContract));
                return result;
            }
            if (Map.class.isAssignableFrom(raw) && arguments[0] == String.class) {
                JsonObject result = typed("object");
                result.add("additionalProperties", schema(arguments[1], visiting, inputContract));
                return result;
            }
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            JsonObject result = typed("array");
            result.add("items", schema(array.getGenericComponentType(), visiting, inputContract));
            return result;
        }
        throw new IllegalArgumentException("Unsupported tool schema type: " + type.getTypeName());
    }

    private static final class Component {
        private final String name;
        private final Type genericType;
        private final java.util.function.Function<Class<? extends java.lang.annotation.Annotation>, java.lang.annotation.Annotation> annotations;
        private Component(String name, Type genericType,
                java.util.function.Function<Class<? extends java.lang.annotation.Annotation>, java.lang.annotation.Annotation> annotations) {
            this.name = name; this.genericType = genericType; this.annotations = annotations;
        }
        String name() { return name; }
        Type genericType() { return genericType; }
        @SuppressWarnings("unchecked") <A extends java.lang.annotation.Annotation> A annotation(Class<A> type) {
            return (A) annotations.apply(type);
        }
    }

    private static java.util.List<Component> components(Class<?> type) {
        java.util.List<Component> components = new java.util.ArrayList<>();
        if (dev.openallay.value.ValueSchemas.supports(type)) {
            for (dev.openallay.value.ValueSchema.Component<?> component : dev.openallay.value.ValueSchemas.of(type).components()) {
                components.add(new Component(component.name(), component.genericType(), component::annotation));
            }
        } else {
            for (RecordMetadata.Component component : RecordMetadata.components(type)) {
                components.add(new Component(component.name(), component.genericType(), component::annotation));
            }
        }
        return components;
    }

    private JsonObject classSchema(Class<?> type, Set<Type> visiting, boolean inputContract) {
        if (type == String.class || type == Character.class || type == char.class) {
            return typed("string");
        }
        if (type == boolean.class || type == Boolean.class) {
            return typed("boolean");
        }
        if (type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == BigInteger.class) {
            return typed("integer");
        }
        if (type == float.class
                || type == double.class
                || type == Float.class
                || type == Double.class
                || type == BigDecimal.class) {
            return typed("number");
        }
        if (type.isEnum()) {
            JsonObject result = typed("string");
            JsonArray values = new JsonArray();
            for (Object constant : type.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            result.add("enum", values);
            return result;
        }
        if (type.isArray()) {
            JsonObject result = typed("array");
            result.add("items", schema(type.getComponentType(), visiting, inputContract));
            return result;
        }
        if (type == java.time.Instant.class
                || type == java.net.URI.class
                || type == java.util.UUID.class) {
            JsonObject result = typed("string");
            result.addProperty("format", type == java.time.Instant.class
                    ? "date-time"
                    : type == java.util.UUID.class ? "uuid" : "uri");
            return result;
        }
        if (com.google.gson.JsonObject.class.isAssignableFrom(type)) {
            return typed("object");
        }
        if (com.google.gson.JsonArray.class.isAssignableFrom(type)) {
            return typed("array");
        }
        if (com.google.gson.JsonElement.class.isAssignableFrom(type) || type == Object.class) {
            return new JsonObject();
        }
        if (dev.openallay.value.ValueSchemas.supports(type) || RecordMetadata.isRecord(type)) {
            if (!visiting.add(type)) {
                throw new IllegalArgumentException("Recursive tool record is unsupported: " + type.getName());
            }
            JsonObject result = typed("object");
            ToolDescription recordDescription = type.getAnnotation(ToolDescription.class);
            if (recordDescription != null) {
                result.addProperty("description", recordDescription.value());
            }
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();
            for (Component component : components(type)) {
                JsonObject componentSchema = schema(
                        component.genericType(), visiting, inputContract);
                ToolDescription description = component.annotation(ToolDescription.class);
                if (description != null) {
                    componentSchema.addProperty("description", description.value());
                }
                ToolPattern pattern = component.annotation(ToolPattern.class);
                if (pattern != null) {
                    componentSchema.addProperty("pattern", pattern.value());
                }
                properties.add(component.name(), componentSchema);
                if (inputContract
                        && component.annotation(ToolOptional.class) == null
                        && !(component.genericType() instanceof ParameterizedType
                                && ((ParameterizedType) component.genericType()).getRawType() == Optional.class)) {
                    required.add(component.name());
                }
            }
            result.add("properties", properties);
            result.add("required", required);
            result.addProperty("additionalProperties", false);
            ToolAtLeastOne atLeastOne = inputContract
                    ? type.getAnnotation(ToolAtLeastOne.class) : null;
            if (atLeastOne != null) {
                Set<String> componentNames = components(type).stream()
                        .map(Component::name)
                        .collect(java.util.stream.Collectors.toSet());
                JsonArray anyOf = new JsonArray();
                for (String name : atLeastOne.value()) {
                    if (!componentNames.contains(name)) {
                        throw new IllegalArgumentException(
                                "Unknown at-least-one component " + name + " on " + type.getName());
                    }
                    JsonObject alternative = new JsonObject();
                    JsonArray alternativeRequired = new JsonArray();
                    alternativeRequired.add(name);
                    alternative.add("required", alternativeRequired);
                    anyOf.add(alternative);
                }
                if ((anyOf.size() == 0)) {
                    throw new IllegalArgumentException(
                            "At-least-one Tool contract must name a component: " + type.getName());
                }
                result.add("anyOf", anyOf);
            }
            visiting.remove(type);
            return result;
        }
        throw new IllegalArgumentException("Unsupported tool schema class: " + type.getName());
    }

    private static JsonObject typed(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }
}
