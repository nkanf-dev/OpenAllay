# SKMB-2026-07-29-032: Preserve Released Guide History Schemas

> Subsequent decision: [041](2026-10-01-041-execution-context-simplification.md)
> selects unversioned Latest Only internal formats before formal 1.0; no migration or compatibility readers remain.
> Its implementation/verification is tracked separately; this document retains
> the historical decision and is not proof that the new behavior is delivered.

- status: accepted
- decided_by: designer
- approval_source: >-
    The designer requested that the remaining historical compatibility baggage
    be removed and approved the parallel cleanup round. The automatic destructive
    pre-release policy conflicts with the project now having released 0.2.x builds.
- date: 2026-07-29
- commit: pending
- patterns: B_state_persistence, F_fail_semantics, G_irreversible_action
- scope: startup handling for SQLite guide history schemas 1 through 4
- supersedes: the automatic rebuild rule in SKMB-2026-07-19-019; it does not
  supersede the fail-closed boundary for future, corrupt, foreign, or unknown files.

## Decision

Schema 5 is the current guide-history format. A database with a recognized older
OpenAllay schema 1, 2, 3, or 4 is no longer automatically rebuilt at startup.
Opening it returns `history_schema_unsupported` and leaves its tables and rows
unchanged. OpenAllay does not migrate, reset, or delete old history implicitly.

The player may use an OpenAllay version compatible with that history schema to
export or otherwise preserve the data. If the player chooses to discard it, the
existing explicitly confirmed Debug Mode database reset remains available; the
UI must make the destructive scope clear and recommend a backup. A future
migration requires a separate accepted design and tests.

Current schema 5 continues to open, page, commit, and reset normally. Future,
corrupt, foreign, missing/inconsistent-metadata, and otherwise unrecognized
schemas keep their existing fail-closed behavior.

## States and transitions

- `history_loading -> history_schema_unsupported`: a structurally recognized
  schema below the current version opens; publish the stable failure and mutate
  no database tables or rows.
- `history_loading -> idle`: the current schema opens successfully.
- `history_loading -> persistence_unavailable`: a future, corrupt, foreign, or
  unrecognized database opens; mutate nothing.
- `persistence_unavailable -> history_reset_pending`: only after the player
  explicitly confirms the existing Debug Mode reset flow; reset behavior remains
  separately scoped by its owning decision.

## Invariants

1. Startup never drops or rewrites a recognized older guide-history schema.
2. `history_schema_unsupported` preserves the database file bytes and logical
   row contents; startup may not reinitialize or truncate it.
3. Current schema 5 remains writable and continues to use the existing ordered
   background repository.
4. Future, malformed, foreign, and unknown databases still fail closed.
5. The only implicit history change for old schemas is none. A migration or
   destructive reset requires an explicit later decision/action.

## Failure semantics

- Recognized schema 1–4: `history_schema_unsupported`; preserve it and explain
  that the file was not changed.
- Current schema 5: use normally.
- Future/corrupt/foreign/unrecognized: preserve and return existing stable
  unsupported/corrupt failure.
- Explicit Debug Mode reset: keep the existing separate confirmation and
  actor/database scope; do not imply the old history can be recovered after it.

## Verification

Tests create recognized schema 1–4 databases with rows and assert startup fails
without changing their bytes or rows. Tests also verify current schema 5 read/write
and existing unknown/future schema failure behavior. Fabric and NeoForge share this
common persistence policy.
