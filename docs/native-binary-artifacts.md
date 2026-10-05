# Native binary release artifacts

Publish one JAR per loader and verified native-binary Minecraft interval, not
one JAR per minor target. Exact external pins remain in
`gradle/minecraft-targets/<target>.properties`. Native source families select
source bindings; they do not declare binary compatibility.

## Current boundary

`gradle/minecraft-artifacts.json` retains only accepted singleton `26.2` families
for Fabric and NeoForge. Development still defaults to `26.2`. Existing names
remain `openallay-fabric-26.2-{version}.jar` and
`openallay-neoforge-26.2-{version}.jar`. This source packet does not rebuild,
replace, relabel, or republish immutable v0.4.1 artifacts. It does not change the
Builder 0.3 manifest, whose game declaration remains 26.2 only.

Candidate intervals are explicitly nonpublishing. The listed sets are research
inputs from source reuse, not promised final splits. Older targets remain source
candidates pending compilation and game acceptance; this phase ends at 1.20.1.
26.3 is also not admitted. The selector refuses every target without a unique
accepted family. It has no admission command.

Prefer the oldest common retained public native API. Split only at a demonstrated
native/mapping/Mixin/loader/Java/fixed-dependency break. Do not widen support from
source families or signatures alone. Share the feature engine and universal
Builder. Ship one selected native adapter, not all target implementations or a
reflective runtime dispatcher. Exact loader and dependency pins stay in target
profiles; this catalog does not invent third-party compatibility ranges.

## Small CLI

Run from the repository root with Python 3.9 or newer, using only its standard
library. The staged packet root can run the same commands without a repo copy.

```text
python3 -B scripts/minecraft-artifacts.py validate
python3 -B scripts/minecraft-artifacts.py resolve --loader fabric --version 0.4.1
python3 -B scripts/minecraft-artifacts.py resolve --loader neoforge --target 26.2
python3 -B -m unittest discover -s scripts -p 'test_minecraft_artifacts.py' -v
```

Resolution prints family `id`, `loader`, `buildTarget`, `supportedTargets`, safe
`filenameTemplate`, and optionally `filename`. It also prints a closed Minecraft
Maven range and equivalent Fabric predicate from the exact accepted endpoints.
For the current singleton these are `[26.2]` and `26.2`. These are future-build
metadata inputs, not claims about the bytes or metadata of existing v0.4.1 JARs.

`targetOrder` is the explicit chronological list of stable external Minecraft
releases in scope. Numeric components order `1.21.9` before `1.21.10`. `1.21` and
`1.21.0` cannot become separate aliases. Every known intermediate target must be
included in an interval. Keep that external release list complete when changing
families. Validation rejects unknown/missing fields, duplicate JSON keys, unsafe
names, duplicate/overlapping accepted families, target gaps, and publishing
candidates. The same exact-shape validation applies to receipts. No internal
schema version, migration, or new framework is introduced.

## Bounded receipt consistency, not acceptance

Build a candidate artifact once. Keep the original immutable path and SHA256.
Verify those SAME bytes on every included target, including intermediates. A
rebuild at the same name, identical bytes at another path, wrong loader, absent
runtime run, or altered evidence must not silently qualify.

A private, untracked receipt has exactly these fields:

- `familyId`, `loader`: the selected accepted or candidate family identity.
- `artifactPath`: an absolute canonical path to that one original JAR.
- `artifactSha256`: its lowercase SHA256.
- `runs`: one entry for each exact target, with no duplicates or extras.
- Each run has exactly `target`, `loader`, `artifactPath`, `artifactSha256`,
  `kind`, `outcome`, `evidencePath`, and `evidenceSha256`.
- The loader/path/SHA fields must match the original JAR. `kind` must be
  `runtime`, and `outcome` must be `passed`. Compilation or preparation alone
  is refused. `evidencePath` is a unique safe relative file under the receipt's
  directory, without symlinks; `evidenceSha256` hashes the retained evidence.

Run `verify-receipt --family FAMILY_ID --loader LOADER --receipt PRIVATE_JSON
--artifact ABSOLUTE_JAR --sha256 ACTUAL_SHA256` with the actual values. The
command reads files only. Limits are 1 MiB per JSON file, 512 MiB per JAR, 16 MiB
per evidence file, and at most 128 known targets/families. It rehashes the actual
artifact before and after checking the per-target receipts. Absolute machine
paths never belong in the checked-in catalog or this source packet.

The output says `receiptConsistency: passed` and
`runtimeAcceptance: requires-external-review`. A declared `runtime` field and
hashed arbitrary text are NOT proof of a game launch, linkage, mappings,
Mixins, or correct behavior. This generic reader does not infer proof type from
a placeholder, file name, or successful compilation. A trusted native acceptance
runner and reviewer must establish those facts from real retained run evidence.
The offline unit tests intentionally use synthetic bytes and demonstrate only
shape, selection, and integrity refusal. They do not establish runtime support.

Receipt inspection works for candidates without publishing them. Moving a
reviewed interval into `acceptedFamilies` is an explicit source change after
real acceptance; remove its matching candidate entry. Newly listed families
also require the full receipt/artifact/SHA trio during `resolve`. Catalog edits
or receipt inspection alone cannot silently admit ranges. The existing 26.2
singleton selection retains its current baseline, not newly fabricated receipts.

## Root wiring route (not applied here)

1. Add these four files only. Run the offline tests. Keep current release gates.
2. Have release callers resolve one target+loader into one family. Build only
   `buildTarget` once with the current `-PminecraftTarget=...` interface. Do not
   add a per-minor build/publication matrix. Development pin selection stays exact.
3. For future builds, feed the family filename and closed Minecraft range/Fabric
   predicate into existing archive/resource expansion. Preserve current 26.2
   names and do not modify delivered v0.4.1 bytes. Target profiles still own
   exact external pins; existing source-family ownership guards stay in force.
4. Verify the one original artifact on ALL exact `supportedTargets` with actual
   native game runs and retained evidence. Keep existing loader/metadata,
   package, Builder, linkage and behavior acceptance checks; do not use this
   integrity reader as a replacement. Loader ranges need their own evidence.
5. Before any publish/network call, require reviewed runtime acceptance,
   `verify-receipt`, family range metadata checks, and existing distribution
   gates against those same bytes. Publish each loader/family once and pass the
   exact target list to external release metadata. No range-admission bypass.

Interface choice: closed range derived from accepted exact endpoints (chosen)
is one range authority. Duplicating legacy metadata ranges in the catalog
(rejected) would add a second authority and risk treating legacy syntax as new
binary proof. Existing Gradle, workflows and release scripts are unchanged.
