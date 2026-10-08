package dev.openallay.guide.ui;

import java.util.List;
import java.util.Objects;

/** Explicit detail dismissal; reading the panel never closes it or clicks through it. */
@dev.openallay.value.ValueType(GuideUiClickRoute.ValueSchemaProvider.class)
public final class GuideUiClickRoute {
    private final Kind kind;
    private final int actionIndex;
    public GuideUiClickRoute(Kind kind, int actionIndex) {

        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.ACTION) != (actionIndex >= 0)) {
            throw new IllegalArgumentException("only action routes have an action index");
        }

        this.kind = kind;
        this.actionIndex = actionIndex;
    }
    public Kind kind() { return kind; }
    public int actionIndex() { return actionIndex; }
public enum Kind { ACTION, DISMISS_DETAIL, INSIDE_DETAIL, OUTSIDE_DETAIL }
public static GuideUiClickRoute resolveDetail(
            GuideUiLayout.Rect detail, List<GuideUiLayout.Rect> actions,
            double x, double y) {
        return resolveDetail(detail, GuideUiLayout.Rect.EMPTY, actions, x, y);
    }
public static GuideUiClickRoute resolveDetail(
            GuideUiLayout.Rect detail, GuideUiLayout.Rect close,
            List<GuideUiLayout.Rect> actions, double x, double y) {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(actions, "actions");
        if (!detail.contains(x, y)) return new GuideUiClickRoute(Kind.OUTSIDE_DETAIL, -1);
        for (int index = 0; index < actions.size(); index++) {
            if (Objects.requireNonNull(actions.get(index), "action").contains(x, y)) {
                return new GuideUiClickRoute(Kind.ACTION, index);
            }
        }
        return new GuideUiClickRoute(
                close.contains(x, y) ? Kind.DISMISS_DETAIL : Kind.INSIDE_DETAIL, -1);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiClickRoute)) return false;
        GuideUiClickRoute that = (GuideUiClickRoute) other;
        return java.util.Objects.equals(kind, that.kind) && actionIndex == that.actionIndex;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Integer.hashCode(actionIndex);
        return hash;
    }
    @Override public String toString() { return "GuideUiClickRoute[kind=" + kind + ", actionIndex=" + actionIndex + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiClickRoute> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiClickRoute.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiClickRoute>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiClickRoute.class, "kind", GuideUiClickRoute::kind), new dev.openallay.value.ValueSchema.Component<>(GuideUiClickRoute.class, "actionIndex", GuideUiClickRoute::actionIndex)), arguments -> new GuideUiClickRoute((Kind) arguments[0], (Integer) arguments[1]));
        }
    }
}
