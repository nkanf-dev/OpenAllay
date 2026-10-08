package dev.openallay.integration.ftb.quests;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.knowledge.KnowledgeDiagnostic;
import dev.openallay.knowledge.KnowledgeDocument;
import dev.openallay.knowledge.KnowledgeKind;
import dev.openallay.knowledge.KnowledgeLoad;
import dev.openallay.knowledge.KnowledgeSourceProvider;
import java.util.List;
import java.util.Set;

public final class FtbQuestsKnowledgeProvider implements KnowledgeSourceProvider {
    private final FtbQuestsBridge bridge;
    private final Object player;
    private final boolean clientSide;
    private final String gameVersion;
    private final String loader;

    public FtbQuestsKnowledgeProvider(FtbQuestsBridge bridge, Object player, boolean clientSide) {
        this(bridge, player, clientSide, "unknown", "unknown");
    }

    public FtbQuestsKnowledgeProvider(
            FtbQuestsBridge bridge,
            Object player,
            boolean clientSide,
            String gameVersion,
            String loader) {
        this.bridge = bridge;
        this.player = player;
        this.clientSide = clientSide;
        this.gameVersion = gameVersion;
        this.loader = loader;
    }

    @Override public String sourceId() { return "ftbquests"; }

    @Override
    public KnowledgeLoad load() {
        java.time.Instant capturedAt = java.time.Instant.now();
        EvidenceMetadata sourceEvidence = new EvidenceMetadata(
                DataAuthority.INTEGRATION_API,
                DataCompleteness.COMPLETE,
                capturedAt,
                "ftbquests:api",
                "ftbquests:bridge",
                gameVersion,
                loader,
                dev.openallay.util.Java8Collections.mapOf("ftbquests:side", clientSide ? "client" : "server"));
        FtbQuestSnapshot.Result result = bridge.snapshot(player, clientSide);
        if (!result.available()) {
            EvidenceMetadata unavailable = new EvidenceMetadata(
                    sourceEvidence.authority(),
                    DataCompleteness.UNKNOWN,
                    sourceEvidence.capturedAt(),
                    sourceEvidence.sourceId(),
                    sourceEvidence.provenance(),
                    sourceEvidence.gameVersion(),
                    sourceEvidence.loader(),
                    dev.openallay.util.Java8Collections.mapOf("ftbquests:side", clientSide ? "client" : "server", "ftbquests:availability", "unavailable"));
            return new KnowledgeLoad(dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(new KnowledgeDiagnostic(
                    sourceId(), result.diagnosticCode(), result.diagnosticMessage(), "ftbquests:bridge")),
                    dev.openallay.util.Java8Collections.listOf(unavailable));
        }
        List<KnowledgeDocument> documents = dev.openallay.util.Java8Collections.toList(result.quests().stream()
                .map(quest -> new KnowledgeDocument(
                        sourceId(),
                        quest.questId(),
                        KnowledgeKind.QUEST,
                        quest.title(),
                        quest.description() + "\nDependencies: " + quest.dependencyIds()
                                + "\nCompleted: " + quest.completed(),
                        "ftbquests",
                        dev.openallay.util.Java8Collections.setOf(),
                        dev.openallay.util.Java8Collections.setOf(),
                        null,
                        true,
                        quest.provenance(),
                        new EvidenceMetadata(
                                sourceEvidence.authority(),
                                sourceEvidence.completeness(),
                                sourceEvidence.capturedAt(),
                                sourceEvidence.sourceId(),
                                sourceEvidence.provenance(),
                                sourceEvidence.gameVersion(),
                                sourceEvidence.loader(),
                                dev.openallay.util.Java8Collections.mapOf("ftbquests:side", clientSide ? "client" : "server", "ftbquests:quest_provenance", quest.provenance())))));
        return new KnowledgeLoad(documents, dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(sourceEvidence));
    }
}
