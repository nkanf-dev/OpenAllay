# Phase 4 Native Domain Views, Retrieval, and Memory Foundation Plan

**Goal:** Deliver embedded data-driven Minecraft-native views, structural tables,
stable streaming/settings interactions, stronger local retrieval, and a documented
player-memory foundation without widening Agent authority.

**Architecture:** Preserve the closed semantic AST. Bind validated same-request
references to detached presentation models, then select optional client-thread
native providers with a deterministic TomeWisp fallback. Keep retrieval local and
evidence-preserving. Keep player memory disabled and separate from factual tools.

**Tech Stack:** Java 25, Minecraft 26.2 GUI APIs, existing CommonMark/Gson/SQLite,
optional JEI/REI public APIs, JUnit 5, Gradle multi-loader builds.

---

### Task 1: Record the state and authority contract

- Add SKMB-022, this design, and the implementation plan.
- Update the SKMB index, states, transitions, invariants, and failures.
- Commit documentation before product changes.

### Task 2: Fix stable screen interaction

- Give Tool/source rows stable focus IDs and separate focus from selected detail.
- Refresh capabilities whenever the guide screen is attached after settings.
- Replace cycle-model behavior with an anchored, scrollable explicit selector.
- Preserve viewport anchors and monotonic mutable-tail height during streaming.
- Add projection/interaction tests and localized labels.

### Task 3: Render structural Markdown tables

- Extend the closed layout model with measured table geometry.
- Implement wide native grids and narrow key/value cards.
- Preserve alignment, wrapping, semantic reference hit regions and narration.
- Add layout/renderer tests for wide, narrow and malformed cases.

### Task 4: Bind exact recipes to detached visual models

- Extend the recipe presentation snapshot without model-authored slots.
- Bind `recipe_grid` to exact same-request Tool output and persist a closed generic
  presentation projection with fallback.
- Add common projector/binder/codec/authority tests.

### Task 5: Add native-view providers

- Define the common view registry and visible-row lifecycle.
- Implement the neutral generic recipe/processing canvas.
- Embed JEI public recipe-layout drawables when exact resolution succeeds.
- Embed REI public display widgets when exact resolution succeeds; otherwise
  diagnose and fall through without weakening JEI/generic behavior.
- Keep provider objects client-thread-only and release them off-viewport.
- Add compatibility, fallback and lifecycle tests.

### Task 6: Improve local knowledge retrieval

- Add heading-aware stable chunks or an equivalent stable section projection.
- Replace ad-hoc occurrence scoring with deterministic field-weighted lexical/
  BM25-style ranking plus exact ID/alias metadata priorities.
- Preserve evidence/provenance and deterministic ties.
- Add a narrow optional reranker seam without enabling embeddings.
- Add multilingual, exact-match, long-document and failure tests.

### Task 7: Update Skills and memory foundation

- Teach recipe/guide Skills to request native components only after exact Tool
  evidence and never author slots, textures or layouts.
- Document the future selective, confirmed, editable player-memory schema and
  its separation from history, summaries and game facts.
- Do not enable durable player-memory writes in this increment.

### Task 8: Verify and retain evidence

- Run focused common suites while iterating.
- Run `./gradlew clean :common:test :fabric:build :neoforge:build`.
- Run package/credential/diff audits.
- Launch opt-in real Fabric/NeoForge clients for retained native-view, table,
  selector, selection and streaming-stability screenshots.
- Record installed viewer/mod versions, hashes, fallback/provider identity and
  honest limitations in a verification report.
- Commit coherent implementation outcomes directly to `main`.

