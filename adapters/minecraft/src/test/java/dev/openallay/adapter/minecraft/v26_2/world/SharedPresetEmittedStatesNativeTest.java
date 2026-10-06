package dev.openallay.adapter.minecraft.v26_2.world;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Cross-check the actual canonical six-generator output, not a replacement expected palette. */
final class SharedPresetEmittedStatesNativeTest {
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void sixPresetsAllFacingsAndCustomPalettesResolveAgainstActualNativeRegistry() throws Exception {
        String configured = System.getProperty("sharedPresetStates");
        JsonArray states;
        if (configured != null) {
            states = dev.openallay.json.JsonTrees.parse(Files.readString(Path.of(configured))).getAsJsonArray();
        } else {
            try (var input = SharedPresetEmittedStatesNativeTest.class.getResourceAsStream(
                    "shared-preset-emitted-states.json")) {
                assertNotNull(input, "Canonical shared generator fixture must be present");
                states = dev.openallay.json.JsonTrees.parse(new java.io.InputStreamReader(input,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            }
        }
        assertEquals(122, states.size(), "Current canonical generator state fixture changed");
        for (JsonElement state : states) {
            assertDoesNotThrow(() -> NativeBlockCodec.decode(state.toString()), state.toString());
        }
    }
}
