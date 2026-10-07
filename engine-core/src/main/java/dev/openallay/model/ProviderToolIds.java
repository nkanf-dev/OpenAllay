package dev.openallay.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Request-local wire IDs. Durable history and model content keep their original identities. */
public final class ProviderToolIds {
    // OpenAI-compatible tool-call IDs must fit the endpoint's 64-character schema field.
    private static final int OPENAI_TOOL_CALL_ID_MAX_LENGTH = 64;

    private final Map<String, String> encoded;

    private ProviderToolIds(List<ModelMessage> messages, Predicate<String> providerSafe) {
        Set<String> ids = new HashSet<>();
        for (ModelMessage message : messages) {
            for (ModelContent content : message.content()) {
                if (content instanceof ModelContent.ToolUse) {
            ModelContent.ToolUse use = (ModelContent.ToolUse) content;
                    ids.add(use.id());
                } else if (content instanceof ModelContent.ToolResult) {
            ModelContent.ToolResult result = (ModelContent.ToolResult) content;
                    ids.add(result.toolUseId());
                }
            }
        }
        Map<String, String> mapping = new HashMap<>();
        Set<String> assigned = new HashSet<>();
        // Reserve all original valid IDs before generating any aliases. An alias must not
        // steal the identity of a later, already valid provider call in the same request.
        for (String id : ids) {
            if (providerSafe.test(id)) {
                mapping.put(id, id);
                assigned.add(id);
            }
        }
        for (String id : dev.openallay.util.Java8Collections.toList(ids.stream().filter(value -> !providerSafe.test(value)).sorted())) {
            String base = digestId(id);
            String candidate = base;
            long suffix = 1;
            while (!assigned.add(candidate)) {
                // The base is 48 characters. A base-36 unsigned long suffix adds at most
                // 14 characters, so even collision aliases fit the OpenAI schema field.
                candidate = base + "_" + Long.toUnsignedString(suffix++, 36);
            }
            mapping.put(id, candidate);
        }
        encoded = dev.openallay.util.Java8Collections.mapCopyOf(mapping);
    }

    public static ProviderToolIds forOpenAiChat(List<ModelMessage> messages) {
        return new ProviderToolIds(messages, id -> id.length() <= OPENAI_TOOL_CALL_ID_MAX_LENGTH
                && hasProviderAlphabet(id));
    }

    public static ProviderToolIds forAnthropicMessages(List<ModelMessage> messages) {
        // Anthropic's tool-use ID pattern excludes the ':' used by durable qualification.
        // Do not impose OpenAI's length limit on this protocol.
        return new ProviderToolIds(messages, ProviderToolIds::hasProviderAlphabet);
    }

    public String encode(String internalId) {
        String result = encoded.get(internalId);
        if (result == null) {
            throw new IllegalArgumentException("Tool ID is not part of this model request");
        }
        return result;
    }

    private static boolean hasProviderAlphabet(String id) {
        if (id.isEmpty()) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char value = id.charAt(index);
            if (!((value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '_' || value == '-')) {
                return false;
            }
        }
        return true;
    }

    private static String digestId(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(StandardCharsets.UTF_8));
            return "call_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
