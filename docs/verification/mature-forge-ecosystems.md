# Mature Forge ecosystem backport

## Delivered task-branch targets

This work adds actual Forge integrations for Minecraft **1.19.2** and **1.18.2**.
Both use **Java 17**, the one Minecraft-free Java 17 feature engine and Rhino,
and the Java 8 public Extension SDK and universal Builder.

The source is on `mc/mature-forge-ecosystems`. Builder source is on
`mc/mature-forge-builder-targets` in OpenAllay-Extensions. Published v0.4.2
continues to cover its original 23 Minecraft versions and 46 loader pairs.
No release or main-branch merge was made for this backport.

| Minecraft | Native loader | Java | Accepted operations |
| --- | --- | --- | --- |
| 1.19.2 | Forge 43.5.0 | 17 | Bundled product, commands, building, original-world reload, native UI and visual review |
| 1.18.2 | Forge 40.3.0 | 17 | Bundled product, commands, building, original-world reload, legacy editor, mixed native toasts, native UI and visual review |
| 26.2 representative | Fabric, exact checked-in pins | 25 | Affected shared-native UI, complete native frame capture and visual review |

Each Forge building acceptance checks **85 actual block landmarks**, **14 durable
journal rows**, the native SavedData world UUID, actor and dimension, and template
bytes. Reload uses the original disposable world and preserves the UUID and
original template SHA256. Worlds remain survival with cheats off. Command checks
use the registered JavaScript Tool and actual client/server feedback. Cancellation,
closed-request reuse, owner-thread checks and restored JavaScript settings are
observed in the same native process.

The 1.18.2 editor receives actual character, key and mouse callbacks. Native paints
confirm wrapping, caret, selection and focus. Copy, cut, paste, undo, redo, character
limits and complete replacement are checked. The fixture restores the original
text, character limit, geometry and clipboard. Committed characters are the tested
input boundary; hardware IME composition and microphone capture are separate items.

The 1.18.2 toast manager preserves its actual five 32-pixel slots. Two 64-pixel cards
and one ordinary 32-pixel toast render at 0, 64 and 128 pixels. A 64-pixel FIFO head
waits when only one tail slot is free. Native removal releases capacity once;
queue order, native animation, rendering and clearing remain owned by Minecraft.

## Original artifact provenance

Accepted files are retained from the original runs. Later source or validator
changes do not relabel those files as new builds.

| Proof | Run / attempt | Product source | Production artifact ID |
| --- | --- | --- | --- |
| Forge 1.19.2 building, reload, commands and UI | [37388892087](https://github.com/nkanf-dev/OpenAllay/actions/runs/37388892087) / 1 | `438ba61a84c48333dc1cda92a55463f7b01e2c16` | `11380706080` |
| Forge 1.18.2 building, reload and commands | [37397120987](https://github.com/nkanf-dev/OpenAllay/actions/runs/37397120987) / 1 | `01636660736f417c7359485a5c834affe5a1ce7d` | `11383755758` |
| Forge 1.18.2 UI, editor and toasts | [37398771932](https://github.com/nkanf-dev/OpenAllay/actions/runs/37398771932) / 1 | `1122e5314677f5ea0483691a3cbd066fb6180866` | `11383699840` |
| Fabric 26.2 affected UI | [37401212610](https://github.com/nkanf-dev/OpenAllay/actions/runs/37401212610) / 1 | `9396684bd95f48bdfbc7cf9c6db5d261247cc6e6` | `11384878871` |

The 1.18.2 building/reload and UI proofs use **two original artifact hashes**.
They are not a claim that one artifact ran every scenario. The modern game scenario
completed all 17 native UI stages. Its original CI run failed afterward because
the validator incorrectly applied a Builder screenshot gate to the UI scenario.
The branch was corrected. [37403544396](https://github.com/nkanf-dev/OpenAllay/actions/runs/37403544396)
passed the unchanged original report and lossless frame revalidation without a
rebuild or game restart. The original failed run remains failed.

```text
c09f84ccc7c680fccecacc36280fd3564c1bfd01c0950c4a2aeff7970b429a64  forge 1.19.2 (building/reload/commands/UI)
8a03c0b111085bbb6ed5434b1605c93c74ada9e43bbaefda9a1f902d4369a2dd  forge 1.18.2 (building/reload/commands)
e5cb3461452f5361e1981fa9de94157c302311c6ee8fd682259adcd14dabb2bf  forge 1.18.2 (UI/editor/native-toasts)
98aece007f0b4177276160f4db87b1db38c78338253e5d14079bb1c8cfcbc6af  fabric 26.2 (affected modern UI with correction receipt)

```

Builder 0.4.0 source `56d26246a3a5fad654379d5f837f0a12104b6015`
has one Java 8 payload and 48 exact declared target pairs. Its runtime declaration
and packaged JSON agree in both values and order. The host keeps normal target,
SDK and lifecycle gates. Earlier successful algorithm tests retain their original
run/source identities; only affected declarations and failed classes were rechecked.

## Native boundaries

- Old Forge names, metadata, channel callbacks, key setup and client shutdown use
  the selected loader's real APIs. Forge is not aliased as NeoForge.
- Shared feature screens remain single-source. Small typed ports own native text
  factories, widgets, lifecycle, resources, command callers, graphics and viewer layouts.
- A real product-owned multiline primitive supplies the native widget absent in
  1.18.2. Modern targets still register and operate their actual native multiline widget.
- Shared Gson code uses the public 2.8.9 ABI with typed Instant handling and Java
  record accessors/canonical constructors. Caller adapters and options stay bound.
  Arbitrary adapters wrapping Gson's private reflective implementation are outside
  this binding contract. No game Gson replacement or final-field/Unsafe record write
  is used.
- Legacy native reobfuscation runs before exact compiled engine entries are placed
  into the final archive. Full byte equality, sole nested SDK/Rhino identity and
  singleton runtime metadata are checked before launch.
- Worker class-loading ownership is scoped and restored. Cancellation interrupts
  active workers, not already-settled completion callbacks.

## Older-anchor decisions

Stock Forge 1.16.5 and the archived Forge 1.12.2 Java17 target later completed
integrated-client adaptation. See the [original validation records](stock-forge-integrated-clients.md)
and the in-progress [Forge 1.12.2 Java8 port](forge1122-java8-port.md).
The following prerequisite decisions describe the earlier research checkpoint.

### Stock Forge 1.16.5: native title prerequisite passed; product port deferred

[37401467532](https://github.com/nkanf-dev/OpenAllay/actions/runs/37401467532)
installed actual Forge **36.2.42** and booted its unmodified client on exact
**Temurin 17.0.18+8**. Two screenshots visually confirmed the native title screen.
Class-load evidence identifies ModLauncher 8.1.3 and ASM 9.6. Only the four official
launcher arguments were used. The client had Minecraft and Forge built-ins, with
no OpenAllay or third-party mod. SIGTERM exit 143 was an intentional collection
stop, not a game-requested orderly-shutdown proof.

At this earlier checkpoint, the full OpenAllay port was deferred. Game Gson 2.8.0 lacks `Gson.newBuilder()` and
other current public APIs. Preserving arbitrary injected Gson adapters and options
would require a replayable builder ownership project across the shared engine,
including lazy record probes and Optional overlays. This is larger than a native
loader seam. The engine/Rhino Java ABI was not lowered, and no private reflection,
shared-game dependency replacement or side process was introduced.

### Cleanroom 1.12.2: named candidate; prerequisite deferred

Cleanroom **0.6.13-alpha**, **Minecraft 1.12.2**, **Java 25** has a real normal
MultiMC-based launcher route and newer loader-owned Gson/Guava/ASM dependencies.
It is an independently named loader, not stock Forge 1.12.2. Fugue and exactly one
Scalar variant are required separately. Its normal mod contract uses `mcmod.info`,
`@Mod`, `ModType=CRL` and native contained dependencies.

No established normal remote launcher profile/account provider exists in this CI
setup. The public launcher CLI still requires a legitimate account/offline state;
interactive account/demo dialogs are not an unattended full-client route. The
prerequisite and native port are deferred rather than inventing an installer or
authentication bypass. Resume with an owner-approved normal provider/profile,
exact launcher/JVM pins, companion choice and official Scalar artifact hash.

### 1.7.10

The stretch anchor was not started. It remains conditional on a concrete supported
runtime and a favorable native-adapter cost. Existing alternative-runtime facts are
retained as research, not OpenAllay support.

## Verification and retention

Use the checked-in Gradle wrapper for builds and focused tests. Heavy packaging
and game acceptance run remotely. The phase's original reports, traces, native
world identity audits, frame hashes, visual reviews, primary API facts and decision
records are archived under the task evidence directory. User worlds, account
configuration, credentials and other owners' worktrees are preserved.
