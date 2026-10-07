package dev.openallay.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Selects corpus cases against one honest fixture capability boundary. */
public final class BenchmarkSelector {
    public Selection select(
            BenchmarkCorpus corpus,
            String fixtureId,
            Set<String> capabilities,
            Set<String> requestedCaseIds,
            int attempts) {
        Objects.requireNonNull(corpus, "corpus");
        if (fixtureId == null || dev.openallay.util.Java8Strings.isBlank(fixtureId)) {
            throw new IllegalArgumentException("fixtureId must not be blank");
        }
        Set<String> availableCapabilities = dev.openallay.util.Java8Collections.setCopyOf(capabilities);
        Set<String> requested = dev.openallay.util.Java8Collections.setCopyOf(requestedCaseIds);
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }

        Set<String> known = corpus.cases().stream()
                .map(BenchmarkCase::id)
                .collect(Collectors.toSet());
        TreeSet<String> unknown = new TreeSet<>(requested);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown benchmark cases: " + unknown);
        }

        ArrayList<BenchmarkCase> selected = new ArrayList<>();
        ArrayList<SkippedCase> skipped = new ArrayList<>();
        for (BenchmarkCase testCase : corpus.cases()) {
            if (!requested.isEmpty() && !requested.contains(testCase.id())) {
                continue;
            }
            List<String> missing = dev.openallay.util.Java8Collections.toList(testCase.requiredCapabilities().stream()
                    .filter(capability -> !availableCapabilities.contains(capability))
                    .sorted());
            if (!testCase.fixture().equals(fixtureId)) {
                skipped.add(new SkippedCase(
                        testCase.id(),
                        testCase.fixture(),
                        SkipReason.FIXTURE_MISMATCH,
                        missing));
            } else if (!missing.isEmpty()) {
                skipped.add(new SkippedCase(
                        testCase.id(),
                        testCase.fixture(),
                        SkipReason.MISSING_CAPABILITIES,
                        missing));
            } else {
                selected.add(testCase.withAttempts(attempts));
            }
        }

        if (!requested.isEmpty() && !skipped.isEmpty()) {
            throw new IllegalArgumentException(
                    "Requested cases are unavailable to fixture " + fixtureId + ": " + skipped);
        }
        return new Selection(selected, skipped);
    }

    public enum SkipReason {
        FIXTURE_MISMATCH,
        MISSING_CAPABILITIES
    }

    @dev.openallay.value.ValueType(SkippedCase.ValueSchemaProvider.class)
public static final class SkippedCase {
    private final String caseId;
    private final String requiredFixture;
    private final SkipReason reason;
    private final List<String> missingCapabilities;
    public SkippedCase(String caseId, String requiredFixture, SkipReason reason, List<String> missingCapabilities) {

            if (caseId == null || dev.openallay.util.Java8Strings.isBlank(caseId)) {
                throw new IllegalArgumentException("caseId must not be blank");
            }
            if (requiredFixture == null || dev.openallay.util.Java8Strings.isBlank(requiredFixture)) {
                throw new IllegalArgumentException("requiredFixture must not be blank");
            }
            Objects.requireNonNull(reason, "reason");
            missingCapabilities = dev.openallay.util.Java8Collections.listCopyOf(missingCapabilities);

        this.caseId = caseId;
        this.requiredFixture = requiredFixture;
        this.reason = reason;
        this.missingCapabilities = missingCapabilities;
    }
    public String caseId() { return caseId; }
    public String requiredFixture() { return requiredFixture; }
    public SkipReason reason() { return reason; }
    public List<String> missingCapabilities() { return missingCapabilities; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkippedCase)) return false;
        SkippedCase that = (SkippedCase) other;
        return java.util.Objects.equals(caseId, that.caseId) && java.util.Objects.equals(requiredFixture, that.requiredFixture) && java.util.Objects.equals(reason, that.reason) && java.util.Objects.equals(missingCapabilities, that.missingCapabilities);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(caseId);
        hash = 31 * hash + java.util.Objects.hashCode(requiredFixture);
        hash = 31 * hash + java.util.Objects.hashCode(reason);
        hash = 31 * hash + java.util.Objects.hashCode(missingCapabilities);
        return hash;
    }
    @Override public String toString() { return "SkippedCase[caseId=" + caseId + ", requiredFixture=" + requiredFixture + ", reason=" + reason + ", missingCapabilities=" + missingCapabilities + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkippedCase> schema() {
            return new dev.openallay.value.ValueSchema<>(SkippedCase.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkippedCase>>asList(new dev.openallay.value.ValueSchema.Component<>(SkippedCase.class, "caseId", SkippedCase::caseId), new dev.openallay.value.ValueSchema.Component<>(SkippedCase.class, "requiredFixture", SkippedCase::requiredFixture), new dev.openallay.value.ValueSchema.Component<>(SkippedCase.class, "reason", SkippedCase::reason), new dev.openallay.value.ValueSchema.Component<>(SkippedCase.class, "missingCapabilities", SkippedCase::missingCapabilities)), arguments -> new SkippedCase((String) arguments[0], (String) arguments[1], (SkipReason) arguments[2], (List) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Selection.ValueSchemaProvider.class)
public static final class Selection {
    private final List<BenchmarkCase> selected;
    private final List<SkippedCase> skipped;
    public Selection(List<BenchmarkCase> selected, List<SkippedCase> skipped) {

            selected = dev.openallay.util.Java8Collections.listCopyOf(selected);
            skipped = dev.openallay.util.Java8Collections.listCopyOf(skipped);

        this.selected = selected;
        this.skipped = skipped;
    }
    public List<BenchmarkCase> selected() { return selected; }
    public List<SkippedCase> skipped() { return skipped; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Selection)) return false;
        Selection that = (Selection) other;
        return java.util.Objects.equals(selected, that.selected) && java.util.Objects.equals(skipped, that.skipped);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selected);
        hash = 31 * hash + java.util.Objects.hashCode(skipped);
        return hash;
    }
    @Override public String toString() { return "Selection[selected=" + selected + ", skipped=" + skipped + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Selection> schema() {
            return new dev.openallay.value.ValueSchema<>(Selection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Selection>>asList(new dev.openallay.value.ValueSchema.Component<>(Selection.class, "selected", Selection::selected), new dev.openallay.value.ValueSchema.Component<>(Selection.class, "skipped", Selection::skipped)), arguments -> new Selection((List) arguments[0], (List) arguments[1]));
        }
    }
}
}
