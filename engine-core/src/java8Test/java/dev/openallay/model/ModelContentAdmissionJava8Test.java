package dev.openallay.model;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.openallay.json.EngineJson;
import dev.openallay.json.JsonTrees;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Postimage-only tests: a foreign implementation cannot compile against the sealed original. */
final class ModelContentAdmissionJava8Test {
    private static final class Foreign implements ModelContent {}
    private static final List<ModelContent> FOREIGN = Collections.<ModelContent>singletonList(new Foreign());
    @Test void exactFiveFinalVariantsAreAdmitted() {
        ImageReference image = new ImageReference(ModelMessageImageJava8Fixture.repeat("a", 64), "image/png", 1, 1, 1);
        for (ModelContent content : Arrays.<ModelContent>asList(new ModelContent.Text("text"),
                new ModelContent.Image(image), new ModelContent.Reasoning("reasoning", null),
                new ModelContent.ToolUse("call", "lookup", new com.google.gson.JsonObject()),
                new ModelContent.ToolResult("call", com.google.gson.JsonNull.INSTANCE, false))) {
            assertTrue(java.lang.reflect.Modifier.isFinal(content.getClass().getModifiers()));
            assertDoesNotThrow(() -> ModelContent.requireKnown(content));
            assertDoesNotThrow(() -> new ModelMessage(content instanceof ModelContent.Image ? ModelRole.USER : ModelRole.ASSISTANT,
                    Collections.singletonList(content)));
            assertDoesNotThrow(() -> new ModelTurn("p", "m", Collections.singletonList(content), "stop", null));
        }
    }
    @Test void bothAggregateIngressesRejectForeignContent() {
        assertThrows(IncompatibleClassChangeError.class, () -> new ModelMessage(ModelRole.ASSISTANT, FOREIGN));
        assertThrows(IncompatibleClassChangeError.class, () -> new ModelTurn("p", "m", FOREIGN, "stop", null));
    }
    @Test void engineJsonDirectSchemaConstructionRejectsForeignContent() {
        Gson gson = EngineJson.derive(EngineJson.create(), builder -> builder.registerTypeAdapter(
                new TypeToken<List<ModelContent>>() {}.getType(), new TypeAdapter<List<ModelContent>>() {
                    @Override public void write(JsonWriter out, List<ModelContent> value) throws IOException { out.beginArray(); out.endArray(); }
                    @Override public List<ModelContent> read(JsonReader in) throws IOException { in.skipValue(); return FOREIGN; }
                }));
        assertThrows(IncompatibleClassChangeError.class, () -> gson.fromJson(
                JsonTrees.parse("{\"role\":\"ASSISTANT\",\"content\":[],\"inputObservation\":null}"), ModelMessage.class));
        assertThrows(IncompatibleClassChangeError.class, () -> gson.fromJson(
                JsonTrees.parse("{\"providerId\":\"p\",\"model\":\"m\",\"content\":[],\"stopReason\":\"stop\",\"usage\":null}"), ModelTurn.class));
    }
}
