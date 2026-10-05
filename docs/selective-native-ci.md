# Selective native CI reverification

Mainline Quality owns the full feature suite on Minecraft 26.2. Native CI checks
15 native API-family representatives. Its client jobs run only the key scenarios
`builder-restricted`, `builder-cancel`, and `ui-stop`. It does not duplicate full
feature acceptance for every minor version.

A pull request keeps the full native route: all 15 builds and six endpoint smokes.
`mc/**` pushes no longer start an automatic native matrix. Use
`workflow_dispatch` to select only failed or affected jobs after a repair.

## Dispatch inputs

- `native_targets`: exact comma-separated target IDs; `all` is the full set and
  `none` skips native builds. Default: `none`.
- `smoke_targets`: exact comma-separated `target/fabric` or `target/neoforge`
  pairs. `default` selects 26.3, 1.21.1, and 1.20.1 on both loaders. `none`
  skips smoke. Default: `none`.
- `artifact_run_id` and `artifact_source_sha`: supply both only when a selected
  smoke target is absent from `native_targets`. They identify one prior native
  run and its exact full source commit SHA. Freshly built targets still download
  their artifacts from the current run.

Native target IDs are: 26.3, 26.1, 1.21.11, 1.21.10, 1.21.8, 1.21.6, 1.21.5,
1.21.4, 1.21.3, 1.21.1, 1.20.6, 1.20.4, 1.20.3, 1.20.2, and 1.20.1.
Unknown targets/loaders, duplicate entries, blank entries, missing artifact
source inputs, and unused source inputs fail before any build/game job starts.
`none` uses guarded inert matrices, not invalid empty GitHub matrices.

The operator chooses the failed/affected scope. Review the repair first. A
shared production API, native source, Builder lock, or shared build dependency
change can affect more than the previously failed jobs. Do not carry old green
results across such changes without rerunning their affected checks.

## Run only the failed native targets

After a repair that affects only these targets:

```sh
gh workflow run minecraft-native.yml --ref <repair-branch> \
  -f native_targets=1.20.2,1.20.3 -f smoke_targets=none
```

A fresh native build may also be smoke-tested on either exact loader:

```sh
gh workflow run minecraft-native.yml --ref <repair-branch> \
  -f native_targets=1.20.2,1.20.3 -f smoke_targets=1.20.2/fabric,1.20.3/neoforge
```

## Run previously skipped smokes without repeating successful builds

Commit workflow/test/doc fixes before production/build-recipe repairs. Then run
six previously skipped endpoint smokes using the unchanged production JARs:

```sh
gh workflow run minecraft-native.yml --ref <workflow-fix-branch> \
  -f native_targets=none -f smoke_targets=default \
  -f artifact_run_id=<prior-native-run-id> \
  -f artifact_source_sha=<exact-prior-run-head-sha>
```

Afterward, put the target-specific build repair in its own coherent commit and
dispatch only its failed/affected native targets. Distinct target/smoke
selections have separate concurrency groups, so the repair dispatch does not
cancel an independent endpoint smoke dispatch. A newer dispatch of the same
selection can still replace its earlier run.

Reuse is fail-closed. The planner compares tracked Git paths between artifact
source and candidate. Production sources/resources, native overlays, build
logic, Gradle inputs, the pinned Builder lock, LICENSE (bundled in every JAR),
package helpers, and unknown inputs block reuse. Docs, workflows, module
`src/test`, Python tests, and the explicitly listed CI runner helpers do not
change the production JAR. A different target's profile does not affect the
selected target tuple. The planner does not interpret arbitrary build.gradle
edits as semantically equivalent. When a build recipe changes, use the staged
route above or rebuild the selected affected smoke targets.

The prior Actions run must belong to this repository and this workflow at the
supplied source head. The exact `Native <target>` job must have succeeded in
that run's current attempt. A failed unrelated native job does not invalidate
a successful source job and does not globally skip the smoke matrix. A missing
or expired exact artifact fails the selected smoke; no alternate JAR is used.

Every smoke downloads `compat-production-<target>-<sourceSHA>` with the original
`SHA256SUMS`. It checks the bytes before launch. Retained `artifact-source.json`
records the original JAR SHA-256 values, successful source job ID/URL/attempt,
actual JAR source SHA, Actions run head, and candidate runner SHA separately.
Client `sourceRevision` is the JAR source, not a newer runner commit. For PR
builds, the tested merge SHA and contributing Actions run head stay separate.
A new runner executing an old unchanged JAR is not proof that new production
code passed. Prior successful jobs remain evidence for their original tested
source, not a relabeled green result at the new candidate.

## Full explicit route and offline selector checks

```sh
gh workflow run minecraft-native.yml --ref <branch> \
  -f native_targets=all -f smoke_targets=default
python3 -B -m unittest discover -s scripts -p 'test_plan_ci_verification.py'
```

These offline tests cover selection, build/smoke dependencies, real Git diff
reuse gates, source-job identity, and unchanged artifact bytes. They do not run
Gradle, Java, or a Minecraft client. Remote CI provides those checks.
