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
    public record Match(BuiltinModelCatalog.Entry entry, Kind kind, double similarity, int identityRank) {}
    private record Candidate(BuiltinModelCatalog.Entry entry, String name,
                             List<String> numbers, List<String> words, int rank) {}

    /** All static names are normalized once, not on the Minecraft render thread. */
    public static final class Index {
        private final Map<String, List<Match>> exact = new HashMap<>();
        private final Map<String, List<Match>> normalized = new HashMap<>();
        private final List<Candidate> candidates = new ArrayList<>();
        private volatile Lookup previous;
        private record Lookup(String name, Optional<Match> match) {}

        public Index(List<BuiltinModelCatalog.Entry> entries) {
            for (var entry : entries) {
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
            if (requested == null || requested.isBlank()) return Optional.empty();
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
        return NUMBERS.matcher(value).results().map(java.util.regex.MatchResult::group).toList();
    }
    private static List<String> identityWords(String value, String family) {
        return java.util.Arrays.stream(value.substring(family.length()).split("[-.0-9]+"))
                .filter(word -> !word.isBlank()).toList();
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
