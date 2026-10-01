# Builder performance and runtime controls

Status: deterministic source and packaging gates passed. No release tag or product version change. No new graphical client or live-provider run was started while the user was testing.

## Source boundary

Core base: `1dd8a07`. Builder base: `174d2f5`. Tested new Builder source: `7923e3ffdceb640ce3e4634afb9b56a89cebcf13`. The independent Extension changes are committed separately: journal streaming/prefix-scan removal (`78275b2`) and coherent native/JavaScript algorithms plus documented shape/physics split (`7923e3f`). The core lock pins the exact clean source.

## Demonstrated work reductions

These are exact fixture/counter results, not measured Minecraft speedup factors:

- Full-height 100x100 flatten: 10,000 region calls become 100; exact requested height and before-images remain. Returned dense JSON falls from about 183 MB to about 2.87 MB in the deterministic fixture.
- Empty 100x100 ceiling: 10,000 calls become 100, with canonical air omitted from transport. Native section palette proof can skip entire confirmed vanilla-air/no-block-entity tiles. Cave/void/custom air and live/pending block entities remain explicit.
- Blocked 100x100 width-3 path: 43,031 region reads plus 5,107 column scans become 29 demand-tile probes; 5,215 visited nodes and deferred error consumption stay unchanged in the fixture.
- Explicit `batch(callback)` groups custom placements. Public `read_region` and `get_blocks` provide fresh batched checks without changing scalar-read semantics.
- Native work is cooperatively sliced. Both exact client and server identity gates remain; a game owner never waits on another owner. A single native hook/readback can exceed the admission deadline.
- Journal writes stream the current shape and remove abort-prefix rescans and terminal whole-history directory scans. Durable intent precedes mutation; actual outcomes precede later work.

## Intentional connection behavior change

`update_connections` now performs native connection-shape normalization and journals actual changes. It no longer implicitly sweeps every halo cell through neighbor/comparator notifications. `sync_physics` explicitly retains that full notification pass. Native block/shape hooks, block entities, before-image checks, partial outcomes and undo conflicts remain. Shape repair is a deterministic section-order single pass, not a fixpoint or a promise that later physics has settled. This behavior change is stated in the Extension README and Skill references; it is not silently described as old full-physics equivalence.

## Manual evidence correlation

Different requests in the same manual directory must not be combined into one diagnosis:

- The specified session-1 partial export belongs to a request completed in 304.345 s. Its build tool failed after 235.250 s with `concurrent_edit`. Its submitted source explicitly requested a 310,329-cell connection halo.
- Session-3 request 16 completed in 598.94 s: 365.60 s of JavaScript tools and about 233.34 s outside those tools. Two connection halos totaled 336,594 cells; nested scalar reads/writes were also present.
- Session-3 request 17 completed in 662.261 s: 280.625 s of JavaScript tools and about 381.636 s outside them. A Java 25 virtual-thread snapshot matched the final 16.227 s tool's scalar `get_block` loop inside `filter`, not a journal/finish stack.
- A historical cancelled request lasted 41 min 43 s; its only JavaScript tool was pending for about 40 min 55 s. It requested a 49x49 scan and performed no building writes, connections or finish. It predates the current manual JAR replacement/process. The retained trace has no native subphase timing and cannot identify which read/capture call stalled.

No claim is made that the user's visually completed, 40-minute building observation maps exactly to one of these retained requests. No provider response body, environment value, credential, original conversation export, world or operation journal is copied into this record.

## Core controls and context

- Commands freeze at request submission. Request guidance/catalog reflects the actual top-level binding, independently of unrestricted Java. The manual model's `mc` catalog inspection was not proof that the top-level global was absent.
- Model profiles can request provider effort. AUTO omits the field and does not assume a gateway default. OpenAI Chat uses `reasoning_effort`; Anthropic uses `output_config.effort`, without implicitly enabling thinking. Specific gateways/models may reject explicit choices.
- Prompt changes remove repeated restrictions and prioritize completing tasks with real capabilities. Authorization remains in code.
- Known credentials are redacted before model continuation, compaction and persistence, including JSON property names. This does not sandbox unrestricted JVM code or promise to protect arbitrary obfuscated/environment secrets.
- Skill delivery is session-owned range metadata derived from actual retained safe text. The active projection owns the only document body; original history remains separate. The manifest states exact full/partial coverage. Changed/deleted documents, compaction loss, system delivery, model/source placement and cancellation are covered independently. Ordinary duplicate calls return reuse without new plaintext. They remain real visible tool calls; no UI events are hidden to invent zero calls.

## Verification so far

- Core before Skill integration: 1,163 tests, zero failures/errors, six opt-in live-provider skips.
- Skill/Agent/bridge integrated focus: 172 tests, zero failures/errors/skips after fixing actual initial-manifest budget reservation and strict malformed-manifest error classification.
- Builder full independent gate: 203 tests, zero failures/errors/skips; both loaders and `verifyLoaderPackages` passed against the integrated core JAR.
- Final pinned core gate: **1,206 common tests**, zero failures/errors, **six opt-in skips**; Fabric and NeoForge builds passed.
- The ordinary default distribution gate rebuilt the exact pinned Extension against the final core API, not a dirty/unpinned fallback.
- Distribution, Phase4 and nested SQLite packaging checks passed. Nested JDBC drivers loaded successfully; Linux/macOS/Windows x86-64 and ARM64 native entries were present.
- **117 offline script tests** passed. They do not count as graphical or live-provider acceptance.

| Local source artifact | SHA-256 |
| --- | --- |
| Fabric | `1bd63da8dd90ad91e9b3c9fda615a41c25082f3a806810dede276faddff82660` |
| NeoForge | `7f54b5b4b6aed8a29e0ed47ee2ff9c02f6b49e985e354e6378f5a503c19b91dc` |

These are newly built source artifacts. Published v0.2.4 assets and tags are unchanged. Existing Gradle deprecation, Javadoc, native-access and dependency warnings did not fail the gate.

## Not measured

No new real-game 100x100 city timing, render/tick latency comparison, live-model effort acceptance, or natural-model three-question Skill-call rate was measured. Deterministic work reduction is not a latency guarantee or proof of a globally optimal algorithm. Unrestricted or explicit full-physics work can still be expensive, and model waiting time remains separate from native execution. Restart is required to load the final packaged source build; the user's running instance was not overwritten.
