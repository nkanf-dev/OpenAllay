package dev.openallay.guide.ui;

import java.util.List;
import java.util.Objects;

/** Closed set of semantic cards the native guide screen is allowed to render. */
public sealed interface GuideDetailCard permits
        GuideDetailCard.Recipe,
        GuideDetailCard.ItemGrid,
        GuideDetailCard.Requirements,
        GuideDetailCard.Table,
        GuideDetailCard.KeyValue,
        GuideDetailCard.DataPreview,
        GuideDetailCard.Text,
        GuideDetailCard.Error {

    @dev.openallay.value.ValueType(Recipe.ValueSchemaProvider.class)
public static final class Recipe implements GuideDetailCard {
    private final GuideRecipeCard recipe;
    public Recipe(GuideRecipeCard recipe) {

            Objects.requireNonNull(recipe, "recipe");

        this.recipe = recipe;
    }
    public GuideRecipeCard recipe() { return recipe; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Recipe)) return false;
        Recipe that = (Recipe) other;
        return java.util.Objects.equals(recipe, that.recipe);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(recipe);
        return hash;
    }
    @Override public String toString() { return "Recipe[recipe=" + recipe + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Recipe> schema() {
            return new dev.openallay.value.ValueSchema<>(Recipe.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Recipe>>asList(new dev.openallay.value.ValueSchema.Component<>(Recipe.class, "recipe", Recipe::recipe)), arguments -> new Recipe((GuideRecipeCard) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(ItemGrid.ValueSchemaProvider.class)
public static final class ItemGrid implements GuideDetailCard {
    private final String titleKey;
    private final List<GuideItemView> items;
    public ItemGrid(String titleKey, List<GuideItemView> items) {

            titleKey = requireText(titleKey, "titleKey");
            items = dev.openallay.util.Java8Collections.listCopyOf(items);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("item grid must not be empty");
            }

        this.titleKey = titleKey;
        this.items = items;
    }
    public String titleKey() { return titleKey; }
    public List<GuideItemView> items() { return items; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemGrid)) return false;
        ItemGrid that = (ItemGrid) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(items, that.items);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(items);
        return hash;
    }
    @Override public String toString() { return "ItemGrid[titleKey=" + titleKey + ", items=" + items + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ItemGrid> schema() {
            return new dev.openallay.value.ValueSchema<>(ItemGrid.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ItemGrid>>asList(new dev.openallay.value.ValueSchema.Component<>(ItemGrid.class, "titleKey", ItemGrid::titleKey), new dev.openallay.value.ValueSchema.Component<>(ItemGrid.class, "items", ItemGrid::items)), arguments -> new ItemGrid((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Requirements.ValueSchemaProvider.class)
public static final class Requirements implements GuideDetailCard {
    private final boolean craftable;
    private final boolean conclusive;
    private final long requestedCrafts;
    private final long maximumCrafts;
    private final List<Requirement> requirements;
    public Requirements(boolean craftable, boolean conclusive, long requestedCrafts, long maximumCrafts, List<Requirement> requirements) {

            if (requestedCrafts <= 0 || maximumCrafts < 0) {
                throw new IllegalArgumentException("craftability counts are invalid");
            }
            requirements = dev.openallay.util.Java8Collections.listCopyOf(requirements);

        this.craftable = craftable;
        this.conclusive = conclusive;
        this.requestedCrafts = requestedCrafts;
        this.maximumCrafts = maximumCrafts;
        this.requirements = requirements;
    }
    public boolean craftable() { return craftable; }
    public boolean conclusive() { return conclusive; }
    public long requestedCrafts() { return requestedCrafts; }
    public long maximumCrafts() { return maximumCrafts; }
    public List<Requirement> requirements() { return requirements; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Requirements)) return false;
        Requirements that = (Requirements) other;
        return craftable == that.craftable && conclusive == that.conclusive && requestedCrafts == that.requestedCrafts && maximumCrafts == that.maximumCrafts && java.util.Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(craftable);
        hash = 31 * hash + Boolean.hashCode(conclusive);
        hash = 31 * hash + Long.hashCode(requestedCrafts);
        hash = 31 * hash + Long.hashCode(maximumCrafts);
        hash = 31 * hash + java.util.Objects.hashCode(requirements);
        return hash;
    }
    @Override public String toString() { return "Requirements[craftable=" + craftable + ", conclusive=" + conclusive + ", requestedCrafts=" + requestedCrafts + ", maximumCrafts=" + maximumCrafts + ", requirements=" + requirements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Requirements> schema() {
            return new dev.openallay.value.ValueSchema<>(Requirements.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Requirements>>asList(new dev.openallay.value.ValueSchema.Component<>(Requirements.class, "craftable", Requirements::craftable), new dev.openallay.value.ValueSchema.Component<>(Requirements.class, "conclusive", Requirements::conclusive), new dev.openallay.value.ValueSchema.Component<>(Requirements.class, "requestedCrafts", Requirements::requestedCrafts), new dev.openallay.value.ValueSchema.Component<>(Requirements.class, "maximumCrafts", Requirements::maximumCrafts), new dev.openallay.value.ValueSchema.Component<>(Requirements.class, "requirements", Requirements::requirements)), arguments -> new Requirements((Boolean) arguments[0], (Boolean) arguments[1], (Long) arguments[2], (Long) arguments[3], (List) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(Requirement.ValueSchemaProvider.class)
public static final class Requirement {
    private final String key;
    private final long required;
    private final long allocated;
    private final long missing;
    private final List<GuideItemView> allocatedItems;
    private final List<GuideItemView> alternatives;
    public Requirement(String key, long required, long allocated, long missing, List<GuideItemView> allocatedItems, List<GuideItemView> alternatives) {

            key = requireText(key, "key");
            if (required <= 0 || allocated < 0 || missing < 0 || allocated + missing != required) {
                throw new IllegalArgumentException("requirement counts are inconsistent");
            }
            allocatedItems = dev.openallay.util.Java8Collections.listCopyOf(allocatedItems);
            alternatives = dev.openallay.util.Java8Collections.listCopyOf(alternatives);

        this.key = key;
        this.required = required;
        this.allocated = allocated;
        this.missing = missing;
        this.allocatedItems = allocatedItems;
        this.alternatives = alternatives;
    }
    public String key() { return key; }
    public long required() { return required; }
    public long allocated() { return allocated; }
    public long missing() { return missing; }
    public List<GuideItemView> allocatedItems() { return allocatedItems; }
    public List<GuideItemView> alternatives() { return alternatives; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Requirement)) return false;
        Requirement that = (Requirement) other;
        return java.util.Objects.equals(key, that.key) && required == that.required && allocated == that.allocated && missing == that.missing && java.util.Objects.equals(allocatedItems, that.allocatedItems) && java.util.Objects.equals(alternatives, that.alternatives);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + Long.hashCode(required);
        hash = 31 * hash + Long.hashCode(allocated);
        hash = 31 * hash + Long.hashCode(missing);
        hash = 31 * hash + java.util.Objects.hashCode(allocatedItems);
        hash = 31 * hash + java.util.Objects.hashCode(alternatives);
        return hash;
    }
    @Override public String toString() { return "Requirement[key=" + key + ", required=" + required + ", allocated=" + allocated + ", missing=" + missing + ", allocatedItems=" + allocatedItems + ", alternatives=" + alternatives + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Requirement> schema() {
            return new dev.openallay.value.ValueSchema<>(Requirement.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Requirement>>asList(new dev.openallay.value.ValueSchema.Component<>(Requirement.class, "key", Requirement::key), new dev.openallay.value.ValueSchema.Component<>(Requirement.class, "required", Requirement::required), new dev.openallay.value.ValueSchema.Component<>(Requirement.class, "allocated", Requirement::allocated), new dev.openallay.value.ValueSchema.Component<>(Requirement.class, "missing", Requirement::missing), new dev.openallay.value.ValueSchema.Component<>(Requirement.class, "allocatedItems", Requirement::allocatedItems), new dev.openallay.value.ValueSchema.Component<>(Requirement.class, "alternatives", Requirement::alternatives)), arguments -> new Requirement((String) arguments[0], (Long) arguments[1], (Long) arguments[2], (Long) arguments[3], (List) arguments[4], (List) arguments[5]));
        }
    }
}

    @dev.openallay.value.ValueType(Text.ValueSchemaProvider.class)
public static final class Text implements GuideDetailCard {
    private final String titleKey;
    private final List<String> lines;
    public Text(String titleKey, List<String> lines) {

            titleKey = requireText(titleKey, "titleKey");
            lines = dev.openallay.util.Java8Collections.listCopyOf(lines);
            if (lines.isEmpty() || lines.stream().anyMatch(line -> line == null || dev.openallay.util.Java8Strings.isBlank(line))) {
                throw new IllegalArgumentException("text card lines must not be blank");
            }

        this.titleKey = titleKey;
        this.lines = lines;
    }
    public String titleKey() { return titleKey; }
    public List<String> lines() { return lines; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Text)) return false;
        Text that = (Text) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(lines, that.lines);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(lines);
        return hash;
    }
    @Override public String toString() { return "Text[titleKey=" + titleKey + ", lines=" + lines + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Text> schema() {
            return new dev.openallay.value.ValueSchema<>(Text.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Text>>asList(new dev.openallay.value.ValueSchema.Component<>(Text.class, "titleKey", Text::titleKey), new dev.openallay.value.ValueSchema.Component<>(Text.class, "lines", Text::lines)), arguments -> new Text((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Table.ValueSchemaProvider.class)
public static final class Table implements GuideDetailCard {
    private final String titleKey;
    private final List<String> columns;
    private final List<List<String>> rows;
    private final boolean complete;
    private final int omittedRows;
    private final int omittedFields;
    public Table(String titleKey, List<String> columns, List<List<String>> rows, boolean complete, int omittedRows, int omittedFields) {

            titleKey = requireText(titleKey, "titleKey");
            columns = dev.openallay.util.Java8Collections.listCopyOf(columns);
            rows = dev.openallay.util.Java8Collections.toList(rows.stream().map(dev.openallay.util.Java8Collections::listCopyOf));
            int columnCount = columns.size();
            if (columns.isEmpty()
                    || columns.stream().anyMatch(value -> value == null || dev.openallay.util.Java8Strings.isBlank(value))
                    || rows.stream().anyMatch(row -> row.size() != columnCount)
                    || omittedRows < 0
                    || omittedFields < 0) {
                throw new IllegalArgumentException("structured table is invalid");
            }

        this.titleKey = titleKey;
        this.columns = columns;
        this.rows = rows;
        this.complete = complete;
        this.omittedRows = omittedRows;
        this.omittedFields = omittedFields;
    }
    public String titleKey() { return titleKey; }
    public List<String> columns() { return columns; }
    public List<List<String>> rows() { return rows; }
    public boolean complete() { return complete; }
    public int omittedRows() { return omittedRows; }
    public int omittedFields() { return omittedFields; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Table)) return false;
        Table that = (Table) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(columns, that.columns) && java.util.Objects.equals(rows, that.rows) && complete == that.complete && omittedRows == that.omittedRows && omittedFields == that.omittedFields;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(columns);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedRows);
        hash = 31 * hash + Integer.hashCode(omittedFields);
        return hash;
    }
    @Override public String toString() { return "Table[titleKey=" + titleKey + ", columns=" + columns + ", rows=" + rows + ", complete=" + complete + ", omittedRows=" + omittedRows + ", omittedFields=" + omittedFields + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Table> schema() {
            return new dev.openallay.value.ValueSchema<>(Table.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Table>>asList(new dev.openallay.value.ValueSchema.Component<>(Table.class, "titleKey", Table::titleKey), new dev.openallay.value.ValueSchema.Component<>(Table.class, "columns", Table::columns), new dev.openallay.value.ValueSchema.Component<>(Table.class, "rows", Table::rows), new dev.openallay.value.ValueSchema.Component<>(Table.class, "complete", Table::complete), new dev.openallay.value.ValueSchema.Component<>(Table.class, "omittedRows", Table::omittedRows), new dev.openallay.value.ValueSchema.Component<>(Table.class, "omittedFields", Table::omittedFields)), arguments -> new Table((String) arguments[0], (List) arguments[1], (List) arguments[2], (Boolean) arguments[3], (Integer) arguments[4], (Integer) arguments[5]));
        }
    }
}

    @dev.openallay.value.ValueType(KeyValue.ValueSchemaProvider.class)
public static final class KeyValue implements GuideDetailCard {
    private final String titleKey;
    private final List<DataCell> entries;
    private final boolean complete;
    private final int omittedFields;
    public KeyValue(String titleKey, List<DataCell> entries, boolean complete, int omittedFields) {

            titleKey = requireText(titleKey, "titleKey");
            entries = dev.openallay.util.Java8Collections.listCopyOf(entries);
            if (entries.isEmpty() || omittedFields < 0) {
                throw new IllegalArgumentException("key/value card is invalid");
            }

        this.titleKey = titleKey;
        this.entries = entries;
        this.complete = complete;
        this.omittedFields = omittedFields;
    }
    public String titleKey() { return titleKey; }
    public List<DataCell> entries() { return entries; }
    public boolean complete() { return complete; }
    public int omittedFields() { return omittedFields; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KeyValue)) return false;
        KeyValue that = (KeyValue) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(entries, that.entries) && complete == that.complete && omittedFields == that.omittedFields;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(entries);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedFields);
        return hash;
    }
    @Override public String toString() { return "KeyValue[titleKey=" + titleKey + ", entries=" + entries + ", complete=" + complete + ", omittedFields=" + omittedFields + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<KeyValue> schema() {
            return new dev.openallay.value.ValueSchema<>(KeyValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<KeyValue>>asList(new dev.openallay.value.ValueSchema.Component<>(KeyValue.class, "titleKey", KeyValue::titleKey), new dev.openallay.value.ValueSchema.Component<>(KeyValue.class, "entries", KeyValue::entries), new dev.openallay.value.ValueSchema.Component<>(KeyValue.class, "complete", KeyValue::complete), new dev.openallay.value.ValueSchema.Component<>(KeyValue.class, "omittedFields", KeyValue::omittedFields)), arguments -> new KeyValue((String) arguments[0], (List) arguments[1], (Boolean) arguments[2], (Integer) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(DataPreview.ValueSchemaProvider.class)
public static final class DataPreview implements GuideDetailCard {
    private final String titleKey;
    private final String resultType;
    private final long cardinality;
    private final List<DataRow> rows;
    private final boolean complete;
    private final int omittedRows;
    private final int omittedFields;
    public DataPreview(String titleKey, String resultType, long cardinality, List<DataRow> rows, boolean complete, int omittedRows, int omittedFields) {

            titleKey = requireText(titleKey, "titleKey");
            resultType = requireText(resultType, "resultType");
            if (cardinality < 0 || omittedRows < 0 || omittedFields < 0) {
                throw new IllegalArgumentException("preview counts must not be negative");
            }
            rows = dev.openallay.util.Java8Collections.listCopyOf(rows);

        this.titleKey = titleKey;
        this.resultType = resultType;
        this.cardinality = cardinality;
        this.rows = rows;
        this.complete = complete;
        this.omittedRows = omittedRows;
        this.omittedFields = omittedFields;
    }
    public String titleKey() { return titleKey; }
    public String resultType() { return resultType; }
    public long cardinality() { return cardinality; }
    public List<DataRow> rows() { return rows; }
    public boolean complete() { return complete; }
    public int omittedRows() { return omittedRows; }
    public int omittedFields() { return omittedFields; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DataPreview)) return false;
        DataPreview that = (DataPreview) other;
        return java.util.Objects.equals(titleKey, that.titleKey) && java.util.Objects.equals(resultType, that.resultType) && cardinality == that.cardinality && java.util.Objects.equals(rows, that.rows) && complete == that.complete && omittedRows == that.omittedRows && omittedFields == that.omittedFields;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(titleKey);
        hash = 31 * hash + java.util.Objects.hashCode(resultType);
        hash = 31 * hash + Long.hashCode(cardinality);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedRows);
        hash = 31 * hash + Integer.hashCode(omittedFields);
        return hash;
    }
    @Override public String toString() { return "DataPreview[titleKey=" + titleKey + ", resultType=" + resultType + ", cardinality=" + cardinality + ", rows=" + rows + ", complete=" + complete + ", omittedRows=" + omittedRows + ", omittedFields=" + omittedFields + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DataPreview> schema() {
            return new dev.openallay.value.ValueSchema<>(DataPreview.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DataPreview>>asList(new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "titleKey", DataPreview::titleKey), new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "resultType", DataPreview::resultType), new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "cardinality", DataPreview::cardinality), new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "rows", DataPreview::rows), new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "complete", DataPreview::complete), new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "omittedRows", DataPreview::omittedRows), new dev.openallay.value.ValueSchema.Component<>(DataPreview.class, "omittedFields", DataPreview::omittedFields)), arguments -> new DataPreview((String) arguments[0], (String) arguments[1], (Long) arguments[2], (List) arguments[3], (Boolean) arguments[4], (Integer) arguments[5], (Integer) arguments[6]));
        }
    }
}

    @dev.openallay.value.ValueType(DataRow.ValueSchemaProvider.class)
public static final class DataRow {
    private final List<DataCell> cells;
    public DataRow(List<DataCell> cells) {

            cells = dev.openallay.util.Java8Collections.listCopyOf(cells);
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("preview row must contain cells");
            }

        this.cells = cells;
    }
    public List<DataCell> cells() { return cells; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DataRow)) return false;
        DataRow that = (DataRow) other;
        return java.util.Objects.equals(cells, that.cells);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cells);
        return hash;
    }
    @Override public String toString() { return "DataRow[cells=" + cells + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DataRow> schema() {
            return new dev.openallay.value.ValueSchema<>(DataRow.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DataRow>>asList(new dev.openallay.value.ValueSchema.Component<>(DataRow.class, "cells", DataRow::cells)), arguments -> new DataRow((List) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(DataCell.ValueSchemaProvider.class)
public static final class DataCell {
    private final String key;
    private final String value;
    public DataCell(String key, String value) {

            key = requireText(key, "key");
            value = requireText(value, "value");
            if (key.length() > 80 || value.length() > 260) {
                throw new IllegalArgumentException("preview cell is too large");
            }

        this.key = key;
        this.value = value;
    }
    public String key() { return key; }
    public String value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DataCell)) return false;
        DataCell that = (DataCell) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "DataCell[key=" + key + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DataCell> schema() {
            return new dev.openallay.value.ValueSchema<>(DataCell.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DataCell>>asList(new dev.openallay.value.ValueSchema.Component<>(DataCell.class, "key", DataCell::key), new dev.openallay.value.ValueSchema.Component<>(DataCell.class, "value", DataCell::value)), arguments -> new DataCell((String) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Error.ValueSchemaProvider.class)
public static final class Error implements GuideDetailCard {
    private final String message;
    public Error(String message) {

            message = requireText(message, "message");

        this.message = message;
    }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Error)) return false;
        Error that = (Error) other;
        return java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Error[message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Error> schema() {
            return new dev.openallay.value.ValueSchema<>(Error.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Error>>asList(new dev.openallay.value.ValueSchema.Component<>(Error.class, "message", Error::message)), arguments -> new Error((String) arguments[0]));
        }
    }
}

    private static String requireText(String value, String label) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
