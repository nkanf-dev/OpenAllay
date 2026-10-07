package dev.openallay.script;

import dev.openallay.settings.AtomicSettingsFile;
import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;

/** Atomic strict persistence for the local unrestricted runtime toggle. */
public final class UnrestrictedJavascriptConfigStore {
    private final Path path;
    private final AtomicSettingsFile files = new AtomicSettingsFile();
    private final UnrestrictedJavascriptConfigLoader loader = new UnrestrictedJavascriptConfigLoader();
    private final UnrestrictedJavascriptConfigWriter writer = new UnrestrictedJavascriptConfigWriter();
    public UnrestrictedJavascriptConfigStore(Path path) { this.path = path.toAbsolutePath().normalize(); }
    public synchronized ToolResult<UnrestrictedJavascriptConfig> reload() { return loader.load(path); }
    public synchronized ToolResult<UnrestrictedJavascriptConfig> save(UnrestrictedJavascriptConfig candidate) {
        try {
            String encoded = writer.encode(candidate);
            dev.openallay.tool.ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> decoded = loader.load(new StringReader(encoded));
            if (decoded instanceof ToolResult.Failure<UnrestrictedJavascriptConfig>) return decoded;
            files.replace(path, encoded);
            return decoded;
        } catch (SettingsWriteException e) { return new ToolResult.Failure<>(e.code(), e.getMessage()); }
        catch (RuntimeException e) { return new ToolResult.Failure<>("settings_write_failed", "Unable to save JavaScript settings"); }
    }
}
