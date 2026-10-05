package dev.openallay.guide.semantic;

import java.util.ArrayList;
import java.util.List;

final class SemanticPlainText {
    private SemanticPlainText() {}

    static String render(List<SemanticBlock> blocks) {
        List<String> rendered = new ArrayList<>();
        for (SemanticBlock block : blocks) {
            String text = block(block, 0).stripTrailing();
            if (!text.isEmpty()) {
                rendered.add(text);
            }
        }
        return String.join("\n\n", rendered);
    }

    static String inline(List<SemanticInline> values) {
        StringBuilder text = new StringBuilder();
        for (SemanticInline value : values) {
            java.util.Objects.requireNonNull(value);
            if (value instanceof SemanticInline.Text literal) {
                text.append(literal.text());
            } else if (value instanceof SemanticInline.Emphasis emphasis) {
                text.append(inline(emphasis.children()));
            } else if (value instanceof SemanticInline.Strong strong) {
                text.append(inline(strong.children()));
            } else if (value instanceof SemanticInline.Code code) {
                text.append(code.text());
            } else if (value instanceof SemanticInline.Break ignored) {
                text.append('\n');
            } else if (value instanceof SemanticInline.Reference reference) {
                text.append(reference.reference().displayText());
            } else {
                throw new IncompatibleClassChangeError();
            }
        }
        return text.toString();
    }

    private static String block(SemanticBlock value, int depth) {
        java.util.Objects.requireNonNull(value);
        if (value instanceof SemanticBlock.Paragraph paragraph) {
            return inline(paragraph.content());
        } else if (value instanceof SemanticBlock.Heading heading) {
            return inline(heading.content());
        } else if (value instanceof SemanticBlock.Quote quote) {
            return prefix(blocks(quote.content(), depth + 1), "> ");
        } else if (value instanceof SemanticBlock.CodeBlock code) {
            return code.code();
        } else if (value instanceof SemanticBlock.ThematicBreak ignored) {
            return "---";
        } else if (value instanceof SemanticBlock.ListBlock list) {
            return list(list, depth);
        } else if (value instanceof SemanticBlock.Table table) {
            return table(table);
        } else if (value instanceof SemanticBlock.Component component) {
            return component.component().fallbackText();
        }
        throw new IncompatibleClassChangeError();
    }

    private static String blocks(List<SemanticBlock> values, int depth) {
        return values.stream().map(value -> block(value, depth)).filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private static String list(SemanticBlock.ListBlock list, int depth) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < list.items().size(); index++) {
            if (index > 0) {
                text.append('\n');
            }
            text.append("  ".repeat(depth));
            text.append(list.ordered() ? (list.start() + index) + ". " : "- ");
            text.append(blocks(list.items().get(index), depth + 1).strip());
        }
        return text.toString();
    }

    private static String table(SemanticBlock.Table table) {
        List<String> rows = new ArrayList<>();
        rows.add(tableRow(table.header()));
        for (SemanticBlock.TableRow row : table.rows()) {
            rows.add(tableRow(row));
        }
        return String.join("\n", rows);
    }

    private static String tableRow(SemanticBlock.TableRow row) {
        return row.cells().stream().map(cell -> inline(cell.content()).strip())
                .reduce((left, right) -> left + " | " + right).orElse("");
    }

    private static String prefix(String value, String prefix) {
        return value.lines().map(line -> prefix + line).reduce((left, right) -> left + "\n" + right)
                .orElse(prefix.stripTrailing());
    }
}
