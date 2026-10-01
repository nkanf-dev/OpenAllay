package dev.openallay.script.workspace;

import com.google.gson.JsonElement;
import dev.openallay.context.SourceObservation;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentResultWorkspace implements AutoCloseable {
    private static final int MAX_RESULTS = 16;
    private static final int MAX_SELECTED_HANDLES = 4;
    private static final long MAX_RESULT_UNITS = 8L * 1024 * 1024;
    private static final long MAX_SELECTED_UNITS = 8L * 1024 * 1024;
    private static final long MAX_WORKSPACE_UNITS = 32L * 1024 * 1024;

    private final String prefix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, JsonElement> values = new LinkedHashMap<>();
    private final Map<String, JavascriptResultShape> shapes = new LinkedHashMap<>();
    private final Map<String, Long> sizes = new LinkedHashMap<>();
    private final Map<String, List<SourceObservation>> sources =
            new LinkedHashMap<>();
    private long storedUnits;
    private boolean closed;

    public synchronized String store(JsonElement value) {
        return store(
                value,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC));
    }

    public synchronized String store(JsonElement value, JavascriptResultShape shape) {
        return store(value, shape, false);
    }

    public synchronized String store(JsonElement value, JavascriptResultShape shape, boolean unrestricted) {
        return store(value, shape, unrestricted, List.of());
    }

    public synchronized String store(
            JsonElement value,
            JavascriptResultShape shape,
            boolean unrestricted,
            List<SourceObservation> sources) {
        requireOpen();
        java.util.Objects.requireNonNull(shape, "shape");
        List<SourceObservation> storedSources = List.copyOf(sources);
        long units = measure(value);
        if (!unrestricted && units > MAX_RESULT_UNITS) {
            throw new WorkspaceException(
                    "workspace_result_too_large",
                    "JavaScript result exceeds the per-result workspace budget");
        }
        if (!unrestricted && (values.size() >= MAX_RESULTS || saturatedAdd(storedUnits, units) > MAX_WORKSPACE_UNITS)) {
            throw new WorkspaceException(
                    "workspace_budget_exceeded",
                    "JavaScript request workspace is full; reuse existing handles or return a smaller result");
        }
        String handle = "r_" + prefix + "_" + sequence.incrementAndGet();
        values.put(handle, value.deepCopy());
        shapes.put(handle, shape);
        sizes.put(handle, units);
        this.sources.put(handle, storedSources);
        storedUnits += units;
        return handle;
    }

    public synchronized JsonElement open(String handle) {
        requireOpen();
        JsonElement value = values.get(handle);
        if (value == null) {
            throw new WorkspaceException(
                    "workspace_handle_unavailable",
                    "Result handle is unavailable in this request");
        }
        return value.deepCopy();
    }

    public synchronized Map<String, JsonElement> select(Collection<String> handles) {
        return select(handles, false);
    }

    public synchronized Map<String, JsonElement> select(Collection<String> handles, boolean unrestricted) {
        requireOpen();
        Collection<String> requested =
                handles == null ? List.<String>of() : handles;
        if (!unrestricted && requested.size() > MAX_SELECTED_HANDLES) {
            throw new WorkspaceException(
                    "workspace_selection_too_large",
                    "A JavaScript execution may reopen at most four result handles");
        }
        long selectedUnits = 0;
        for (String handle : requested) {
            Long units = sizes.get(handle);
            if (units == null) {
                throw new WorkspaceException(
                        "workspace_handle_unavailable",
                        "Result handle is unavailable in this request");
            }
            selectedUnits = saturatedAdd(selectedUnits, units);
            if (!unrestricted && selectedUnits > MAX_SELECTED_UNITS) {
                throw new WorkspaceException(
                        "workspace_selection_too_large",
                        "Selected result handles exceed the execution budget");
            }
        }
        LinkedHashMap<String, JsonElement> selected = new LinkedHashMap<>();
        for (String handle : requested) {
            // The Rhino adapter is read-only, so it can safely retain the workspace-owned
            // canonical tree without another deep copy or a stringify/parse cycle.
            selected.put(handle, values.get(handle));
        }
        return Map.copyOf(selected);
    }

    public synchronized List<SourceObservation> sources(String handle) {
        requireOpen();
        List<SourceObservation> value = sources.get(handle);
        if (value == null) {
            throw new WorkspaceException(
                    "workspace_handle_unavailable", "Result handle is unavailable in this request");
        }
        return value;
    }

    public synchronized Map<String, List<SourceObservation>>
            selectSources(Collection<String> handles) {
        requireOpen();
        java.util.LinkedHashMap<String, List<SourceObservation>> selected =
                new java.util.LinkedHashMap<>();
        for (String handle : handles == null ? List.<String>of() : handles) {
            List<SourceObservation> value = sources.get(handle);
            if (value == null) {
                throw new WorkspaceException(
                        "workspace_handle_unavailable", "Result handle is unavailable in this request");
            }
            selected.put(handle, value);
        }
        return Map.copyOf(selected);
    }

    public synchronized Map<String, JavascriptResultShape> selectShapes(
            Collection<String> handles) {
        requireOpen();
        Collection<String> requested =
                handles == null ? List.<String>of() : handles;
        LinkedHashMap<String, JavascriptResultShape> selected = new LinkedHashMap<>();
        for (String handle : requested) {
            JavascriptResultShape shape = shapes.get(handle);
            if (shape == null) {
                throw new WorkspaceException(
                        "workspace_handle_unavailable",
                        "Result handle is unavailable in this request");
            }
            selected.put(handle, shape);
        }
        return Map.copyOf(selected);
    }

    public synchronized int size() {
        return values.size();
    }

    @Override
    public synchronized void close() {
        closed = true;
        values.clear();
        shapes.clear();
        sizes.clear();
        sources.clear();
        storedUnits = 0;
    }

    private void requireOpen() {
        if (closed) {
            throw new WorkspaceException(
                    "workspace_closed", "Result workspace is already closed");
        }
    }

    private static long measure(JsonElement root) {
        java.util.ArrayDeque<JsonElement> pending = new java.util.ArrayDeque<>();
        pending.add(root);
        long units = 0;
        while (!pending.isEmpty()) {
            JsonElement value = pending.removeLast();
            units = saturatedAdd(units, 1);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonPrimitive()) {
                units = saturatedAdd(
                        units,
                        value.getAsJsonPrimitive().isString()
                                ? saturatedMultiply(value.getAsString().length(), 3)
                                : value.getAsString().length());
            } else if (value.isJsonArray()) {
                value.getAsJsonArray().forEach(pending::add);
            } else {
                value.getAsJsonObject().entrySet().forEach(entry -> {
                    pending.add(entry.getValue());
                });
                for (String key : value.getAsJsonObject().keySet()) {
                    units = saturatedAdd(units, saturatedMultiply(key.length(), 3));
                }
            }
        }
        return units;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }
}
