package dev.openallay.guide.e2e;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideNativeCommandE2EProbeTest {
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String TOKEN = "openallay_native_command_unique";
    private static final String ERROR = "Expected native localized help error";

    @Test
    void requiresActualHelpSignedTokenAndDistinctInvalidPathFeedbackFromTheCapturedActor() {
        assertDoesNotThrow(() -> GuideNativeCommandE2EProbe.requireResults(valid(), ACTOR, TOKEN));
        JsonObject wrongActor = valid();
        wrongActor.getAsJsonObject("signed").addProperty("actorId", UUID.randomUUID().toString());
        assertThrows(IllegalStateException.class,
                () -> GuideNativeCommandE2EProbe.requireResults(wrongActor, ACTOR, TOKEN));
        JsonObject wrongToken = valid();
        JsonArray unrelated = new JsonArray(); unrelated.add("unrelated player chat");
        wrongToken.getAsJsonObject("signed").add("messages", unrelated);
        assertThrows(IllegalStateException.class,
                () -> GuideNativeCommandE2EProbe.requireResults(wrongToken, ACTOR, TOKEN));
        JsonObject unrelatedError = valid();
        unrelatedError.getAsJsonObject("error").add("messages", valid().getAsJsonObject("help").get("messages"));
        assertThrows(IllegalStateException.class,
                () -> GuideNativeCommandE2EProbe.requireResults(unrelatedError, ACTOR, TOKEN));
    }

    @Test
    void packetSubmissionAloneAndNonSequentialResultsCannotPassNativeAcceptance() {
        JsonObject noFeedback = valid();
        noFeedback.getAsJsonObject("signed").addProperty("state", "no_feedback");
        noFeedback.getAsJsonObject("signed").addProperty("feedbackObserved", false);
        noFeedback.getAsJsonObject("signed").add("messages", new JsonArray());
        assertThrows(IllegalStateException.class,
                () -> GuideNativeCommandE2EProbe.requireResults(noFeedback, ACTOR, TOKEN));
        JsonObject reordered = valid();
        reordered.getAsJsonObject("error").addProperty("sequence", 8);
        assertThrows(IllegalStateException.class,
                () -> GuideNativeCommandE2EProbe.requireResults(reordered, ACTOR, TOKEN));
    }

    private static JsonObject valid() {
        JsonObject receipt = new JsonObject();
        receipt.addProperty("helpNode", "help");
        receipt.addProperty("messageNode", "me <action>");
        receipt.add("help", result(1, "help me", "/me <action>"));
        receipt.add("signed", result(2, "me " + TOKEN, "* Player " + TOKEN));
        receipt.add("error", result(3, "help " + TOKEN + "_missing", ERROR));
        return receipt;
    }

    private static JsonObject result(long sequence, String command, String message) {
        JsonObject value = new JsonObject();
        value.addProperty("actorId", ACTOR.toString());
        value.addProperty("command", command);
        value.addProperty("sequence", sequence);
        value.addProperty("state", "feedback");
        value.addProperty("feedbackObserved", true);
        JsonArray messages = new JsonArray(); messages.add(message);
        value.add("messages", messages);
        return value;
    }
}
