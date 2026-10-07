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
        final class $oaPattern0_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Paragraph bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Paragraph && (($oaPattern0_holder.bound = (SemanticBlock.Paragraph) $oaPattern0_holder.value) != null))) {
            validateInlines($oaPattern0_holder.bound.content(), references);
        } else {
final class $oaPattern1_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Heading bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Heading && (($oaPattern1_holder.bound = (SemanticBlock.Heading) $oaPattern1_holder.value) != null))) {
            validateInlines($oaPattern1_holder.bound.content(), references);
        } else {
final class $oaPattern2_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern2_holder.bound = (SemanticBlock.ListBlock) $oaPattern2_holder.value) != null))) {
            $oaPattern2_holder.bound.items().forEach(item ->
                    item.forEach(value -> validateBlock(value, references)));
        } else {
final class $oaPattern3_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern3_holder.bound = (SemanticBlock.Quote) $oaPattern3_holder.value) != null))) {
            $oaPattern3_holder.bound.content().forEach(value -> validateBlock(value, references));
        } else {
final class $oaPattern4_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Table bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Table && (($oaPattern4_holder.bound = (SemanticBlock.Table) $oaPattern4_holder.value) != null))) {
            validateRow($oaPattern4_holder.bound.header(), references);
            $oaPattern4_holder.bound.rows().forEach(row -> validateRow(row, references));
        } else {
final class $oaPattern5_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.CodeBlock bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.CodeBlock && (($oaPattern5_holder.bound = (SemanticBlock.CodeBlock) $oaPattern5_holder.value) != null))) {
        } else {
final class $oaPattern6_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ThematicBreak bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ThematicBreak && (($oaPattern6_holder.bound = (SemanticBlock.ThematicBreak) $oaPattern6_holder.value) != null))) {
        } else {
final class $oaPattern7_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern7_holder.bound = (SemanticBlock.Component) $oaPattern7_holder.value) != null))) {
            validateComponent($oaPattern7_holder.bound.component(), references);
        } else {
            throw new IncompatibleClassChangeError();
        }
}
}
}
}
}
}
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
            final class $oaPattern8_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Reference bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Reference && (($oaPattern8_holder.bound = (SemanticInline.Reference) $oaPattern8_holder.value) != null))) {
                validateReference($oaPattern8_holder.bound.reference(), references);
            } else {
final class $oaPattern9_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Emphasis bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Emphasis && (($oaPattern9_holder.bound = (SemanticInline.Emphasis) $oaPattern9_holder.value) != null))) {
                validateInlines($oaPattern9_holder.bound.children(), references);
            } else {
final class $oaPattern10_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Strong bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Strong && (($oaPattern10_holder.bound = (SemanticInline.Strong) $oaPattern10_holder.value) != null))) {
                validateInlines($oaPattern10_holder.bound.children(), references);
            } else {
final class $oaPattern11_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Text bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Text && (($oaPattern11_holder.bound = (SemanticInline.Text) $oaPattern11_holder.value) != null))) {
            } else {
final class $oaPattern12_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Code bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Code && (($oaPattern12_holder.bound = (SemanticInline.Code) $oaPattern12_holder.value) != null))) {
            } else {
final class $oaPattern13_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Break bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Break && (($oaPattern13_holder.bound = (SemanticInline.Break) $oaPattern13_holder.value) != null))) {
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
}
}
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
        final class $oaPattern14_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern14_holder.bound = (RichComponent.ItemRow) $oaPattern14_holder.value) != null))) {
            $oaPattern14_holder.bound.items().forEach(item -> require(
                    references, SemanticReferenceKind.ITEM,
                    item.itemId(), item.originInvocationId()));
        } else {
final class $oaPattern15_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern15_holder.bound = (RichComponent.RecipeGrid) $oaPattern15_holder.value) != null))) {
            requireRecipe(references, $oaPattern15_holder.bound.recipe(), $oaPattern15_holder.bound.originInvocationId());
        } else {
final class $oaPattern16_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if ((($oaPattern16_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern16_holder.bound = (RichComponent.IngredientCheck) $oaPattern16_holder.value) != null))) {
            $oaPattern16_holder.bound.ingredients().forEach(item -> require(
                    references, SemanticReferenceKind.ITEM,
                    item.itemId(), item.originInvocationId()));
        } else {
final class $oaPattern17_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if ((($oaPattern17_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern17_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern17_holder.value) != null))) {
            requireRecipe(references, $oaPattern17_holder.bound.recipe(), $oaPattern17_holder.bound.originInvocationId());
        } else {
final class $oaPattern18_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern18_holder.bound = (RichComponent.SourceSummary) $oaPattern18_holder.value) != null))) {
            $oaPattern18_holder.bound.sources().forEach(source -> require(
                    references, SemanticReferenceKind.SOURCE,
                    source.sourceId(), source.originInvocationId()));
        } else {
final class $oaPattern19_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern19_holder.bound = (RichComponent.ProgressSteps) $oaPattern19_holder.value) != null))) {
        } else {
final class $oaPattern20_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern20_holder.bound = (RichComponent.StatusBadge) $oaPattern20_holder.value) != null))) {
        } else {
final class $oaPattern21_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern21_holder.bound = (RichComponent.ChoiceGroup) $oaPattern21_holder.value) != null))) {
        } else {
            throw new IncompatibleClassChangeError();
        }
}
}
}
}
}
}
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
