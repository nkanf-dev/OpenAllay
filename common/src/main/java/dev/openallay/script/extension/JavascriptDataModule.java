package dev.openallay.script.extension;

import com.google.gson.JsonElement;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import java.util.List;

/**
 * Trusted Java-side projector for optional extension data already detached into a request context.
 *
 * <p>This method runs on the Agent worker and therefore must only inspect immutable values from
 * {@link ToolInvocationContext}. It must not call Minecraft or mod APIs, use reflection to reach
 * live objects, or retain thread-owned state. Loader integrations that need live state capture and
 * detach it on the owning Minecraft thread before constructing the request context. The model and
 * Rhino receive only the returned JSON, never Java objects, classes, or reflection authority.
 */
public interface JavascriptDataModule {
    String id();

    Snapshot capture(ToolInvocationContext context);

    record Snapshot(JsonElement value, List<EvidenceMetadata> evidence) {
        public Snapshot {
            value = java.util.Objects.requireNonNull(value, "value").deepCopy();
            evidence = List.copyOf(evidence);
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException("JavaScript module snapshot requires evidence");
            }
        }

        @Override
        public JsonElement value() {
            return value.deepCopy();
        }
    }
}
