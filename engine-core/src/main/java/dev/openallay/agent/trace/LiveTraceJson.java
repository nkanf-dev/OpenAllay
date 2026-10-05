package dev.openallay.agent.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.openallay.json.EngineJson;

/** Serializes the recorded Agent trace without rewriting player or tool data. */
public final class LiveTraceJson {
    private final Gson gson = EngineJson.withInstant(new GsonBuilder().serializeNulls().setPrettyPrinting().create());

    public String encode(LiveAgentTrace trace) {
        return gson.toJson(trace);
    }
}
