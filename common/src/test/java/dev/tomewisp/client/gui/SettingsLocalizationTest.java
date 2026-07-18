package dev.tomewisp.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SettingsLocalizationTest {
    private static final Set<String> REQUIRED = Set.of(
            "screen.tomewisp.settings.title",
            "screen.tomewisp.settings.short",
            "screen.tomewisp.settings.general",
            "screen.tomewisp.settings.models",
            "screen.tomewisp.settings.knowledge",
            "screen.tomewisp.settings.history",
            "screen.tomewisp.settings.diagnostics",
            "screen.tomewisp.settings.models.add",
            "screen.tomewisp.settings.models.base_url",
            "screen.tomewisp.settings.models.api_key_env",
            "screen.tomewisp.settings.models.context_window",
            "screen.tomewisp.settings.models.test",
            "screen.tomewisp.settings.confirm_billable_test");

    @Test
    void englishAndChineseExposeTheSameCompleteSettingsKeys() throws Exception {
        JsonObject english = read("en_us.json");
        JsonObject chinese = read("zh_cn.json");

        assertEquals(english.keySet(), chinese.keySet());
        for (String key : REQUIRED) {
            assertTrue(english.has(key), key);
            assertTrue(chinese.has(key), key);
            assertTrue(!english.get(key).getAsString().isBlank(), key);
            assertTrue(!chinese.get(key).getAsString().isBlank(), key);
        }
    }

    private static JsonObject read(String file) throws Exception {
        Path path = Path.of("src/main/resources/assets/tomewisp/lang").resolve(file);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
