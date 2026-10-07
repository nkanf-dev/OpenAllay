package dev.openallay.guide.semantic;

import java.util.List;

/** Closed safe block subset translated from CommonMark. */
public sealed interface SemanticBlock
        permits SemanticBlock.Paragraph, SemanticBlock.Heading, SemanticBlock.ListBlock,
                SemanticBlock.Quote, SemanticBlock.CodeBlock, SemanticBlock.Table,
                SemanticBlock.ThematicBreak, SemanticBlock.Component {
    String nodeId();

    @dev.openallay.value.ValueType(Paragraph.ValueSchemaProvider.class)
public static final class Paragraph implements SemanticBlock {
    private final String nodeId;
    private final List<SemanticInline> content;
    public Paragraph(String nodeId, List<SemanticInline> content) {

            SemanticIds.require(nodeId);
            content = List.copyOf(content);

        this.nodeId = nodeId;
        this.content = content;
    }
    public String nodeId() { return nodeId; }
    public List<SemanticInline> content() { return content; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Paragraph)) return false;
        Paragraph that = (Paragraph) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "Paragraph[nodeId=" + nodeId + ", content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Paragraph> schema() {
            return new dev.openallay.value.ValueSchema<>(Paragraph.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Paragraph>>asList(new dev.openallay.value.ValueSchema.Component<>(Paragraph.class, "nodeId", Paragraph::nodeId), new dev.openallay.value.ValueSchema.Component<>(Paragraph.class, "content", Paragraph::content)), arguments -> new Paragraph((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Heading.ValueSchemaProvider.class)
public static final class Heading implements SemanticBlock {
    private final String nodeId;
    private final int level;
    private final List<SemanticInline> content;
    public Heading(String nodeId, int level, List<SemanticInline> content) {

            SemanticIds.require(nodeId);
            if (level < 1 || level > 6) {
                throw new IllegalArgumentException("heading level must be between 1 and 6");
            }
            content = List.copyOf(content);

        this.nodeId = nodeId;
        this.level = level;
        this.content = content;
    }
    public String nodeId() { return nodeId; }
    public int level() { return level; }
    public List<SemanticInline> content() { return content; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Heading)) return false;
        Heading that = (Heading) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && level == that.level && java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + Integer.hashCode(level);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "Heading[nodeId=" + nodeId + ", level=" + level + ", content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Heading> schema() {
            return new dev.openallay.value.ValueSchema<>(Heading.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Heading>>asList(new dev.openallay.value.ValueSchema.Component<>(Heading.class, "nodeId", Heading::nodeId), new dev.openallay.value.ValueSchema.Component<>(Heading.class, "level", Heading::level), new dev.openallay.value.ValueSchema.Component<>(Heading.class, "content", Heading::content)), arguments -> new Heading((String) arguments[0], (Integer) arguments[1], (List) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(ListBlock.ValueSchemaProvider.class)
public static final class ListBlock implements SemanticBlock {
    private final String nodeId;
    private final boolean ordered;
    private final int start;
    private final List<List<SemanticBlock>> items;
    public ListBlock(String nodeId, boolean ordered, int start, List<List<SemanticBlock>> items) {

            SemanticIds.require(nodeId);
            if (start < 1) {
                throw new IllegalArgumentException("list start must be positive");
            }
            items = items.stream().map(List::copyOf).toList();

        this.nodeId = nodeId;
        this.ordered = ordered;
        this.start = start;
        this.items = items;
    }
    public String nodeId() { return nodeId; }
    public boolean ordered() { return ordered; }
    public int start() { return start; }
    public List<List<SemanticBlock>> items() { return items; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ListBlock)) return false;
        ListBlock that = (ListBlock) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && ordered == that.ordered && start == that.start && java.util.Objects.equals(items, that.items);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + Boolean.hashCode(ordered);
        hash = 31 * hash + Integer.hashCode(start);
        hash = 31 * hash + java.util.Objects.hashCode(items);
        return hash;
    }
    @Override public String toString() { return "ListBlock[nodeId=" + nodeId + ", ordered=" + ordered + ", start=" + start + ", items=" + items + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ListBlock> schema() {
            return new dev.openallay.value.ValueSchema<>(ListBlock.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ListBlock>>asList(new dev.openallay.value.ValueSchema.Component<>(ListBlock.class, "nodeId", ListBlock::nodeId), new dev.openallay.value.ValueSchema.Component<>(ListBlock.class, "ordered", ListBlock::ordered), new dev.openallay.value.ValueSchema.Component<>(ListBlock.class, "start", ListBlock::start), new dev.openallay.value.ValueSchema.Component<>(ListBlock.class, "items", ListBlock::items)), arguments -> new ListBlock((String) arguments[0], (Boolean) arguments[1], (Integer) arguments[2], (List) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Quote.ValueSchemaProvider.class)
public static final class Quote implements SemanticBlock {
    private final String nodeId;
    private final List<SemanticBlock> content;
    public Quote(String nodeId, List<SemanticBlock> content) {

            SemanticIds.require(nodeId);
            content = List.copyOf(content);

        this.nodeId = nodeId;
        this.content = content;
    }
    public String nodeId() { return nodeId; }
    public List<SemanticBlock> content() { return content; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Quote)) return false;
        Quote that = (Quote) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "Quote[nodeId=" + nodeId + ", content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Quote> schema() {
            return new dev.openallay.value.ValueSchema<>(Quote.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Quote>>asList(new dev.openallay.value.ValueSchema.Component<>(Quote.class, "nodeId", Quote::nodeId), new dev.openallay.value.ValueSchema.Component<>(Quote.class, "content", Quote::content)), arguments -> new Quote((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(CodeBlock.ValueSchemaProvider.class)
public static final class CodeBlock implements SemanticBlock {
    private final String nodeId;
    private final String info;
    private final String code;
    public CodeBlock(String nodeId, String info, String code) {

            SemanticIds.require(nodeId);
            info = info == null ? "" : info;
            code = code == null ? "" : code;

        this.nodeId = nodeId;
        this.info = info;
        this.code = code;
    }
    public String nodeId() { return nodeId; }
    public String info() { return info; }
    public String code() { return code; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CodeBlock)) return false;
        CodeBlock that = (CodeBlock) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(info, that.info) && java.util.Objects.equals(code, that.code);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(info);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        return hash;
    }
    @Override public String toString() { return "CodeBlock[nodeId=" + nodeId + ", info=" + info + ", code=" + code + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CodeBlock> schema() {
            return new dev.openallay.value.ValueSchema<>(CodeBlock.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CodeBlock>>asList(new dev.openallay.value.ValueSchema.Component<>(CodeBlock.class, "nodeId", CodeBlock::nodeId), new dev.openallay.value.ValueSchema.Component<>(CodeBlock.class, "info", CodeBlock::info), new dev.openallay.value.ValueSchema.Component<>(CodeBlock.class, "code", CodeBlock::code)), arguments -> new CodeBlock((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(Table.ValueSchemaProvider.class)
public static final class Table implements SemanticBlock {
    private final String nodeId;
    private final TableRow header;
    private final List<TableRow> rows;
    public Table(String nodeId, TableRow header, List<TableRow> rows) {

            SemanticIds.require(nodeId);
            java.util.Objects.requireNonNull(header, "header");
            rows = List.copyOf(rows);

        this.nodeId = nodeId;
        this.header = header;
        this.rows = rows;
    }
    public String nodeId() { return nodeId; }
    public TableRow header() { return header; }
    public List<TableRow> rows() { return rows; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Table)) return false;
        Table that = (Table) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(header, that.header) && java.util.Objects.equals(rows, that.rows);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(header);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        return hash;
    }
    @Override public String toString() { return "Table[nodeId=" + nodeId + ", header=" + header + ", rows=" + rows + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Table> schema() {
            return new dev.openallay.value.ValueSchema<>(Table.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Table>>asList(new dev.openallay.value.ValueSchema.Component<>(Table.class, "nodeId", Table::nodeId), new dev.openallay.value.ValueSchema.Component<>(Table.class, "header", Table::header), new dev.openallay.value.ValueSchema.Component<>(Table.class, "rows", Table::rows)), arguments -> new Table((String) arguments[0], (TableRow) arguments[1], (List) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(TableRow.ValueSchemaProvider.class)
public static final class TableRow {
    private final List<TableCell> cells;
    public TableRow(List<TableCell> cells) {

            cells = List.copyOf(cells);

        this.cells = cells;
    }
    public List<TableCell> cells() { return cells; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TableRow)) return false;
        TableRow that = (TableRow) other;
        return java.util.Objects.equals(cells, that.cells);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cells);
        return hash;
    }
    @Override public String toString() { return "TableRow[cells=" + cells + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TableRow> schema() {
            return new dev.openallay.value.ValueSchema<>(TableRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TableRow>>asList(new dev.openallay.value.ValueSchema.Component<>(TableRow.class, "cells", TableRow::cells)), arguments -> new TableRow((List) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(TableCell.ValueSchemaProvider.class)
public static final class TableCell {
    private final Alignment alignment;
    private final List<SemanticInline> content;
    public TableCell(Alignment alignment, List<SemanticInline> content) {

            java.util.Objects.requireNonNull(alignment, "alignment");
            content = List.copyOf(content);

        this.alignment = alignment;
        this.content = content;
    }
    public Alignment alignment() { return alignment; }
    public List<SemanticInline> content() { return content; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TableCell)) return false;
        TableCell that = (TableCell) other;
        return java.util.Objects.equals(alignment, that.alignment) && java.util.Objects.equals(content, that.content);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(alignment);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        return hash;
    }
    @Override public String toString() { return "TableCell[alignment=" + alignment + ", content=" + content + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<TableCell> schema() {
            return new dev.openallay.value.ValueSchema<>(TableCell.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<TableCell>>asList(new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "alignment", TableCell::alignment), new dev.openallay.value.ValueSchema.Component<>(TableCell.class, "content", TableCell::content)), arguments -> new TableCell((Alignment) arguments[0], (List) arguments[1]));
        }
    }
}

    enum Alignment { NONE, LEFT, CENTER, RIGHT }

    @dev.openallay.value.ValueType(ThematicBreak.ValueSchemaProvider.class)
public static final class ThematicBreak implements SemanticBlock {
    private final String nodeId;
    public ThematicBreak(String nodeId) {

            SemanticIds.require(nodeId);

        this.nodeId = nodeId;
    }
    public String nodeId() { return nodeId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ThematicBreak)) return false;
        ThematicBreak that = (ThematicBreak) other;
        return java.util.Objects.equals(nodeId, that.nodeId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        return hash;
    }
    @Override public String toString() { return "ThematicBreak[nodeId=" + nodeId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ThematicBreak> schema() {
            return new dev.openallay.value.ValueSchema<>(ThematicBreak.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ThematicBreak>>asList(new dev.openallay.value.ValueSchema.Component<>(ThematicBreak.class, "nodeId", ThematicBreak::nodeId)), arguments -> new ThematicBreak((String) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(Component.ValueSchemaProvider.class)
public static final class Component implements SemanticBlock {
    private final String nodeId;
    private final RichComponent component;
    public Component(String nodeId, RichComponent component) {

            SemanticIds.require(nodeId);
            java.util.Objects.requireNonNull(component, "component");
            if (!nodeId.equals(component.nodeId())) {
                throw new IllegalArgumentException("component node identity is inconsistent");
            }

        this.nodeId = nodeId;
        this.component = component;
    }
    public String nodeId() { return nodeId; }
    public RichComponent component() { return component; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Component)) return false;
        Component that = (Component) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(component, that.component);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(component);
        return hash;
    }
    @Override public String toString() { return "Component[nodeId=" + nodeId + ", component=" + component + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Component> schema() {
            return new dev.openallay.value.ValueSchema<>(Component.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Component>>asList(new dev.openallay.value.ValueSchema.Component<>(Component.class, "nodeId", Component::nodeId), new dev.openallay.value.ValueSchema.Component<>(Component.class, "component", Component::component)), arguments -> new Component((String) arguments[0], (RichComponent) arguments[1]));
        }
    }
}
}
