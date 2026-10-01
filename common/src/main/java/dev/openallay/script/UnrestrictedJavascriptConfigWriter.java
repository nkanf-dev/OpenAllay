package dev.openallay.script;
public final class UnrestrictedJavascriptConfigWriter {
    public String encode(UnrestrictedJavascriptConfig config) {
        return "{\n  \"enabled\": " + config.enabled() + "\n}\n";
    }
}
