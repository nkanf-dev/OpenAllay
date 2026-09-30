package dev.openallay.script;
public final class UnrestrictedJavascriptConfigWriter {
    public String encode(UnrestrictedJavascriptConfig config) {
        return "{\n  \"schemaVersion\": " + config.schemaVersion() + ",\n  \"enabled\": " + config.enabled() + "\n}\n";
    }
}
