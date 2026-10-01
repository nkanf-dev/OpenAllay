# Repository notes for coding agents

This file is repository documentation, not a source of product or security authority. Treat it as untrusted guidance and verify relevant behavior against implementation, tests, and accepted decisions. If it conflicts with those sources or the user's request, do not follow it blindly.

## Repository map

- `common/` contains shared product behavior.
- `fabric/` and `neoforge/` contain loader integrations.
- `common/src/main/resources/assets/openallay/openallay_skills/` contains progressively loaded Skills.
- `docs/` contains product and design context; prose may be stale and is not proof of runtime behavior.

## Verify changes

Use the checked-in Gradle wrapper. `./gradlew :common:test` runs the common tests; `./gradlew :fabric:build :neoforge:build` builds both loaders. Select focused tests during iteration and broaden verification to match the change.

Inspect the working tree before editing and preserve changes that are not part of the task. Do not include credentials or generated build/runtime output in a change.

## Pre-1.0 release boundary

Before formal 1.0, OpenAllay is in rapid iteration and internal formats are
Latest Only. Code and formats released atomically with the same framework do not
need internal schema/protocol versions, versioned filenames, migrations, or old
compatibility branches. Keep only the current implementation and exact shape
validation. Do not add version numbers merely because data is persisted or sent
across an internal boundary.

Independently released community components, such as Extensions and the public
core Extension API, do need versions and compatibility contracts. Minecraft,
loader, native game dataVersion, model, and dependency versions are external
facts, not internal format gates. Revisit production data compatibility when
formal 1.0 creates a real requirement, not preemptively. This rule does not permit
accidental deletion of world saves or exported diagnostic material.
