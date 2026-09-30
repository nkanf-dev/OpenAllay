# Unrestricted JavaScript guidance implementation plan

**Goal:** Automatically provide Java/JVM guidance for a request with frozen client-local unrestricted authorization, without granting authority through a Skill.

**Architecture:** Use the existing bundled Skill loader and immutable request Skill catalog. The same request authorization controls the prompt, Skill availability, and JavaScript runtime. Keep server models and callbacks isolated. Core Skill instructions are available immediately; longer examples remain declared references.

## Tasks

1. Inspect command Skill publication, unrestricted request freeze, prompt composition, and Skill executor snapshots.
2. Add `unrestricted-javascript` bundled instructions and a declared Java interop reference. Verify every API example against the Rhino runtime; do not imply Nashorn-only helpers exist.
3. Make authorized request prompts explicitly support Java/JVM operations and automatically include the core Skill. Default requests remain isolated. Preserve the deny-only tool/Skill policy and the existing immutable request lifetime.
4. Prevent server command prompt construction from unhiding unrelated optional Skills. Test enabled/disabled requests, freeze across setting changes, server exclusion, references, and contradictory prompt text.
5. Update decision 033 and development documentation with automatic guidance semantics. Skills never grant authority; preserve Minecraft thread ownership, cancellation, evidence boundaries, and secret non-disclosure.
6. Run targeted tests, then `./gradlew clean :common:test :fabric:build :neoforge:build` and package verification. Review the exact diff and preserve unrelated `.DS_Store` files.

## Review notes

Implementation and focused review are complete. The parent session owns the
full common/loader build and package gate before commit.

- Prompt metadata, automatic core instructions, and `load_skill` use the same
  immutable request catalog. References stay progressive rather than being
  copied into every prompt. A request with no available Skills has no `load_skill`.
- Authorization comes from the frozen local request, never from the Skill.
  Changing the toggle, repository, or capability policy leaves an active request
  unchanged. Future requests use their captured mode and published catalog.
- Denying the Skill hides its instructions and references even in authorized
  mode. Denying a required Tool still follows the existing deny-only policy.
- Server models and server-originated callbacks remain isolated. The server
  command prompt only enables command guidance, not other runtime-hidden Skills.
  Both loader callbacks use `captureServerToolContext`, which captures false.
- Context reservation covers the larger prompt variant. Bundled Java examples
  execute against Rhino. Thread ownership, cancellation without rollback,
  evidence limits, and secret non-disclosure remain explicit without restoring
  unrelated warning boilerplate or inventing call/retry caps.
- Removed an unrelated benchmark reminder from the command reference and the
  fixed retry limit from missing-recipe guidance. Tests guard against both.
- Focused verification: 62 tests across 11 classes; zero failures. This covers
  prompt composition, request execution and freeze, capability resolution,
  bundled Skills and references, Rhino examples, Skill loading/repository,
  callback dispatch, and local Tool execution.
- No generic Extension, metadata-requirements, or builder changes. Unrelated
  `.DS_Store` files are preserved. No commit or push was made during this review.
