package dev.openallay.agent.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;
import java.util.regex.Pattern;

/** One redaction boundary for traces and durable model messages. */
public final class LiveTraceJson {
    private static final String SECRET_NAMES =
            "authorization|x-api-key|api[-_ ]?key|(?:access|refresh|id)[-_ ]?token|token|secret|password|cookie|set-cookie";
    private static final Pattern HEADER_SECRET = Pattern.compile(
            "(?i)(?<![a-z0-9_])((?:authorization|cookie|set-cookie)[\\t ]*:[\\t ]*)(?:\"[^\"]*\"|\\x27[^\\x27]*\\x27|[^\\r\\n\"\\x27{}]+)");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)([\"']?(?:" + SECRET_NAMES + ")[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|(?:basic|bearer)\\s+[^\\s;,}\\)\\]]+|\\[REDACTED\\]|[^\\s;,}\\)\\]]+)");
    private static final Pattern BEARER_SECRET = Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._~+/=-]{12,}");
    private static final Pattern PREFIXED_SECRET = Pattern.compile("(?i)\\b(?:sk|pk)-[a-z0-9_-]{12,}\\b");
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@]+@");
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(?:" + SECRET_NAMES + "|authorizationHeader|cookieHeader|setCookieHeader)");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** Immutable matching plan. Sort credentials once, not once per result leaf. */
    public static final class SecretPlan {
        private final java.util.List<String> ordered;

        public SecretPlan(Set<String> secrets) {
            ordered = secrets.stream().filter(value -> value != null && !value.isBlank())
                    .sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList();
        }
    }

    public String encode(LiveAgentTrace trace, Set<String> secrets) {
        return encode(trace, new SecretPlan(secrets));
    }

    public String encode(LiveAgentTrace trace, SecretPlan plan) {
        return gson.toJson(redact(gson.toJsonTree(trace), plan));
    }

    public static String redact(String text, Set<String> secrets) {
        return redact(text, new SecretPlan(secrets));
    }

    public static String redact(String text, SecretPlan plan) {
        String safe = text;
        for (String secret : plan.ordered) safe = safe.replace(secret, "[REDACTED]");
        safe = HEADER_SECRET.matcher(safe).replaceAll(LiveTraceJson::maskedAssignment);
        safe = URL_USERINFO.matcher(safe).replaceAll("$1[REDACTED]@");
        safe = NAMED_SECRET.matcher(safe).replaceAll(LiveTraceJson::maskedAssignment);
        safe = BEARER_SECRET.matcher(safe).replaceAll("Bearer [REDACTED]");
        return PREFIXED_SECRET.matcher(safe).replaceAll("[REDACTED]");
    }

    /** Preserve call/result pairing without merging two distinct secret-bearing invocation IDs. */
    public static String redactIdentity(String identity, Set<String> secrets) {
        return redactIdentity(identity, new SecretPlan(secrets));
    }

    public static String redactIdentity(String identity, SecretPlan plan) {
        String safe = redact(identity, plan);
        if (safe.equals(identity)) return identity;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "redacted_" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String maskedAssignment(java.util.regex.MatchResult match) {
        String prefix = match.group(1);
        String value = match.group().substring(prefix.length());
        String quote = value.startsWith("\"") ? "\"" : value.startsWith("'") ? "'" : "";
        return java.util.regex.Matcher.quoteReplacement(prefix + quote + "[REDACTED]" + quote);
    }

    public static JsonElement redact(JsonElement value, Set<String> secrets) {
        return redact(value, new SecretPlan(secrets));
    }

    /** No mutation. Unchanged subtrees keep identity; callers own any required defensive copy. */
    public static JsonElement redact(JsonElement value, SecretPlan plan) {
        if (value == null || value.isJsonNull()) return com.google.gson.JsonNull.INSTANCE;
        if (value.isJsonPrimitive()) {
            if (!value.getAsJsonPrimitive().isString()) return value;
            String original = value.getAsString();
            String safe = redact(original, plan);
            return safe.equals(original) ? value : new com.google.gson.JsonPrimitive(safe);
        }
        if (value.isJsonArray()) {
            com.google.gson.JsonArray safe = new com.google.gson.JsonArray();
            boolean changed = false;
            for (JsonElement item : value.getAsJsonArray()) {
                JsonElement replacement = redact(item, plan);
                changed |= replacement != item;
                safe.add(replacement);
            }
            return changed ? safe : value;
        }
        JsonObject safe = new JsonObject();
        JsonObject original = value.getAsJsonObject();
        java.util.Map<String, Integer> nextSuffix = new java.util.HashMap<>();
        boolean changed = false;
        for (var entry : original.entrySet()) {
            String key = redact(entry.getKey(), plan);
            if (!key.equals(entry.getKey())) {
                String base = key;
                int suffix = nextSuffix.getOrDefault(base, 2);
                while (safe.has(key) || original.has(key)) key = base + "_" + suffix++;
                nextSuffix.put(base, suffix);
            }
            JsonElement replacement = SECRET_KEY.matcher(entry.getKey()).matches()
                    ? new com.google.gson.JsonPrimitive("[REDACTED]") : redact(entry.getValue(), plan);
            changed |= !key.equals(entry.getKey()) || !replacement.equals(entry.getValue());
            safe.add(key, replacement);
        }
        return changed ? safe : value;
    }
}
