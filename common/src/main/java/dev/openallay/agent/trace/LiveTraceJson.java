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

    public String encode(LiveAgentTrace trace, Set<String> secrets) {
        return gson.toJson(redact(gson.toJsonTree(trace), secrets));
    }

    public static String redact(String text, Set<String> secrets) {
        String safe = text;
        for (String secret : secrets.stream().filter(value -> value != null && !value.isBlank())
                .sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList()) {
            safe = safe.replace(secret, "[REDACTED]");
        }
        safe = HEADER_SECRET.matcher(safe).replaceAll(LiveTraceJson::maskedAssignment);
        safe = URL_USERINFO.matcher(safe).replaceAll("$1[REDACTED]@");
        safe = NAMED_SECRET.matcher(safe).replaceAll(LiveTraceJson::maskedAssignment);
        safe = BEARER_SECRET.matcher(safe).replaceAll("Bearer [REDACTED]");
        return PREFIXED_SECRET.matcher(safe).replaceAll("[REDACTED]");
    }

    private static String maskedAssignment(java.util.regex.MatchResult match) {
        String prefix = match.group(1);
        String value = match.group().substring(prefix.length());
        String quote = value.startsWith("\"") ? "\"" : value.startsWith("'") ? "'" : "";
        return java.util.regex.Matcher.quoteReplacement(prefix + quote + "[REDACTED]" + quote);
    }

    public static JsonElement redact(JsonElement value, Set<String> secrets) {
        if (value == null || value.isJsonNull()) return com.google.gson.JsonNull.INSTANCE;
        if (value.isJsonPrimitive()) {
            return value.getAsJsonPrimitive().isString()
                    ? new com.google.gson.JsonPrimitive(redact(value.getAsString(), secrets))
                    : value.deepCopy();
        }
        if (value.isJsonArray()) {
            com.google.gson.JsonArray safe = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(item -> safe.add(redact(item, secrets)));
            return safe;
        }
        JsonObject safe = new JsonObject();
        value.getAsJsonObject().entrySet().forEach(entry -> safe.add(entry.getKey(),
                SECRET_KEY.matcher(entry.getKey()).matches()
                        ? new com.google.gson.JsonPrimitive("[REDACTED]")
                        : redact(entry.getValue(), secrets)));
        return safe;
    }
}
