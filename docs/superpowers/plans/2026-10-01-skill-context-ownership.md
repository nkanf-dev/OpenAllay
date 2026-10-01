# Skill Context Ownership Implementation Plan

> **For agentic workers:** Execute this parent-approved slice inline. Do not commit or run Gradle until the parent schedules verification.

**Goal:** Reuse only Skill plaintext actually retained in the active context and expose its document/range facts to the model.

**Architecture:** Captured Skill documents own immutable source text and precomputed fingerprints. The session owns an index of retained ranges and weak validation receipts, not another plaintext cache. Every actual projection reconciles this index; request executors bind to it and emit a factual manifest. Original history remains separate from projection changes.

**Tech Stack:** Java 21, existing model messages and tool executors, JUnit 5.

---

- [ ] Precompute immutable document identity in SkillDocument; preserve public accessors.
- [ ] Replace request-owned Output caches with session-owned range facts and weak exact validation.
- [ ] Project stale/duplicate instruction payloads without changing historical result error flags.
- [ ] Bind session index through executor prepareContext and render a factual loaded document manifest.
- [ ] Keep original request history before projection changes; exclude private reasoning from retained turns.
- [ ] Let repeated load_skill calls execute and report real reuse results, not synthetic repeated-call failures.
- [ ] Cover full/partial retained ranges, compaction, restore, changed/deleted documents, session isolation, and exact model requests.
- [ ] Coordinate client-placed catalog identity with the parent before bridge protocol edits.
- [ ] Run static checks now; parent runs focused common tests and loader builds later.

## Verification and limits

- Source reads during `load_skill` use the frozen in-memory catalog. A Tool card proves a model call, not a disk read.
- Repeated `load_skill` still emits real started/completed events. A successful reuse output has no instruction plaintext.
- The loaded manifest is an ephemeral system-prompt fact. It is never appended to original model history.
- Full document availability requires the retained range union to cover `[0, document length)`. A tail chunk reaching EOF is not a full document.
- Changed source, disabled Skill, deleted reference, or changed exact document bytes invalidate only that document's projected availability. Historic status bits and archived request transcripts remain unchanged.
- A client's safe frozen documents may differ from a server's later outbound secret scrub. That exceptional delivery reports `skill_delivery_redacted`; it does not forge a safe content identity or auto-load in a loop.
- Source identity is an opaque digest of actual side and captured source identity. Model routing is not a source change.
- Source fingerprints/chunks are computed once per immutable document. Reconciliation is O(current message blocks + new instruction bytes + retained ranges log ranges); unchanged exact Tool results use a bounded weak identity cache. Coverage queries are O(log intervals). The index owns only metadata and weak receipts, not plaintext.
- Stop detaches the cancelled lease's index. A late callback cannot change the successor's loaded facts. Restore/hydrate creates a fresh index and validates the retained exact safe plaintext.

Focused tests: `dev.openallay.agent.AgentSkillOwnershipTest`, `dev.openallay.agent.session.AgentSkillLeaseIsolationTest`, `dev.openallay.skill.SkillOwnershipTest`, and `dev.openallay.bridge.client.RemoteSkillContextBridgeTest`, plus the existing Skill/bridge/server lifecycle suites.
