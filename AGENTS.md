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
