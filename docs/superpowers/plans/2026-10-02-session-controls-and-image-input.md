# Session Controls, Session Cost, and Image Input Implementation Plan

> **For agentic workers:** Work in assigned isolated worktrees. Root coordinates shared interfaces and serializes native Gradle verification. Do not read actual credentials, reset player data, start a game, make paid-provider calls, commit, or publish without root coordination.

**Goal:** Show the current session's actual accumulated cost and cache-hit information; support branching, queued follow-ups, safe-boundary steering, and pasted images for explicitly image-capable models. Research future game perception without implementing screenshot capture.

**Architecture:** One typed user-message contract carries text and immutable image references through compose, queue, steer, branch, history and provider encoding. The session owns controls and request identities; the Agent receives steering only at complete model/tool boundaries. Provider attempts own numeric usage observations, and durable per-request aggregates retain truthful session totals. Image bytes live in a managed attachment store, not model-history JSON or tokenized Base64.

**Tech Stack:** Java 25, current Minecraft 26.2 native UI, existing JDK/Gson/SQLite/JTokkit/provider codecs, ImageIO and platform clipboard APIs. No generic content DLP or new internal format versions/migrations.

## User-visible contracts

- Footer cost is the selected session's cumulative reference estimate, not the latest request. Missing reports/prices remain partial or unknown. Cache-hit ratio uses canonical cached-read tokens / total input tokens, with explicit known/unknown state.
- Per-call prices are captured when reported; older costs are not recomputed with a later model/catalog price. Summary calls and retried real provider attempts belong to the same session accounting, with exactly-once attempt identities.
- Fork selects a completed request boundary and creates a separate session/history identity. It never replays tools or revives old result handles. Inherited history is not a new billed operation. Missing actual boundary context fails honestly.
- Follow-up is queued for a new request after the active request truly releases its resources. Steer is a USER message accepted for the next complete safe model boundary of the same request, without changing frozen model/permissions. If no same-request boundary remains, it becomes an explicitly queued follow-up.
- Stop cancels current work and all unconsumed follow-ups/steers for the selected session. It does not undo completed world changes. Session close/delete/disconnect revoke queued work and fence late callbacks.
- Clipboard images are pasted only by an explicit player action. They show removable previews and remain in the draft until submission succeeds. Text-only paste remains normal. Screen/session/draft generation protects late clipboard callbacks.
- Image-input capability is SUPPORTED / UNSUPPORTED / UNKNOWN with a source. It is not inferred from a model name or generic audio/video multimodality. Unknown gateway aliases can be explicitly configured rather than silently switched to another model.
- Images are encoded only by provider HTTP adapters. OpenAI image_url and Anthropic base64 content use the real model-facing API; history/trace/export contain references and descriptions, not bulk Base64. Images are not scanned for sensitive content.
- Native text tokenizer accounting remains mature BPE. Unknown image accounting is labelled unknown/estimated rather than treating the binary representation as text tokens or inventing a universal image-token formula.
- No automatic game screenshot capture, periodic frame collection, screenshot permission prompt or renderer interception is implemented in this task.

## Owned workstreams

1. **Billing/cache:** canonical usage presence semantics, provider parsing, request/session totals, SQLite usage projection, restore/fork ownership, footer display.
2. **Usage source:** one numeric observation per real provider attempt, including summaries and failures; no private reasoning/body. Model/Agent event codecs share exact current shape.
3. **Fork:** ordered boundary persistence, atomic full-history copy up to a stable cursor, independent request identities and bounded returned viewport, menu/actions.
4. **Follow-up/steer:** pending inbox and safe consumption, lifecycle/cancellation/release correlation, remote control packets and APIs. No Screen edits.
5. **Image pipeline:** model content/reference/store, declared model image support and updater, provider encoding, budgets/history/export/bridge attachment resolution.
6. **Composer:** text/image per-session draft, clipboard platform adapter, removable thumbnails, queued-message/steer controls, responsive layout. Owns composer Screen methods only.
7. **Perception research:** primary-source documentation comparing structured game observations, imported images and future explicit screenshot capture.
8. **Independent `/compact`:** local composer command, idle-only two-phase summary preparation and publication, real summary usage in session totals, preserved original history and draft images. `//` sends a literal slash. Unknown commands keep the draft. Ship as a separate commit; server control is explicitly unavailable in this first implementation.

## Shared ownership

- GuideService: billing owns telemetry/usage event application; fork owns fork/cutoff helpers; controls owns ask/submit/cancel/queue/release; image pipeline supplies typed submission and validation hooks without duplicating submission lifecycle.
- SQLite/history: billing owns usage columns/codecs/aggregate; fork owns fork transaction and boundary capture tables; image refs stay in ModelContextCodec and the independent managed store.
- OpenAllayScreen: billing owns footer; fork owns session/request action menus; composer owns compose/init/paste/mode/pending controls.
- Model codecs: billing owns usage parsing; image worker owns image content encoding; shared files are merged by method, not overwritten wholesale.

## Verification

- [ ] Canonical cache counters: OpenAI total input includes cache; Anthropic total includes uncached/read/write as actually reported. Cache-write is not a hit. Missing fields differ from explicit zero.
- [ ] Attempt usage exactly once: duplicate updates/completions, retries, failed/cancelled attempts, queued cancellation, summary calls, late usage and server event round trips.
- [ ] Durable session totals: more than one UI page, reload/restart, model switches, missing/partial price information, immutable quoted prices and inherited fork billing exclusion.
- [ ] Fork full history and exact context: cutoff while source continues, complete call/result pairs, missing boundary, source/branch deletion independence, image retention and no side-effect replay.
- [ ] Controls: follow-up order/edit/cancel, active steer with model/tools/rate limit, last-turn race, Stop clearing pending, resource release, disconnect/delete generation fencing and local/server parity.
- [ ] Images: valid PNG/JPEG, malformed/unsupported/oversized input, alpha/dimensions, path confinement, integrity hash, attachment missing/error, image-only messages, draft retention, two-provider wire shape, long sessions/compaction, branch/retry/queue reference lifetime.
- [ ] Clipboard/layout: deterministic fake providers and owner dispatch; no real clipboard capture in worker tests; small-window accessibility and text paste behavior remain intact.
- [ ] Full common suite, both loader builds, package checks, scripts and exact coordinated-source tests. No unrequested live-provider costs.
- [ ] Record remaining live/manual validation honestly; commit/push coherent batches, no aggregate mega commit or unrequested release/tag.

Existing player worlds, databases, exported conversations and credentials are never implicitly deleted, reset or migrated. Current-shape failures remain visible and non-destructive.
