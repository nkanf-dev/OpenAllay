package dev.openallay.api.extension;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiValueTest {
    private static final SupportTarget OLD = new SupportTarget("forge", "1.12.2", "[0.4.1,)", "[0.3.0,0.4.0)");
    private static JavascriptHostMethod method(String name) {
        return new JavascriptHostMethod(name, Arrays.asList(JavascriptHostValueType.STRING),
                JavascriptHostValueType.STRING,
                new JavascriptHostMethod.Invoker() {
                    @Override public String invoke(ExtensionInvocation context, List<String> json) { return json.get(0); }
                });
    }
    private static SkillSource skill(String directory) {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put(directory + "/SKILL.md", "# Skill");
        return new SkillSource("fixture:skill", directory + "/SKILL.md", files);
    }
    @Test void supportIsAnExplicitUnionAndFactsStaySeparate() {
        SupportTarget future = new SupportTarget("unreleased-loader", "26.3", "[0.4.1,)", "0.3.0");
        List<SupportTarget> targets = new ArrayList<SupportTarget>(Arrays.asList(OLD, future));
        Set<String> features = new HashSet<String>(Arrays.asList("minecraft:world-access"));
        Set<String> validated = new HashSet<String>(Arrays.asList("forge-1.12.2-java8"));
        SupportDeclaration declaration = new SupportDeclaration(targets, 8, features, validated);
        targets.clear(); features.clear(); validated.clear();
        assertEquals(Arrays.asList(OLD, future), declaration.targets());
        assertEquals(Collections.singleton("forge-1.12.2-java8"), declaration.validatedTargetIds());
        assertEquals(Collections.singleton("minecraft:world-access"), declaration.requiredHostFeatures());
        assertThrows(UnsupportedOperationException.class, () -> declaration.targets().clear());
        assertThrows(UnsupportedOperationException.class, () -> declaration.requiredHostFeatures().clear());
        assertThrows(UnsupportedOperationException.class, () -> declaration.validatedTargetIds().clear());
        assertTrue(new SupportDeclaration(Arrays.asList(future), 8,
                Collections.<String>emptySet(), Collections.<String>emptySet()).validatedTargetIds().isEmpty());
    }
    @Test void supportRejectsEmptyDuplicateNullAndInvalidFacts() {
        assertThrows(IllegalArgumentException.class, () -> new SupportDeclaration(
                Collections.<SupportTarget>emptyList(), 8, Collections.<String>emptySet(), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class, () -> new SupportDeclaration(
                Arrays.asList(OLD, OLD), 8, Collections.<String>emptySet(), Collections.<String>emptySet()));
        assertThrows(NullPointerException.class, () -> new SupportDeclaration(
                Arrays.asList((SupportTarget)null), 8, Collections.<String>emptySet(), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class, () -> new SupportDeclaration(
                Arrays.asList(OLD), 7, Collections.<String>emptySet(), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class, () -> new SupportDeclaration(
                Arrays.asList(OLD), 8, Collections.singleton("bad feature"), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class, () -> new SupportDeclaration(
                Arrays.asList(OLD), 8, Collections.<String>emptySet(), Collections.singleton("*")));
        assertThrows(IllegalArgumentException.class, () -> new SupportTarget("Forge", "1.12.2", "0.4.1", "0.3.0"));
    }
    @Test void rangeSyntaxOnlyDoesNotInventVersionOrdering() {
        for (String valid : Arrays.asList("1.12.2", "26.3-pre1", "[1.12.2]", "[1.12.2,1.13)", "(,26.3]", "[0.4.1,)"))
            assertEquals(valid, new SupportTarget("future", valid, "0.4.1", "0.3.0").minecraftVersionRange());
        // Ordering/matching is core-owned, not a hidden semver engine in the SDK.
        assertEquals("[26.3,1.12.2]", new SupportTarget("future", "[26.3,1.12.2]", "0.4.1", "0.3.0").minecraftVersionRange());
        for (String invalid : Arrays.asList("", " ", "*", "[1.12.2", "1.12.2]", "(1.12.2)", "[,]", "(,)", "[1,2,3]", "[1,2] [3,4]", "[,2]", "[1,]"))
            assertThrows(IllegalArgumentException.class, () -> new SupportTarget("forge", invalid, "0.4.1", "0.3.0"), invalid);
    }
    @Test void environmentCopiesExactFactsWithoutGrantSemantics() {
        Set<String> versions = new HashSet<String>(Arrays.asList("0.2.2", "0.3.0"));
        Set<String> features = new HashSet<String>(Arrays.asList("world-access"));
        ExtensionEnvironment environment = new ExtensionEnvironment("unknown-loader", "26.3", "0.4.1", versions, 25, features);
        versions.clear(); features.clear();
        assertEquals(2, environment.openAllayApiVersions().size());
        assertEquals(25, environment.javaVersion());
        assertEquals(Collections.singleton("world-access"), environment.hostFeatures());
        assertThrows(UnsupportedOperationException.class, () -> environment.hostFeatures().clear());
        assertThrows(UnsupportedOperationException.class, () -> environment.openAllayApiVersions().clear());
        assertThrows(IllegalArgumentException.class, () -> new ExtensionEnvironment("forge", "1.12.2", "0.4.1",
                Collections.<String>emptySet(), 8, Collections.<String>emptySet()));
    }
    @Test void advisoryRequirementsAreImmutableAndValidateDistinctIdentityKinds() {
        Set<String> caps = new HashSet<String>(Arrays.asList("world_write", "fixture:write"));
        Set<String> exts = new HashSet<String>(Arrays.asList("fixture:builder"));
        Set<String> skills = new HashSet<String>(Arrays.asList("minecraft-builder"));
        ExtensionRequirements requirements = new ExtensionRequirements(caps, exts, skills);
        caps.clear(); exts.clear(); skills.clear();
        assertEquals(2, requirements.capabilities().size());
        assertFalse(requirements.isEmpty()); assertTrue(ExtensionRequirements.EMPTY.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> requirements.extensions().clear());
        assertThrows(UnsupportedOperationException.class, () -> requirements.skills().clear());
        assertThrows(IllegalArgumentException.class, () -> new ExtensionRequirements(
                Collections.<String>emptySet(), Collections.singleton("unnamespaced"), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionRequirements(
                Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.singleton("bad_skill")));
    }
    @Test void descriptorsAndValueObjectsHaveFieldEquality() {
        SupportDeclaration support = new SupportDeclaration(Arrays.asList(OLD), 8,
                Collections.<String>emptySet(), Collections.<String>emptySet());
        ExtensionDescriptor a = new ExtensionDescriptor("fixture:hello", "Hello", "1.0.0", "Fixture", "Summary", "fixture:src", support, ExtensionRequirements.EMPTY);
        ExtensionDescriptor b = new ExtensionDescriptor("fixture:hello", "Hello", "1.0.0", "Fixture", "Summary", "fixture:src", support, ExtensionRequirements.EMPTY);
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode()); assertNotEquals(a, support);
        assertEquals(OLD, new SupportTarget("forge", "1.12.2", "[0.4.1,)", "[0.3.0,0.4.0)"));
        assertEquals(new ResultViewDeclaration("fixture:result", ResultViewDeclaration.Kind.TABLE, "Table"),
                new ResultViewDeclaration("fixture:result", ResultViewDeclaration.Kind.TABLE, "Table"));
        assertEquals(skill("hello"), skill("hello"));
        assertEquals(ExtensionContribution.empty(), ExtensionContribution.empty());
        assertEquals(new WorldSession.RepairOutcome("{}", "{}"), new WorldSession.RepairOutcome("{}", "{}"));
        RuntimeException failure = new ExtensionException("read_failed", "Readback failed.");
        assertEquals(new WorldSession.WriteOutcome(null, true, failure), new WorldSession.WriteOutcome(null, true, failure));
    }
    @Test void exactIDsAndTextAreNotSilentlyNormalized() {
        assertThrows(IllegalArgumentException.class, () -> new JavascriptModuleSource(" fixture:module", "x"));
        assertThrows(IllegalArgumentException.class, () -> new ResultViewDeclaration("fixture:Bad", ResultViewDeclaration.Kind.GENERIC, "Description"));
        assertThrows(IllegalArgumentException.class, () -> new JavascriptModuleSource("fixture:module", "\u2003"));
        assertThrows(IllegalArgumentException.class, () -> new ResultViewDeclaration("fixture:view", ResultViewDeclaration.Kind.GENERIC, ""));
        assertThrows(NullPointerException.class, () -> new ResultViewDeclaration("fixture:view", null, "View"));
        String source = "  module.exports = {};\n";
        assertEquals(source, new JavascriptModuleSource("fixture:module", source).source());
    }
    @Test void skillFilesNormalizeCopyAndRequireTheEntry() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("hello/./SKILL.md", "# Hello"); files.put("hello/sub/../notes.txt", "");
        SkillSource skill = new SkillSource("fixture:hello", "hello/./SKILL.md", files);
        files.clear();
        assertEquals("hello/SKILL.md", skill.entryPath()); assertEquals("hello", skill.directoryName());
        assertEquals("", skill.files().get("hello/notes.txt")); assertEquals(SkillSource.Origin.EXTERNAL, skill.origin());
        assertThrows(UnsupportedOperationException.class, () -> skill.files().clear());
        for (String bad : Arrays.asList("../SKILL.md", "/hello/SKILL.md", "hello\\SKILL.md", "C:/hello/SKILL.md", "SKILL.md"))
            assertThrows(IllegalArgumentException.class, () -> new SkillSource("fixture:hello", bad, Collections.<String,String>emptyMap()));
        Map<String, String> duplicate = new LinkedHashMap<String, String>();
        duplicate.put("hello/./SKILL.md", "one"); duplicate.put("hello/SKILL.md", "two");
        assertThrows(IllegalArgumentException.class, () -> new SkillSource("fixture:hello", "hello/SKILL.md", duplicate));
        assertThrows(IllegalArgumentException.class, () -> new SkillSource("fixture:hello", "hello/SKILL.md", Collections.<String,String>emptyMap()));
    }
    @Test void evidenceCopiesDetailsAndUsesCurrentClosedVocabularies() {
        Map<String, String> details = new LinkedHashMap<String, String>(); details.put("fixture:detail", "value");
        ExtensionEvidence evidence = new ExtensionEvidence(ExtensionEvidence.Authority.DETERMINISTIC_TEST,
                ExtensionEvidence.Completeness.PARTIAL, Instant.EPOCH, "fixture:source", "fixture:provenance", "1.12.2", "forge", details);
        details.clear(); assertEquals("value", evidence.details().get("fixture:detail"));
        assertThrows(UnsupportedOperationException.class, () -> evidence.details().clear());
        assertEquals(evidence, new ExtensionEvidence(evidence.authority(), evidence.completeness(), evidence.capturedAt(),
                evidence.sourceId(), evidence.provenance(), evidence.gameVersion(), evidence.loader(), evidence.details()));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionEvidence(evidence.authority(), evidence.completeness(),
                Instant.EPOCH, "bad", "fixture:provenance", "1.12.2", "forge", Collections.<String,String>emptyMap()));
    }
    @Test void methodAndBindingListsAreCopiedAndValidateTheirFields() {
        JavascriptHostMethod.Invoker invoker = method("echo").invoker();
        List<JavascriptHostValueType> parameters = new ArrayList<JavascriptHostValueType>(Arrays.asList(JavascriptHostValueType.JSON));
        JavascriptHostMethod method = new JavascriptHostMethod("echo", parameters, JavascriptHostValueType.JSON, invoker);
        parameters.clear();
        assertEquals("echo", method.name());
        assertEquals(Arrays.asList(JavascriptHostValueType.JSON), method.parameters());
        assertEquals(JavascriptHostValueType.JSON, method.result());
        assertSame(invoker, method.invoker());
        assertThrows(UnsupportedOperationException.class, () -> method.parameters().clear());
        JavascriptHostMethod equal = new JavascriptHostMethod("echo", Arrays.asList(JavascriptHostValueType.JSON),
                JavascriptHostValueType.JSON, invoker);
        assertEquals(method, equal); assertEquals(method.hashCode(), equal.hashCode());
        assertNotEquals(method, new JavascriptHostMethod("other", method.parameters(), method.result(), invoker));
        assertNotEquals(method, new JavascriptHostMethod("echo", Collections.<JavascriptHostValueType>emptyList(), method.result(), invoker));
        assertNotEquals(method, new JavascriptHostMethod("echo", method.parameters(), JavascriptHostValueType.NULL, invoker));
        assertNotEquals(method, new JavascriptHostMethod("echo", method.parameters(), method.result(), method("echo").invoker()));
        List<JavascriptHostMethod> methods = new ArrayList<JavascriptHostMethod>(Arrays.asList(method));
        JavascriptHostBinding binding = new JavascriptHostBinding("fixture:host", methods); methods.clear();
        assertEquals(Arrays.asList(method), binding.methods());
        assertEquals(binding, new JavascriptHostBinding("fixture:host", Arrays.asList(equal)));
        assertThrows(UnsupportedOperationException.class, () -> binding.methods().clear());
        assertThrows(IllegalArgumentException.class, () -> new JavascriptHostBinding("fixture:host", Arrays.asList(method, method)));
        for (String bad : Arrays.asList("constructor", "__proto__", "prototype", "0bad", "has space"))
            assertThrows(IllegalArgumentException.class, () -> method(bad));
        assertThrows(NullPointerException.class, () -> new JavascriptHostMethod("echo", null, JavascriptHostValueType.NULL, invoker));
        assertThrows(NullPointerException.class, () -> new JavascriptHostMethod("echo", Arrays.asList((JavascriptHostValueType)null),
                JavascriptHostValueType.NULL, invoker));
        assertThrows(NullPointerException.class, () -> new JavascriptHostMethod("echo", Collections.<JavascriptHostValueType>emptyList(), null, invoker));
        assertThrows(NullPointerException.class, () -> new JavascriptHostMethod("echo", Collections.<JavascriptHostValueType>emptyList(),
                JavascriptHostValueType.NULL, null));
    }
    @Test void contributionsCopyCollectionsAndRejectDuplicateRegistrationIDs() {
        JavascriptModuleSource module = new JavascriptModuleSource("fixture:module", "module.exports = {};");
        List<JavascriptModuleSource> modules = new ArrayList<JavascriptModuleSource>(Arrays.asList(module));
        List<SkillSource> skills = new ArrayList<SkillSource>(Arrays.asList(skill("hello")));
        ExtensionContribution contribution = new ExtensionContribution(modules, skills,
                Collections.<ResultViewDeclaration>emptyList(), Collections.<JavascriptInvocationParticipant>emptyList(),
                Collections.<JavascriptHostBinding>emptyList());
        modules.clear(); skills.clear();
        assertEquals(Arrays.asList(module), contribution.javascriptModules());
        assertEquals(Arrays.asList(skill("hello")), contribution.skills());
        assertThrows(UnsupportedOperationException.class, () -> contribution.javascriptModules().clear());
        assertThrows(UnsupportedOperationException.class, () -> contribution.skills().clear());
        assertThrows(IllegalArgumentException.class, () -> new ExtensionContribution(Arrays.asList(module,module),
                Collections.<SkillSource>emptyList(), Collections.<ResultViewDeclaration>emptyList(),
                Collections.<JavascriptInvocationParticipant>emptyList(), Collections.<JavascriptHostBinding>emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionContribution(Arrays.asList(module),
                Collections.<SkillSource>emptyList(), Collections.<ResultViewDeclaration>emptyList(),
                Collections.<JavascriptInvocationParticipant>emptyList(), Arrays.asList(new JavascriptHostBinding("fixture:module", Arrays.asList(method("echo"))))));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Arrays.asList(skill("hello"),skill("hello")), Collections.<ResultViewDeclaration>emptyList(),
                Collections.<JavascriptInvocationParticipant>emptyList(), Collections.<JavascriptHostBinding>emptyList()));
        assertThrows(NullPointerException.class, () -> new ExtensionContribution(Arrays.asList((JavascriptModuleSource)null),
                Collections.<SkillSource>emptyList(), Collections.<ResultViewDeclaration>emptyList(),
                Collections.<JavascriptInvocationParticipant>emptyList(), Collections.<JavascriptHostBinding>emptyList()));
    }
    @Test void sdkFailureHasStableCodeSafeSummaryAndOptionalDiagnosticCause() {
        RuntimeException cause = new RuntimeException("diagnostic");
        ExtensionException failure = new ExtensionException("javascript_native_unavailable", "World access is unavailable.", cause);
        assertEquals("javascript_native_unavailable", failure.code()); assertEquals("World access is unavailable.", failure.summary());
        assertEquals(failure.summary(), failure.getMessage()); assertSame(cause, failure.getCause());
        assertNull(new ExtensionException("cancelled", "Cancelled.").getCause());
        for (String bad : Arrays.asList("", "HAS SPACE", "fixture:denied"))
            assertThrows(IllegalArgumentException.class, () -> new ExtensionException(bad, "Safe summary."));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionException("denied", " "));
    }
    @Test void allContributionCategoriesHaveExactDuplicateAndCopyChecks() {
        ResultViewDeclaration view = new ResultViewDeclaration("fixture:view", ResultViewDeclaration.Kind.GENERIC, "View");
        JavascriptInvocationParticipant participant = new JavascriptInvocationParticipant() {
            @Override public String id() { return "fixture:scope"; }
            @Override public AutoCloseable open(ExtensionInvocation context) { return new AutoCloseable() { @Override public void close() {} }; }
        };
        JavascriptHostBinding binding = new JavascriptHostBinding("fixture:host", Arrays.asList(method("echo")));
        List<ResultViewDeclaration> views = new ArrayList<ResultViewDeclaration>(Arrays.asList(view));
        List<JavascriptInvocationParticipant> participants = new ArrayList<JavascriptInvocationParticipant>(Arrays.asList(participant));
        List<JavascriptHostBinding> bindings = new ArrayList<JavascriptHostBinding>(Arrays.asList(binding));
        ExtensionContribution contribution = new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Collections.<SkillSource>emptyList(), views, participants, bindings);
        views.clear(); participants.clear(); bindings.clear();
        assertEquals(Arrays.asList(view), contribution.resultViews());
        assertEquals(Arrays.asList(participant), contribution.javascriptInvocationParticipants());
        assertEquals(Arrays.asList(binding), contribution.hostBindings());
        assertThrows(UnsupportedOperationException.class, () -> contribution.resultViews().clear());
        assertThrows(UnsupportedOperationException.class, () -> contribution.javascriptInvocationParticipants().clear());
        assertThrows(UnsupportedOperationException.class, () -> contribution.hostBindings().clear());
        ExtensionContribution equal = new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Collections.<SkillSource>emptyList(), Arrays.asList(view), Arrays.asList(participant), Arrays.asList(binding));
        assertEquals(contribution, equal); assertEquals(contribution.hashCode(), equal.hashCode());
        assertNotEquals(contribution, ExtensionContribution.empty());
        assertThrows(IllegalArgumentException.class, () -> new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Collections.<SkillSource>emptyList(), Arrays.asList(view,view), Arrays.asList(participant), Arrays.asList(binding)));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Collections.<SkillSource>emptyList(), Arrays.asList(view), Arrays.asList(participant,participant), Arrays.asList(binding)));
        assertThrows(IllegalArgumentException.class, () -> new ExtensionContribution(Collections.<JavascriptModuleSource>emptyList(),
                Collections.<SkillSource>emptyList(), Arrays.asList(view), Arrays.asList(participant), Arrays.asList(binding,binding)));
    }

}
