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
            final class $oaPattern0_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Text bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Text && (($oaPattern0_holder.bound = (SemanticInline.Text) $oaPattern0_holder.value) != null))) {
                text.append($oaPattern0_holder.bound.text());
            } else {
final class $oaPattern1_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Emphasis bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Emphasis && (($oaPattern1_holder.bound = (SemanticInline.Emphasis) $oaPattern1_holder.value) != null))) {
                text.append(inline($oaPattern1_holder.bound.children()));
            } else {
final class $oaPattern2_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Strong bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Strong && (($oaPattern2_holder.bound = (SemanticInline.Strong) $oaPattern2_holder.value) != null))) {
                text.append(inline($oaPattern2_holder.bound.children()));
            } else {
final class $oaPattern3_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Code bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Code && (($oaPattern3_holder.bound = (SemanticInline.Code) $oaPattern3_holder.value) != null))) {
                text.append($oaPattern3_holder.bound.text());
            } else {
final class $oaPattern4_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Break bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Break && (($oaPattern4_holder.bound = (SemanticInline.Break) $oaPattern4_holder.value) != null))) {
                text.append('\n');
            } else {
final class $oaPattern5_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Reference bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Reference && (($oaPattern5_holder.bound = (SemanticInline.Reference) $oaPattern5_holder.value) != null))) {
                text.append($oaPattern5_holder.bound.reference().displayText());
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
}
}
}
        }
        return text.toString();
    }

    private static String block(SemanticBlock value, int depth) {
        java.util.Objects.requireNonNull(value);
        final class $oaPattern6_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Paragraph bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Paragraph && (($oaPattern6_holder.bound = (SemanticBlock.Paragraph) $oaPattern6_holder.value) != null))) {
            return inline($oaPattern6_holder.bound.content());
        } else {
final class $oaPattern7_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Heading bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Heading && (($oaPattern7_holder.bound = (SemanticBlock.Heading) $oaPattern7_holder.value) != null))) {
            return inline($oaPattern7_holder.bound.content());
        } else {
final class $oaPattern8_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern8_holder.bound = (SemanticBlock.Quote) $oaPattern8_holder.value) != null))) {
            return prefix(blocks($oaPattern8_holder.bound.content(), depth + 1), "> ");
        } else {
final class $oaPattern9_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.CodeBlock bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.CodeBlock && (($oaPattern9_holder.bound = (SemanticBlock.CodeBlock) $oaPattern9_holder.value) != null))) {
            return $oaPattern9_holder.bound.code();
        } else {
final class $oaPattern10_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ThematicBreak bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.ThematicBreak && (($oaPattern10_holder.bound = (SemanticBlock.ThematicBreak) $oaPattern10_holder.value) != null))) {
            return "---";
        } else {
final class $oaPattern11_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern11_holder.bound = (SemanticBlock.ListBlock) $oaPattern11_holder.value) != null))) {
            return list($oaPattern11_holder.bound, depth);
        } else {
final class $oaPattern12_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Table bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Table && (($oaPattern12_holder.bound = (SemanticBlock.Table) $oaPattern12_holder.value) != null))) {
            return table($oaPattern12_holder.bound);
        } else {
final class $oaPattern13_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern13_holder.bound = (SemanticBlock.Component) $oaPattern13_holder.value) != null))) {
            return $oaPattern13_holder.bound.component().fallbackText();
        }
}
}
}
}
}
}
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
