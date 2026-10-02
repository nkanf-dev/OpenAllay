package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.model.ModelMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Closed set of minimum durable changes accepted by the history store. */
public sealed interface GuideHistoryMutation permits
        GuideHistoryMutation.UpsertPartition,
        GuideHistoryMutation.UpsertSession,
        GuideHistoryMutation.UpsertRequest,
        GuideHistoryMutation.UpsertMessage,
        GuideHistoryMutation.UpsertTimelineEntry,
        GuideHistoryMutation.ReplaceRequestSources,
        GuideHistoryMutation.ReplaceContext,
        GuideHistoryMutation.ReplaceRequestContext,
        GuideHistoryMutation.UpsertCheckpoint,
        GuideHistoryMutation.AppendCheckpoint,
        GuideHistoryMutation.CaptureRequestBoundary,
        GuideHistoryMutation.ForkSession,
        GuideHistoryMutation.DeleteSession,
        GuideHistoryMutation.ClearSession {

    record UpsertPartition(String selectedSession, Instant updatedAt)
            implements GuideHistoryMutation {
        public UpsertPartition {
            if (selectedSession == null || selectedSession.isBlank()) {
                throw new IllegalArgumentException("selected session is required");
            }
            java.util.Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    record UpsertSession(
            String sessionId, int ordinal, GuideModelSelection modelSelection)
            implements GuideHistoryMutation {
        public UpsertSession {
            requireSession(sessionId);
            if (ordinal < 0) throw new IllegalArgumentException("session ordinal is invalid");
            java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        }
    }

    record UpsertRequest(long sequence, GuideRequestSnapshot request)
            implements GuideHistoryMutation {
        public UpsertRequest {
            if (sequence < 0) throw new IllegalArgumentException("request sequence is invalid");
            java.util.Objects.requireNonNull(request, "request");
        }
    }

    record UpsertMessage(String sessionId, int ordinal, GuideMessage message)
            implements GuideHistoryMutation {
        public UpsertMessage {
            requireSession(sessionId);
            if (ordinal < 0) throw new IllegalArgumentException("message ordinal is invalid");
            java.util.Objects.requireNonNull(message, "message");
        }
    }

    record UpsertTimelineEntry(UUID requestId, GuideTimelineEntry entry)
            implements GuideHistoryMutation {
        public UpsertTimelineEntry {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(entry, "entry");
        }
    }

    record ReplaceRequestSources(UUID requestId, List<GuideSource> sources)
            implements GuideHistoryMutation {
        public ReplaceRequestSources {
            java.util.Objects.requireNonNull(requestId, "requestId");
            sources = List.copyOf(sources);
        }
    }

    /** Replaces the actual Agent transcript, not any player-visible history projection. */
    record ReplaceContext(String sessionId, List<ModelMessage> messages)
            implements GuideHistoryMutation {
        public ReplaceContext {
            requireSession(sessionId);
            messages = ModelContextCodec.safe(messages);
        }
    }

    /** Original model-visible request messages retained independently of session compaction. */
    record ReplaceRequestContext(UUID requestId, List<ModelMessage> messages)
            implements GuideHistoryMutation {
        public ReplaceRequestContext {
            java.util.Objects.requireNonNull(requestId, "requestId");
            messages = ModelContextCodec.safe(messages);
        }
    }

    record UpsertCheckpoint(
            String sessionId, int ordinal, ContextCheckpoint checkpoint)
            implements GuideHistoryMutation {
        public UpsertCheckpoint {
            requireSession(sessionId);
            if (ordinal < 0) throw new IllegalArgumentException("checkpoint ordinal is invalid");
            java.util.Objects.requireNonNull(checkpoint, "checkpoint");
        }
    }

    /** Allocates the durable append ordinal; repeated checkpoint identity does not create another row. */
    record AppendCheckpoint(String sessionId, ContextCheckpoint checkpoint) implements GuideHistoryMutation {
        public AppendCheckpoint {
            requireSession(sessionId);
            java.util.Objects.requireNonNull(checkpoint, "checkpoint");
        }
    }

    /** Actual safe Agent projection after a terminal request, not a display reconstruction. */
    record CaptureRequestBoundary(
            UUID requestId, List<ModelMessage> messages, List<ContextCheckpoint> checkpoints)
            implements GuideHistoryMutation {
        public CaptureRequestBoundary {
            java.util.Objects.requireNonNull(requestId, "requestId");
            messages = ModelContextCodec.safe(messages);
            checkpoints = List.copyOf(checkpoints);
        }
    }

    /** Applies only after an exact terminal request; never resumes a pending tool step. */
    record ForkSession(
            String sourceSessionId, GuideHistoryCursor cutoff, String sessionId,
            int ordinal, GuideModelSelection modelSelection) implements GuideHistoryMutation {
        public ForkSession {
            requireSession(sourceSessionId);
            java.util.Objects.requireNonNull(cutoff, "cutoff");
            requireSession(sessionId);
            if (sourceSessionId.equals(sessionId)) {
                throw new IllegalArgumentException("fork target must be a new session");
            }
            if (ordinal < 0) throw new IllegalArgumentException("session ordinal is invalid");
            java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        }
    }

    record DeleteSession(String sessionId) implements GuideHistoryMutation {
        public DeleteSession { requireSession(sessionId); }
    }

    record ClearSession(String sessionId) implements GuideHistoryMutation {
        public ClearSession { requireSession(sessionId); }
    }

    private static void requireSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
    }
}
