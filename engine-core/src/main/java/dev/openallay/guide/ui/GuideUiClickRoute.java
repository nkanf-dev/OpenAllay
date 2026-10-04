package dev.openallay.guide.ui;

import java.util.List;
import java.util.Objects;

/** Explicit detail dismissal; reading the panel never closes it or clicks through it. */
public record GuideUiClickRoute(Kind kind, int actionIndex) {
    public enum Kind { ACTION, DISMISS_DETAIL, INSIDE_DETAIL, OUTSIDE_DETAIL }

    public GuideUiClickRoute {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.ACTION) != (actionIndex >= 0)) {
            throw new IllegalArgumentException("only action routes have an action index");
        }
    }

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
}
