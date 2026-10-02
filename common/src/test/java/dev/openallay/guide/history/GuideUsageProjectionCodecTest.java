package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.openallay.guide.GuideUsageSnapshot;
import dev.openallay.model.ModelUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

final class GuideUsageProjectionCodecTest {
    private final GuideHistoryCodec codec = new GuideHistoryCodec();

    @Test
    void exactCurrentUsageAndQuoteRoundTripPreservesMissingPresenceAndDecimal() {
        var usage = ModelUsage.anthropic(100, true, 5, true, 50, true, 0, false);
        assertEquals(usage, codec.decodeModelUsage(codec.encodeModelUsage(usage)));
        var snapshot = new GuideUsageSnapshot(150, 5, 50, 0, 2, 1, true, true,
                new BigDecimal("0.000123456789"), true);
        assertEquals(snapshot, codec.decodeUsageProjection(codec.encodeUsageProjection(snapshot)));
        assertEquals(GuideUsageSnapshot.empty(), codec.decodeUsageProjection(codec.encodeUsageProjection(GuideUsageSnapshot.empty())));
        assertEquals(GuideUsageSnapshot.unknown(), codec.decodeUsageProjection(codec.encodeUsageProjection(GuideUsageSnapshot.unknown())));
    }

    @Test
    void missingExtraFractionalAndWrongTypedFieldsAreRejectedNotMigrated() {
        var encoded = JsonParser.parseString(codec.encodeModelUsage(new ModelUsage(10, 3, 0))).getAsJsonObject();
        encoded.remove("cacheReadKnown");
        assertThrows(IllegalArgumentException.class, () -> codec.decodeModelUsage(encoded.toString()));
        var extra = JsonParser.parseString(codec.encodeUsageProjection(GuideUsageSnapshot.empty())).getAsJsonObject();
        extra.addProperty("version", 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUsageProjection(extra.toString()));
        var fractional = JsonParser.parseString(codec.encodeUsageProjection(GuideUsageSnapshot.empty())).getAsJsonObject();
        fractional.addProperty("actualCalls", 0.5);
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUsageProjection(fractional.toString()));
        var wrong = JsonParser.parseString(codec.encodeUsageProjection(GuideUsageSnapshot.empty())).getAsJsonObject();
        wrong.addProperty("costIncomplete", "true");
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUsageProjection(wrong.toString()));
    }
}
