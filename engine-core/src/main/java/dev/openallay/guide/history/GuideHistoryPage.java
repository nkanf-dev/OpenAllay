package dev.openallay.guide.history;

import dev.openallay.guide.GuideRequestSnapshot;
import java.util.List;

@dev.openallay.value.ValueType(GuideHistoryPage.ValueSchemaProvider.class)
public final class GuideHistoryPage {
    private final String sessionId;
    private final List<GuideRequestSnapshot> requests;
    private final GuideHistoryCursor first;
    private final GuideHistoryCursor last;
    private final boolean hasEarlier;
    private final boolean hasLater;
    public GuideHistoryPage(String sessionId, List<GuideRequestSnapshot> requests, GuideHistoryCursor first, GuideHistoryCursor last, boolean hasEarlier, boolean hasLater) {

        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
        requests = dev.openallay.util.Java8Collections.listCopyOf(requests);
        if (requests.isEmpty() ? first != null || last != null : first == null || last == null) {
            throw new IllegalArgumentException("history page cursor metadata is inconsistent");
        }

        this.sessionId = sessionId;
        this.requests = requests;
        this.first = first;
        this.last = last;
        this.hasEarlier = hasEarlier;
        this.hasLater = hasLater;
    }
    public String sessionId() { return sessionId; }
    public List<GuideRequestSnapshot> requests() { return requests; }
    public GuideHistoryCursor first() { return first; }
    public GuideHistoryCursor last() { return last; }
    public boolean hasEarlier() { return hasEarlier; }
    public boolean hasLater() { return hasLater; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryPage)) return false;
        GuideHistoryPage that = (GuideHistoryPage) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(requests, that.requests) && java.util.Objects.equals(first, that.first) && java.util.Objects.equals(last, that.last) && hasEarlier == that.hasEarlier && hasLater == that.hasLater;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(requests);
        hash = 31 * hash + java.util.Objects.hashCode(first);
        hash = 31 * hash + java.util.Objects.hashCode(last);
        hash = 31 * hash + Boolean.hashCode(hasEarlier);
        hash = 31 * hash + Boolean.hashCode(hasLater);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryPage[sessionId=" + sessionId + ", requests=" + requests + ", first=" + first + ", last=" + last + ", hasEarlier=" + hasEarlier + ", hasLater=" + hasLater + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryPage> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryPage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryPage>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPage.class, "sessionId", GuideHistoryPage::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPage.class, "requests", GuideHistoryPage::requests), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPage.class, "first", GuideHistoryPage::first), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPage.class, "last", GuideHistoryPage::last), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPage.class, "hasEarlier", GuideHistoryPage::hasEarlier), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryPage.class, "hasLater", GuideHistoryPage::hasLater)), arguments -> new GuideHistoryPage((String) arguments[0], (List) arguments[1], (GuideHistoryCursor) arguments[2], (GuideHistoryCursor) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5]));
        }
    }
}
