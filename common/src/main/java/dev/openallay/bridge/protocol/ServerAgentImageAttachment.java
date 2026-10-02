package dev.openallay.bridge.protocol;

import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

/** Encoded bytes exist only at the player-bound request transport boundary. */
public record ServerAgentImageAttachment(ImageReference reference, String base64Data) {
    public ServerAgentImageAttachment {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(base64Data, "base64Data");
        try {
            ImageInputLimits.defaults().validate(reference);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid wire image limits", invalid);
        }
        long encodedLength = 4 * ((reference.byteSize() + 2) / 3);
        if (base64Data.length() != encodedLength) {
            throw new IllegalArgumentException("Image base64 length does not match metadata");
        }
        int padding = (int) ((3 - reference.byteSize() % 3) % 3);
        int dataEnd = base64Data.length() - padding;
        for (int index = 0; index < base64Data.length(); index++) {
            char value = base64Data.charAt(index);
            boolean valid = index >= dataEnd ? value == '='
                    : value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
                            || value >= '0' && value <= '9' || value == '+' || value == '/';
            if (!valid) throw new IllegalArgumentException("Image data must be canonical base64");
        }
        if (padding > 0) {
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
            int last = alphabet.indexOf(base64Data.charAt(dataEnd - 1));
            int unusedMask = padding == 2 ? 15 : 3;
            if ((last & unusedMask) != 0) {
                throw new IllegalArgumentException("Image data must be canonical base64");
            }
        }
    }

    public static ServerAgentImageAttachment from(ImageReference reference, byte[] bytes) {
        return new ServerAgentImageAttachment(reference, Base64.getEncoder().encodeToString(bytes));
    }

    /** Heavy byte/hash validation is performed only in the image preparation worker. */
    public byte[] bytes() {
        byte[] bytes = Base64.getDecoder().decode(base64Data);
        if (bytes.length != reference.byteSize()
                || !ResultChunker.sha256(bytes).equals(reference.sha256())) {
            throw new IllegalArgumentException("Image bytes do not match reference metadata");
        }
        return bytes;
    }
}
