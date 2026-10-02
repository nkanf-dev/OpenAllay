package dev.openallay.client.gui;

import java.util.Objects;

/** Local presentation feedback. Input rejection stays next to the composer, not the model status. */
public record GuideUiNotice(Severity severity, Placement placement, String message) {
    public GuideUiNotice {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(message, "message");
    }
    public static GuideUiNotice info(String message) { return new GuideUiNotice(Severity.INFO, Placement.COMPOSER, message); }
    public static GuideUiNotice success(String message) { return new GuideUiNotice(Severity.SUCCESS, Placement.COMPOSER, message); }
    public static GuideUiNotice warning(String message) { return new GuideUiNotice(Severity.WARNING, Placement.COMPOSER, message); }
    public static GuideUiNotice error(String message) { return new GuideUiNotice(Severity.ERROR, Placement.COMPOSER, message); }
    public boolean empty() { return message.isBlank(); }
    public int color() { return switch (severity) {
        case SUCCESS -> OpenAllayWidgetTheme.SUCCESS;
        case INFO -> OpenAllayWidgetTheme.INFO;
        case WARNING -> OpenAllayWidgetTheme.WARNING;
        case ERROR -> OpenAllayWidgetTheme.ERROR;
    }; }
    public enum Severity { INFO, SUCCESS, WARNING, ERROR }
    public enum Placement { HEADER, COMPOSER }
}
