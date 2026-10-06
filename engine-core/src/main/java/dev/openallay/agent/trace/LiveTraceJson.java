package dev.openallay.agent.trace;

import com.google.gson.Gson;

/** Serializes the recorded Agent trace without rewriting player or tool data. */
public final class LiveTraceJson {
    private final Gson gson = dev.openallay.json.EngineJson.create(builder -> builder.serializeNulls().setPrettyPrinting());

    public String encode(LiveAgentTrace trace) {
        return gson.toJson(trace);
    }
}
