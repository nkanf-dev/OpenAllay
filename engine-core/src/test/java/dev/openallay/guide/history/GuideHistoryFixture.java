package dev.openallay.guide.history;

import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideSessionSnapshot;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record GuideHistoryFixture(
        GuideHistoryScope scope,
        String selectedSession,
        List<GuideSessionSnapshot> sessions,
        Instant updatedAt) {
    public GuideHistoryFixture {
        Objects.requireNonNull(scope, "scope");
        if (selectedSession == null || selectedSession.isBlank()) {
            throw new IllegalArgumentException("selectedSession must not be blank");
        }
        sessions = List.copyOf(sessions);
        if (sessions.stream().noneMatch(session -> session.sessionId().equals(selectedSession))) {
            throw new IllegalArgumentException("selectedSession does not exist in durable history");
        }
        Set<UUID> allRequests = new HashSet<>();
        for (GuideSessionSnapshot session : sessions) {
            Set<UUID> sessionRequests = new HashSet<>();
            session.requests().forEach(request -> {
                if (!request.sessionId().equals(session.sessionId())) {
                    throw new IllegalArgumentException("durable request belongs to another session");
                }
                if (!sessionRequests.add(request.requestId()) || !allRequests.add(request.requestId())) {
                    throw new IllegalArgumentException("durable request identity is duplicated");
                }
            });
            for (GuideMessage message : session.messages()) {
                if (!sessionRequests.contains(message.requestId())) {
                    throw new IllegalArgumentException("durable message has no same-session request");
                }
            }
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    static void seed(GuideHistoryStore store, GuideHistoryFixture fixture) {
        List<GuideHistoryMutation> mutations = new java.util.ArrayList<>();
        mutations.add(new GuideHistoryMutation.UpsertPartition(
                fixture.selectedSession(), fixture.updatedAt()));
        for (int ordinal = 0; ordinal < fixture.sessions().size(); ordinal++) {
            GuideSessionSnapshot session = fixture.sessions().get(ordinal);
            mutations.add(new GuideHistoryMutation.UpsertSession(
                    session.sessionId(), ordinal, session.modelSelection()));
            for (int sequence = 0; sequence < session.requests().size(); sequence++) {
                var request = session.requests().get(sequence);
                mutations.add(new GuideHistoryMutation.UpsertRequest(sequence, request));
                request.timeline().forEach(entry -> mutations.add(
                        new GuideHistoryMutation.UpsertTimelineEntry(request.requestId(), entry)));
                mutations.add(new GuideHistoryMutation.ReplaceRequestSources(
                        request.requestId(), request.sources()));
            }
            for (int message = 0; message < session.messages().size(); message++) {
                mutations.add(new GuideHistoryMutation.UpsertMessage(
                        session.sessionId(), message, session.messages().get(message)));
            }
            for (int checkpoint = 0; checkpoint < session.checkpoints().size(); checkpoint++) {
                mutations.add(new GuideHistoryMutation.UpsertCheckpoint(
                        session.sessionId(), checkpoint, session.checkpoints().get(checkpoint)));
            }
        }
        store.commit(new GuideHistoryCommit(fixture.scope(), mutations));
    }
}
