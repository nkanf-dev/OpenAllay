package dev.openallay.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.openallay.json.EngineJson;
import dev.openallay.json.JsonTrees;
import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.image.ModelImages;
import dev.openallay.value.RecordMetadata;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.ClientObservationAnchorJson;
import dev.openallay.world.InputObservationFixtures;
import dev.openallay.world.WorldViewCapture;
import dev.openallay.world.WorldViewRequest;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Same complete-owner behavior vectors for immutable original modern sources and genuine Java8. */
public final class ModelMessageImageJava8Fixture {
    private static final Gson BASE = EngineJson.create();
    // Fixture-only wire discriminator for the actual public marker. Production wire codecs remain unchanged.
    private static final Gson GSON = EngineJson.derive(BASE, builder -> builder.registerTypeAdapter(
            new com.google.gson.reflect.TypeToken<List<ModelContent>>() {}.getType(), new ContentListAdapter()));
    private static final ImageReference IMAGE = new ImageReference(repeat("a", 64), "image/png", 32, 24, 100);
    private static final ImageReference SECOND = new ImageReference(repeat("b", 64), "image/jpeg", 8, 7, 90);
    private static final ArrayList<String> VECTORS = new ArrayList<String>();
    private ModelMessageImageJava8Fixture() {}

    public static String repeat(String text, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(text);
        return out.toString();
    }
    public static String[] componentNames(Class<?> owner) {
        ArrayList<String> names = new ArrayList<String>();
        if (ValueSchemas.supports(owner)) {
            for (ValueSchema.Component<?> component : ValueSchemas.of(owner).components()) names.add(component.name());
        } else {
            for (RecordMetadata.Component component : RecordMetadata.components(owner)) names.add(component.name());
        }
        return names.toArray(new String[0]);
    }
    private static void fact(String name, Object value) { VECTORS.add(name + "=" + value); }
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        fact(name, true);
    }
    private interface Action { void run() throws Exception; }
    private static void fails(Class<? extends Throwable> type, String name, Action action) {
        try { action.run(); }
        catch (Throwable error) {
            if (!type.isInstance(error)) throw new AssertionError(name + ": " + error, error);
            fact(name, error.getClass().getName());
            return;
        }
        throw new AssertionError(name + " did not reject");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void value(Object original, String... expectedNames) throws Exception {
        Class owner = original.getClass();
        String name = owner.getName();
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<String> types = new ArrayList<String>();
        ArrayList<Object> arguments = new ArrayList<Object>();
        Object rebuilt;
        if (ValueSchemas.supports(owner)) {
            ValueSchema schema = ValueSchemas.of(owner);
            for (Object raw : schema.components()) {
                ValueSchema.Component component = (ValueSchema.Component) raw;
                names.add(component.name());
                types.add(component.genericType().getTypeName());
                arguments.add(component.read(original));
                check(Modifier.isPrivate(component.fieldMetadata().getModifiers())
                        && Modifier.isFinal(component.fieldMetadata().getModifiers()), name + ".privateFinal." + component.name());
            }
            rebuilt = schema.construct(arguments.toArray());
            Class<?> provider = owner.getAnnotation(dev.openallay.value.ValueType.class).value();
            check(Modifier.isPublic(provider.getModifiers()) && Modifier.isPublic(provider.getConstructor().getModifiers()),
                    name + ".providerPresent");
        } else {
            List<RecordMetadata.Component> components = RecordMetadata.components(owner);
            Class<?>[] rawTypes = new Class<?>[components.size()];
            for (int i = 0; i < components.size(); i++) {
                RecordMetadata.Component component = components.get(i);
                names.add(component.name());
                types.add(component.genericType().getTypeName());
                arguments.add(owner.getMethod(component.name()).invoke(original));
                rawTypes[i] = component.rawType();
                check(Modifier.isPrivate(component.fieldMetadata().getModifiers())
                        && Modifier.isFinal(component.fieldMetadata().getModifiers()), name + ".privateFinal." + component.name());
            }
            rebuilt = owner.getConstructor(rawTypes).newInstance(arguments.toArray());
            fact(name + ".providerPresent", true); // Explicit provider replaces modern JVM metadata.
        }
        check(names.equals(Arrays.asList(expectedNames)), name + ".componentOrder");
        fact(name + ".genericTypes", types);
        check(original.equals(rebuilt) && rebuilt.equals(original), name + ".constructorEquality");
        check(original.hashCode() == rebuilt.hashCode(), name + ".constructorHash");
        fact(name + ".hash", original.hashCode());
        fact(name + ".string", original.toString());
        check(!original.equals(null) && !original.equals(new Object()), name + ".foreignEquality");
        if (!(original instanceof ModelRequest) && !(original instanceof ClientObservationAnchor)) {
            JsonElement json = GSON.toJsonTree(original, owner);
            Object decoded = GSON.fromJson(json, owner);
            check(original.equals(decoded), name + ".jsonEquality");
            fact(name + ".json", json);
        }
    }

    /** Stable synthetic request resolver keeps record hash/string vectors process-independent. */
    private static final class Resolver implements ImagePayloadResolver {
        @Override public byte[] read(ImageReference reference) { return new byte[(int) reference.byteSize()]; }
        @Override public int hashCode() { return 31; }
        @Override public String toString() { return "SyntheticResolver"; }
    }
    private static final class ContentListAdapter extends TypeAdapter<List<ModelContent>> {
        private final ContentAdapter content = new ContentAdapter();
        @Override public void write(JsonWriter out, List<ModelContent> list) throws IOException {
            if (list == null) { out.nullValue(); return; }
            out.beginArray();
            for (ModelContent block : list) content.write(out, block);
            out.endArray();
        }
        @Override public List<ModelContent> read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return null; }
            ArrayList<ModelContent> list = new ArrayList<ModelContent>();
            in.beginArray();
            while (in.hasNext()) list.add(content.read(in));
            in.endArray();
            return list;
        }
    }
    private static final class ContentAdapter extends TypeAdapter<ModelContent> {
        @Override public void write(JsonWriter out, ModelContent content) throws IOException {
            if (content == null) { out.nullValue(); return; }
            JsonObject shape = new JsonObject();
            shape.addProperty("kind", content.getClass().getSimpleName());
            shape.add("value", BASE.toJsonTree(content, content.getClass()));
            BASE.toJson(shape, out);
        }
        @Override public ModelContent read(JsonReader in) throws IOException {
            JsonElement json = JsonTrees.parse(in);
            if (json.isJsonNull()) return null;
            JsonObject shape = json.getAsJsonObject();
            String kind = shape.get("kind").getAsString();
            Class<? extends ModelContent> type;
            if (kind.equals("Text")) type = ModelContent.Text.class;
            else if (kind.equals("Image")) type = ModelContent.Image.class;
            else if (kind.equals("Reasoning")) type = ModelContent.Reasoning.class;
            else if (kind.equals("ToolUse")) type = ModelContent.ToolUse.class;
            else if (kind.equals("ToolResult")) type = ModelContent.ToolResult.class;
            else throw new IllegalArgumentException("Unknown fixture kind");
            return BASE.fromJson(shape.get("value"), type);
        }
    }

    public static List<String> vectors() throws Exception {
        VECTORS.clear();
        JsonObject input = JsonTrees.parse("{\"n\":9007199254740993.125,\"flags\":[true,null,\"kept\"]}").getAsJsonObject();
        JsonElement resultValue = JsonTrees.parse("{\"text\":\"actual result\",\"n\":9007199254740993.125}");
        ModelContent.Text text = new ModelContent.Text("hello");
        ModelContent.Image image = new ModelContent.Image(IMAGE, "call");
        ModelContent.Reasoning reasoning = new ModelContent.Reasoning("thought", null);
        ModelContent.ToolUse use = new ModelContent.ToolUse("call", "lookup", input);
        ModelContent.ToolResult result = new ModelContent.ToolResult("call", resultValue, false, Arrays.asList(IMAGE, SECOND));
        ModelToolDefinition tool = new ModelToolDefinition("lookup", "actual description", input);
        value(text, "text"); value(image, "reference", "originToolUseId"); value(reasoning, "text", "signature");
        value(use, "id", "name", "input"); value(result, "toolUseId", "value", "error", "images");
        value(tool, "name", "description", "inputSchema");
        value(IMAGE, "sha256", "mimeType", "width", "height", "byteSize");
        for (ModelContent content : Arrays.<ModelContent>asList(text, image, reasoning, use, result)) {
            check(Modifier.isPublic(content.getClass().getModifiers()) && Modifier.isFinal(content.getClass().getModifiers())
                    && Modifier.isStatic(content.getClass().getModifiers()), content.getClass().getName() + ".publicFinalStaticAbi");
            check(ModelContent.class.isAssignableFrom(content.getClass()), content.getClass().getName() + ".markerAbi");
        }
        JsonObject unknownText = new JsonObject(); unknownText.addProperty("text", "hello"); unknownText.addProperty("extra", "ignored");
        check(BASE.fromJson(unknownText, ModelContent.Text.class).equals(text), "genericJsonUnknownFieldPolicyUnchanged");
        ModelContent.ToolResult missingFlags = BASE.fromJson(JsonTrees.parse("{\"toolUseId\":\"call\",\"value\":null,\"images\":[]}"), ModelContent.ToolResult.class);
        check(!missingFlags.error(), "genericJsonMissingPrimitiveDefaultsFalse");
        fails(com.google.gson.JsonParseException.class, "genericJsonExplicitNullPrimitiveRejected", () -> BASE.fromJson(
                JsonTrees.parse("{\"toolUseId\":\"call\",\"value\":null,\"error\":null,\"images\":[]}"), ModelContent.ToolResult.class));
        value(ImageInputLimits.defaults(), "maxByteSize", "maxDimension", "maxPixels");
        value(WorldViewRequest.defaults(), "target");
        input.addProperty("mutated", true); resultValue.getAsJsonObject().addProperty("mutated", true);
        check(!use.input().has("mutated") && !tool.inputSchema().has("mutated")
                && !result.value().getAsJsonObject().has("mutated"), "constructorDefensiveJsonCopy");
        int useHash = use.hashCode(), resultHash = result.hashCode(), toolHash = tool.hashCode();
        use.input().addProperty("accessorMutation", true);
        result.value().getAsJsonObject().addProperty("accessorMutation", true);
        tool.inputSchema().addProperty("accessorMutation", true);
        check(!use.input().has("accessorMutation") && !result.value().getAsJsonObject().has("accessorMutation")
                && !tool.inputSchema().has("accessorMutation"), "accessorDefensiveJsonCopy");
        check(useHash == use.hashCode() && resultHash == result.hashCode() && toolHash == tool.hashCode(), "rawFieldHashStable");
        fails(UnsupportedOperationException.class, "toolImagesImmutable", () -> result.images().clear());
        check(!use.input().equals(new JsonObject()) && !result.value().equals(JsonNull.INSTANCE), "rawFieldsRetained");
        ModelContent.ToolResult equalResult = new ModelContent.ToolResult("call", result.value(), false, result.images());
        check(result.equals(equalResult) && result != equalResult, "equalResultsRemainDistinctIdentities");
        List<ModelContent> known = Arrays.<ModelContent>asList(text, reasoning, use, result);
        ArrayList<ModelContent> mutable = new ArrayList<ModelContent>(known);
        ModelMessage message = new ModelMessage(ModelRole.ASSISTANT, mutable);
        ModelTurn turn = new ModelTurn("provider", "model", mutable, "tool_use", new ModelUsage(8, 2, 3));
        mutable.clear();
        check(message.content().get(2) == use && message.content().get(3) == result
                && turn.content().get(2) == use && turn.content().get(3) == result, "aggregateCopiesPreserveCacheObjectIdentities");
        value(message, "role", "content", "inputObservation");
        value(turn, "providerId", "model", "content", "stopReason", "usage");
        check(turn.text().equals("hello") && turn.toolUses().equals(Collections.singletonList(use)), "turnProjection");
        fails(UnsupportedOperationException.class, "turnToolUsesImmutable", () -> turn.toolUses().clear());
        fails(UnsupportedOperationException.class, "messageContentImmutable", () -> message.content().clear());
        ModelMessage visual = ModelMessage.userInput("look", Arrays.asList(IMAGE));
        value(visual, "role", "content", "inputObservation");
        ModelTurn imageTurn = new ModelTurn("provider", "model", Arrays.<ModelContent>asList(image), "stop", null);
        value(imageTurn, "providerId", "model", "content", "stopReason", "usage");
        ClientObservationAnchor anchor = InputObservationFixtures.anchor(SECOND);
        WorldViewCapture capture = anchor.image().get();
        value(anchor, "associationId", "capturedAt", "focus", "image");
        value(capture, "captureId", "capturedAt", "actorId", "dimension", "target", "includedHud", "includedGameUi",
                "sourceWidth", "sourceHeight", "guiScale", "camera", "screen", "image", "evidence");
        fails(IllegalArgumentException.class, "anchorFocusTimeMismatch", () -> new ClientObservationAnchor(anchor.associationId(), anchor.capturedAt().plusMillis(1), anchor.focus(), anchor.image()));
        ClientObservationAnchor foreignFocus = new ClientObservationAnchor(anchor.associationId(), anchor.capturedAt(),
                InputObservationFixtures.focus(java.util.UUID.fromString("00000000-0000-0000-0000-000000000099")), anchor.image());
        fails(IllegalArgumentException.class, "messageSourceIdentityMismatch", () -> visual.withInputObservation(foreignFocus));
        fails(IllegalArgumentException.class, "viewCaptureTimeMismatch", () -> new WorldViewCapture(capture.captureId(),
                capture.capturedAt().plusMillis(1), capture.actorId(), capture.dimension(), capture.target(), capture.includedHud(),
                capture.includedGameUi(), capture.sourceWidth(), capture.sourceHeight(), capture.guiScale(), capture.camera(),
                capture.screen(), capture.image(), capture.evidence()));
        fails(NullPointerException.class, "viewTargetRequired", () -> new WorldViewRequest(null));
        ModelMessage associated = visual.withInputObservation(anchor);
        value(associated, "role", "content", "inputObservation");
        check(ClientObservationAnchorJson.decode(ClientObservationAnchorJson.encode(Optional.of(anchor))).get().equals(anchor), "anchorWireRoundtrip");
        Class<?> shape = Class.forName("dev.openallay.world.ClientObservationAnchorJson$Shape");
        check(Modifier.isPrivate(shape.getModifiers()) && Modifier.isStatic(shape.getModifiers()) && Modifier.isFinal(shape.getModifiers()), "privateShapeAbi");
        ArrayList<String> shapeTypes = new ArrayList<String>();
        if (ValueSchemas.supports(shape)) {
            for (ValueSchema.Component<?> component : ValueSchemas.of(shape).components()) shapeTypes.add(component.name() + ":" + component.genericType().getTypeName());
        } else {
            for (RecordMetadata.Component component : RecordMetadata.components(shape)) shapeTypes.add(component.name() + ":" + component.genericType().getTypeName());
        }
        fact("privateShapeMetadata", shapeTypes);
        check(shapeTypes.equals(Arrays.asList("associationId:java.util.UUID", "capturedAt:java.time.Instant", "focus:dev.openallay.world.WorldFocusObservation", "image:dev.openallay.world.WorldViewCapture")), "privateShapeOrder");
        com.google.gson.annotations.JsonAdapter optionalAnnotation = ModelMessage.class.getDeclaredField("inputObservation").getAnnotation(com.google.gson.annotations.JsonAdapter.class);
        check(optionalAnnotation != null && !optionalAnnotation.nullSafe() && optionalAnnotation.value() == ClientObservationAnchorJson.OptionalAdapter.class, "optionalFieldAdapterPreserved");
        check(ClientObservationAnchorJson.decode(JsonNull.INSTANCE).equals(Optional.empty()), "anchorWireNull");
        fact("anchorExactJson", ClientObservationAnchorJson.encode(Optional.of(anchor)));
        JsonObject extra = ClientObservationAnchorJson.encode(Optional.of(anchor)).getAsJsonObject();
        extra.addProperty("unknown", true);
        fails(IllegalArgumentException.class, "anchorUnknownRejected", () -> ClientObservationAnchorJson.decode(extra));
        JsonObject missing = ClientObservationAnchorJson.encode(Optional.of(anchor)).getAsJsonObject();
        missing.remove("associationId");
        fails(IllegalArgumentException.class, "anchorMissingRejected", () -> ClientObservationAnchorJson.decode(missing));
        JsonObject fractional = ClientObservationAnchorJson.encode(Optional.of(anchor)).getAsJsonObject();
        fractional.getAsJsonObject("image").addProperty("sourceWidth", 1920.0);
        fails(IllegalArgumentException.class, "anchorNonIntegerRejected", () -> ClientObservationAnchorJson.decode(fractional));
        fails(IllegalArgumentException.class, "anchorNullArgumentRejected", () -> ClientObservationAnchorJson.decode(null));
        ModelMessage imageOnly = ModelMessage.userInput("", Collections.<ImageReference>emptyList(), Optional.of(anchor));
        check(imageOnly.content().equals(Collections.singletonList(new ModelContent.Text(""))), "associatedImageOnlyPlaceholder");
        check(ModelImages.occurrences(Arrays.asList(associated, new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(result))))
                .equals(Arrays.asList(IMAGE, SECOND, IMAGE, SECOND)), "imageOccurrenceOrderAndDuplicates");
        check(ModelImages.uniqueReferences(Arrays.asList(associated, new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(result))))
                .equals(Arrays.asList(IMAGE, SECOND)), "imageUniqueFirstOccurrenceOrder");
        check(ModelImages.observationContent(Arrays.asList(new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(result))))
                .equals(Arrays.<ModelContent>asList(new ModelContent.Image(IMAGE, "call"), new ModelContent.Image(SECOND, "call"))), "toolImageOriginPreserved");
        check(ModelImages.hasImages(Collections.singletonList(imageOnly)) && !ModelImages.hasImages(Collections.singletonList(ModelMessage.userText("plain"))), "typedImagePresence");
        fact("inputObservationLabel", ModelImages.inputObservationLabel(anchor));
        fact("toolObservationLabel", ModelImages.observationLabel("call"));
        fails(IllegalArgumentException.class, "conflictingImageHash", () -> ModelImages.unique(Arrays.asList(IMAGE,
                new ImageReference(IMAGE.sha256(), "image/png", 33, 24, 100))));
        fails(IllegalArgumentException.class, "failedToolCannotPublishImages", () -> new ModelContent.ToolResult("call", result.value(), true, Arrays.asList(IMAGE)));
        fails(IllegalArgumentException.class, "blankToolOrigin", () -> new ModelContent.Image(IMAGE, " "));
        fails(IllegalArgumentException.class, "visualAssistantRejected", () -> new ModelMessage(ModelRole.ASSISTANT, Arrays.<ModelContent>asList(new ModelContent.Image(IMAGE))));
        fails(IllegalArgumentException.class, "playerToolOriginRejected", () -> ModelMessage.requireUserInput(new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(image))));
        fails(IllegalArgumentException.class, "emptyMessageRejected", () -> new ModelMessage(ModelRole.USER, Collections.<ModelContent>emptyList()));
        fails(NullPointerException.class, "nullBlockRejectedBeforeEmpty", () -> new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList((ModelContent) null)));
        fails(NullPointerException.class, "nullObservationRejected", () -> new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(text), null));
        fails(IllegalArgumentException.class, "blankVisualInputRejected", () -> ModelMessage.userInput(" ", Collections.<ImageReference>emptyList()));
        fails(IllegalArgumentException.class, "toolUseBlankRejected", () -> new ModelContent.ToolUse(" ", "name", new JsonObject()));
        fails(IllegalArgumentException.class, "toolResultBlankRejected", () -> new ModelContent.ToolResult(" ", JsonNull.INSTANCE, false));
        fails(IllegalArgumentException.class, "turnProviderValidationBeforeCopy", () -> new ModelTurn("", "m", null, "", null));
        fails(NullPointerException.class, "turnNullContentBeforeStop", () -> new ModelTurn("p", "m", null, "", null));
        fails(IllegalArgumentException.class, "turnStopRejected", () -> new ModelTurn("p", "m", Collections.<ModelContent>emptyList(), "", null));
        ModelRequest request = new ModelRequest("system", Arrays.asList(message), Arrays.asList(tool), false, "session", 100, new Resolver());
        value(request, "systemPrompt", "messages", "tools", "stream", "sessionKey", "maxOutputTokens", "images");
        check(request.effectiveMaxOutputTokens(80) == 80 && request.effectiveMaxOutputTokens(200) == 100, "requestCapOnlyReduces");
        check(new ModelRequest("system", Arrays.asList(message), Collections.<ModelToolDefinition>emptyList(), false)
                .effectiveMaxOutputTokens(90) == 90, "requestDefaultCap");
        fails(IOException.class, "requestDefaultResolverDenied", () -> new ModelRequest("system", Arrays.asList(message), Collections.<ModelToolDefinition>emptyList(), false).images().read(IMAGE));
        fails(IllegalArgumentException.class, "requestZeroCapRejected", () -> new ModelRequest("system", Arrays.asList(message), Collections.<ModelToolDefinition>emptyList(), false, "session", 0));
        ImageInputLimits limits = new ImageInputLimits(100, 40, 768);
        limits.validate(IMAGE); fact("imageLimitsAtBoundary", true);
        fails(IOException.class, "imageBytesOverLimit", () -> new ImageInputLimits(99, 40, 768).validate(IMAGE));
        fails(IOException.class, "imagePixelsOverLimit", () -> new ImageInputLimits(100, 40, 767).validate(IMAGE));
        fails(IOException.class, "imageAxisOverLimit", () -> new ImageInputLimits(100, 31, 768).validate(IMAGE));
        fails(IllegalArgumentException.class, "imageLimitArrayOverflow", () -> new ImageInputLimits(1L + Integer.MAX_VALUE, 1, 1));
        return Collections.unmodifiableList(new ArrayList<String>(VECTORS));
    }
    public static void main(String[] arguments) throws Exception {
        for (String vector : vectors()) System.out.println(vector);
    }
}
