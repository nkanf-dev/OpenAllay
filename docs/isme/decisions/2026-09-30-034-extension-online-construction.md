# SKMB-2026-09-30-034: Extension-owned online construction and invocation context

Status: accepted for the user-requested implementation. The user explicitly
approved full online construction, independent Extension ownership, default
inclusion, and completion; implementation details below select the narrow
existing-authority path. This is not approval of an additional remote write
protocol or server-model JVM permission.

## Scope and ownership

The independent OpenAllay-Extensions repository owns construction code, its
Skill, JS modules, native Minecraft adapter, templates, tests and artifacts.
OpenAllay core contains no building functions, schemas or Skill-name checks.
Default product distribution includes the separately built loader Extension;
installation never enables unrestricted JavaScript.

All intended building features of pofice/minecraft-builder are included: online
setup/version/player discovery, blocks and connection repair, all geometry,
decoration and six presets, terrain/ground scans/flattening/vegetation removal,
terrain-following paths, and persistent structure scan/save/load/list/paste
with rotation and mirroring. Implement algorithms independently, not by copying
source for which the complete license notice has not been verified. Do not
reproduce incorrect namespaces, fixed dimension heights, silent path fallback,
or incomplete native block-state transformations.

Offline save opening, dependency installation and region-file editing have no
place in the live process. Setup binds the active world; finish flushes only
Extension artifacts and releases the operation, never closes Minecraft. Online
before-images support explicit conflict-aware undo, not whole-save backup or
atomic rollback. Entity simulation and unrelated changes are outside this
block-edit journal. No automatic restore on error, cancellation or startup.

## Generic trusted invocation interface

An Extension can contribute JavascriptInvocationParticipant declarations, each
with a namespaced ID, and open(JavascriptInvocationContext) returning an
AutoCloseable scope. The core validates/publishes participants transactionally
with existing contributions, preserving the four-list constructor for existing
extensions. Context contains the immutable ToolInvocationContext, cancellation,
active-scope check and an execution-local trusted evidence sink. The runtime
opens participants around one JavaScript call and closes in reverse order on
success, failure or cancellation. A read-only completion outcome distinguishes
a successful script return from failed/cancelled scope exit; lifetime revocation
alone is not an operation outcome. The successful-return marker is not proof
of tool normalization, evidence validation or a domain operation succeeding.
Request close revokes in-flight scopes so a
late native operation cannot continue queued work. Failed participant setup
closes previously opened scopes and fails that execution, never leaves bindings
from a previous execution. No live-object host binding or extra tool is added.

A participant may bind its own worker-local facade. All loader/world discovery,
thread scheduling, domain operations, result records and cleanup belong to the
Extension. It cannot promote permissions; the frozen context remains
unchanged. Disabled contexts do not get a builder facade. Native helper access
uses the existing unrestricted Java bridge under decision 033.

Evidence is added only for actually completed captures or mutation/readback
operations, not participant installation or merely loading a module. Existing
no-evidence failure remains. Each source describes real authority, coverage,
capture time and provenance. An extension receipt is not proof that every
subsequent script claim is true. A late/closed sink rejects publication.

## Online execution and failure

The full native backend binds the active integrated server, player, connection,
level and dimension identity. Every queued read/write revalidates that binding
and scope cancellation on the owner thread. Persistent undo identity uses an
Extension UUID stored through the live server SavedData API, not a reusable save
path or seed; normal game-owned SavedData loading/saving is not a custom offline
editor. A client world is never used as an
authoritative write backend. An unavailable authoritative world returns an
explicit structured unavailable result. This does not silently fall back to
commands, offline files, client ghost writes, or a planning-only product.
Existing remote command capability remains independent; this decision does not
add a server write endpoint or assume client Java can edit a remote server.

Workers submit immutable actions; game threads do not execute Rhino callbacks,
wait for workers, perform file IO or run long unyielding loops. Large operations
use cooperative scheduling slices, not total feature/volume limits. Reads and
writes reject unavailable chunks, invalid states, dimensions and bounds rather
than treating them as air. If chunk acquisition is supported it is explicit.

Operations distinguish ready/running/completed/failed-partial/cancelled-partial/
closed. Cancellation prevents further queued writes, not already applied world
effects. A before-image journal records original and expected resulting block
states, including block-entity data, before a write; durable progress is updated
after application. Undo is another authorized operation against the same world
and checks for intervening edits. Conflicts and unverified/uncertain intents are
reported, never silently replaced. Native neighbor/physics updates are executed
but are not retrospectively attributed to the direct block-edit journal across
ticks; later physics changes may therefore produce honest undo conflicts.
Explicit shape-repair writes keep their captured expected-before comparisons. Known-not-started intents are removed or restore their prior verified image, so
undo cannot mistake an external matching edit for this operation. Ambiguous
started writes retain uncertain intent/readback for reconciliation. IO failure
before recording intent prevents that write. Interrupted
journals survive as interrupted; no automatic replay.

Templates are separate versioned application artifacts with relative positions,
namespaced states/properties, optional block entity data, explicit air policy,
and game/data versions. Validate on load, persist atomically, and preserve
unknown/missing-registry failures without dropping content. Use native state
mirror/rotation and neighbor updates; report unsupported opaque block-entity
transform semantics rather than fabricate fidelity. Whole-world/entity restore
is not claimed by this block construction API.

## Requirement declarations

Additional metadata declares needed capabilities and dependencies. Under the
user's latest instruction these are advisory UI requirements: missing needs do
not prevent install, enable, or explicit Continue anyway. They do not grant
permission. The actual native call still requires frozen unrestricted local
authorization and a supported active backend. Requirements do not repeatedly
inject policy text into model context. Decision 035 owns the generic UI contract.

## Verification

Cover every public capability and preset with deterministic Rhino/backend
contracts; native scheduling, cancellation, same-identity reconnect and partial
failure; template round-trip/transform/persistence/undo conflicts; trusted sink
isolation and request close; no authority on server callbacks; both loader jars,
package manifests and default inclusion. No graphical/live-provider acceptance
claim without explicit user-requested retained runtime evidence.
