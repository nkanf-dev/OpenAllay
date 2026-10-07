package dev.openallay.guide.ui;

import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.semantic.SemanticReference;
import java.util.List;

/** Detached native-render plan; no Minecraft registry or widget objects are retained. */
@dev.openallay.value.ValueType(SemanticLayout.ValueSchemaProvider.class)
public final class SemanticLayout {
    private final int width;
    private final int height;
    private final List<Line> lines;
    private final String narration;
    public SemanticLayout(int width, int height, List<Line> lines, String narration) {

        if (width <= 0 || height < 0) throw new IllegalArgumentException("invalid semantic layout");
        lines = List.copyOf(lines);
        narration = narration == null ? "" : narration;
        if (lines.stream().mapToInt(Line::height).sum() != height) {
            throw new IllegalArgumentException("semantic layout height is inconsistent");
        }

        this.width = width;
        this.height = height;
        this.lines = lines;
        this.narration = narration;
    }
    public int width() { return width; }
    public int height() { return height; }
    public List<Line> lines() { return lines; }
    public String narration() { return narration; }
public enum Kind { TEXT, HEADING, QUOTE, CODE, TABLE, RULE, COMPONENT }
public enum Style { NORMAL, EMPHASIS, STRONG, CODE, REFERENCE }
@dev.openallay.value.ValueType(Run.ValueSchemaProvider.class)
public static final class Run {
    private final String text;
    private final Style style;
    private final SemanticReference reference;
    public Run(String text, Style style, SemanticReference reference) {

            text = text == null ? "" : text;
            java.util.Objects.requireNonNull(style, "style");
            if ((style == Style.REFERENCE) != (reference != null)) {
                throw new IllegalArgumentException("semantic run reference is inconsistent");
            }

        this.text = text;
        this.style = style;
        this.reference = reference;
    }
    public String text() { return text; }
    public Style style() { return style; }
    public SemanticReference reference() { return reference; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Run)) return false;
        Run that = (Run) other;
        return java.util.Objects.equals(text, that.text) && java.util.Objects.equals(style, that.style) && java.util.Objects.equals(reference, that.reference);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(style);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        return hash;
    }
    @Override public String toString() { return "Run[text=" + text + ", style=" + style + ", reference=" + reference + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Run> schema() {
            return new dev.openallay.value.ValueSchema<>(Run.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Run>>asList(new dev.openallay.value.ValueSchema.Component<>(Run.class, "text", Run::text), new dev.openallay.value.ValueSchema.Component<>(Run.class, "style", Run::style), new dev.openallay.value.ValueSchema.Component<>(Run.class, "reference", Run::reference)), arguments -> new Run((String) arguments[0], (Style) arguments[1], (SemanticReference) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(Line.ValueSchemaProvider.class)
public static final class Line {
    private final String nodeId;
    private final Kind kind;
    private final int indent;
    private final int height;
    private final List<Run> runs;
    private final RichComponent component;
    private final TableBox table;
    public Line(String nodeId, Kind kind, int indent, int height, List<Run> runs, RichComponent component, TableBox table) {

            if (nodeId == null || nodeId.isBlank() || indent < 0 || height <= 0) {
                throw new IllegalArgumentException("semantic line geometry is invalid");
            }
            java.util.Objects.requireNonNull(kind, "kind");
            runs = List.copyOf(runs);
            if ((kind == Kind.COMPONENT) != (component != null)) {
                throw new IllegalArgumentException("semantic component line is inconsistent");
            }
            if ((kind == Kind.TABLE) != (table != null)) {
                throw new IllegalArgumentException("semantic table line is inconsistent");
            }

        this.nodeId = nodeId;
        this.kind = kind;
        this.indent = indent;
        this.height = height;
        this.runs = runs;
        this.component = component;
        this.table = table;
    }
    public String nodeId() { return nodeId; }
    public Kind kind() { return kind; }
    public int indent() { return indent; }
    public int height() { return height; }
    public List<Run> runs() { return runs; }
    public RichComponent component() { return component; }
    public TableBox table() { return table; }
public Line(
                String nodeId,
                Kind kind,
                int indent,
                int height,
                List<Run> runs,
                RichComponent component) {
            this(nodeId, kind, indent, height, runs, component, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Line)) return false;
        Line that = (Line) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(kind, that.kind) && indent == that.indent && height == that.height && java.util.Objects.equals(runs, that.runs) && java.util.Objects.equals(component, that.component) && java.util.Objects.equals(table, that.table);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Integer.hashCode(indent);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(runs);
        hash = 31 * hash + java.util.Objects.hashCode(component);
        hash = 31 * hash + java.util.Objects.hashCode(table);
        return hash;
    }
    @Override public String toString() { return "Line[nodeId=" + nodeId + ", kind=" + kind + ", indent=" + indent + ", height=" + height + ", runs=" + runs + ", component=" + component + ", table=" + table + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Line> schema() {
            return new dev.openallay.value.ValueSchema<>(Line.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Line>>asList(new dev.openallay.value.ValueSchema.Component<>(Line.class, "nodeId", Line::nodeId), new dev.openallay.value.ValueSchema.Component<>(Line.class, "kind", Line::kind), new dev.openallay.value.ValueSchema.Component<>(Line.class, "indent", Line::indent), new dev.openallay.value.ValueSchema.Component<>(Line.class, "height", Line::height), new dev.openallay.value.ValueSchema.Component<>(Line.class, "runs", Line::runs), new dev.openallay.value.ValueSchema.Component<>(Line.class, "component", Line::component), new dev.openallay.value.ValueSchema.Component<>(Line.class, "table", Line::table)), arguments -> new Line((String) arguments[0], (Kind) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (List) arguments[4], (RichComponent) arguments[5], (TableBox) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(TableBox.ValueSchemaProvider.class)
public static final class TableBox {
    private final Mode mode;
    private final int width;
    private final int height;
    private final int lineHeight;
    private final List<TableRow> rows;
    public TableBox(Mode mode, int width, int height, int lineHeight, List<TableRow> rows) {

            java.util.Objects.requireNonNull(mode, "mode");
            if (width <= 0 || height <= 0 || lineHeight <= 0) {
                throw new IllegalArgumentException("semantic table geometry is invalid");
            }
            rows = List.copyOf(rows);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("semantic table must contain a row");
            }

        this.mode = mode;
        this.width = width;
        this.height = height;
        this.lineHeight = lineHeight;
        this.rows = rows;
    }
    public Mode mode() { return mode; }
    public int width() { return width; }
    public int height() { return height; }
    public int lineHeight() { return lineHeight; }
    public List<TableRow> rows() { return rows; }
public enum Mode { GRID, KEY_VALUE_CARDS }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TableBox)) return false;
        TableBox that = (TableBox) other;
        return java.util.Objects.equals(mode, that.mode) && width == that.width && height == that.height && lineHeight == that.lineHeight && java.util.Objects.equals(rows, that.rows);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(mode);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Integer.hashCode(lineHeight);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        return hash;
    }
    @Override public String toString() { return "TableBox[mode=" + mode + ", width=" + width + ", height=" + height + ", lineHeight=" + lineHeight + ", rows=" + rows + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TableBox> schema() {
            return new dev.openallay.value.ValueSchema<>(TableBox.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TableBox>>asList(new dev.openallay.value.ValueSchema.Component<>(TableBox.class, "mode", TableBox::mode), new dev.openallay.value.ValueSchema.Component<>(TableBox.class, "width", TableBox::width), new dev.openallay.value.ValueSchema.Component<>(TableBox.class, "height", TableBox::height), new dev.openallay.value.ValueSchema.Component<>(TableBox.class, "lineHeight", TableBox::lineHeight), new dev.openallay.value.ValueSchema.Component<>(TableBox.class, "rows", TableBox::rows)), arguments -> new TableBox((Mode) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (List) arguments[4]));
        }
    }
}
@dev.openallay.value.ValueType(TableRow.ValueSchemaProvider.class)
public static final class TableRow {
    private final boolean header;
    private final int y;
    private final int height;
    private final List<TableCell> cells;
    public TableRow(boolean header, int y, int height, List<TableCell> cells) {

            if (y < 0 || height <= 0) {
                throw new IllegalArgumentException("semantic table row geometry is invalid");
            }
            cells = List.copyOf(cells);
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("semantic table row must contain a cell");
            }

        this.header = header;
        this.y = y;
        this.height = height;
        this.cells = cells;
    }
    public boolean header() { return header; }
    public int y() { return y; }
    public int height() { return height; }
    public List<TableCell> cells() { return cells; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TableRow)) return false;
        TableRow that = (TableRow) other;
        return header == that.header && y == that.y && height == that.height && java.util.Objects.equals(cells, that.cells);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(header);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(cells);
        return hash;
    }
    @Override public String toString() { return "TableRow[header=" + header + ", y=" + y + ", height=" + height + ", cells=" + cells + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TableRow> schema() {
            return new dev.openallay.value.ValueSchema<>(TableRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TableRow>>asList(new dev.openallay.value.ValueSchema.Component<>(TableRow.class, "header", TableRow::header), new dev.openallay.value.ValueSchema.Component<>(TableRow.class, "y", TableRow::y), new dev.openallay.value.ValueSchema.Component<>(TableRow.class, "height", TableRow::height), new dev.openallay.value.ValueSchema.Component<>(TableRow.class, "cells", TableRow::cells)), arguments -> new TableRow((Boolean) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (List) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(TableCell.ValueSchemaProvider.class)
public static final class TableCell {
    private final int column;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final SemanticBlock.Alignment alignment;
    private final List<CellLine> labelLines;
    private final List<CellLine> valueLines;
    public TableCell(int column, int x, int y, int width, int height, SemanticBlock.Alignment alignment, List<CellLine> labelLines, List<CellLine> valueLines) {

            if (column < 0 || x < 0 || y < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("semantic table cell geometry is invalid");
            }
            java.util.Objects.requireNonNull(alignment, "alignment");
            labelLines = List.copyOf(labelLines);
            valueLines = List.copyOf(valueLines);
            if (valueLines.isEmpty()) {
                throw new IllegalArgumentException("semantic table cell value must contain a line");
            }

        this.column = column;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.alignment = alignment;
        this.labelLines = labelLines;
        this.valueLines = valueLines;
    }
    public int column() { return column; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public SemanticBlock.Alignment alignment() { return alignment; }
    public List<CellLine> labelLines() { return labelLines; }
    public List<CellLine> valueLines() { return valueLines; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TableCell)) return false;
        TableCell that = (TableCell) other;
        return column == that.column && x == that.x && y == that.y && width == that.width && height == that.height && java.util.Objects.equals(alignment, that.alignment) && java.util.Objects.equals(labelLines, that.labelLines) && java.util.Objects.equals(valueLines, that.valueLines);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(column);
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(alignment);
        hash = 31 * hash + java.util.Objects.hashCode(labelLines);
        hash = 31 * hash + java.util.Objects.hashCode(valueLines);
        return hash;
    }
    @Override public String toString() { return "TableCell[column=" + column + ", x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + ", alignment=" + alignment + ", labelLines=" + labelLines + ", valueLines=" + valueLines + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TableCell> schema() {
            return new dev.openallay.value.ValueSchema<>(TableCell.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TableCell>>asList(new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "column", TableCell::column), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "x", TableCell::x), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "y", TableCell::y), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "width", TableCell::width), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "height", TableCell::height), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "alignment", TableCell::alignment), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "labelLines", TableCell::labelLines), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "valueLines", TableCell::valueLines)), arguments -> new TableCell((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (SemanticBlock.Alignment) arguments[5], (List) arguments[6], (List) arguments[7]));
        }
    }
}
@dev.openallay.value.ValueType(CellLine.ValueSchemaProvider.class)
public static final class CellLine {
    private final int y;
    private final int width;
    private final List<Run> runs;
    public CellLine(int y, int width, List<Run> runs) {

            if (y < 0 || width < 0) {
                throw new IllegalArgumentException("semantic table text geometry is invalid");
            }
            runs = List.copyOf(runs);

        this.y = y;
        this.width = width;
        this.runs = runs;
    }
    public int y() { return y; }
    public int width() { return width; }
    public List<Run> runs() { return runs; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellLine)) return false;
        CellLine that = (CellLine) other;
        return y == that.y && width == that.width && java.util.Objects.equals(runs, that.runs);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(runs);
        return hash;
    }
    @Override public String toString() { return "CellLine[y=" + y + ", width=" + width + ", runs=" + runs + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CellLine> schema() {
            return new dev.openallay.value.ValueSchema<>(CellLine.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CellLine>>asList(new dev.openallay.value.ValueSchema.Component<>(CellLine.class, "y", CellLine::y), new dev.openallay.value.ValueSchema.Component<>(CellLine.class, "width", CellLine::width), new dev.openallay.value.ValueSchema.Component<>(CellLine.class, "runs", CellLine::runs)), arguments -> new CellLine((Integer) arguments[0], (Integer) arguments[1], (List) arguments[2]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SemanticLayout)) return false;
        SemanticLayout that = (SemanticLayout) other;
        return width == that.width && height == that.height && java.util.Objects.equals(lines, that.lines) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(lines);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "SemanticLayout[width=" + width + ", height=" + height + ", lines=" + lines + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SemanticLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(SemanticLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SemanticLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(SemanticLayout.class, "width", SemanticLayout::width), new dev.openallay.value.ValueSchema.Component<>(SemanticLayout.class, "height", SemanticLayout::height), new dev.openallay.value.ValueSchema.Component<>(SemanticLayout.class, "lines", SemanticLayout::lines), new dev.openallay.value.ValueSchema.Component<>(SemanticLayout.class, "narration", SemanticLayout::narration)), arguments -> new SemanticLayout((Integer) arguments[0], (Integer) arguments[1], (List) arguments[2], (String) arguments[3]));
        }
    }
}
