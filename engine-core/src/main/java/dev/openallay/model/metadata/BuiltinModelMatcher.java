package dev.openallay.model.metadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pure deterministic BEST matching. Identity evidence never rewrites a provider request ID. */
public final class BuiltinModelMatcher {
    private static final Pattern NUMBERS = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final double MIN_SIMILARITY = 0.82;
    private static final Comparator<Match> BEST = Comparator.comparing(Match::kind)
            .thenComparing(Comparator.comparingDouble(Match::similarity).reversed())
            .thenComparingInt(Match::identityRank)
            .thenComparing(match -> hosted(match.entry().provider()) ? 1 : 0)
            .thenComparing(match -> match.entry().id());

    private BuiltinModelMatcher() {}
    public enum Kind { EXACT, NORMALIZED, SIMILAR }
    @dev.openallay.value.ValueType(Match.ValueSchemaProvider.class)
public static final class Match {
    private final BuiltinModelCatalog.Entry entry;
    private final Kind kind;
    private final double similarity;
    private final int identityRank;
    public Match(BuiltinModelCatalog.Entry entry, Kind kind, double similarity, int identityRank) {
        this.entry = entry;
        this.kind = kind;
        this.similarity = similarity;
        this.identityRank = identityRank;
    }
    public BuiltinModelCatalog.Entry entry() { return entry; }
    public Kind kind() { return kind; }
    public double similarity() { return similarity; }
    public int identityRank() { return identityRank; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Match)) return false;
        Match that = (Match) other;
        return java.util.Objects.equals(entry, that.entry) && java.util.Objects.equals(kind, that.kind) && Double.compare(similarity, that.similarity) == 0 && identityRank == that.identityRank;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entry);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Double.hashCode(similarity);
        hash = 31 * hash + Integer.hashCode(identityRank);
        return hash;
    }
    @Override public String toString() { return "Match[entry=" + entry + ", kind=" + kind + ", similarity=" + similarity + ", identityRank=" + identityRank + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Match> schema() {
            return new dev.openallay.value.ValueSchema<>(Match.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Match>>asList(new dev.openallay.value.ValueSchema.Component<>(Match.class, "entry", Match::entry), new dev.openallay.value.ValueSchema.Component<>(Match.class, "kind", Match::kind), new dev.openallay.value.ValueSchema.Component<>(Match.class, "similarity", Match::similarity), new dev.openallay.value.ValueSchema.Component<>(Match.class, "identityRank", Match::identityRank)), arguments -> new Match((BuiltinModelCatalog.Entry) arguments[0], (Kind) arguments[1], (Double) arguments[2], (Integer) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Candidate.ValueSchemaProvider.class)
private static final class Candidate {
    private final BuiltinModelCatalog.Entry entry;
    private final String name;
    private final List<String> numbers;
    private final List<String> words;
    private final int rank;
    private Candidate(BuiltinModelCatalog.Entry entry, String name, List<String> numbers, List<String> words, int rank) {
        this.entry = entry;
        this.name = name;
        this.numbers = numbers;
        this.words = words;
        this.rank = rank;
    }
    public BuiltinModelCatalog.Entry entry() { return entry; }
    public String name() { return name; }
    public List<String> numbers() { return numbers; }
    public List<String> words() { return words; }
    public int rank() { return rank; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Candidate)) return false;
        Candidate that = (Candidate) other;
        return java.util.Objects.equals(entry, that.entry) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(numbers, that.numbers) && java.util.Objects.equals(words, that.words) && rank == that.rank;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entry);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(numbers);
        hash = 31 * hash + java.util.Objects.hashCode(words);
        hash = 31 * hash + Integer.hashCode(rank);
        return hash;
    }
    @Override public String toString() { return "Candidate[entry=" + entry + ", name=" + name + ", numbers=" + numbers + ", words=" + words + ", rank=" + rank + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Candidate> schema() {
            return new dev.openallay.value.ValueSchema<>(Candidate.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Candidate>>asList(new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "entry", Candidate::entry), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "name", Candidate::name), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "numbers", Candidate::numbers), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "words", Candidate::words), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "rank", Candidate::rank)), arguments -> new Candidate((BuiltinModelCatalog.Entry) arguments[0], (String) arguments[1], (List) arguments[2], (List) arguments[3], (Integer) arguments[4]));
        }
    }
}

    /** All static names are normalized once, not on the Minecraft render thread. */
    public static final class Index {
        private final Map<String, List<Match>> exact = new HashMap<>();
        private final Map<String, List<Match>> normalized = new HashMap<>();
        private final List<Candidate> candidates = new ArrayList<>();
        private volatile Lookup previous;
        @dev.openallay.value.ValueType(Lookup.ValueSchemaProvider.class)
private static final class Lookup {
    private final String name;
    private final Optional<Match> match;
    private Lookup(String name, Optional<Match> match) {
        this.name = name;
        this.match = match;
    }
    public String name() { return name; }
    public Optional<Match> match() { return match; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Lookup)) return false;
        Lookup that = (Lookup) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(match, that.match);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(match);
        return hash;
    }
    @Override public String toString() { return "Lookup[name=" + name + ", match=" + match + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Lookup> schema() {
            return new dev.openallay.value.ValueSchema<>(Lookup.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Lookup>>asList(new dev.openallay.value.ValueSchema.Component<>(Lookup.class, "name", Lookup::name), new dev.openallay.value.ValueSchema.Component<>(Lookup.class, "match", Lookup::match)), arguments -> new Lookup((String) arguments[0], (Optional) arguments[1]));
        }
    }
}

        public Index(List<BuiltinModelCatalog.Entry> entries) {
            for (BuiltinModelCatalog.Entry entry : entries) {
                List<String> names = new ArrayList<>();
                names.add(entry.id()); names.add(entry.upstreamModelId()); names.addAll(entry.aliases());
                for (int index = 0; index < names.size(); index++) {
                    String name = names.get(index);
                    int rank = Math.min(index, 2);
                    String folded = name.trim().toLowerCase(Locale.ROOT);
                    String normal = normalize(name);
                    exact.computeIfAbsent(folded, ignored -> new ArrayList<>())
                            .add(new Match(entry, Kind.EXACT, 1, rank));
                    normalized.computeIfAbsent(normal, ignored -> new ArrayList<>())
                            .add(new Match(entry, Kind.NORMALIZED, 1, rank));
                    if (normal.startsWith(entry.family())) candidates.add(new Candidate(entry,
                            normal, numbers(normal), identityWords(normal, entry.family()), rank));
                }
            }
        }

        public Optional<Match> match(String requested) {
            if (requested == null || dev.openallay.util.Java8Strings.isBlank(requested)) return Optional.empty();
            Lookup cached = previous;
            if (cached != null && cached.name().equals(requested)) return cached.match();
            Optional<Match> result = find(requested);
            previous = new Lookup(requested, result);
            return result;
        }
        private Optional<Match> find(String requested) {
            List<Match> direct = exact.get(requested.trim().toLowerCase(Locale.ROOT));
            if (direct != null) return direct.stream().min(BEST);
            String name = normalize(requested);
            List<Match> wrapped = normalized.get(name);
            if (wrapped != null) return wrapped.stream().min(BEST);
            List<String> versions = numbers(name);
            List<Match> close = new ArrayList<>();
            for (Candidate candidate : candidates) {
                String family = candidate.entry().family();
                if (!name.startsWith(family) || !versions.equals(candidate.numbers())) continue;
                List<String> words = identityWords(name, family);
                if (!compatible(words, candidate.words(), versions.isEmpty())) continue;
                double score = similarity(name, candidate.name());
                if (score >= MIN_SIMILARITY)
                    close.add(new Match(candidate.entry(), Kind.SIMILAR, score, candidate.rank()));
            }
            return close.stream().min(BEST);
        }
    }
    public static Optional<Match> match(List<BuiltinModelCatalog.Entry> entries, String requested) {
        return new Index(entries).match(requested);
    }
    private static boolean hosted(String provider) {
        return provider.equals("openrouter") || provider.equals("amazon-bedrock");
    }
    static String normalize(String value) {
        String name = value.trim().toLowerCase(Locale.ROOT);
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll(":(?:free|nitro|floor|online)$", "");
        name = name.replaceAll("(?:[-_]20[0-9]{2}[-_]?[0-9]{2}[-_]?[0-9]{2})$", "");
        name = name.replaceAll("[-_](?:latest)$", "");
        return name.replaceAll("(?<=[0-9])\\.(?=[0-9])", "-")
                .replaceAll("[ _]+", "-").replaceAll("-+", "-");
    }
    private static boolean compatible(List<String> first, List<String> second, boolean unversioned) {
        if (first.size() != second.size() || (unversioned && first.isEmpty())) return false;
        for (int index = 0; index < first.size(); index++) {
            String word = first.get(index);
            String other = second.get(index);
            if (!word.equals(other) && (word.length() < 4 || other.length() < 4
                    || similarity(word, other) < 0.75)) return false;
        }
        return true;
    }
    private static List<String> numbers(String value) {
        java.util.regex.Matcher matcher = NUMBERS.matcher(value);
        List<String> numbers = new ArrayList<>();
        while (matcher.find()) numbers.add(matcher.group());
        return java.util.Collections.unmodifiableList(numbers);
    }
    private static List<String> identityWords(String value, String family) {
        return dev.openallay.util.Java8Collections.toList(
                java.util.Arrays.stream(value.substring(family.length()).split("[-.0-9]+"))
                .filter(word -> !dev.openallay.util.Java8Strings.isBlank(word)));
    }
    private static double similarity(String first, String second) {
        int[] previous = new int[second.length() + 1];
        for (int j = 0; j < previous.length; j++) previous[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            int[] current = new int[second.length() + 1];
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return 1.0 - (double) previous[second.length()] / Math.max(first.length(), second.length());
    }
}
