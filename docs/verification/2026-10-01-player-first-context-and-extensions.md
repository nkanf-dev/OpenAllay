# Player-first context and scoped Extensions

This is source delivery, not a release tag. The previous built runtime was the 87473c2 source build. Existing worlds, credentials, model configuration, original exports and history files were not reset or migrated.

## Verified source changes

- Large game data lives in the request workspace, not in the provider transcript. Its model view states the real canonical size, schema/cardinality, selected data, omissions and handle lifetime. Derived scalar findings stay intact; complex datasets are represented explicitly rather than silently described as complete.
- Admission uses published JTokkit 1.1.0 encodings. Plain-text BPE counts are exact for the selected encoding; provider framing and unknown/Anthropic encoders remain labelled estimates/surrogates, not invented exact counts. No bytes-to-token factor, arbitrary percentage margin or hardcoded hidden-prompt reserve is used.
- The actual request's completion plan and whole input budget govern selected result views and summary requests. Complete ordered ToolUse/ToolResult exchanges, current questions/errors and original diagnostic records remain separate from reduced active views. Restored expired handles do not imply a live workspace.
- Generic result/content sensitive scanning is removed, not replaced with a smaller scan or a mode flag. Player-returned `token`, `password`, `APIkey` and other fields retain their values. Framework-owned credentials/authentication headers are not introduced into neutral prompt/trace/config structures; provider-private Reasoning is excluded by typed projection.
- Builder uses trusted invocation-scoped Extension methods without granting Agent Java/JVM access. Native write authority is a single independently declared per-Extension grant, default off and captured for the request. Reads do not imply writes, installation does not grant writes, and server-origin callbacks do not inherit local write grants.
- Rhino host JSON uses its mature Gson-recognized read-only interfaces. Native Array call construction and String coercion/allocation guards use existing Rhino native APIs. The dynamic schema helper preserves heterogeneous sampled shape without the broken recursive lexical callback behavior.
- History acknowledges only successful ordered writes and preserves failed batches for later cumulative persistence. Context reads cannot overtake captured writes. Model/capability publication uses one immutable state, preventing ordinary FIFO metadata completion from reverting newer saved settings.
- Guidance is generic and concise. Extension usage remains in its Skill. No Builder/model/scene names are hardcoded into system routing.

## Actual verification

- Full final common suite: **1393 tests**, zero failures/errors, **6 opt-in live-provider skips**, 26 seconds.
- Final integrated Fabric/NeoForge core builds and common API JAR passed.
- The final API JAR SHA-256 was `f056efa9ca3fb0bd8ff27ccbc399db18876abd12c4bcd1cb871bf625ca19444a`.
- Builder rebuilt and tested against that exact final API JAR: **221 tests**, zero failures/errors/skips; both loaders and `verifyLoaderPackages` passed.
- Actual nested JTokkit packages, encoding resource hashes and license verification passed for both loaders. No runtime download/native tokenizer artifact is required.

## Fault evidence and limits

The reported session-2 trace put 1,597,403 UTF-8 bytes of complete model text into the current protected exchange. Its terrain array held 16,641 columns and accounted for about 98.8% of the pure result. The first request could not summarize any older prefix; the next summarized a small prefix but retained the giant exchange. This was a result/admission fault, not a need to enlarge the model window.

A diagnostic native test also exposed quadratic URL redaction matching on a large string before admission. That path is gone with content scanning; it was not “fixed” by reducing the fixture, skipping tests, changing token ratios or relaxing deadlines. No claim is made that a selected encoding reproduces an unpublished gateway tokenizer exactly.

No fresh live-provider or graphical acceptance run is counted as passing here. Real-world latency and natural-model repeated-Skill-call rates remain manual acceptance items. The full independent review also recorded additional non-blocking parser/installer/retrieval/SSE issues; a static audit is not a claim those are all repaired.
