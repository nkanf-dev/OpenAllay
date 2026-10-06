package dev.openallay.logging;

import java.lang.System.Logger.Level;

/** Product logging for the engine's existing brace-formatted messages. */
public final class OpenAllayLogger {
    private final System.Logger logger;

    public OpenAllayLogger(String name) {
        logger = System.getLogger(name);
    }

    public void info(String message, Object... arguments) {
        log(Level.INFO, message, arguments);
    }

    public void warn(String message, Object... arguments) {
        log(Level.WARNING, message, arguments);
    }

    public void error(String message, Object... arguments) {
        log(Level.ERROR, message, arguments);
    }

    private void log(Level level, String message, Object[] arguments) {
        if (!logger.isLoggable(level)) {
            return;
        }
        int count = arguments.length;
        Throwable failure = count > 0 && arguments[count - 1] instanceof Throwable throwable
                ? throwable : null;
        if (failure != null) {
            count--;
        }
        String formatted = format(message, arguments, count);
        if (failure == null) {
            logger.log(level, formatted);
        } else {
            logger.log(level, formatted, failure);
        }
    }

    // The current call sites use literal {} and scalar/list values, with no escape DSL.
    private static String format(String message, Object[] arguments, int count) {
        if (message == null || count == 0) {
            return message;
        }
        StringBuilder formatted = new StringBuilder(message.length());
        int cursor = 0;
        for (int index = 0; index < count; index++) {
            int placeholder = message.indexOf("{}", cursor);
            if (placeholder < 0) {
                break;
            }
            formatted.append(message, cursor, placeholder);
            formatted.append(String.valueOf(arguments[index]));
            cursor = placeholder + 2;
        }
        return formatted.append(message, cursor, message.length()).toString();
    }
}
