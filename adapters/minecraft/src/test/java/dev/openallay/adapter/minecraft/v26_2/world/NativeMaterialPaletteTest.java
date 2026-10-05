package dev.openallay.adapter.minecraft.v26_2.world;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Exact preset material facts, not full native validation of every generated preset write. */
final class NativeMaterialPaletteTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void all47ExactPresetRolesDecodeAndRetainEveryRequestedProperty() throws Exception {
        try (InputStream stream=NativeBlockCodec.class.getResourceAsStream("expected-material-palette-inputs.json")) {
            assertNotNull(stream,"The independent exact 47-role expectation fixture must be packaged");
            JsonObject inputs=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject palette=NativeBlockCodec.materialPalette();
            assertEquals(47,inputs.size(),"Exact current preset role inventory");
            assertEquals(inputs.keySet(),palette.keySet(),"No omitted or invented role");
            List<Executable> checks=new ArrayList<>();
            for (Map.Entry<String,JsonElement> entry:inputs.entrySet()) {
                checks.add(() -> {
                    String role=entry.getKey();
                    JsonObject requested=entry.getValue().getAsJsonObject();
                    JsonObject actual=palette.getAsJsonObject(role);
                    assertEquals(requested.get("id"),actual.get("id"),role);
                    assertFalse(actual.has("blockEntity"),role+" palette must not invent container content");
                    var decoded=assertDoesNotThrow(() -> NativeBlockCodec.decode(actual.toString()),role);
                    assertEquals(actual.toString(),NativeBlockCodec.stateJson(decoded),role+" canonical defaults");
                    if (requested.has("properties")) {
                        for (Map.Entry<String,JsonElement> property:requested.getAsJsonObject("properties").entrySet())
                            assertEquals(property.getValue(),actual.getAsJsonObject("properties").get(property.getKey()),
                                    role+" requested property "+property.getKey());
                    }
                });
            }
            assertAll("All exact preset roles must be actual native states with requested properties",checks);
        } finally {NativeBlockCodec.releasePalette();}
    }
}
