# Packaged Builder real-client acceptance

This is a development harness. It does not use Gradle `runClient` or project source classes. It launches the built default OpenAllay `0.2.3` JAR, including its nested Builder Extension, with Minecraft `26.2` on Java `25`.

## Runtime prerequisites

- A local official Minecraft `26.2` installation with version metadata and the official client JAR.
- For NeoForge, the locally installed `neoforge-26.2.0.25-beta` profile and its production patched client and universal JARs.
- For Fabric, cached Fabric Loader `0.19.3` libraries and a local Fabric API for `26.2`.
- An available desktop graphics session. macOS launches include `-XstartOnFirstThread`.
- The default JAR rebuilt with the opt-in native E2E bootstrap. The launcher records its SHA-256 and refuses to launch an artifact without that hook.

The launcher uses official installed libraries or exact cached Gradle library versions. It never puts project build classes, a Gradle patched game JAR, or a separate Builder JAR on the runtime classpath. Fabric's disposable `mods` folder contains OpenAllay and Fabric API only. NeoForge's contains OpenAllay only.

## Prepare assets without opening a client

```text
python3 -B scripts/run-packaged-builder-acceptance.py --prepare-assets
```

`--fetch-assets` is an alias. This downloads only official Mojang asset index `32` and missing objects to ignored `build/e2e/runtime/assets`. Each object is checked against its official SHA-1 and size. Existing installed objects are copied only after verification. The shared Minecraft installation is not changed.

## Prepare a fresh disposable run

Start the loopback fixture separately. Supply a secret-free model config with `env:VARIABLE_NAME` credential references, or omit `--model-config` to generate the default loopback fixture profile.

```text
python3 -B scripts/run-packaged-builder-acceptance.py fabric   --run-id fabric-disabled-UNIQUE   --scenario builder-disabled   --model-config build/e2e/fixture-models.json
```

Use `neoforge` instead of `fabric` for the other loader. This command only prepares a run. It does not launch Minecraft.

Review the generated `build/e2e/packaged-builder/<loader>/<run-id>/launch.json`. It records the exact command, artifact identity, nested Builder path, classpath, world name, and prepared file hashes. It contains no provider credential values. A pre-bootstrap production JAR is labeled separately from an acceptance-instrumented packaged JAR.

For enabled scenarios, add `--enable-unrestricted` explicitly. This writes `enabled: true` only to that generated disposable game directory. The command capability remains disabled. Supported deterministic scenarios are `builder-disabled`, `builder-acceptance`, `builder-partial`, and `builder-cancel`. The frozen-authority check uses `builder-acceptance --revoke-unrestricted-after-capture`.

A local credential-free HTTP proxy can be enabled with `--http-proxy-from-env`. This reads only conventional HTTP(S) proxy variables. It rejects proxy credentials and non-local hosts. Provider keys are not passed on the Java command line.

`--low-impact` changes only the disposable client to an 854×480 window, FPS 10, and a 256 MiB / 1536 MiB heap. It retains render distance 4 and simulation distance 5 for native fixture coverage. It does not change macOS settings.

`--model-diagnostics` explicitly adds `-Dopenallay.model.diagnostics=true` to the prepared JVM command and records that opt-in in the manifest; it is off by default and retained when a phase resumes.

## Launch after review

```text
python3 -B scripts/run-packaged-builder-acceptance.py   --launch-prepared build/e2e/packaged-builder/fabric/fabric-disabled-UNIQUE
```

The exact reviewed files must still match their hashes. The saves folder must be empty. The native opt-in controller waits for the title screen, then calls `WorldOpenFlows.createFreshLevel`. It creates a unique `openallay-builder-...` world in survival mode, with commands off and a superflat preset. It does not edit a loaded world offline or automate GUI clicks. Existing user saves are not read or changed.

The client runs under supervision and writes its console output to ignored `client.log`. The controller captures report, trace, and screenshots, then closes the client. The launcher returns failure if Java fails, a report is missing, the request is not `COMPLETED`, or the independent `nativeAcceptance.outcome` is not `PASSED`. A successful Java exit alone is not acceptance.

## Reopen only the native-created acceptance world

After a successful `builder-acceptance` run:

```text
python3 -B scripts/run-packaged-builder-acceptance.py   --resume-prepared build/e2e/packaged-builder/fabric/fabric-acceptance-UNIQUE   --run-id fabric-reload-UNIQUE   --scenario builder-reload   --enable-unrestricted
```

This only prepares a second phase. Its output is below the original run's `phases/` directory. It verifies the prior native acceptance report, unchanged packaged mods, and exactly the original disposable save. The native controller uses `WorldOpenFlows.openWorld` through `openallay.e2e.resumeWorld`. No `level.dat` or region edits occur. Launch the phase with its new `--launch-prepared` directory.

If a later development-only E2E hook needs correction, resume can explicitly add `--jar <new-default-production-named-JAR>`. This first verifies the original installed mod hashes and the prior native `PASSED` report. The replacement must preserve the loader, Minecraft version, OpenAllay version, and exact nested Builder bytes. The original JAR is retained under the original run's ignored `evidence/harness-artifacts/<old-sha256>.jar`. Only the new phase records `previousPackagedArtifact`, `newPackagedArtifact`, and `testHarnessUpgrade` with old/new SHA-256 and the unchanged nested Builder SHA-256. The original manifest and report are not rewritten. Without `--jar`, resume retains its original behavior.

## Real-provider copy and undo phases

Use `builder-live-copy` with `--enable-unrestricted`, an explicit `--model-config`, and an ordinary `--question`. The launcher does not replace that question with fixture text. The provider profile must use environment credential references. Provider selection belongs to the operator; this launcher does not probe or select another provider.

After that copy phase reports native `PASSED`, prepare `builder-live-undo` with `--resume-prepared` pointing to that exact copy run. Pass a new ordinary `--question`, the explicit real-provider `--model-config`, and `--enable-unrestricted`. This pairing only reopens the copy run's disposable world and preserves the copy report. Live undo cannot create a fresh world.

## Harness tests

```text
python3 -B -m unittest discover -s scripts -p test_packaged_builder_acceptance.py
```

These tests never launch Minecraft. They cover packaged-only launch construction, opt-in authority, disposable path checks, environment-reference configs, native result gating, asset verification, local proxy validation, and controlled reload preparation.

## Local launch corrections

The synthetic offline username is `BuilderProbe` (12 characters). Minecraft limits login names to 16 characters. The offline UUID is calculated from that same name with Java-compatible `nameUUIDFromBytes` semantics. This avoids a login-packet encoder failure. The generated simulation distance is 5, within the Minecraft26.2 option range.

The launcher is a separate process from Java. It records the Java PID and supervises its deadline. `--timeout-seconds 900` sets a 900-second native E2E deadline and a 960-second supervisor deadline for that test phase. The native default remains 300 seconds; new manifests record a supervisor deadline 60 seconds longer. This is a development-test deadline, not a product execution limit. A SIGTERM or keyboard interrupt sent to the launcher stops that Java process group. This cannot guarantee recovery from a WindowServer or kernel stall; user-space watchdogs may not run during such a stall. Do not start another graphical client or a heavy build while diagnosing desktop stability.

## Recorded real-client results — 2026-09-30

The root operator ran actual packaged Minecraft26.2 clients on Java25. The tested
product artifacts were OpenAllay0.2.3 **acceptance-instrumented** JARs, not source
classes. The following retained reports each record request `COMPLETED` and
independent native acceptance `PASSED`:

| Loader / phase | Native check count | Report below `build/e2e/packaged-builder/` |
| --- | ---: | --- |
| Fabric disabled | 1 | `fabric/20260930-disabled-04/report.json` |
| Fabric full composite | 85 | `fabric/20260930-full-01/report.json` |
| Fabric native restart | 85 | `fabric/20260930-full-01/phases/20260930-reload-01/report.json` |
| NeoForge disabled | 1 | `neoforge/20260930-disabled-04/report.json` |
| NeoForge full composite | 85 | `neoforge/20260930-full-01/report.json` |
| NeoForge native restart | 85 | `neoforge/20260930-full-01/phases/20260930-reload-02/report.json` |
| Fabric / Luna real-provider copy | 58 | `fabric/20260930-luna-copy-02/report.json` |
| Fabric / Luna same-world linked undo | 80 | `fabric/20260930-luna-copy-02/phases/20260930-luna-undo-01/report.json` |

These are fixed native check counts, not a claim that every possible building
operation or world block was tested. The full deterministic cases exercise six
presets, geometry, decoration, terrain, directional template transforms,
partial application, cancellation, undo, and frozen request authority. Both full
reports record `frozenAuthorityTimingPassed: true`. Both successful restart
reports record `exactPersistencePassed: true` for the saved operation and template
state. Native world metadata confirms survival, commands off, and flat terrain.

The NeoForge restart retained the original native anchor even though the player
position changed during ordinary game runtime. A development-only harness
upgrade was explicitly recorded in the second phase. Its original full report
and manifest remain unchanged; no saved origin or region data was edited offline.

### Tested artifact SHA-256

| Artifact / phases | SHA-256 |
| --- | --- |
| Fabric disabled, full, restart | `07412c8125df800aa987f3644826a3d30f98eb6d5a333f15988c50d7fbc361ca` |
| NeoForge disabled and full | `efa5c39124004ef0758f233b59716e431fc4135e61a33fad9ad3d9cb53f9be54` |
| NeoForge restart after explicit test-harness upgrade | `af47a107bbab6875e18913baae898528c601c915a3b94e4ac251c64ec4fc34bd` |
| Fabric / Luna copy and linked undo | `f569013a11077593dfdef947624c92a33478bfb5829e662ff47d84b9a33e7a17` |

NeoForge's upgrade marker preserves nested Builder SHA-256
`6a0b0e866b202d87ea7cf5852be8da665875e5e1caff017006bd7e88f87f988e`.
The real Luna Fabric phases record nested Builder SHA-256
`46c1ad2d4e65ddc1ab263b40a99381cbd34b8f19e4c791c04a62607bab659aeb`.

The only real provider used was `gpt-kanglives` / `gpt-6-luna`, with an explicitly
configured 1,000,000-token context. Its ordinary copy task and linked undo were
separate retained requests. The strict copy validator also passed; its receipt is
`build/e2e/builder-acceptance-2026-09-30/luna-copy-proof.json`. It corroborates 75
journal entries, 29 changed entries, a 90-degree transformed template, and exact
operation ID linkage from the structured production Tool result. Assistant
narration is not used as evidence.

The native undo report passes 80 independent readbacks. The separate strict
external undo validator also passed after its JSON count grammar was corrected
to accept integral numeric values such as `75.0`; 10 validator tests passed.
It links exact undo operation `76d5a754-584a-4c8f-afb9-91ed113bff3d` to copy
operation `ca746c4c-9127-4ce8-809b-329609b699c3` and verifies all 75 inverse
journal positions and postimages in the same native world. Conflicts and
uncertain result lists are empty. The root operator is retaining the CLI proof
separately. Earlier failed attempts remain retained and are not counted as
passing evidence.

This checkpoint does not claim a final combined release gate, GUI acceptance of
new intent/catalog features, server-hosted model isolation, or runtime acceptance
of a later OpenAllay version. Those are separate checks.
