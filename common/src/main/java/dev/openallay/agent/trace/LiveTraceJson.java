package dev.openallay.agent.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Serializes the recorded Agent trace without rewriting player or tool data. */
public final class LiveTraceJson {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public String encode(LiveAgentTrace trace) {
        return gson.toJson(trace);
    }
}
