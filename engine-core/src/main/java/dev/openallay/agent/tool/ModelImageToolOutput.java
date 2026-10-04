package dev.openallay.agent.tool;

import dev.openallay.model.image.ImageReference;
import java.util.List;

/** Internal successful core-tool output channel for managed visual input, separate from JSON. */
public interface ModelImageToolOutput {
    List<ImageReference> images();
}
