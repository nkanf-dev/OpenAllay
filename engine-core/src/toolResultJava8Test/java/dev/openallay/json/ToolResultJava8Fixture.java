package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import dev.openallay.tool.ToolResult;
import dev.openallay.util.Java8Strings;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Standalone constructor-adapter proof; modern tests also run these vectors through EngineJson. */
public final class ToolResultJava8Fixture {
    private ToolResultJava8Fixture() {}
    private interface Checked { void run() throws Exception; }

    public static void main(String[] arguments) throws Exception {
        equal("1.8", System.getProperty("java.specification.version"));
        for (Class<?> owner : Arrays.<Class<?>>asList(ToolResult.class, ToolResult.Success.class,
                ToolResult.Failure.class, ToolResult.Success.Schema.class, ToolResult.Failure.Schema.class,
                ValueSchema.class, ValueSchemas.class, ValueType.class, Java8Strings.class,
                ConstructorValueJsonAdapter.class, ToolResultJava8Fixture.class,
                dev.openallay.agent.tool.ToolDescription.class, dev.openallay.agent.tool.ToolOptional.class)) classMajor(owner);
        run(ToolResultJava8Fixture::standaloneGson);
        System.out.println("PASS actual canonical ToolResult constructor adapter Java8 " + System.getProperty("java.version"));
    }

    private static Gson standaloneGson(Consumer<GsonBuilder> configuration) {
        GsonBuilder builder = new GsonBuilder();
        configuration.accept(builder);
        return builder.registerTypeAdapterFactory(new TypeAdapterFactory() {
            @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (!ValueSchemas.supports(type.getRawType())) return null;
                GsonBuilder metadata = new GsonBuilder();
                configuration.accept(metadata);
                return ConstructorValueJsonAdapter.create(gson, type,
                        ConstructorValueJsonAdapter.fields(metadata, type)).nullSafe();
            }
        }).create();
    }

    public static void run(Function<Consumer<GsonBuilder>, Gson> create) throws Exception {
        check(ToolResult.class.isInterface(), "ordinary interface");
        for (Class<?> owner : Arrays.<Class<?>>asList(ToolResult.Success.class, ToolResult.Failure.class)) {
            check(Modifier.isFinal(owner.getModifiers()), "final result value");
            check(owner.getSuperclass() == Object.class, "ordinary value class");
            for (java.lang.reflect.Field field : owner.getDeclaredFields()) {
                check(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), "immutable fields");
            }
        }
        equal(Object.class, ToolResult.Success.class.getConstructor(Object.class).getParameterTypes()[0]);
        equal(Object.class, ToolResult.Success.class.getMethod("value").getReturnType());
        equal(String.class, ToolResult.Failure.class.getConstructor(String.class, String.class).getParameterTypes()[0]);
        equal(String.class, ToolResult.Failure.class.getMethod("code").getReturnType());
        equal(String.class, ToolResult.Failure.class.getMethod("message").getReturnType());
        ToolResult.Success<String> success = new ToolResult.Success<>("ok");
        equal("ok", success.value());
        equal(success, new ToolResult.Success<Object>("ok"));
        equal("ok".hashCode(), success.hashCode());
        equal("Success[value=ok]", success.toString());
        check(!success.equals(null) && !success.equals("ok"), "success class equality");
        NullPointerException nullValue = fails(NullPointerException.class, () -> new ToolResult.Success<>(null));
        equal("value", nullValue.getMessage());
        ToolResult.Failure<String> failure = new ToolResult.Failure<>("code", "message");
        equal(failure, new ToolResult.Failure<Object>("code", "message"));
        equal(31 * "code".hashCode() + "message".hashCode(), failure.hashCode());
        equal("Failure[code=code, message=message]", failure.toString());
        check(!failure.equals(success) && !failure.equals(null)
                && !failure.equals(new ToolResult.Failure<>("other", "message")), "failure class equality");
        for (String blank : new String[] {null, "", " ", "\t\n\r", "\u2003", "\u3000", "\u2000\u2028"}) {
            equal("Failure code and message are required", fails(IllegalArgumentException.class,
                    () -> new ToolResult.Failure<>(blank, "message")).getMessage());
            equal("Failure code and message are required", fails(IllegalArgumentException.class,
                    () -> new ToolResult.Failure<>("code", blank)).getMessage());
        }
        for (String nonblank : new String[] {"\u00a0", "\u2007", "\u202f", "\u200b", " code "}) {
            equal(nonblank, new ToolResult.Failure<>(nonblank, nonblank).code());
            equal(nonblank, new ToolResult.Failure<>(nonblank, nonblank).message());
        }
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            String text = new String(Character.toChars(codePoint));
            boolean rejected = false;
            try { new ToolResult.Failure<>(text, "message"); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (rejected != Character.isWhitespace(codePoint)) throw new AssertionError("code whitespace classification: " + codePoint);
            rejected = false;
            try { new ToolResult.Failure<>("code", text); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (rejected != Character.isWhitespace(codePoint)) throw new AssertionError("message whitespace classification: " + codePoint);
        }
        ValueSchema<ToolResult.Success> schema = ValueSchemas.of(ToolResult.Success.class);
        equal(1, schema.components().size());
        equal("value", schema.components().get(0).name());
        check(schema.components().get(0).genericType() instanceof TypeVariable<?>, "generic O accessor metadata");
        equal(ToolResult.Success.class, ((TypeVariable<?>) schema.components().get(0).genericType()).getGenericDeclaration());
        equal(Object.class, schema.components().get(0).rawType());
        equal(success, schema.construct(new Object[] {"ok"}));
        equal("ok", schema.components().get(0).read(success));
        fails(NullPointerException.class, () -> schema.construct(new Object[] {null}));
        fails(IllegalArgumentException.class, () -> schema.construct(new Object[0]));
        fails(UnsupportedOperationException.class, () -> schema.components().clear());
        ValueSchema<ToolResult.Failure> failureSchema = ValueSchemas.of(ToolResult.Failure.class);
        equal("code", failureSchema.components().get(0).name());
        equal("message", failureSchema.components().get(1).name());
        equal(failure, failureSchema.construct(new Object[] {"code", "message"}));
        fails(IllegalArgumentException.class, () -> failureSchema.construct(new Object[] {"\u2003", "message"}));
        Gson gson = create.apply(builder -> {});
        Type successType = new TypeToken<ToolResult.Success<String>>() {}.getType();
        equal("{\"value\":\"ok\"}", gson.toJson(success, successType));
        equal(success, gson.fromJson("{\"value\":\"ok\"}", successType));
        Type failureType = new TypeToken<ToolResult.Failure<String>>() {}.getType();
        equal("{\"code\":\"code\",\"message\":\"message\"}", gson.toJson(failure, failureType));
        equal(failure, gson.fromJson("{\"code\":\"code\",\"message\":\"message\"}", failureType));
        Type nestedType = new TypeToken<ToolResult.Success<List<ToolResult.Failure<String>>>>() {}.getType();
        ToolResult.Success<List<ToolResult.Failure<String>>> nested = new ToolResult.Success<>(Arrays.asList(failure));
        String nestedJson = "{\"value\":[{\"code\":\"code\",\"message\":\"message\"}]}";
        equal(nestedJson, gson.toJson(nested, nestedType));
        ToolResult.Success<List<ToolResult.Failure<String>>> restored = gson.fromJson(nestedJson, nestedType);
        equal(nested, restored);
        equal(ToolResult.Failure.class, restored.value().get(0).getClass());
        for (String invalid : new String[] {"{}", "{\"value\":null}", "{\"value\":\"a\",\"value\":\"b\"}", "[]"}) {
            fails(JsonParseException.class, () -> gson.fromJson(invalid, successType));
        }
        for (String invalid : new String[] {"{}", "{\"code\":null,\"message\":\"m\"}",
                "{\"code\":\"c\",\"message\":null}", "{\"code\":\"\u2003\",\"message\":\"m\"}",
                "{\"code\":\"c\",\"message\":\"\u3000\"}",
                "{\"code\":\"c\",\"code\":\"d\",\"message\":\"m\"}", "[]"}) {
            fails(JsonParseException.class, () -> gson.fromJson(invalid, failureType));
        }
        equal(null, gson.fromJson("null", successType));
        equal(null, gson.fromJson("null", failureType));
    }

    private static void classMajor(Class<?> type) throws Exception {
        try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            check(stream != null, "class resource");
            byte[] header = new byte[8]; int position = 0;
            while (position < header.length) {
                int count = stream.read(header, position, header.length - position);
                check(count > 0, "class header"); position += count;
            }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static <E extends Throwable> E fails(Class<E> expected, Checked operation) throws Exception {
        try { operation.run(); } catch (Throwable failure) {
            if (expected.isInstance(failure)) return expected.cast(failure);
            throw new AssertionError("Expected " + expected.getName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getName());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) {
        check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual);
    }
}
