# Phase 4 Model Boundary Corrections Implementation Plan

> Approved from the designer's direct correction instructions; execute on
> `main` without a separate review checkpoint.

**Goal:** Make model origin/configuration unambiguous, add authenticated model
ID discovery, recover safely from provider Tool-name aliases, and allow a
server-hosted Agent to call the requesting player's client read-only Tools.

**Architecture:** Keep profile/catalog work in the client settings service;
add a strict shared model-catalog HTTP adapter; bind model names to canonical
registered Tools; extend the common bridge with request-scoped reverse Tool
execution and thin Fabric/NeoForge transport adapters.

**Tech Stack:** Java 25, Minecraft 26.2 native widgets, Gson strict codecs,
existing shared `HttpTransport`, common bridge chunk/correlation primitives,
JUnit 5, Gradle multi-loader builds.

## Task 1: Lock model ownership and recovery

- Add tests that new and recovered sessions use the local default after a
  connection boundary when persisted selection was server.
- Project the server canonical model ID as a clearly server-provided read-only
  choice; label client choices separately.
- Keep Models settings CRUD limited to client profiles and local credentials.

## Task 2: Add authenticated model catalog fetching

- Add strict provider catalog request/result/client types over shared HTTP.
- Cover OpenAI Bearer and Anthropic API-key headers, URI resolution, parsing,
  cancellation, status classification, malformed responses, and redaction.
- Extend model settings backend/service with one cancellable foreground fetch
  using typed replacement key first and saved credential second.
- Determine saved-key presence from the credential store rather than reference
  syntax.

## Task 3: Add model field Fetch/Choose UX

- Keep the model ID editable; add Fetch and searchable Choose controls.
- Invalidate candidates on draft/key generation changes and ignore stale
  completions.
- Add clear saved/not-saved/replacement hints and narration.
- Add English and Simplified Chinese translations and projection tests.

## Task 4: Normalize Tool aliases and failures

- Add tests reproducing canonical `tomewisp:inspect_game_state` returned by a
  provider despite the schema-safe encoded name.
- Route only registered encoded/canonical aliases and emit canonical IDs.
- Convert unknown/unavailable execution into normalized Tool results so the
  model can continue.
- Remove the generic “using a read-only Tool” invocation sentence and strengthen
  shared prompt/schema-name guidance.

## Task 5: Add server-Agent to player-client Tool execution

- Add accepted request capability and reverse call/result/cancel payloads with
  strict codecs and chunk/correlation tests.
- Add a request-scoped server executor that intersects client IDs with trusted
  read-only registrations, freezes placement, and normalizes remote failures.
- Add a client execution endpoint bound to the active request and frozen local
  capability snapshot, with owning-thread context capture.
- Wire symmetric packet kinds in Fabric and NeoForge and cover actor isolation,
  cancellation, disconnect, stale/late response suppression, duplicate IDs,
  and loader parity.

## Task 6: Real and deterministic acceptance

- Run focused common tests after each slice.
- Add an opt-in configured live Agent acceptance that resolves the ignored
  SQLite credential internally and never prints it.
- Run the live `mimo-v2.5-pro` greeting and real game-state Tool scenarios.
- Run a graphical Fabric server-model client-state scenario and retain a
  redacted report/screenshots.
- Run `./gradlew clean :common:test :fabric:build :neoforge:build`, scan tracked
  diff/artifacts for credentials, update development/status evidence, and
  commit coherent outcomes on `main`.
