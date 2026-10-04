package dev.openallay.context.minecraft;

import com.google.gson.JsonElement;
import java.util.Map;

/**
 * Trusted Extension hook for public registry facts that have no data-component or registry codec.
 * It runs only during owning-thread capture; returned JSON is detached immediately.
 * resourceId is the canonical namespace:path string, not a native Minecraft ID object.
 */
public interface RegistryPropertyContributor {
    String id();

    Map<String, JsonElement> capture(String kind, String resourceId, Object registryValue);
}
