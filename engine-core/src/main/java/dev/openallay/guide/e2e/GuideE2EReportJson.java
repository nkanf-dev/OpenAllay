package dev.openallay.guide.e2e;

import com.google.gson.Gson;
import dev.openallay.json.EngineJson;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.Objects;

public final class GuideE2EReportJson {
    private final Gson gson;

    public GuideE2EReportJson(Gson gson) {
        this.gson = EngineJson.withInstant(Objects.requireNonNull(gson, "gson"));
    }

    public String encode(GuideE2EReport report) {
        return gson.toJson(new ToolResultNormalizer(gson)
                .canonicalize(gson.toJsonTree(report)));
    }
}
