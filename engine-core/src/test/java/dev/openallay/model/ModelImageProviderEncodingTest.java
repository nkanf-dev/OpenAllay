package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.openai.OpenAiJsonCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ModelImageProviderEncodingTest {
    private final Gson gson = dev.openallay.json.EngineJson.create();
    private final OpenAiJsonCodec openAi = new OpenAiJsonCodec(gson);
    private final AnthropicJsonCodec anthropic = new AnthropicJsonCodec(gson);
    // Encoder fixtures: the scoped artifact resolver separately verifies real image bytes.
    private static final byte[] FIRST = {0, 1, 2, 3, (byte) 255, 7};
    private static final byte[] SECOND = {9, 8, 7, 6};
    private static final ImageReference PNG = reference("a", "image/png", 32, 24, FIRST.length);
    private static final ImageReference JPEG = reference("b", "image/jpeg", 64, 48, SECOND.length);

    @Test
    void associatedReferenceIsInsideSamePlayerMessageWithRealBytesAndConciseSourceLabel() {
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(PNG);
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var reads = new ArrayList<ImageReference>();
            var input = ModelMessage.userText("Which one?").withInputObservation(anchor);
            var nativeBody = dev.openallay.json.JsonTrees.parse(body(protocol, request(input, image -> {
                reads.add(image); return FIRST;
            }))).getAsJsonObject();
            assertEquals(protocol == ModelProtocol.OPENAI_CHAT ? 2 : 1, nativeBody.getAsJsonArray("messages").size());
            var parts = content(protocol, nativeBody);
            assertEquals(3, parts.size());
            assertEquals("Which one?", parts.get(0).getAsJsonObject().get("text").getAsString());
            String label = parts.get(1).getAsJsonObject().get("text").getAsString();
            assertTrue(label.contains("player-input reference context"));
            assertTrue(label.contains(anchor.capturedAt().toString()));
            assertTrue(label.contains(anchor.focus().actorId().toString()));
            assertTrue(label.contains("minecraft:oak_stairs at 12,65,-4"));
            assertTrue(label.contains("hovered menu slot 4"));
            assertTrue(label.contains("Named sword"));
            assertTrue(label.contains(anchor.image().orElseThrow().capturedAt().toString()));
            assertFalse(label.contains("minecraft:custom_data"));
            assertFalse(label.contains("9007199254740993"));
            assertImage(protocol, parts.get(2).getAsJsonObject(), PNG, FIRST);
            assertEquals(List.of(PNG), reads);
            var imageOnly = ModelMessage.userInput("", List.of(), java.util.Optional.of(anchor));
            assertEquals(3, content(protocol, dev.openallay.json.JsonTrees.parse(body(protocol, request(imageOnly,
                    image -> FIRST))).getAsJsonObject()).size());
            var focusOnly = ModelMessage.userText("Which item?").withInputObservation(
                    dev.openallay.world.InputObservationFixtures.anchor(null));
            assertEquals(2, content(protocol, dev.openallay.json.JsonTrees.parse(body(protocol, request(focusOnly,
                    image -> { throw new AssertionError("no source frame captured"); }))).getAsJsonObject()).size());
        }
    }

    @Test
    void nativeImagesPreserveEveryTextAndImageBlockInOriginalOrder() {
        var message = new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.Text("before"), new ModelContent.Image(PNG),
                new ModelContent.Text("between"), new ModelContent.Image(JPEG),
                new ModelContent.Text("after")));
        for (ModelProtocol protocol : ModelProtocol.values()) {
            for (boolean stream : List.of(false, true)) {
                var reads = new ArrayList<ImageReference>();
                ModelRequest request = new ModelRequest("System", List.of(message), List.of(), stream,
                        "session", null, image -> {
                            reads.add(image);
                            return image.equals(PNG) ? FIRST : SECOND;
                        });
                String body = body(protocol, request);
                var root = dev.openallay.json.JsonTrees.parse(body).getAsJsonObject();
                JsonArray content = content(protocol, root);
                assertEquals(5, content.size());
                assertEquals("before", content.get(0).getAsJsonObject().get("text").getAsString());
                assertEquals("between", content.get(2).getAsJsonObject().get("text").getAsString());
                assertEquals("after", content.get(4).getAsJsonObject().get("text").getAsString());
                assertImage(protocol, content.get(1).getAsJsonObject(), PNG, FIRST);
                assertImage(protocol, content.get(3).getAsJsonObject(), JPEG, SECOND);
                assertEquals(List.of(PNG, JPEG), reads);
                assertEquals(stream, root.get("stream").getAsBoolean());
                assertFalse(body.contains(PNG.sha256()));
                assertFalse(body.contains(JPEG.sha256()));
                assertFalse(body.contains("file:"));
                assertFalse(body.contains("https:"));
            }
        }
    }

    @Test
    void imageOnlyNativeInputHasNoInventedText() {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var request = request(ModelMessage.userInput(null, List.of(PNG)), image -> FIRST);
            JsonArray content = content(protocol, dev.openallay.json.JsonTrees.parse(body(protocol, request)).getAsJsonObject());
            assertEquals(1, content.size());
            assertImage(protocol, content.get(0).getAsJsonObject(), PNG, FIRST);
        }
    }

    @Test
    void offlineImageFramingUsesOnlyMetadataAndNeverResolvesPayloads() {
        var message = ModelMessage.userInput("what is this?", List.of(PNG, JPEG));
        for (ModelProtocol protocol : ModelProtocol.values()) {
            JsonObject root = context(protocol, List.of(message));
            JsonArray content = content(protocol, root);
            assertEquals(3, content.size());
            JsonObject metadata = content.get(1).getAsJsonObject().getAsJsonObject(
                    protocol == ModelProtocol.OPENAI_CHAT ? "image_url" : "source");
            assertEquals("image/png", metadata.get("media_type").getAsString());
            assertEquals(32, metadata.get("width").getAsInt());
            assertEquals(24, metadata.get("height").getAsInt());
            assertEquals(FIRST.length, metadata.get("byte_size").getAsLong());
            assertFalse(metadata.has("url"));
            assertFalse(metadata.has("data"));
            assertFalse(metadata.has("type"));
            String offline = gson.toJson(root);
            assertFalse(offline.contains("base64"));
            assertFalse(offline.contains(PNG.sha256()));
            assertFalse(offline.contains(Base64.getEncoder().encodeToString(FIRST)));
            assertEquals(root, context(protocol, List.of(message)));
        }
    }

    @Test
    void textOnlyInputRetainsTheOriginalWireShapeWithoutCallingTheResolver() {
        var message = new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.Text("one"), new ModelContent.Text("two")));
        var request = request(message, image -> { throw new AssertionError("text input read an image"); });
        for (ModelProtocol protocol : ModelProtocol.values()) {
            JsonObject root = dev.openallay.json.JsonTrees.parse(body(protocol, request)).getAsJsonObject();
            root.remove("model");
            root.remove("stream");
            root.remove(protocol == ModelProtocol.OPENAI_CHAT ? "max_completion_tokens" : "max_tokens");
            assertEquals(context(protocol, List.of(message)), root);
            if (protocol == ModelProtocol.OPENAI_CHAT) {
                assertEquals("onetwo", root.getAsJsonArray("messages").get(1).getAsJsonObject()
                        .get("content").getAsString());
            } else {
                assertEquals(2, content(protocol, root).size());
            }
        }
    }

    @Test
    void missingUnauthorizedAndMismatchedBytesFailExplicitly() {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var message = ModelMessage.userInput(null, List.of(PNG));
            assertThrows(UncheckedIOException.class, () -> body(protocol,
                    new ModelRequest("System", List.of(message), List.of(), false)));
            assertThrows(UncheckedIOException.class, () -> body(protocol,
                    request(message, image -> { throw new IOException("wrong actor"); })));
            assertThrows(IllegalArgumentException.class, () -> body(protocol,
                    request(message, image -> new byte[FIRST.length - 1])));
        }
    }

    @Test
    void nativeImageCountLimitsRejectBeforeReadingAnyBytes() {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            int limit = protocol == ModelProtocol.OPENAI_CHAT ? 500 : 600;
            var reads = new AtomicInteger();
            ImagePayloadResolver resolver = image -> { reads.incrementAndGet(); return FIRST; };
            var overLimit = ModelMessage.userInput(null, Collections.nCopies(limit + 1, PNG));
            assertThrows(IllegalArgumentException.class, () -> body(protocol, request(overLimit, resolver)));
            assertEquals(0, reads.get());
            var atLimit = ModelMessage.userInput(null, Collections.nCopies(limit, PNG));
            assertEquals(limit, content(protocol,
                    dev.openallay.json.JsonTrees.parse(body(protocol, request(atLimit, resolver))).getAsJsonObject()).size());
            assertEquals(limit, reads.get());
        }
    }

    @Test
    void anthropicDirectApiBase64AndDimensionLimitsDoNotBecomeGenericOpenAiLimits() {
        // Primary vision guidance: "10 MB (base64-encoded) ... Claude API directly".
        // 7,500,000 raw bytes encode to exactly 10,000,000 Base64 bytes.
        for (ImageReference invalid : List.of(
                reference("c", "image/png", 32, 24, 7_500_001),
                reference("c", "image/png", 32, 24, Long.MAX_VALUE),
                reference("c", "image/png", 8_001, 24, 1),
                reference("c", "image/jpeg", 32, 8_001, 1))) {
            assertThrows(IllegalArgumentException.class, () -> body(ModelProtocol.ANTHROPIC_MESSAGES,
                    request(ModelMessage.userInput(null, List.of(invalid)),
                            image -> { throw new AssertionError("invalid image read"); })));
        }
        byte[] exactMaximum = new byte[7_500_000];
        var atLimit = reference("c", "image/png", 8_000, 8_000, exactMaximum.length);
        var atLimitMessage = ModelMessage.userInput(null, List.of(atLimit));
        assertDoesNotThrow(() -> body(ModelProtocol.ANTHROPIC_MESSAGES,
                request(atLimitMessage, image -> exactMaximum)));
        // A formerly hardcoded 5,000,000 raw-byte gate was not a direct-API fact.
        var aboveOldRawLimit = reference("c", "image/png", 32, 24, 5_000_001);
        assertDoesNotThrow(() -> body(ModelProtocol.ANTHROPIC_MESSAGES,
                request(ModelMessage.userInput(null, List.of(aboveOldRawLimit)),
                        image -> new byte[(int) image.byteSize()])));
        var openAiLargeDimensions = reference("d", "image/png", 8_001, 8_001, FIRST.length);
        assertDoesNotThrow(() -> body(ModelProtocol.OPENAI_CHAT,
                request(ModelMessage.userInput(null, List.of(openAiLargeDimensions)), image -> FIRST)));
        var openAiLargeBytes = reference("d", "image/png", 32, 24, 5_000_001);
        assertDoesNotThrow(() -> body(ModelProtocol.OPENAI_CHAT,
                request(ModelMessage.userInput(null, List.of(openAiLargeBytes)),
                        image -> new byte[(int) image.byteSize()])));
    }

    @Test
    void manualContextBudgetAndCrossPlatformResizeAdviceDoNotInventNativeModelLimits() {
        // Current primary docs distinguish 100 images for published 200k-context
        // models from 600 for other models. The configured budget is not that fact.
        var image = reference("f", "image/png", 2_001, 2_001, FIRST.length);
        var message = ModelMessage.userInput(null, Collections.nCopies(101, image));
        var source = config(ModelProtocol.ANTHROPIC_MESSAGES);
        var manualBudget = new ModelConfig(source.enabled(), source.protocol(), source.baseUri(),
                source.model(), source.apiKey(), 200_000, source.maxOutputTokens(),
                source.connectTimeout(), source.requestTimeout());
        assertDoesNotThrow(() -> anthropic.requestBody(manualBudget, request(message, ignored -> FIRST)));
    }

    @Test
    void aggregateEncodedImageBytesCannotBypassNativeRequestLimits() {
        var anthropicImage = reference("e", "image/png", 32, 24, 5_000_000);
        assertThrows(IllegalArgumentException.class, () -> body(ModelProtocol.ANTHROPIC_MESSAGES,
                request(ModelMessage.userInput(null, Collections.nCopies(5, anthropicImage)),
                        image -> { throw new AssertionError("oversized request read"); })));
        var openAiImage = reference("e", "image/png", 32, 24, 37_500_001);
        assertThrows(IllegalArgumentException.class, () -> body(ModelProtocol.OPENAI_CHAT,
                request(ModelMessage.userInput(null, List.of(openAiImage)),
                        image -> { throw new AssertionError("oversized request read"); })));
    }

    @Test
    void fullRequestLimitsIncludeExactUtf8TextAndJsonFramingNotJustImageBytes() {
        // Non-ASCII input makes character length insufficient for native byte limits.
        for (ModelProtocol protocol : ModelProtocol.values()) {
            int limit = protocol == ModelProtocol.OPENAI_CHAT ? 50_000_000 : 32_000_000;
            String oversizedSystem = "界".repeat(limit / 3 + 1);
            ModelRequest request = new ModelRequest(oversizedSystem,
                    List.of(ModelMessage.userText("text")), List.of(), false);
            assertTrue(oversizedSystem.getBytes(StandardCharsets.UTF_8).length > limit);
            assertThrows(IllegalArgumentException.class, () -> body(protocol, request));
        }
    }

    @Test
    void nestedToolImagesUseSupportedProviderFramingAndKeepCanonicalResultGroup() {
        List<ModelMessage> canonical = toolExchange(List.of(PNG, JPEG, PNG));
        for (ModelProtocol protocol : ModelProtocol.values()) {
            for (boolean stream : List.of(false, true)) {
                var reads = new ArrayList<ImageReference>();
                var request = new ModelRequest("System", canonical, List.of(), stream, "session", null, image -> {
                    reads.add(image);
                    return image.equals(PNG) ? FIRST : SECOND;
                });
                JsonArray messages = dev.openallay.json.JsonTrees.parse(body(protocol, request)).getAsJsonObject()
                        .getAsJsonArray("messages");
                JsonArray visual;
                if (protocol == ModelProtocol.OPENAI_CHAT) {
                    assertEquals(5, messages.size()); // System, assistant, both tool replies, visual supplement.
                    assertEquals("tool", messages.get(2).getAsJsonObject().get("role").getAsString());
                    assertEquals("tool", messages.get(3).getAsJsonObject().get("role").getAsString());
                    assertTrue(messages.get(2).getAsJsonObject().get("content").isJsonPrimitive());
                    assertEquals("user", messages.get(4).getAsJsonObject().get("role").getAsString());
                    visual = messages.get(4).getAsJsonObject().getAsJsonArray("content");
                    assertTrue(visual.get(0).getAsJsonObject().get("text").getAsString().contains("capture-call"));
                    assertTrue(visual.get(0).getAsJsonObject().get("text").getAsString().contains("not a new player"));
                } else {
                    assertEquals(2, messages.size());
                    JsonArray results = messages.get(1).getAsJsonObject().getAsJsonArray("content");
                    assertEquals(2, results.size());
                    assertEquals("capture-call", results.get(0).getAsJsonObject().get("tool_use_id").getAsString());
                    visual = results.get(0).getAsJsonObject().getAsJsonArray("content");
                    assertTrue(results.get(1).getAsJsonObject().get("content").isJsonPrimitive());
                }
                assertEquals(4, visual.size());
                assertImage(protocol, visual.get(1).getAsJsonObject(), PNG, FIRST);
                assertImage(protocol, visual.get(2).getAsJsonObject(), JPEG, SECOND);
                assertImage(protocol, visual.get(3).getAsJsonObject(), PNG, FIRST);
                assertEquals(List.of(PNG, JPEG, PNG), reads);
                assertEquals(2, canonical.size());
                assertTrue(canonical.getLast().content().stream().allMatch(ModelContent.ToolResult.class::isInstance));
                String offline = context(protocol, canonical).toString();
                assertFalse(offline.contains("base64"));
                assertFalse(offline.contains(Base64.getEncoder().encodeToString(FIRST)));
            }
        }
    }

    @Test
    void nestedImagesCountTowardNativeLimitsAndMissingPayloadCannotBecomeTextOnly() {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            int limit = protocol == ModelProtocol.OPENAI_CHAT ? 500 : 600;
            var reads = new AtomicInteger();
            var oversized = new ModelRequest("System", toolExchange(Collections.nCopies(limit + 1, PNG)),
                    List.of(), false, "session", null, image -> { reads.incrementAndGet(); return FIRST; });
            assertThrows(IllegalArgumentException.class, () -> body(protocol, oversized));
            assertEquals(0, reads.get());
            assertThrows(UncheckedIOException.class, () -> body(protocol,
                    new ModelRequest("System", toolExchange(List.of(PNG)), List.of(), false)));
        }
    }

    @Test
    void carriedToolOriginUsesTypedProviderLabelsWithTheActualPixels() {
        var observation = new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text("derived memory"),
                new ModelContent.Image(PNG, "original-view"), new ModelContent.Image(PNG, "original-view")));
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var reads = new ArrayList<ImageReference>();
            var request = new ModelRequest("System", List.of(observation), List.of(), false, "session", null, image -> {
                reads.add(image); return FIRST;
            });
            JsonArray content = content(protocol, dev.openallay.json.JsonTrees.parse(body(protocol, request)).getAsJsonObject());
            assertEquals(5, content.size());
            assertTrue(content.get(1).getAsJsonObject().get("text").getAsString().contains("original-view"));
            assertTrue(content.get(3).getAsJsonObject().get("text").getAsString().contains("not a new player"));
            assertImage(protocol, content.get(2).getAsJsonObject(), PNG, FIRST);
            assertImage(protocol, content.get(4).getAsJsonObject(), PNG, FIRST);
            assertEquals(List.of(PNG, PNG), reads);
            String offline = context(protocol, List.of(observation)).toString();
            assertTrue(offline.contains("original-view"));
            assertFalse(offline.contains("base64"));
        }
    }


    private static List<ModelMessage> toolExchange(List<ImageReference> images) {
        return List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse("capture-call", "capture", new JsonObject()),
                        new ModelContent.ToolUse("other-call", "other", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(
                        new ModelContent.ToolResult("capture-call", new com.google.gson.JsonPrimitive("captured"), false, images),
                        new ModelContent.ToolResult("other-call", new com.google.gson.JsonPrimitive("fact"), false))));
    }

    private String body(ModelProtocol protocol, ModelRequest request) {
        return protocol == ModelProtocol.OPENAI_CHAT
                ? openAi.requestBody(config(protocol), request)
                : anthropic.requestBody(config(protocol), request);
    }

    private JsonObject context(ModelProtocol protocol, List<ModelMessage> messages) {
        return protocol == ModelProtocol.OPENAI_CHAT
                ? openAi.contextInput("System", messages, List.of())
                : anthropic.contextInput("System", messages, List.of());
    }

    private static JsonArray content(ModelProtocol protocol, JsonObject root) {
        return root.getAsJsonArray("messages").get(protocol == ModelProtocol.OPENAI_CHAT ? 1 : 0)
                .getAsJsonObject().getAsJsonArray("content");
    }

    private static void assertImage(
            ModelProtocol protocol, JsonObject part, ImageReference reference, byte[] bytes) {
        String base64 = Base64.getEncoder().encodeToString(bytes);
        if (protocol == ModelProtocol.OPENAI_CHAT) {
            assertEquals("image_url", part.get("type").getAsString());
            assertEquals("data:" + reference.mimeType() + ";base64," + base64,
                    part.getAsJsonObject("image_url").get("url").getAsString());
        } else {
            assertEquals("image", part.get("type").getAsString());
            JsonObject source = part.getAsJsonObject("source");
            assertEquals("base64", source.get("type").getAsString());
            assertEquals(reference.mimeType(), source.get("media_type").getAsString());
            assertEquals(base64, source.get("data").getAsString());
        }
    }

    private static ImageReference reference(String hash, String mime, int width, int height, long bytes) {
        return new ImageReference(hash.repeat(64), mime, width, height, bytes);
    }

    private static ModelRequest request(ModelMessage message, ImagePayloadResolver images) {
        return new ModelRequest("System", List.of(message), List.of(), false, "session", null, images);
    }

    private static ModelConfig config(ModelProtocol protocol) {
        return new ModelConfig(true, protocol, URI.create("https://example.invalid/v1/"),
                "compatible-model", SecretValue.of("test-secret"), 128_000, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }
}
