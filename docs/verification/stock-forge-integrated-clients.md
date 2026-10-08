# Stock Forge integrated-client validation

## Original task-branch validation records

| Minecraft | Stock Forge | Runtime | Record |
| --- | --- | --- | --- |
| 1.16.5 | 36.2.42 | Java 17 | [Native validation](../forge-1.16.5-native-validation.json) |
| 1.12.2 | 14.23.5.2864 | Java 17 | [Native validation](../forge-1.12.2-native-validation.json) |

These original records used the canonical Java 17 feature engine and Rhino,
the Java 8 public Extension SDK, and one universal Java 8 Builder. Native
adapters own game and loader APIs. The task branches preserve the integrations
and exact artifact providers. The Forge 1.12.2 Java17 delivery is archived;
the active [Java8 port](forge1122-java8-port.md) is a nonpublishing candidate.
Forge 1.16.5 remains in the current release catalog.

## Historical Forge 1.12.2 Java17 validation

The source described below is preserved in
[`legacy/forge1122-java17-20261007`](https://github.com/nkanf-dev/OpenAllay/tree/66f010368bbba90acd223fcd3b1eb325604fcbe9).
The dedicated component and instrumentation delivery paths are retired from
active source. Their original runtime receipts below retain their identities.

The original accepted topology was the stock client with an integrated server. Genuine
ForgeGradle 3.0.197 and its Java 8 tooling island perform native compilation,
annotation processing and reobfuscation. Product feature classes use Java 17.
The runtime prerequisite uses narrowly guarded product-owned instrumentation
for stock LaunchWrapper, Pack200, ObjectHolder, capability and DimensionType
operations. The stock game class loader and host ASM remain owned by Forge.
The component product consists of the feature core, Java 8 lifecycle facade,
and private relocated Mixin/ASM support. Exact provider hashes are in the record.

Accepted operations:

- Native settings, error recovery, recipes, HUD scrolling and sliders, actual
  layout drag, Apply and reopen: 41 checkpoints.
- World SDK identity, native DataVersion 1343, block write/readback, full chest
  NBT, rotation, strict IDs and bounds, restoration and revoked-session rejection.
- Two-process world save/reload with the same UUID and full chest NBT.
- Builder Skill/module discovery and normal restricted JavaScript execution.
- Partial failure, cancellation and undo, including preserved undo conflicts.
- Bundled-only Builder discovery, hollow box, checkerboard, path, template
  save/load/list, persisted template bytes, rotation, mirror and air: 17 exact
  server-owner-thread block-state checks.
- Genuine native repair regression cases for distinct property collection views,
  foreign same-name property identities, derived dirt/fence/stair properties,
  persisted stair facing, native metadata roundtrip and changed-block rejection.

All accepted functional clients exit normally with code 0 and no stop signal.
The final bundled scenario is
[37549916767](https://github.com/nkanf-dev/OpenAllay/actions/runs/37549916767).
Its Builder raw JAR is unchanged from the tested candidate. Normal bundled
resource discovery caches those exact bytes; no community Builder is installed.
Missing preset material roles produce `SKIPPED`. The native palette and exact
missing-role inventory are recorded for each preset.

The mirror repair compares exact native property identities. Minecraft wraps
`getPropertyKeys()` with `Collections.unmodifiableCollection`, so comparing
collection objects was incorrect. Block identity, allowed values and native
metadata normalization remain enforced.

## Evidence and delivery

The records bind each original run, source revision and artifact hash. Earlier
scenarios retain their original products. Engine and native entry refreshes
preserve all other physical entries. Initial assembly and compilation-classpath
hashes are recorded separately from the final loaded engine and native JARs.
Original failed runs remain failed in the evidence history.

Main/release integration, optional viewer combinations, dedicated-server
acceptance, screenshot visual review and physical microphone/IME checks are
separate work items. User worlds, credentials, configurations and history remain
preserved.
