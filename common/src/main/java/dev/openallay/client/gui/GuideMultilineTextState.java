package dev.openallay.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Product text primitive for native families without a multiline widget. No screen decisions. */
public final class GuideMultilineTextState {
    @dev.openallay.value.ValueType(Line.ValueSchemaProvider.class)
public static final class Line {
    private final int start;
    private final int end;
    public Line(int start, int end) {
        this.start = start;
        this.end = end;
    }
    public int start() { return start; }
    public int end() { return end; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Line)) return false;
        Line that = (Line) other;
        return start == that.start && end == that.end;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(start);
        hash = 31 * hash + Integer.hashCode(end);
        return hash;
    }
    @Override public String toString() { return "Line[start=" + start + ", end=" + end + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Line> schema() {
            return new dev.openallay.value.ValueSchema<>(Line.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Line>>asList(new dev.openallay.value.ValueSchema.Component<>(Line.class, "start", Line::start), new dev.openallay.value.ValueSchema.Component<>(Line.class, "end", Line::end)), arguments -> new Line((Integer) arguments[0], (Integer) arguments[1]));
        }
    }
}
    @dev.openallay.value.ValueType(Snapshot.ValueSchemaProvider.class)
private static final class Snapshot {
    private final String value;
    private final int cursor;
    private final int anchor;
    private Snapshot(String value, int cursor, int anchor) {
        this.value = value;
        this.cursor = cursor;
        this.anchor = anchor;
    }
    public String value() { return value; }
    public int cursor() { return cursor; }
    public int anchor() { return anchor; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Snapshot)) return false;
        Snapshot that = (Snapshot) other;
        return java.util.Objects.equals(value, that.value) && cursor == that.cursor && anchor == that.anchor;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + Integer.hashCode(cursor);
        hash = 31 * hash + Integer.hashCode(anchor);
        return hash;
    }
    @Override public String toString() { return "Snapshot[value=" + value + ", cursor=" + cursor + ", anchor=" + anchor + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Snapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(Snapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Snapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "value", Snapshot::value), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "cursor", Snapshot::cursor), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "anchor", Snapshot::anchor)), arguments -> new Snapshot((String) arguments[0], (Integer) arguments[1], (Integer) arguments[2]));
        }
    }
}
    private String value = "";
    private int cursor;
    private int anchor;
    private int limit = Integer.MAX_VALUE;
    private int desiredX = -1;
    private Consumer<String> listener = ignored -> {};
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    private List<Line> lines = dev.openallay.util.Java8Collections.listOf(new Line(0, 0));
    private ToIntFunction<String> measure = String::length;
    private int wrapWidth = Integer.MAX_VALUE;

    public String value() { return value; }
    public int cursor() { return cursor; }
    public int selectionStart() { return Math.min(cursor, anchor); }
    public int selectionEnd() { return Math.max(cursor, anchor); }
    public String selected() { return value.substring(selectionStart(), selectionEnd()); }
    public List<Line> lines() { return lines; }
    public void listener(Consumer<String> listener) { this.listener = Objects.requireNonNull(listener); }
    public void characterLimit(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative character limit");
        if (this.limit == limit) return;
        this.limit = limit;
        undo.clear();
        redo.clear();
        if (value.length() > limit) setValue(value);
    }
    public void setValue(String next) {
        next = truncate(Objects.requireNonNull(next), limit);
        if (next.equals(value)) return;
        value = next;
        cursor = value.length();
        anchor = cursor;
        desiredX = -1;
        undo.clear();
        redo.clear();
        reflow();
        listener.accept(value);
    }
    public void insert(String text) {
        text = truncate(normalize(Objects.requireNonNull(text)), limit - value.length() + selectionEnd() - selectionStart());
        replace(selectionStart(), selectionEnd(), text);
    }
    private void replace(int start, int end, String text) {
        String next = value.substring(0, start) + text + value.substring(end);
        if (next.equals(value)) return;
        remember(undo);
        redo.clear();
        value = next;
        cursor = start + text.length();
        anchor = cursor;
        desiredX = -1;
        reflow();
        listener.accept(value);
    }
    public void delete(int direction, boolean word) {
        if (anchor != cursor) { replace(selectionStart(), selectionEnd(), ""); return; }
        int end = word ? wordPosition(direction) : step(cursor, direction);
        replace(Math.min(cursor, end), Math.max(cursor, end), "");
    }
    public void moveHorizontal(int direction, boolean selecting, boolean word) {
        if (!selecting && anchor != cursor && !word) {
            seek(direction < 0 ? selectionStart() : selectionEnd(), false);
        } else seek(word ? wordPosition(direction) : step(cursor, direction), selecting);
    }
    public void seek(int index, boolean selecting) {
        cursor = boundary(Math.max(0, Math.min(value.length(), index)));
        if (!selecting) anchor = cursor;
        desiredX = -1;
    }
    public void selectAll() { anchor = 0; cursor = value.length(); desiredX = -1; }
    public void home(boolean selecting, boolean wholeText) {
        seek(wholeText ? 0 : lines.get(cursorLine()).start(), selecting);
    }
    public void end(boolean selecting, boolean wholeText) {
        seek(wholeText ? value.length() : lines.get(cursorLine()).end(), selecting);
    }
    public void moveVertical(int amount, boolean selecting) {
        int line = cursorLine();
        if (desiredX < 0) desiredX = measure.applyAsInt(value.substring(lines.get(line).start(), cursor));
        int wantedX = desiredX;
        seek(indexAt(Math.max(0, Math.min(lines.size() - 1, line + amount)), wantedX), selecting);
        desiredX = wantedX;
    }
    public int cursorLine() {
        for (int i = 0; i < lines.size() - 1; i++) {
            Line line = lines.get(i);
            if (cursor <= line.end() && cursor < lines.get(i + 1).start()) return i;
            if (cursor < line.end()) return i;
        }
        return lines.size() - 1;
    }
    public int indexAt(int lineNumber, int x) {
        Line line = lines.get(Math.max(0, Math.min(lines.size() - 1, lineNumber)));
        int previousWidth = 0;
        for (int index = line.start(); index < line.end();) {
            int next = step(index, 1);
            int nextWidth = measure.applyAsInt(value.substring(line.start(), next));
            if (x < (previousWidth + nextWidth) / 2.0) return index;
            previousWidth = nextWidth;
            index = next;
        }
        return line.end();
    }
    public void wrap(int width, ToIntFunction<String> measure) {
        if (width <= 0) throw new IllegalArgumentException("No readable content width");
        this.wrapWidth = width;
        this.measure = Objects.requireNonNull(measure);
        desiredX = -1;
        reflow();
    }
    private void reflow() {
        List<Line> result = new ArrayList<>();
        int start = 0;
        while (start <= value.length()) {
            int newline = value.indexOf('\n', start);
            int paragraphEnd = newline < 0 ? value.length() : newline;
            if (start == paragraphEnd) result.add(new Line(start, start));
            while (start < paragraphEnd) {
                int end = start;
                int wordBreak = -1;
                while (end < paragraphEnd) {
                    int next = step(end, 1);
                    if (measure.applyAsInt(value.substring(start, next)) > wrapWidth && end > start) break;
                    if (Character.isWhitespace(value.codePointAt(end))) wordBreak = next;
                    end = next;
                }
                if (end < paragraphEnd && wordBreak > start) end = wordBreak;
                result.add(new Line(start, end));
                start = end;
            }
            if (newline < 0) break;
            start = newline + 1;
        }
        lines = dev.openallay.util.Java8Collections.listCopyOf(result);
    }
    private int wordPosition(int direction) {
        int index = cursor;
        if (direction < 0) {
            while (index > 0 && Character.isWhitespace(value.codePointBefore(index))) index = step(index, -1);
            while (index > 0 && !Character.isWhitespace(value.codePointBefore(index))) index = step(index, -1);
        } else {
            while (index < value.length() && !Character.isWhitespace(value.codePointAt(index))) index = step(index, 1);
            while (index < value.length() && Character.isWhitespace(value.codePointAt(index))) index = step(index, 1);
        }
        return index;
    }
    private int step(int index, int direction) {
        if (direction < 0) return index == 0 ? 0 : value.offsetByCodePoints(index, -1);
        return index == value.length() ? index : value.offsetByCodePoints(index, 1);
    }
    private int boundary(int index) {
        return index > 0 && index < value.length() && Character.isLowSurrogate(value.charAt(index))
                && Character.isHighSurrogate(value.charAt(index - 1)) ? index - 1 : index;
    }
    public void undo() { restore(undo, redo); }
    public void redo() { restore(redo, undo); }
    private void remember(ArrayDeque<Snapshot> stack) {
        stack.addLast(new Snapshot(value, cursor, anchor));
        if (stack.size() > 100) stack.removeFirst();
    }
    private void restore(ArrayDeque<Snapshot> from, ArrayDeque<Snapshot> to) {
        if (from.isEmpty()) return;
        remember(to);
        Snapshot snapshot = from.removeLast();
        value = snapshot.value();
        cursor = snapshot.cursor();
        anchor = snapshot.anchor();
        desiredX = -1;
        reflow();
        listener.accept(value);
    }
    private static String truncate(String text, int length) {
        int end = Math.max(0, Math.min(length, text.length()));
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end);
    }
    private static String normalize(String text) {
        StringBuilder clean = new StringBuilder();
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text.codePoints().filter(c -> c == '\n' || c == '\t' || c >= 32 && c != 127 && c != 167)
                .forEach(c -> { if (c == '\t') clean.append("    "); else clean.appendCodePoint(c); });
        return clean.toString();
    }
}
