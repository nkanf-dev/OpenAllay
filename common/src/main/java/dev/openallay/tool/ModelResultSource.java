package dev.openallay.tool;

import com.google.gson.JsonElement;

/** Request-lifetime capability to project external canonical data without serializing it into history. */
public interface ModelResultSource {
    JsonElement project(int maximumUtf8Bytes);

    /** A search ceiling, not a request budget. No projection is performed to obtain this hint. */
    int projectionSizeUpperBound();

    /** Size of the producer's real selected control-plane view, not canonical storage capacity. */
    default int preferredSizeUtf8Bytes() { return projectionSizeUpperBound(); }

}
