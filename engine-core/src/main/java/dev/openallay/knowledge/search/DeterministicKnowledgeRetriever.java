package dev.openallay.knowledge.search;

import dev.openallay.knowledge.KnowledgeDocument;
import dev.openallay.knowledge.KnowledgeSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Field-weighted, heading-aware BM25 retrieval with stable deterministic ordering. */
public final class DeterministicKnowledgeRetriever implements KnowledgeRetriever {
    private static final Pattern ATX_HEADING =
            Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$");
    private static final double K1 = 1.2d;
    private static final double B = 0.75d;
    private static final int EXACT_DOCUMENT = 5;
    private static final int EXACT_RESOURCE = 4;
    private static final int EXACT_ALIAS = 3;
    private static final int EXACT_TITLE = 2;
    private static final int PHRASE_MATCH = 1;

    private final KnowledgeTokenizer tokenizer;
    private final List<IndexedSection> sections;
    private final Corpus corpus;

    public DeterministicKnowledgeRetriever(KnowledgeSnapshot snapshot, KnowledgeTokenizer tokenizer) {
        Objects.requireNonNull(snapshot, "snapshot");
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.sections = dev.openallay.util.Java8Collections.toList(snapshot.documents().stream()
                .flatMap(document -> sections(document).stream()));
        this.corpus = Corpus.from(sections);
    }

    @Override
    public List<KnowledgeSearchResult> retrieve(String query) {
        if (query == null || dev.openallay.util.Java8Strings.isBlank(query)) {
            return dev.openallay.util.Java8Collections.listOf();
        }
        String normalizedQuery = tokenizer.normalize(query);
        List<String> terms = significantTerms(tokenizer.tokenize(query));
        Map<String, RankedSection> bestByDocument = new HashMap<>();
        for (IndexedSection section : sections) {
            RankedSection ranked = score(section, normalizedQuery, terms, corpus);
            if (ranked == null) {
                continue;
            }
            bestByDocument.merge(section.document().key(), ranked, DeterministicKnowledgeRetriever::better);
        }

        List<RankedSection> ranked = new ArrayList<>(bestByDocument.values());
        ranked.sort(Comparator.comparingInt(RankedSection::priority)
                .reversed()
                .thenComparing(Comparator.comparingDouble(RankedSection::lexicalScore).reversed())
                .thenComparing(hit -> hit.section().document().sourceId())
                .thenComparing(hit -> hit.section().document().documentId())
                .thenComparing(hit -> hit.section().sectionId()));
        return dev.openallay.util.Java8Collections.toList(ranked.stream().map(hit -> result(hit, normalizedQuery, terms)));
    }

    private RankedSection score(
            IndexedSection section,
            String normalizedQuery,
            List<String> terms,
            Corpus corpus) {
        KnowledgeDocument document = section.document();
        Set<String> matched = new TreeSet<>();
        int priority = 0;
        double phraseScore = 0.0d;
        String documentId = tokenizer.normalize(document.documentId());
        String title = tokenizer.normalize(document.title());
        String heading = tokenizer.normalize(section.heading());
        Set<String> resources = normalized(document.itemIds(), document.recipeIds());
        Set<String> aliases = aliases(document);

        if (documentId.equals(normalizedQuery)
                || tokenizer.normalize(document.key()).equals(normalizedQuery)) {
            priority = EXACT_DOCUMENT;
            matched.add("documentId");
        }
        if (resources.contains(normalizedQuery)) {
            priority = Math.max(priority, EXACT_RESOURCE);
            matched.add(document.itemIds().stream()
                            .map(tokenizer::normalize)
                            .anyMatch(normalizedQuery::equals)
                    ? "itemIds"
                    : "recipeIds");
        }
        if (aliases.contains(normalizedQuery)) {
            priority = Math.max(priority, EXACT_ALIAS);
            matched.add("aliases");
        }
        if (title.equals(normalizedQuery) || heading.equals(normalizedQuery)) {
            priority = Math.max(priority, EXACT_TITLE);
            matched.add(title.equals(normalizedQuery) ? "title" : "sectionHeading");
        }

        phraseScore += phrase(section.titleText(), normalizedQuery, 90.0d, "title", matched);
        phraseScore += phrase(section.heading(), normalizedQuery, 75.0d, "sectionHeading", matched);
        phraseScore += phrase(section.identityText(), normalizedQuery, 60.0d, "aliases", matched);
        phraseScore += phrase(section.metadataText(), normalizedQuery, 35.0d, "metadata", matched);
        phraseScore += phrase(section.body(), normalizedQuery, 18.0d, "body", matched);
        if (phraseScore > 0.0d) {
            priority = Math.max(priority, PHRASE_MATCH);
        }

        double lexical = phraseScore;
        for (String term : terms) {
            double idf = corpus.idf(term);
            lexical += fieldScore(section.titleTokens(), term, idf, corpus.averageTitleLength(), 6.0d, "title", matched);
            lexical += fieldScore(section.headingTokens(), term, idf, corpus.averageHeadingLength(), 5.0d,
                    "sectionHeading", matched);
            lexical += fieldScore(section.identityTokens(), term, idf, corpus.averageIdentityLength(), 4.0d,
                    "aliases", matched);
            lexical += fieldScore(section.metadataTokens(), term, idf, corpus.averageMetadataLength(), 3.0d,
                    "metadata", matched);
            lexical += fieldScore(section.bodyTokens(), term, idf, corpus.averageBodyLength(), 1.0d, "body", matched);
        }
        return priority == 0 && lexical == 0.0d
                ? null
                : new RankedSection(section, priority, lexical, dev.openallay.util.Java8Collections.setCopyOf(matched));
    }

    private double fieldScore(
            List<String> fieldTokens,
            String term,
            double idf,
            double averageLength,
            double weight,
            String field,
            Set<String> matched) {
        int frequency = 0;
        for (String token : fieldTokens) {
            if (token.equals(term)) {
                frequency++;
            }
        }
        if (frequency == 0) {
            return 0.0d;
        }
        matched.add(field);
        double lengthNormalization = 1.0d - B + B * fieldTokens.size() / Math.max(averageLength, 1.0d);
        return weight * idf * frequency * (K1 + 1.0d) / (frequency + K1 * lengthNormalization);
    }

    private double phrase(
            String value,
            String query,
            double weight,
            String field,
            Set<String> matched) {
        if (!query.isEmpty() && tokenizer.normalize(value).contains(query)) {
            matched.add(field);
            return weight;
        }
        return 0.0d;
    }

    private KnowledgeSearchResult result(
            RankedSection hit, String normalizedQuery, List<String> terms) {
        IndexedSection section = hit.section();
        KnowledgeDocument document = section.document();
        int score = Math.max(1, (int) Math.min(
                Integer.MAX_VALUE,
                hit.priority() * 100_000_000L + Math.round(hit.lexicalScore() * 1_000.0d)));
        return new KnowledgeSearchResult(
                document.sourceId(),
                document.documentId(),
                section.sectionId(),
                section.heading(),
                document.kind(),
                document.title(),
                excerpt(section.body(), normalizedQuery, terms),
                score,
                hit.matchedFields(),
                document.provenance(),
                document.evidence());
    }

    private Set<String> aliases(KnowledgeDocument document) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addAliases(aliases, document.documentId());
        addAliases(aliases, document.key());
        document.itemIds().forEach(value -> addAliases(aliases, value));
        document.recipeIds().forEach(value -> addAliases(aliases, value));
        return dev.openallay.util.Java8Collections.setCopyOf(aliases);
    }

    private void addAliases(Set<String> aliases, String value) {
        String normalized = tokenizer.normalize(value);
        aliases.add(normalized);
        int namespace = normalized.indexOf(':');
        String path = namespace >= 0 ? normalized.substring(namespace + 1) : normalized;
        aliases.add(path);
        aliases.add(path.replace('_', ' ').replace('-', ' ').replace('/', ' '));
    }

    private Set<String> normalized(Set<String> first, Set<String> second) {
        Set<String> normalized = new LinkedHashSet<>();
        first.forEach(value -> normalized.add(tokenizer.normalize(value)));
        second.forEach(value -> normalized.add(tokenizer.normalize(value)));
        return normalized;
    }

    private List<IndexedSection> sections(KnowledgeDocument document) {
        List<IndexedSection> result = new ArrayList<>();
        Map<String, Integer> duplicateIds = new HashMap<>();
        String heading = document.title();
        String sectionId = "document";
        StringBuilder body = new StringBuilder();
        char fence = 0;
        int fenceLength = 0;
        for (String line : document.body().split("\\R", -1)) {
            String stripped = dev.openallay.util.Java8Strings.stripLeading(line);
            int indentation = line.length() - stripped.length();
            int markerLength = fenceMarkerLength(stripped);
            if (indentation <= 3 && markerLength >= 3) {
                char marker = stripped.charAt(0);
                if (fence == 0) {
                    fence = marker;
                    fenceLength = markerLength;
                } else if (fence == marker && markerLength >= fenceLength) {
                    fence = 0;
                    fenceLength = 0;
                }
            }
            Matcher matcher = ATX_HEADING.matcher(line);
            if (fence == 0 && markerLength == 0 && matcher.matches()) {
                addSection(result, document, sectionId, heading, body.toString());
                heading = dev.openallay.util.Java8Strings.strip(matcher.group(2));
                String baseId = slug(heading);
                int occurrence = duplicateIds.merge(baseId, 1, Integer::sum);
                sectionId = occurrence == 1 ? baseId : baseId + "-" + occurrence;
                body.setLength(0);
            } else {
                if (!((body).length() == 0)) {
                    body.append('\n');
                }
                body.append(line);
            }
        }
        addSection(result, document, sectionId, heading, body.toString());
        return result;
    }

    private static int fenceMarkerLength(String line) {
        if (line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')) {
            return 0;
        }
        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length() && line.charAt(length) == marker) {
            length++;
        }
        return length;
    }

    private void addSection(
            List<IndexedSection> result,
            KnowledgeDocument document,
            String sectionId,
            String heading,
            String body) {
        if (!result.isEmpty() || !dev.openallay.util.Java8Strings.isBlank(body) || sectionId.equals("document")) {
            result.add(indexed(document, sectionId, heading, dev.openallay.util.Java8Strings.strip(body)));
        }
    }

    private IndexedSection indexed(
            KnowledgeDocument document, String sectionId, String heading, String body) {
        String identity = aliases(document).stream().sorted().collect(java.util.stream.Collectors.joining(" "));
        String metadata = document.sourceId() + " " + document.namespace() + " "
                + document.kind().name().toLowerCase(Locale.ROOT) + " "
                + document.evidence().sourceId() + " "
                + String.join(" ", document.evidence().details().values());
        return new IndexedSection(
                document,
                sectionId,
                heading,
                body,
                document.title(),
                identity,
                metadata,
                tokenizer.tokenize(document.title()),
                tokenizer.tokenize(heading),
                tokenizer.tokenize(identity),
                tokenizer.tokenize(metadata),
                tokenizer.tokenize(body));
    }

    private String slug(String heading) {
        String normalized = tokenizer.normalize(heading);
        StringBuilder slug = new StringBuilder();
        boolean separator = false;
        for (int codePoint : normalized.codePoints().toArray()) {
            if (Character.isLetterOrDigit(codePoint)) {
                if (separator && !((slug).length() == 0)) {
                    slug.append('-');
                }
                slug.appendCodePoint(codePoint);
                separator = false;
            } else {
                separator = true;
            }
        }
        return ((slug).length() == 0) ? "section" : slug.toString();
    }

    private static RankedSection better(RankedSection first, RankedSection second) {
        int comparison = Comparator.comparingInt(RankedSection::priority)
                .thenComparingDouble(RankedSection::lexicalScore)
                .thenComparing(hit -> hit.section().sectionId(), Comparator.reverseOrder())
                .compare(first, second);
        return comparison >= 0 ? first : second;
    }

    private static List<String> significantTerms(List<String> tokens) {
        List<String> distinct = dev.openallay.util.Java8Collections.toList(tokens.stream().distinct());
        if (distinct.stream().noneMatch(term -> term.codePointCount(0, term.length()) > 1)) {
            return distinct;
        }
        return dev.openallay.util.Java8Collections.toList(distinct.stream()
                .filter(term -> term.codePointCount(0, term.length()) > 1));
    }

    private static String excerpt(String body, String query, List<String> terms) {
        if (dev.openallay.util.Java8Strings.isBlank(body)) {
            return "";
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        int match = normalized.indexOf(query);
        int matchLength = query.length();
        if (match < 0) {
            for (String term : terms) {
                match = normalized.indexOf(term);
                if (match >= 0) {
                    matchLength = term.length();
                    break;
                }
            }
        }
        if (match < 0) {
            match = 0;
            matchLength = 1;
        }
        int start = Math.max(0, match - 80);
        int end = Math.min(body.length(), match + Math.max(matchLength, 1) + 160);
        return body.substring(start, end);
    }

    @dev.openallay.value.ValueType(IndexedSection.ValueSchemaProvider.class)
private static final class IndexedSection {
    private final KnowledgeDocument document;
    private final String sectionId;
    private final String heading;
    private final String body;
    private final String titleText;
    private final String identityText;
    private final String metadataText;
    private final List<String> titleTokens;
    private final List<String> headingTokens;
    private final List<String> identityTokens;
    private final List<String> metadataTokens;
    private final List<String> bodyTokens;
    private IndexedSection(KnowledgeDocument document, String sectionId, String heading, String body, String titleText, String identityText, String metadataText, List<String> titleTokens, List<String> headingTokens, List<String> identityTokens, List<String> metadataTokens, List<String> bodyTokens) {
        this.document = document;
        this.sectionId = sectionId;
        this.heading = heading;
        this.body = body;
        this.titleText = titleText;
        this.identityText = identityText;
        this.metadataText = metadataText;
        this.titleTokens = titleTokens;
        this.headingTokens = headingTokens;
        this.identityTokens = identityTokens;
        this.metadataTokens = metadataTokens;
        this.bodyTokens = bodyTokens;
    }
    public KnowledgeDocument document() { return document; }
    public String sectionId() { return sectionId; }
    public String heading() { return heading; }
    public String body() { return body; }
    public String titleText() { return titleText; }
    public String identityText() { return identityText; }
    public String metadataText() { return metadataText; }
    public List<String> titleTokens() { return titleTokens; }
    public List<String> headingTokens() { return headingTokens; }
    public List<String> identityTokens() { return identityTokens; }
    public List<String> metadataTokens() { return metadataTokens; }
    public List<String> bodyTokens() { return bodyTokens; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IndexedSection)) return false;
        IndexedSection that = (IndexedSection) other;
        return java.util.Objects.equals(document, that.document) && java.util.Objects.equals(sectionId, that.sectionId) && java.util.Objects.equals(heading, that.heading) && java.util.Objects.equals(body, that.body) && java.util.Objects.equals(titleText, that.titleText) && java.util.Objects.equals(identityText, that.identityText) && java.util.Objects.equals(metadataText, that.metadataText) && java.util.Objects.equals(titleTokens, that.titleTokens) && java.util.Objects.equals(headingTokens, that.headingTokens) && java.util.Objects.equals(identityTokens, that.identityTokens) && java.util.Objects.equals(metadataTokens, that.metadataTokens) && java.util.Objects.equals(bodyTokens, that.bodyTokens);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(sectionId);
        hash = 31 * hash + java.util.Objects.hashCode(heading);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        hash = 31 * hash + java.util.Objects.hashCode(titleText);
        hash = 31 * hash + java.util.Objects.hashCode(identityText);
        hash = 31 * hash + java.util.Objects.hashCode(metadataText);
        hash = 31 * hash + java.util.Objects.hashCode(titleTokens);
        hash = 31 * hash + java.util.Objects.hashCode(headingTokens);
        hash = 31 * hash + java.util.Objects.hashCode(identityTokens);
        hash = 31 * hash + java.util.Objects.hashCode(metadataTokens);
        hash = 31 * hash + java.util.Objects.hashCode(bodyTokens);
        return hash;
    }
    @Override public String toString() { return "IndexedSection[document=" + document + ", sectionId=" + sectionId + ", heading=" + heading + ", body=" + body + ", titleText=" + titleText + ", identityText=" + identityText + ", metadataText=" + metadataText + ", titleTokens=" + titleTokens + ", headingTokens=" + headingTokens + ", identityTokens=" + identityTokens + ", metadataTokens=" + metadataTokens + ", bodyTokens=" + bodyTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<IndexedSection> schema() {
            return new dev.openallay.value.ValueSchema<>(IndexedSection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<IndexedSection>>asList(new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "document", IndexedSection::document), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "sectionId", IndexedSection::sectionId), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "heading", IndexedSection::heading), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "body", IndexedSection::body), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "titleText", IndexedSection::titleText), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "identityText", IndexedSection::identityText), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "metadataText", IndexedSection::metadataText), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "titleTokens", IndexedSection::titleTokens), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "headingTokens", IndexedSection::headingTokens), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "identityTokens", IndexedSection::identityTokens), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "metadataTokens", IndexedSection::metadataTokens), new dev.openallay.value.ValueSchema.Component<>(IndexedSection.class, "bodyTokens", IndexedSection::bodyTokens)), arguments -> new IndexedSection((KnowledgeDocument) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6], (List) arguments[7], (List) arguments[8], (List) arguments[9], (List) arguments[10], (List) arguments[11]));
        }
    }
}

    @dev.openallay.value.ValueType(RankedSection.ValueSchemaProvider.class)
private static final class RankedSection {
    private final IndexedSection section;
    private final int priority;
    private final double lexicalScore;
    private final Set<String> matchedFields;
    private RankedSection(IndexedSection section, int priority, double lexicalScore, Set<String> matchedFields) {
        this.section = section;
        this.priority = priority;
        this.lexicalScore = lexicalScore;
        this.matchedFields = matchedFields;
    }
    public IndexedSection section() { return section; }
    public int priority() { return priority; }
    public double lexicalScore() { return lexicalScore; }
    public Set<String> matchedFields() { return matchedFields; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RankedSection)) return false;
        RankedSection that = (RankedSection) other;
        return java.util.Objects.equals(section, that.section) && priority == that.priority && Double.compare(lexicalScore, that.lexicalScore) == 0 && java.util.Objects.equals(matchedFields, that.matchedFields);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(section);
        hash = 31 * hash + Integer.hashCode(priority);
        hash = 31 * hash + Double.hashCode(lexicalScore);
        hash = 31 * hash + java.util.Objects.hashCode(matchedFields);
        return hash;
    }
    @Override public String toString() { return "RankedSection[section=" + section + ", priority=" + priority + ", lexicalScore=" + lexicalScore + ", matchedFields=" + matchedFields + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RankedSection> schema() {
            return new dev.openallay.value.ValueSchema<>(RankedSection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RankedSection>>asList(new dev.openallay.value.ValueSchema.Component<>(RankedSection.class, "section", RankedSection::section), new dev.openallay.value.ValueSchema.Component<>(RankedSection.class, "priority", RankedSection::priority), new dev.openallay.value.ValueSchema.Component<>(RankedSection.class, "lexicalScore", RankedSection::lexicalScore), new dev.openallay.value.ValueSchema.Component<>(RankedSection.class, "matchedFields", RankedSection::matchedFields)), arguments -> new RankedSection((IndexedSection) arguments[0], (Integer) arguments[1], (Double) arguments[2], (Set) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Corpus.ValueSchemaProvider.class)
private static final class Corpus {
    private final int sectionCount;
    private final Map<String, Integer> documentFrequencies;
    private final double averageTitleLength;
    private final double averageHeadingLength;
    private final double averageIdentityLength;
    private final double averageMetadataLength;
    private final double averageBodyLength;
    private Corpus(int sectionCount, Map<String, Integer> documentFrequencies, double averageTitleLength, double averageHeadingLength, double averageIdentityLength, double averageMetadataLength, double averageBodyLength) {
        this.sectionCount = sectionCount;
        this.documentFrequencies = documentFrequencies;
        this.averageTitleLength = averageTitleLength;
        this.averageHeadingLength = averageHeadingLength;
        this.averageIdentityLength = averageIdentityLength;
        this.averageMetadataLength = averageMetadataLength;
        this.averageBodyLength = averageBodyLength;
    }
    public int sectionCount() { return sectionCount; }
    public Map<String, Integer> documentFrequencies() { return documentFrequencies; }
    public double averageTitleLength() { return averageTitleLength; }
    public double averageHeadingLength() { return averageHeadingLength; }
    public double averageIdentityLength() { return averageIdentityLength; }
    public double averageMetadataLength() { return averageMetadataLength; }
    public double averageBodyLength() { return averageBodyLength; }
private static Corpus from(List<IndexedSection> sections) {
            Map<String, Integer> frequencies = new HashMap<>();
            double titleLength = 0.0d;
            double headingLength = 0.0d;
            double identityLength = 0.0d;
            double metadataLength = 0.0d;
            double bodyLength = 0.0d;
            for (IndexedSection section : sections) {
                titleLength += section.titleTokens().size();
                headingLength += section.headingTokens().size();
                identityLength += section.identityTokens().size();
                metadataLength += section.metadataTokens().size();
                bodyLength += section.bodyTokens().size();
                Set<String> present = new LinkedHashSet<>();
                present.addAll(section.titleTokens());
                present.addAll(section.headingTokens());
                present.addAll(section.identityTokens());
                present.addAll(section.metadataTokens());
                present.addAll(section.bodyTokens());
                present.forEach(term -> frequencies.merge(term, 1, Integer::sum));
            }
            int count = sections.size();
            return new Corpus(
                    count,
                    dev.openallay.util.Java8Collections.mapCopyOf(frequencies),
                    average(titleLength, count),
                    average(headingLength, count),
                    average(identityLength, count),
                    average(metadataLength, count),
                    average(bodyLength, count));
        }
private double idf(String term) {
            int frequency = documentFrequencies.getOrDefault(term, 0);
            return Math.log(1.0d + (sectionCount - frequency + 0.5d) / (frequency + 0.5d));
        }
private static double average(double total, int count) {
            return count == 0 ? 1.0d : Math.max(total / count, 1.0d);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Corpus)) return false;
        Corpus that = (Corpus) other;
        return sectionCount == that.sectionCount && java.util.Objects.equals(documentFrequencies, that.documentFrequencies) && Double.compare(averageTitleLength, that.averageTitleLength) == 0 && Double.compare(averageHeadingLength, that.averageHeadingLength) == 0 && Double.compare(averageIdentityLength, that.averageIdentityLength) == 0 && Double.compare(averageMetadataLength, that.averageMetadataLength) == 0 && Double.compare(averageBodyLength, that.averageBodyLength) == 0;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(sectionCount);
        hash = 31 * hash + java.util.Objects.hashCode(documentFrequencies);
        hash = 31 * hash + Double.hashCode(averageTitleLength);
        hash = 31 * hash + Double.hashCode(averageHeadingLength);
        hash = 31 * hash + Double.hashCode(averageIdentityLength);
        hash = 31 * hash + Double.hashCode(averageMetadataLength);
        hash = 31 * hash + Double.hashCode(averageBodyLength);
        return hash;
    }
    @Override public String toString() { return "Corpus[sectionCount=" + sectionCount + ", documentFrequencies=" + documentFrequencies + ", averageTitleLength=" + averageTitleLength + ", averageHeadingLength=" + averageHeadingLength + ", averageIdentityLength=" + averageIdentityLength + ", averageMetadataLength=" + averageMetadataLength + ", averageBodyLength=" + averageBodyLength + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Corpus> schema() {
            return new dev.openallay.value.ValueSchema<>(Corpus.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Corpus>>asList(new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "sectionCount", Corpus::sectionCount), new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "documentFrequencies", Corpus::documentFrequencies), new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "averageTitleLength", Corpus::averageTitleLength), new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "averageHeadingLength", Corpus::averageHeadingLength), new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "averageIdentityLength", Corpus::averageIdentityLength), new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "averageMetadataLength", Corpus::averageMetadataLength), new dev.openallay.value.ValueSchema.Component<>(Corpus.class, "averageBodyLength", Corpus::averageBodyLength)), arguments -> new Corpus((Integer) arguments[0], (Map) arguments[1], (Double) arguments[2], (Double) arguments[3], (Double) arguments[4], (Double) arguments[5], (Double) arguments[6]));
        }
    }
}
}
