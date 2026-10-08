package dev.openallay.context;

import com.google.gson.JsonObject;
import dev.openallay.recipe.*;
import dev.openallay.value.ValueSchemas;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Same complete canonical recipe/context owners execute original-modern and Java8. */
public final class RecipeContextJava8Fixture {
    private RecipeContextJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        if (java8) {
            equal("1.8", System.getProperty("java.specification.version"));
            for (Class<?> type : Arrays.<Class<?>>asList(IngredientAlternativeSnapshot.class, IngredientRequirementSnapshot.class,
                    FluidRequirementSnapshot.class, RecipeOutputSnapshot.class, RecipeEntrySnapshot.class,
                    RecipeReference.class, RecipeSnapshot.class, SourceObservationCollector.class,
                    RecipeProviderSnapshot.class, RecipeSemanticGroup.class, RecipeCatalogDiagnostic.class,
                    RecipeProviderDiagnostic.class, RecipeCanonicalizer.class)) classMajor(type);
        }
        EvidenceMetadata evidence = evidence(Instant.EPOCH);
        IngredientAlternativeSnapshot alternative = new IngredientAlternativeSnapshot("item", "test:input", Arrays.asList("test:input"));
        IngredientRequirementSnapshot requirement = new IngredientRequirementSnapshot("slot0", 2, true, Arrays.asList(alternative));
        FluidRequirementSnapshot fluid = new FluidRequirementSnapshot("test:water", 1000, true);
        RecipeOutputSnapshot output = new RecipeOutputSnapshot(new ItemStackSnapshot("test:output", 1, "Output"), 0.75);
        JsonObject extension = new JsonObject(); extension.addProperty("value", 1.2300);
        Map<String, JsonObject> extensionMap = new LinkedHashMap<>(); extensionMap.put("test:extension", extension);
        RecipeEntrySnapshot input = recipe(new RecipeReference("test:provider", zeros(), "test:recipe"), evidence,
                requirement, fluid, output, extensionMap);
        extension.addProperty("later", true);
        check(!input.extensions().get("test:extension").has("later"), "constructor extension copy");
        input.extensions().get("test:extension").addProperty("mutated", true);
        check(!input.extensions().get("test:extension").has("mutated"), "accessor extension copy");
        String fingerprint = RecipeCanonicalizer.semanticFingerprint(input);
        RecipeProviderSnapshot provider = RecipeProviderSnapshot.available("test:provider", DataCompleteness.COMPLETE,
                Arrays.asList(input), Collections.<RecipeProviderDiagnostic>emptyList());
        RecipeEntrySnapshot rebound = provider.recipes().get(0);
        equal(provider.generation(), rebound.reference().generation());
        equal(RecipeCanonicalizer.providerGeneration("test:provider", provider.recipes()), provider.generation());
        equal(fingerprint, RecipeCanonicalizer.semanticFingerprint(rebound));
        RecipeSemanticGroup group = new RecipeSemanticGroup(fingerprint, rebound,
                Arrays.asList(rebound.reference()), Arrays.asList(evidence));
        RecipeReference second = new RecipeReference("other:provider", zeros(), "test:recipe");
        RecipeCatalogDiagnostic diagnostic = new RecipeCatalogDiagnostic("conflict", "test:recipe",
                Arrays.asList(rebound.reference(), second), "Conflicting recipe");
        RecipeSnapshot snapshot = new RecipeSnapshot(evidence, provider.recipes(), Arrays.asList(provider), Arrays.asList(group), Arrays.asList(diagnostic));
        equal(1, snapshot.recipes().size());
        for (Object value : Arrays.asList(alternative, requirement, fluid, output, rebound.reference(),
                RecipeProviderSnapshot.unavailable("test:provider", "missing", "Missing"),
                RecipeProviderSnapshot.failed("test:provider", "failed", "Failed"), diagnostic)) System.out.println(value.toString());
        System.out.println("fingerprint=" + fingerprint);
        System.out.println("generation=" + provider.generation());
        System.out.println("snapshotCounts=" + snapshot.recipes().size() + "/" + snapshot.providers().size() + "/" + snapshot.groups().size() + "/" + snapshot.diagnostics().size());
        equal(input, recipe(input.reference(), evidence, requirement, fluid, output, Collections.singletonMap("test:extension", input.extensions().get("test:extension"))));
        int hash = 0;
        for (Object value : new Object[] {input.reference(), input.id(), input.type(), input.layout(), input.workstation(),
                input.ingredients(), input.catalysts(), input.fluids(), input.outputs(), input.byproducts(), input.processing(),
                input.conditions(), input.extensions(), input.unlockState(), input.evidence()}) hash = 31 * hash + java.util.Objects.hashCode(value);
        equal(hash, input.hashCode());
        failure("alternativeKind", () -> new IngredientAlternativeSnapshot("other", "test:input", Arrays.asList("test:input")));
        failure("alternativeId", () -> new IngredientAlternativeSnapshot("item", "invalid", Arrays.asList("test:input")));
        failure("alternativeResolved", () -> new IngredientAlternativeSnapshot("tag", "test:tag", Arrays.asList("invalid")));
        failure("requirementCount", () -> new IngredientRequirementSnapshot("slot", 0, true, Arrays.asList(alternative)));
        failure("requirementEmpty", () -> new IngredientRequirementSnapshot("slot", 1, true, Collections.<IngredientAlternativeSnapshot>emptyList()));
        failure("fluidAmount", () -> new FluidRequirementSnapshot("test:water", 0, true));
        failure("outputNan", () -> new RecipeOutputSnapshot(output.stack(), Double.NaN));
        failure("outputProbability", () -> new RecipeOutputSnapshot(output.stack(), 1.1));
        failure("referenceGeneration", () -> new RecipeReference("test:provider", "ABC", "test:recipe"));
        failure("duplicateRequirements", () -> new RecipeEntrySnapshot(input.reference(), input.id(), input.type(), input.layout(), input.workstation(),
                Arrays.asList(requirement, requirement), input.catalysts(), input.fluids(), input.outputs(), input.byproducts(), input.processing(), input.conditions(), input.extensions(), input.unlockState(), evidence));
        failure("providerGeneration", () -> new RecipeProviderSnapshot("test:provider", zeros(), RecipeProviderState.AVAILABLE,
                DataCompleteness.COMPLETE, provider.recipes(), Collections.<RecipeProviderDiagnostic>emptyList()));
        failure("providerUnavailableRecipes", () -> new RecipeProviderSnapshot("test:provider", null, RecipeProviderState.UNAVAILABLE,
                DataCompleteness.UNKNOWN, provider.recipes(), Collections.<RecipeProviderDiagnostic>emptyList()));
        failure("groupEmpty", () -> new RecipeSemanticGroup(fingerprint, rebound, Collections.<RecipeReference>emptyList(), Collections.<EvidenceMetadata>emptyList()));
        failure("catalogOneReference", () -> new RecipeCatalogDiagnostic("conflict", "test:recipe", Arrays.asList(rebound.reference()), "Conflict"));
        failure("diagnosticBlank", () -> new RecipeProviderDiagnostic("test:provider", " ", "Failure"));
        SourceObservationCollector collector = new SourceObservationCollector();
        collector.add(evidence); collector.add(new SourceObservation(evidence(Instant.ofEpochSecond(4)), Instant.ofEpochSecond(6)));
        equal(1, collector.snapshot().size());
        equal(Instant.EPOCH, collector.snapshot().get(0).firstCapturedAt());
        equal(Instant.ofEpochSecond(6), collector.snapshot().get(0).lastCapturedAt());
        collector.addAll(Arrays.asList(new SourceObservation(evidence(Instant.ofEpochSecond(2)), Instant.ofEpochSecond(3))));
        equal(1, collector.snapshot().size());
        System.out.println("collector=" + collector.snapshot().get(0).firstCapturedAt() + "/" + collector.snapshot().get(0).lastCapturedAt());
        try { collector.snapshot().clear(); throw new AssertionError("mutable collector snapshot"); } catch (UnsupportedOperationException expected) {}
        if (java8) {
            equal(input, ValueSchemas.of(RecipeEntrySnapshot.class).construct(new Object[] {input.reference(), input.id(), input.type(), input.layout(),
                    input.workstation(), input.ingredients(), input.catalysts(), input.fluids(), input.outputs(), input.byproducts(), input.processing(),
                    input.conditions(), input.extensions(), input.unlockState(), input.evidence()}));
            check(ValueSchemas.of(RecipeSnapshot.class).components().get(1).genericType() instanceof java.lang.reflect.ParameterizedType, "generic recipe metadata");
        }
        System.out.println("PASS canonical recipe/context values and collector");
    }
    private static RecipeEntrySnapshot recipe(RecipeReference reference, EvidenceMetadata evidence, IngredientRequirementSnapshot requirement,
            FluidRequirementSnapshot fluid, RecipeOutputSnapshot output, Map<String, JsonObject> extensions) {
        return new RecipeEntrySnapshot(reference, "test:recipe", "test:crafting", new RecipeLayoutSnapshot(1, 1, true), "test:table",
                Arrays.asList(requirement), Collections.<IngredientRequirementSnapshot>emptyList(), Arrays.asList(fluid), Arrays.asList(output),
                Collections.<RecipeOutputSnapshot>emptyList(), new RecipeProcessingSnapshot(20L, 40L, 100.0), Arrays.asList("unlocked"), extensions,
                RecipeUnlockState.UNLOCKED, evidence);
    }
    private static EvidenceMetadata evidence(Instant time) {
        return new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST, DataCompleteness.COMPLETE, time,
                "test:source", "test:proof", "1.12.2", "forge", Collections.singletonMap("test:key", "value"));
    }
    private static String zeros() { return "0000000000000000000000000000000000000000000000000000000000000000"; }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void classMajor(Class<?> owner) throws Exception {
        try (InputStream input = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int position = 0;
            while (position < 8) { int count = input.read(header, position, 8 - position); check(count > 0, "header"); position += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
