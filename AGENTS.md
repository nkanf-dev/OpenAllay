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

## Disk space and temporary worktrees

Disk efficiency is required for all work in this project.

- Check free space and the size of project temporary directories before large
  builds, downloads, or parallel work. Check again at delivery. Treat less than
  10 GiB free as a warning: stop avoidable large allocations and clean verified
  disposable output before continuing.
- Use only the worktrees needed by active independent tasks. Reuse a suitable
  clean worktree where practical. Avoid giving each worker its own full build,
  game runtime, asset download, or model copy. Share existing immutable artifacts
  and normal dependency caches when safe; keep mutable game profiles isolated.
- The coordinating agent owns the temporary-file inventory and cleanup. Record
  each worktree or large temporary directory, its purpose, owner, and retirement
  condition. Workers must report their temporary paths and cleanup needs.
- Retire completed worktrees promptly after verified delivery. Inspect Git
  status, commit recoverability, and active process references first. Preserve
  unpublished commits, uncommitted source, and unique diagnostics with a verified
  archive or durable Git reference. Use `git worktree remove`, not a blanket
  directory deletion. Keep branches unless their deletion is separately justified.
- Remove unused, reproducible module build outputs, worktree-local `.gradle`
  caches, temporary virtual environments, and duplicate download archives when
  their retained replacement has been verified. Preserve test reports and the
  required delivered artifacts first. Do not clear shared dependency caches just
  to remove duplicate worktree output.
- A directory named `build`, `tmp`, or an old profile is not automatically
  disposable. Check running process command lines, working directories, open
  files, native libraries, and configured paths. Never delete active dependencies
  or force-stop a player client to make cleanup easier.
- Preserve worlds, configurations, credentials, history databases and their
  WAL/SHM sidecars, voice models, screenshots, exports, and unique acceptance
  evidence. In particular, `build/e2e` and manual profiles can contain real game
  data. Do not overwrite a playable world's later native changes with an archive.
- Keep archival retention compact: use one hash-verified copy of unique evidence
  rather than many full directory backups. Move/archive data only for preservation,
  not as a substitute for actually freeing disk space. Report what was removed,
  what was preserved, the measured space recovered, and any remaining candidates.

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
