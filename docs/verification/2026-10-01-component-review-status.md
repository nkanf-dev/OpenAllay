# Component review status

This record separates confirmed defects, fixes under native verification, and findings that remain open. It is not a claim that a static audit proves all runtime behavior. User data, credentials and worlds were not used as regression fixtures.

## Main repair and verification lanes

- Large result injection: confirmed 1.6 MB complete model view. Canonical data stays external; selected model views, real token counts and whole-request admission are being verified together.
- Tokenizer: published JTokkit 1.1.0 with pinned public resources and MIT license. Content counts are exact for the selected encoding; unknown models/Anthropic are labelled surrogate estimates, not guaranteed provider-native counts. No byte/token ratio or arbitrary margin is used.
- Agent/Extension authority: native world-action grants are independently declared per Extension. Builder calls must not grant the Agent arbitrary Java. Controlled argument/return types, cancellation, frozen policy and server isolation have an initial 127-test focused gate.
- Rhino helper: actual nested schema failure reproduced. Callback-free helper implementation retains its full schema semantics; 49 focused tests passed. A separately verified native `Array(...)` constructor prototype defect and String allocation guard coercion issues are being handled through the existing native runtime APIs, not by rewriting user code.
- Host JSON: closed views now implement the mature Rhino/Gson-recognized read-only interfaces. The final focused gate passed 48 tests. This fixes both data correctness and implementation metadata leakage without claiming reflection RCE had been demonstrated.
- History writes: a confirmed failed-write ownership bug can lose original context after a successor succeeds. Ordered acknowledgement/coalescing and context-read barriers are under separate native verification.
- Settings publication: ordinary FIFO metadata/save completion can revert the runtime while disk/UI show newer settings. A single immutable publication state and CAS/committed-publication paths are under separate native verification.

## Findings not silently declared fixed

The full reviews also found or proposed work on Extension installer filename collisions; two-rename Skill update crash windows; strict JSON duplicate/fraction parsing; actor-wide remote chunk assembly resources; SSE framing; output ceiling/request/reserve semantics; retrieval TF and inverted indexing; mature YAML/frontmatter parsing; duplicate Markdown parsing; and template/journal I/O edges. These require their own precise tests and coherent changes. They are not fixed merely by adopting a tokenizer or a modern library.

## Component reuse rule

Existing JDK HTTP/concurrency, Gson/SQLite/CommonMark and native Rhino interfaces are retained when they fit the required contract. Replacement libraries are evaluated for maintenance, license, Java 25 and both loaders, native artifacts, cancellation and serialization behavior. No broad engine/framework migration is justified solely by the word “modern”. No task, Extension name, model alias or scene size is hardcoded into system prompts as a workaround.

## Remaining validation boundary

Focused counts above predate final whole-project integration unless explicitly stated otherwise. New code is not published or a test client restarted until the final relevant suites and package gates pass. Model-visible samples remain samples, original history remains original, and expired workspace handles are not fabricated into working recovery paths.
