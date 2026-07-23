package dev.openallay.guide.ui;

import java.util.List;
import java.util.Objects;

/** Closed set of semantic cards the native guide screen is allowed to render. */
public sealed interface GuideDetailCard permits
        GuideDetailCard.Recipe,
        GuideDetailCard.ItemGrid,
        GuideDetailCard.Requirements,
        GuideDetailCard.DataPreview,
        GuideDetailCard.Text,
        GuideDetailCard.Error {

    record Recipe(GuideRecipeCard recipe) implements GuideDetailCard {
        public Recipe {
            Objects.requireNonNull(recipe, "recipe");
        }
    }

    record ItemGrid(String titleKey, List<GuideItemView> items) implements GuideDetailCard {
        public ItemGrid {
            titleKey = requireText(titleKey, "titleKey");
            items = List.copyOf(items);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("item grid must not be empty");
            }
        }
    }

    record Requirements(
            boolean craftable,
            boolean conclusive,
            long requestedCrafts,
            long maximumCrafts,
            List<Requirement> requirements) implements GuideDetailCard {
        public Requirements {
            if (requestedCrafts <= 0 || maximumCrafts < 0) {
                throw new IllegalArgumentException("craftability counts are invalid");
            }
            requirements = List.copyOf(requirements);
        }
    }

    record Requirement(
            String key,
            long required,
            long allocated,
            long missing,
            List<GuideItemView> allocatedItems,
            List<GuideItemView> alternatives) {
        public Requirement {
            key = requireText(key, "key");
            if (required <= 0 || allocated < 0 || missing < 0 || allocated + missing != required) {
                throw new IllegalArgumentException("requirement counts are inconsistent");
            }
            allocatedItems = List.copyOf(allocatedItems);
            alternatives = List.copyOf(alternatives);
        }
    }

    record Text(String titleKey, List<String> lines) implements GuideDetailCard {
        public Text {
            titleKey = requireText(titleKey, "titleKey");
            lines = List.copyOf(lines);
            if (lines.isEmpty() || lines.stream().anyMatch(line -> line == null || line.isBlank())) {
                throw new IllegalArgumentException("text card lines must not be blank");
            }
        }
    }

    record DataPreview(
            String titleKey,
            String resultType,
            long cardinality,
            List<DataRow> rows,
            boolean complete,
            int omittedRows,
            int omittedFields)
            implements GuideDetailCard {
        public DataPreview {
            titleKey = requireText(titleKey, "titleKey");
            resultType = requireText(resultType, "resultType");
            if (cardinality < 0 || omittedRows < 0 || omittedFields < 0) {
                throw new IllegalArgumentException("preview counts must not be negative");
            }
            rows = List.copyOf(rows);
            if (rows.size() > 6) {
                throw new IllegalArgumentException("preview must contain at most six rows");
            }
        }
    }

    record DataRow(List<DataCell> cells) {
        public DataRow {
            cells = List.copyOf(cells);
            if (cells.isEmpty() || cells.size() > 16) {
                throw new IllegalArgumentException("preview row must contain 1..16 cells");
            }
        }
    }

    record DataCell(String key, String value) {
        public DataCell {
            key = requireText(key, "key");
            value = requireText(value, "value");
            if (key.length() > 80 || value.length() > 260) {
                throw new IllegalArgumentException("preview cell is too large");
            }
        }
    }

    record Error(String message) implements GuideDetailCard {
        public Error {
            message = requireText(message, "message");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
