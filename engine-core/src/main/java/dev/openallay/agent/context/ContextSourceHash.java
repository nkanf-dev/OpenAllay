package dev.openallay.agent.context;

import com.google.gson.Gson;
import dev.openallay.model.ModelMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import dev.openallay.util.Java8Hex;
import java.util.List;

/** Stable hash for provider-neutral, reasoning-free checkpoint source messages. */
public final class ContextSourceHash {
    private ContextSourceHash() {}

    public static String compute(Gson gson, List<ModelMessage> messages) {
        try {
            byte[] bytes = gson.toJson(ContextStructure.summarySafe(messages))
                    .getBytes(StandardCharsets.UTF_8);
            return Java8Hex.formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
