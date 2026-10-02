package dev.openallay.guide.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** View-local fold choices. Nothing is saved to settings, history, or another owner. */
public final class GuideToolStepFlowState {
    private Object owner;
    private boolean defaultCollapsed;
    private final Map<String, Boolean> choices = new HashMap<>();

    public boolean synchronize(Object nextOwner, List<GuideUiRow.Tool> tools, boolean collapsedDefault) {
        Objects.requireNonNull(nextOwner, "owner");
        boolean changedOwner = !Objects.equals(owner, nextOwner);
        if (changedOwner || defaultCollapsed != collapsedDefault) choices.clear();
        owner = nextOwner;
        defaultCollapsed = collapsedDefault;
        HashSet<String> retained = new HashSet<>();
        for (GuideUiRow.Tool tool : tools) retained.add(id(tool));
        choices.keySet().retainAll(retained);
        return changedOwner;
    }

    public void resetChoices() { choices.clear(); }

    public boolean expanded(GuideUiRow.Tool tool) {
        return !choices.getOrDefault(id(tool), defaultCollapsed);
    }

    public void toggle(GuideUiRow.Tool tool) { choices.put(id(tool), expanded(tool)); }

    /** Batch changes only the steps that exist now. A new call still uses the configured default. */
    public void toggleTask(List<GuideUiRow.Tool> tools) {
        boolean collapse = tools.stream().anyMatch(this::expanded);
        for (GuideUiRow.Tool tool : tools) choices.put(id(tool), collapse);
    }

    public static String id(GuideUiRow.Tool tool) {
        return "tool:" + tool.requestId() + ":" + tool.activity().invocationId();
    }
}
