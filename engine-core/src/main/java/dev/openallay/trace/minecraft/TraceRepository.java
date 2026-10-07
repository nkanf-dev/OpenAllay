package dev.openallay.trace.minecraft;

import dev.openallay.tool.ToolResult;
import dev.openallay.trace.json.TraceParser;
import dev.openallay.trace.model.AgentTrace;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class TraceRepository {
    @FunctionalInterface
    public interface ReaderOpener {
        Reader open() throws IOException;
    }

    @dev.openallay.value.ValueType(TraceSource.ValueSchemaProvider.class)
public static final class TraceSource {
    private final String name;
    private final ReaderOpener opener;
    public TraceSource(String name, ReaderOpener opener) {

            if (name == null || dev.openallay.util.Java8Strings.isBlank(name)) {
                throw new IllegalArgumentException("Trace source name must not be blank");
            }
            Objects.requireNonNull(opener, "opener");

        this.name = name;
        this.opener = opener;
    }
    public String name() { return name; }
    public ReaderOpener opener() { return opener; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TraceSource)) return false;
        TraceSource that = (TraceSource) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(opener, that.opener);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(opener);
        return hash;
    }
    @Override public String toString() { return "TraceSource[name=" + name + ", opener=" + opener + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TraceSource> schema() {
            return new dev.openallay.value.ValueSchema<>(TraceSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TraceSource>>asList(new dev.openallay.value.ValueSchema.Component<>(TraceSource.class, "name", TraceSource::name), new dev.openallay.value.ValueSchema.Component<>(TraceSource.class, "opener", TraceSource::opener)), arguments -> new TraceSource((String) arguments[0], (ReaderOpener) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(LoadedTraces.ValueSchemaProvider.class)
public static final class LoadedTraces {
    private final Map<String, AgentTrace> traces;
    public LoadedTraces(Map<String, AgentTrace> traces) {

            traces = dev.openallay.util.Java8Collections.mapCopyOf(new TreeMap<>(traces));

        this.traces = traces;
    }
    public Map<String, AgentTrace> traces() { return traces; }
public List<String> ids() {
            return dev.openallay.util.Java8Collections.toList(traces.keySet().stream().sorted());
        }
public Optional<AgentTrace> find(String id) {
            return Optional.ofNullable(traces.get(id));
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LoadedTraces)) return false;
        LoadedTraces that = (LoadedTraces) other;
        return java.util.Objects.equals(traces, that.traces);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(traces);
        return hash;
    }
    @Override public String toString() { return "LoadedTraces[traces=" + traces + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<LoadedTraces> schema() {
            return new dev.openallay.value.ValueSchema<>(LoadedTraces.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<LoadedTraces>>asList(new dev.openallay.value.ValueSchema.Component<>(LoadedTraces.class, "traces", LoadedTraces::traces)), arguments -> new LoadedTraces((Map) arguments[0]));
        }
    }
}

    private final TraceParser parser;

    public TraceRepository(TraceParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public ToolResult<LoadedTraces> load(Collection<TraceSource> sources) {
        TreeMap<String, AgentTrace> traces = new TreeMap<>();
        for (TraceSource source : dev.openallay.util.Java8Collections.toList(sources.stream()
                .sorted(java.util.Comparator.comparing(TraceSource::name)))) {
            String filenameId;
            try {
                filenameId = filenameId(source.name());
            } catch (IllegalArgumentException exception) {
                return new ToolResult.Failure<>("invalid_trace", exception.getMessage());
            }

            ToolResult<AgentTrace> parsed;
            try (Reader reader = source.opener().open()) {
                parsed = parser.parse(reader);
            } catch (IOException exception) {
                return new ToolResult.Failure<>(
                        "invalid_trace", "Unable to read " + source.name() + ": " + exception.getMessage());
            }
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.trace.model.AgentTrace> value; ToolResult.Failure<AgentTrace> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = parsed) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<AgentTrace>) $oaPattern0_holder.value) != null))) {
                return new ToolResult.Failure<>(
                        "invalid_trace", source.name() + ": " + $oaPattern0_holder.bound.message());
            }

            AgentTrace trace = ((ToolResult.Success<AgentTrace>) parsed).value();
            if (!trace.id().equals(filenameId)) {
                return new ToolResult.Failure<>(
                        "invalid_trace",
                        source.name() + " declares id " + trace.id() + " but filename requires " + filenameId);
            }
            AgentTrace previous = traces.putIfAbsent(trace.id(), trace);
            if (previous != null) {
                return new ToolResult.Failure<>(
                        "invalid_trace", "Duplicate trace id from multiple resources: " + trace.id());
            }
        }
        return new ToolResult.Success<>(new LoadedTraces(traces));
    }

    private static String filenameId(String source) {
        int slash = source.lastIndexOf('/');
        String filename = source.substring(slash + 1);
        if (!filename.endsWith(".json") || filename.length() == ".json".length()) {
            throw new IllegalArgumentException("Trace resource must end in a named .json file: " + source);
        }
        return filename.substring(0, filename.length() - ".json".length());
    }
}
