package dev.openallay.integration.ftb.quests;

import java.util.List;
import java.util.Set;

@dev.openallay.value.ValueType(FtbQuestSnapshot.ValueSchemaProvider.class)
public final class FtbQuestSnapshot {
    private final String questId;
    private final String chapterId;
    private final String chapterTitle;
    private final String title;
    private final String description;
    private final Set<String> dependencyIds;
    private final boolean completed;
    private final String provenance;
    public FtbQuestSnapshot(String questId, String chapterId, String chapterTitle, String title, String description, Set<String> dependencyIds, boolean completed, String provenance) {

        dependencyIds = Set.copyOf(dependencyIds);

        this.questId = questId;
        this.chapterId = chapterId;
        this.chapterTitle = chapterTitle;
        this.title = title;
        this.description = description;
        this.dependencyIds = dependencyIds;
        this.completed = completed;
        this.provenance = provenance;
    }
    public String questId() { return questId; }
    public String chapterId() { return chapterId; }
    public String chapterTitle() { return chapterTitle; }
    public String title() { return title; }
    public String description() { return description; }
    public Set<String> dependencyIds() { return dependencyIds; }
    public boolean completed() { return completed; }
    public String provenance() { return provenance; }
@dev.openallay.value.ValueType(Result.ValueSchemaProvider.class)
public static final class Result {
    private final boolean available;
    private final List<FtbQuestSnapshot> quests;
    private final String diagnosticCode;
    private final String diagnosticMessage;
    public Result(boolean available, List<FtbQuestSnapshot> quests, String diagnosticCode, String diagnosticMessage) {

            quests = List.copyOf(quests);

        this.available = available;
        this.quests = quests;
        this.diagnosticCode = diagnosticCode;
        this.diagnosticMessage = diagnosticMessage;
    }
    public boolean available() { return available; }
    public List<FtbQuestSnapshot> quests() { return quests; }
    public String diagnosticCode() { return diagnosticCode; }
    public String diagnosticMessage() { return diagnosticMessage; }
public static Result unavailable(String code, String message) {
            return new Result(false, List.of(), code, message);
        }
public static Result available(List<FtbQuestSnapshot> quests) {
            return new Result(true, quests, null, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Result)) return false;
        Result that = (Result) other;
        return available == that.available && java.util.Objects.equals(quests, that.quests) && java.util.Objects.equals(diagnosticCode, that.diagnosticCode) && java.util.Objects.equals(diagnosticMessage, that.diagnosticMessage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(quests);
        hash = 31 * hash + java.util.Objects.hashCode(diagnosticCode);
        hash = 31 * hash + java.util.Objects.hashCode(diagnosticMessage);
        return hash;
    }
    @Override public String toString() { return "Result[available=" + available + ", quests=" + quests + ", diagnosticCode=" + diagnosticCode + ", diagnosticMessage=" + diagnosticMessage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Result> schema() {
            return new dev.openallay.value.ValueSchema<>(Result.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Result>>asList(new dev.openallay.value.ValueSchema.Component<>(Result.class, "available", Result::available), new dev.openallay.value.ValueSchema.Component<>(Result.class, "quests", Result::quests), new dev.openallay.value.ValueSchema.Component<>(Result.class, "diagnosticCode", Result::diagnosticCode), new dev.openallay.value.ValueSchema.Component<>(Result.class, "diagnosticMessage", Result::diagnosticMessage)), arguments -> new Result((Boolean) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FtbQuestSnapshot)) return false;
        FtbQuestSnapshot that = (FtbQuestSnapshot) other;
        return java.util.Objects.equals(questId, that.questId) && java.util.Objects.equals(chapterId, that.chapterId) && java.util.Objects.equals(chapterTitle, that.chapterTitle) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(dependencyIds, that.dependencyIds) && completed == that.completed && java.util.Objects.equals(provenance, that.provenance);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(questId);
        hash = 31 * hash + java.util.Objects.hashCode(chapterId);
        hash = 31 * hash + java.util.Objects.hashCode(chapterTitle);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(dependencyIds);
        hash = 31 * hash + Boolean.hashCode(completed);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        return hash;
    }
    @Override public String toString() { return "FtbQuestSnapshot[questId=" + questId + ", chapterId=" + chapterId + ", chapterTitle=" + chapterTitle + ", title=" + title + ", description=" + description + ", dependencyIds=" + dependencyIds + ", completed=" + completed + ", provenance=" + provenance + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<FtbQuestSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(FtbQuestSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<FtbQuestSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "questId", FtbQuestSnapshot::questId), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "chapterId", FtbQuestSnapshot::chapterId), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "chapterTitle", FtbQuestSnapshot::chapterTitle), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "title", FtbQuestSnapshot::title), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "description", FtbQuestSnapshot::description), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "dependencyIds", FtbQuestSnapshot::dependencyIds), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "completed", FtbQuestSnapshot::completed), new dev.openallay.value.ValueSchema.Component<>(FtbQuestSnapshot.class, "provenance", FtbQuestSnapshot::provenance)), arguments -> new FtbQuestSnapshot((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (Set) arguments[5], (Boolean) arguments[6], (String) arguments[7]));
        }
    }
}
