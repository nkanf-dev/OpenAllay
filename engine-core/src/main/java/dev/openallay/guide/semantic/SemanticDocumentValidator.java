package dev.openallay.guide.semantic;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Revalidates that grounded nodes belong to the target request before publication. */
public final class SemanticDocumentValidator {
    public SemanticDocument validate(
            UUID requestId, SemanticDocument document, SemanticReferenceIndex references) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(references, "references");
        if (!requestId.equals(references.requestId())) {
            throw new IllegalArgumentException("semantic reference index belongs to another request");
        }
        for (SemanticBlock block : document.blocks()) {
            validateBlock(block, references);
        }
        return document;
    }

    private static void validateBlock(
            SemanticBlock block, SemanticReferenceIndex references) {
        Objects.requireNonNull(block);
        if (block instanceof SemanticBlock.Paragraph paragraph) {
            validateInlines(paragraph.content(), references);
        } else if (block instanceof SemanticBlock.Heading heading) {
            validateInlines(heading.content(), references);
        } else if (block instanceof SemanticBlock.ListBlock list) {
            list.items().forEach(item ->
                    item.forEach(value -> validateBlock(value, references)));
        } else if (block instanceof SemanticBlock.Quote quote) {
            quote.content().forEach(value -> validateBlock(value, references));
        } else if (block instanceof SemanticBlock.Table table) {
            validateRow(table.header(), references);
            table.rows().forEach(row -> validateRow(row, references));
        } else if (block instanceof SemanticBlock.CodeBlock ignored) {
        } else if (block instanceof SemanticBlock.ThematicBreak ignored) {
        } else if (block instanceof SemanticBlock.Component component) {
            validateComponent(component.component(), references);
        } else {
            throw new IncompatibleClassChangeError();
        }
    }

    private static void validateRow(
            SemanticBlock.TableRow row, SemanticReferenceIndex references) {
        row.cells().forEach(cell -> validateInlines(cell.content(), references));
    }

    private static void validateInlines(
            List<SemanticInline> inlines, SemanticReferenceIndex references) {
        for (SemanticInline inline : inlines) {
            Objects.requireNonNull(inline);
            if (inline instanceof SemanticInline.Reference value) {
                validateReference(value.reference(), references);
            } else if (inline instanceof SemanticInline.Emphasis value) {
                validateInlines(value.children(), references);
            } else if (inline instanceof SemanticInline.Strong value) {
                validateInlines(value.children(), references);
            } else if (inline instanceof SemanticInline.Text ignored) {
            } else if (inline instanceof SemanticInline.Code ignored) {
            } else if (inline instanceof SemanticInline.Break ignored) {
            } else {
                throw new IncompatibleClassChangeError();
            }
        }
    }

    private static void validateReference(
            SemanticReference reference, SemanticReferenceIndex references) {
        if (reference.grounded()
                && !references.origin(reference.kind(), reference.target())
                        .filter(reference.originInvocationId()::equals).isPresent()) {
            throw new IllegalArgumentException("grounded semantic reference is stale or foreign");
        }
    }

    private static void validateComponent(
            RichComponent component, SemanticReferenceIndex references) {
        Objects.requireNonNull(component);
        if (component instanceof RichComponent.ItemRow row) {
            row.items().forEach(item -> require(
                    references, SemanticReferenceKind.ITEM,
                    item.itemId(), item.originInvocationId()));
        } else if (component instanceof RichComponent.RecipeGrid recipe) {
            requireRecipe(references, recipe.recipe(), recipe.originInvocationId());
        } else if (component instanceof RichComponent.IngredientCheck check) {
            check.ingredients().forEach(item -> require(
                    references, SemanticReferenceKind.ITEM,
                    item.itemId(), item.originInvocationId()));
        } else if (component instanceof RichComponent.CraftabilitySummary summary) {
            requireRecipe(references, summary.recipe(), summary.originInvocationId());
        } else if (component instanceof RichComponent.SourceSummary summary) {
            summary.sources().forEach(source -> require(
                    references, SemanticReferenceKind.SOURCE,
                    source.sourceId(), source.originInvocationId()));
        } else if (component instanceof RichComponent.ProgressSteps ignored) {
        } else if (component instanceof RichComponent.StatusBadge ignored) {
        } else if (component instanceof RichComponent.ChoiceGroup ignored) {
        } else {
            throw new IncompatibleClassChangeError();
        }
    }

    private static void requireRecipe(
            SemanticReferenceIndex references,
            dev.openallay.context.RecipeReference recipe,
            String origin) {
        require(references, SemanticReferenceKind.RECIPE,
                RecipeSemanticHandle.encode(recipe), origin);
    }

    private static void require(
            SemanticReferenceIndex references,
            SemanticReferenceKind kind,
            String target,
            String origin) {
        if (!references.origin(kind, target).filter(origin::equals).isPresent()) {
            throw new IllegalArgumentException("component reference is stale or foreign");
        }
    }
}
