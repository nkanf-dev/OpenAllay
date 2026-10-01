# Execution/context simplification verification

Date: 2026-10-01. Status: deterministic and packaging gates passed. Source delivery
only; no release tag or product version change. Existing v0.2.4 artifacts remain
unchanged. Graphical/live-provider acceptance was not rerun for this refactor.

## Reproduced defects

The retained manual traces and two player exports showed actual tool errors in
immediate model continuations, but only status/presentation/source summaries on
the next question. All thirteen failures with a later same-session trace lost
their code/message there. Skill plaintext was lost through the same projection.
One context contained 1,738 source records with only five distinct projected
identities; source repetition occupied about 73% of its largest model request.
No compaction event occurred in those traces; the incompatible plaintext/JSON
reducer was a separate source-confirmed defect.

Decision041 removes manual roots declarations, mandatory origins for generic
computation, display-to-model reconstruction, knowledge-field reduction and
per-read duplicate source payloads. Exact actual tool exchanges and Skill text
are retained independently of compacted context. Origins are auxiliary; captured
world facts retain truthful authority/coverage. Internal pre-1.0 formats are
Latest Only. External independently released Extension/API/game facts remain.

## Final local verification

```bash
./gradlew clean :common:test :fabric:build :neoforge:build
bash scripts/verify-distribution.sh
bash scripts/verify-phase4-package.sh
bash scripts/verify-sqlite-packaging.sh
python3 -m unittest discover -s scripts -p 'test_*.py'
```

- **1,123 common tests**, zero failures/errors, six opt-in skips.
- Fabric and NeoForge production artifacts built with the exact pinned Builder.
- Distribution and Phase4 checks passed, including nested Extension provenance.
- SQLite packaging proof loaded the actual nested JDBC driver from each artifact;
  Linux/macOS/Windows x86-64 and ARM64 native entries were present.
- **117 offline script tests** passed; mocked launcher output is not graphical
  acceptance. All nine shell scripts passed `bash -n`.
- Builder **155 common tests** passed with the new core JAR. Both Extension loader
  builds and `verifyLoaderPackages` passed.

| Final artifact | SHA-256 |
| --- | --- |
| Fabric 26.2 / 0.2.4 source build | `8570dbeb8af1d958749a7eafc1f100b56e1e5a988040b246b1ec3dfd6e7b6607` |
| NeoForge 26.2 / 0.2.4 source build | `aa402ca2709fd5d7808ddee54fc636b1671cb52fc8494491bd31bbf6b49e1a50` |

These files are local source builds, not replacement release assets. No tag moved.
Existing deprecation/Javadoc warnings and NeoForge's two missing Fabric annotation
type warnings remain; they did not fail compilation.

## Failure-path coverage and review

The first full integration run had 1,092 tests with ten failures. Corrections did
not hide those failures: they removed stale current-format fixtures, fixed a real
Cookie-prefix redaction leak and repaired cancellation behavior. A later broader
616-test gate and the final clean suite passed.

Cancellation coverage includes immediate visible Stop, off-owner cleanup,
noncooperative model/summary futures, full predeclared tool-pair settlement,
mid-launch cancellation, exact original-result retention, logical immediate retry,
old-owner identity fencing, cancelled-request final archive after viewport eviction,
completed-before-visible-Stop, both-loader callback lifetime, delayed old-request
cancel packets and cancellation before server capture. One explicit final context
handoff may archive a cancelled request, but never overwrite a newer submitted
request. Ordinary late events remain suppressed; deletion/disconnect reject the
handoff. Process interruption adds a plain diagnostic and never replays work.

Independent read-only reviews identified and closed the Builder checkpoint
publication poisoning, remaining per-voxel terrain preparation and derived-edit
planning/preflight races. Context review identified and closed the viewport,
loader-callback and server request-correlation gaps. Tests cover exact Skill text
reuse, changed-document invalidation, real errors after restore/compaction, original
export chronology and point-in-time boundaries, source grouping, and latest-only
format validation without migrations.

## Builder source and remote verification

- `81e64f6`: batch scans/terrain preparation/writes/undo and incremental journals.
- `fb6cb21`: full CI failure diagnostics.
- `85886eb`: align one complete native-workflow fixture with Builder's actual
  unrestricted execution mode.
- **Pinned final source:** `174d2f515872ca60d356b084095cfd026e275944`, also removing
  the private source-lock schema marker without changing external core/API pins.

Initial Builder native CI failed the full 49x49 fixture. The diagnostic rerun
confirmed its restricted 10-second interpreter budget was exceeded. The test
created 1,227,058 synthetic observations; timing was not its acceptance condition.
The correction uses Builder's required unrestricted mode for that one case, keeps
all dimensions and exact state/dispatch assertions, and leaves other safe fixtures
unchanged. It does not increase a production timeout or reduce the workload.

Both workflows passed for the final pinned source:

- [quality 36816829212](https://github.com/nkanf-dev/OpenAllay-Extensions/actions/runs/36816829212)
- [Builder native quality 36816829099](https://github.com/nkanf-dev/OpenAllay-Extensions/actions/runs/36816829099)

The earlier failed CI runs remain historical failures. Core CI is checked after
source publication; local build success alone is not a CI claim.

## Not run and remaining limits

- No fresh graphical client, manual gameplay or live-provider acceptance run.
- No in-game `/fill` latency comparison. Facade-call reductions and journal byte
  growth tests are not measured Minecraft owner-thread timings.
- No compatibility promise or migration for earlier internal test formats.
- No world save, existing exported conversation, model secret or ignored runtime
  configuration was rewritten by this work. The manual world and both original
  exports were checked intact after the final clean build.

## Core CI follow-up

Core Quality run 36817829332 compiled both loaders and passed its Gradle build,
but its later Phase4 script failed. The Linux runner lacked `rg`, and the source
scanner found literal synthetic Bearer values in a newly tracked redaction test.
The pre-commit local scan had not included that then-untracked test file.

The correction uses standard `grep`, includes untracked source in the local
credential scan, and splits synthetic test literals without changing their runtime
values or assertions. The focused model-context codec tests, shell syntax and
Phase4 package check passed. No production behavior or artifact bytes changed.
The next remote run is checked separately rather than treating this failed run as
successful.
