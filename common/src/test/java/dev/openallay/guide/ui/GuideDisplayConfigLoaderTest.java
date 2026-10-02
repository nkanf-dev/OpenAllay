package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuideDisplayConfigLoaderTest {
    @TempDir Path temporary;

    @Test
    void missingConfigDefaultsToProductNameDebugOffAndAnimationsOn() {
        var load = new GuideDisplayConfigLoader().load(temporary.resolve("missing.json"));
        assertEquals(GuideDisplayConfig.defaults(), load.config());
        assertFalse(load.config().debugMode());
        assertTrue(load.config().animationsEnabled());
        assertEquals("OpenAllay", load.config().assistantName());
        assertFalse(load.config().ui().hud().enabled());
        assertFalse(load.config().ui().notifications().enabled());
        assertNull(load.failure());
    }

    @Test
    void canonicalWriterRoundTripsEveryCurrentNestedField() {
        var ui = new GuideUiConfig(
                new GuideUiConfig.Fullscreen(GuideUiConfig.Density.COMPACT, false, false, GuideUiConfig.Theme.MINT),
                new GuideUiConfig.Hud(true, GuideUiConfig.Anchor.BOTTOM_RIGHT, -40, -24, 400, 180, 1.5, .2,
                        true, 8, false, true, false, false),
                new GuideUiConfig.Notifications(true, GuideUiConfig.NotificationPolicy.ALWAYS, false, true, false, 12));
        var candidate = new GuideDisplayConfig(true, false, "  小羽  ", ui);
        String encoded = new GuideDisplayConfigWriter().encode(candidate);
        var load = new GuideDisplayConfigLoader().load(new StringReader(encoded));
        assertEquals(candidate, load.config());
        assertNull(load.failure());
        assertEquals("小羽", load.config().assistantName());
        assertTrue(encoded.endsWith(System.lineSeparator()));
        assertTrue(encoded.contains("\"backgroundOpacity\": 0.2"));
        assertFalse(encoded.contains("schemaVersion"));
        assertFalse(encoded.contains("formatVersion"));
        assertEquals(encoded, new GuideDisplayConfigWriter().encode(load.config()));
    }

    @Test
    void rejectsOldShapeWithoutMigrationOrRewriting() throws Exception {
        Path path = temporary.resolve("display.json");
        String old = "{\"debugMode\":true,\"animationsEnabled\":false,\"assistantName\":\"小羽\"}";
        Files.writeString(path, old);
        var load = new GuideDisplayConfigLoader().load(path);
        assertEquals(GuideDisplayConfig.defaults(), load.config());
        assertNotNull(load.failure());
        assertEquals("invalid_display_config", load.failure().code());
        assertEquals(old, Files.readString(path));
    }

    @Test
    void rejectsMissingUnknownOrWrongTypesAtEveryObjectDepth() {
        for (String domain : new String[] {"root", "ui", "fullscreen", "hud", "notifications"}) {
            JsonObject root = current();
            object(root, domain).addProperty("unexpected", true);
            assertInvalid(root);
            root = current();
            String field = object(root, domain).keySet().iterator().next();
            object(root, domain).remove(field);
            assertInvalid(root);
            root = current();
            object(root, domain).add(field, new com.google.gson.JsonArray());
            assertInvalid(root);
        }
        for (String field : new String[] {"debugMode", "animationsEnabled"}) {
            JsonObject root = current();
            root.addProperty(field, "true");
            assertInvalid(root);
        }
        for (String name : new String[] {"", "   ", "bad\nname"}) {
            JsonObject root = current();
            root.addProperty("assistantName", name);
            assertInvalid(root);
        }
        JsonObject root = current();
        root.addProperty("assistantName", false);
        assertInvalid(root);
        root = current();
        object(root, "fullscreen").addProperty("density", "compact");
        assertInvalid(root);
        root = current();
        object(root, "notifications").addProperty("policy", "SOMETIMES");
        assertInvalid(root);
    }

    @Test
    void rangeBoundariesAreAcceptedAndInvalidNumbersNeverCoerce() {
        Object[][] ranges = {
            {"hud", "offsetX", -4096, 4096, true},
            {"hud", "offsetY", -4096, 4096, true},
            {"hud", "width", 160, 480, true},
            {"hud", "height", 44, 240, true},
            {"hud", "scale", .75, 1.75, false},
            {"hud", "backgroundOpacity", 0, 1, false},
            {"hud", "maxReplyLines", 0, 80, true},
            {"notifications", "durationSeconds", 3, 15, true}
        };
        for (Object[] row : ranges) {
            String domain = (String) row[0];
            String field = (String) row[1];
            double minimum = ((Number) row[2]).doubleValue();
            double maximum = ((Number) row[3]).doubleValue();
            for (double valid : new double[] {minimum, maximum}) {
                JsonObject root = current();
                object(root, domain).addProperty(field, valid);
                assertNull(new GuideDisplayConfigLoader().load(new StringReader(root.toString())).failure(), field);
            }
            for (double invalid : new double[] {minimum - 1, maximum + 1, Double.MAX_VALUE}) {
                JsonObject root = current();
                object(root, domain).addProperty(field, invalid);
                assertInvalid(root);
            }
            if ((Boolean) row[4]) {
                JsonObject root = current();
                object(root, domain).addProperty(field, minimum + .5);
                assertInvalid(root);
            }
            JsonObject root = current();
            object(root, domain).addProperty(field, "1");
            assertInvalid(root);
            root = current();
            object(root, domain).add(field, com.google.gson.JsonNull.INSTANCE);
            assertInvalid(root);
        }
        for (String token : new String[] {"NaN", "Infinity", "1e400", "-1e400"}) {
            String encoded = new GuideDisplayConfigWriter().encode(GuideDisplayConfig.defaults())
                    .replace("\"scale\": 1.0", "\"scale\": " + token);
            assertNotNull(new GuideDisplayConfigLoader().load(new StringReader(encoded)).failure(), token);
        }
    }

    private static JsonObject current() {
        return JsonParser.parseString(new GuideDisplayConfigWriter().encode(GuideDisplayConfig.defaults())).getAsJsonObject();
    }

    private static JsonObject object(JsonObject root, String domain) {
        if (domain.equals("root")) return root;
        JsonObject ui = root.getAsJsonObject("ui");
        return domain.equals("ui") ? ui : ui.getAsJsonObject(domain);
    }

    private static void assertInvalid(JsonObject root) {
        var load = new GuideDisplayConfigLoader().load(new StringReader(root.toString()));
        assertEquals(GuideDisplayConfig.defaults(), load.config());
        assertNotNull(load.failure());
        assertEquals("invalid_display_config", load.failure().code());
    }
}
