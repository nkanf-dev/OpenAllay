# Advisory Skill and Extension Requirements Implementation Plan

> **For agentic workers:** Use the installed `executing-plans` skill to execute and review this plan. Work is already delegated in parallel. The checked-in Decision035 is authoritative; this plan is not another approval gate.

**Goal:** Show advisory capability, Extension, and Skill requirements in package details and before installation. Let the player enable supported requirements explicitly, cancel, or Continue anyway without granting implicit authority.

**Architecture:** `RequirementSet` and its strict codecs describe needs. A pure evaluator reports immutable local facts without filtering packages or changing runtime authority. The settings service owns reviewed, unpublished package candidates and calls existing setting owners only after explicit consent.

**Tech Stack:** Java common module, JUnit 5, Gson, current Agent Skills string-map parser, native Minecraft settings screens, Gradle wrapper, English and Simplified Chinese resources.

---

## 1. Authority, status, and limits

Accepted design: `docs/isme/decisions/2026-09-30-035-advisory-extension-skill-requirements.md`.

The user approved advisory requirements, **not a generic hard dependency gate**. A valid Skill can be installed, read, and explicitly used despite unmet new requirements. Its attempt to use an unavailable runtime action still fails. Continue anyway does not enable requirements, grant permissions, mutate deny policy, install dependent packages, or invoke a fallback.

Preserve all of the following:

- Actual registered Tool availability and deny policy.
- Explicit Skill deny policy.
- Existing legacy `allowed-tools` and `openallay/required-mods` contracts.
- Loader/game/API compatibility, package integrity, actual contribution validation, and atomic publication.
- Frozen per-request authority and server-model/callback isolation.
- Existing builtin unrestricted guidance semantics under Decision033. Arbitrary advisory metadata must not expose that builtin as enabled or claim Java access.
- Normal existing enable buttons, with explicit visible danger consent when unrestricted JavaScript is enabled through requirement review.

Do not add automatic recursive downloads, a dependency solver, implicit transitive enablement, arbitrary count/depth limits, Extension hot loading, a fabricated generic Extension toggle, per-package special cases, or repeated generic risk/context boilerplate in prompts.

This plan was prepared from a dirty working tree while implementations were starting. Tests listed below are required verification steps, not claims that they have passed. Preserve unrelated edits. The initial design report was saved at `/tmp/openallay-requirements-design.md`; this reconciled plan replaces its superseded naming/schema proposals.

## 2. Parallel ownership and shared API

### Worker A: `implement-requirements-metadata`

Owns the generic declaration model, codecs, evaluator, descriptor/catalog changes, related schema validation, and their tests. Does not edit the native screen, settings service, installers, or UI-owned settings adapters.

### Worker B: `implement-requirements-ui`

Owns package prepare/commit lifecycle, Skill/Extension settings backends and views, settings orchestration, environment adapter, review projection/screen, localization, and corresponding tests. It may delegate installer and screen work internally with explicit file ownership.

### Shared contract (verified against in-progress source)

Package: `dev.openallay.requirement`.

```java
public record RequirementSet(
        Set<String> capabilities,
        Set<String> extensions,
        Set<String> skills) {
    public static final RequirementSet EMPTY;
    public boolean isEmpty();
}

public final class RequirementCodec {
    public static final String CAPABILITIES_KEY = "openallay/requires-capabilities";
    public static final String EXTENSIONS_KEY = "openallay/requires-extensions";
    public static final String SKILLS_KEY = "openallay/requires-skills";
    public static RequirementSet fromMetadata(Map<String, String> metadata);
    public static RequirementSet decode(JsonElement value);
    public static JsonObject encode(RequirementSet requirements);
}

public enum RequirementKind { CAPABILITY, EXTENSION, SKILL }
public enum RequirementStatus {
    SATISFIED, DISABLED, MISSING, UNAVAILABLE, UNKNOWN, RESTART_REQUIRED
}

public record RequirementAvailability(
        String name, RequirementStatus status, String detail) {}
public record RequirementEnvironment(
        Map<String, RequirementAvailability> capabilities,
        Map<String, RequirementAvailability> extensions,
        Map<String, RequirementAvailability> skills) {}
public record RequirementAssessment(
        RequirementKind kind, String id, String name,
        RequirementStatus status, String detail) {}
public record RequirementReport(List<RequirementAssessment> entries) {
    public boolean allSatisfied();
    public List<RequirementAssessment> unmet();
}
public final class RequirementEvaluator {
    public static RequirementReport evaluate(
            RequirementSet requirements, RequirementEnvironment environment);
}
```

`allSatisfied()` is a presentation helper, **never an install/enable/catalog admission condition**.

Metadata accessors:

```java
SkillMetadata.requirements()                    // derived from attributes()
OpenAllayExtensionDescriptor.requirements()      // added record component
ExtensionCatalogEntry.requirements()            // added record component
```

Keep existing Java descriptor/catalog constructor arities available, forwarding `RequirementSet.EMPTY`. `ExtensionCatalogEntry.descriptor()` and `descriptorFor(loader)` must carry requirements.

UI adapter package: `dev.openallay.settings.requirement`.

- `RequirementSettingsEnvironment.from(ClientSettingsSnapshot)` builds an immutable environment.
- Its overload accepts capability, Skill, Extension, command, and unrestricted settings views.
- `RequirementSettingsEnvironment.changes(report, capabilities)` returns only real supported setting changes.
- `RequirementChange(RequirementKind kind, String id, boolean unrestrictedConsentRequired)` describes one exact change, not a recursive dependency closure.
- `RequirementReview` holds the identity token, subject kind/ID/name/version/digest, report, and available exact changes.

The UI worker confirmed these service methods:

```java
// Existing player actions now prepare a candidate; they do not publish immediately.
installCommunitySkill(id)
installCommunityExtension(id)
importLocalSkillPackage(path)
importLocalExtensionPackage(path)

// Backend preparation contracts.
prepareCommunity(id, cancellation)
prepareLocalPackage(path)

// Explicit review operations.
continuePackageInstall(RequirementReview.Token token)
cancelPackageInstall(RequirementReview.Token token)
cancelPackagePreparation()
enablePackageRequirement(RequirementReview.Token token,
        RequirementKind kind, String id, boolean unrestrictedConfirmed)
```

`ClientSettingsSnapshot.requirementReview()` is optional. The same candidate token can remain while its report is re-evaluated, but replacing/cancelling the candidate invalidates it. Any enable request must also validate its exact current advertised change and consent; token identity alone is not a capability grant. Do not duplicate these entrypoints or invent a second controller solely to match an earlier draft.

## 3. Existing owners and integration traps

| Owner | Current implementation | Required boundary |
|---|---|---|
| `skill/SkillParser.java` and `SkillMetadata.java` | Extra metadata is already a string map; unsupported top-level frontmatter and non-string metadata are rejected. | Use namespaced extra strings. Do not add nested YAML requirement objects or top-level arrays. |
| `SkillRepository`, `SkillPackageInstaller`, `ClientCapabilityResolver` | Existing required-mods/allowed-tools validation and explicit policy filtering. | New advisory requirement sets must not feed those legacy gates. |
| `SkillCatalogSnapshot`, `LoadSkillTool`, request wiring | Dirty builtin guidance work captures authorization separately. | Preserve builtin033 behavior; do not generalize its special visibility behavior into a new requirements filter. |
| `extension/OpenAllayExtensionDescriptor.java` | Nine original components; called by external SPI implementations and tests. | Retain the old constructor signature; additional requirements default empty. |
| `ExtensionPackageManifestCodec` | Strict package schema 1, originally exact field equality. | Add one explicitly optional `requirements` field. Other unknown fields still fail. |
| `ExtensionCatalogCodec` | Strict catalog schema 2; logical entry has independently verified artifacts per loader. | Add optional entry requirements; keep artifact shape and selection unchanged. |
| `ExtensionPackageInstaller.stage(...)` | Compares **whole descriptor equality** against catalog descriptor. | Stop comparing advisory fields as package identity. Compare all original identity/compatibility fields explicitly; retain checksum/mod/loader validation. |
| Both installers | Validation and publication are currently one operation. | Split prepare from commit so the review never discovers requirements by installing first. |
| `ClientSettingsService` | Existing Skill/Extension install/import methods immediately trigger publication. | Route all player-facing variants through preparation/review. Keep async reservation/dispatch ownership. |
| `OpenAllaySettingsScreen` | Direct install actions and unrestricted toggle; warning currently uses a tooltip. | Add review routing and explicit full-warning unrestricted confirmation. Avoid making the large screen own package I/O. |
| Extension state | Active registrations, community rows, and staged restart-required JARs. No generic enable toggle. | Missing/failed/staged Extensions are not enableable capability settings. |
| `SettingsLocalizationTest` | Enforces identical English/Chinese key sets. | Test complete matching requirement keys and matching format placeholders. |

## 4. Final metadata format

### 4.1 Skills

Use existing `metadata` scalar strings:

```yaml
metadata:
  openallay/requires-capabilities: "openallay:unrestricted_javascript"
  openallay/requires-extensions: "example:catalog example:analysis"
  openallay/requires-skills: "inspect-materials summarize-results"
```

- Split on whitespace only. Do not document commas as supported syntax.
- Empty/absent keys mean no advisory requirement of that kind.
- IDs are exact and unique; validate recognized key syntax. The shared codec rejects malformed/duplicate declarations rather than inventing an action.
- Preserve unrelated metadata and its existing string-only constraint.
- Unknown syntactically valid capability IDs remain in the declaration and display as unknown/unavailable; they do not register or authorize anything.
- This syntax does not redefine legacy fields. A declaration that also uses a legacy hard field remains subject to that pre-existing field's validation.

`SkillMetadata.requirements()` derives from `attributes()`; there is no second durable requirements file and no implicit permission state.

### 4.2 Extensions

Add the optional typed field to the Extension descriptor, embedded package manifest, and logical catalog entry:

```json
"requirements": {
  "capabilities": ["openallay:unrestricted_javascript"],
  "extensions": ["example:catalog"],
  "skills": ["inspect-materials"]
}
```

The nested members are optional arrays of unique, nonblank exact IDs. Missing object or missing members means empty. Unknown object members, explicit null, wrong value types, duplicates, invalid IDs, and non-string array elements are format errors. This is strict declaration validation, not an availability check.

Retain package schema **1** and Extension catalog schema **2**. The optional expansion is explicitly accepted by Decision035. New readers must accept old valid documents without the field. New writers omit an entirely empty requirements object, preserving representation for old metadata. Inside a nonempty object, empty member arrays may also be omitted by the canonical codec.

Top-level strict validation becomes “all old required fields present; only old fields plus `requirements` allowed.” Do not ignore unknown fields or accept historical/future schema versions.

**Compatibility qualification:** older strict readers reject a new nonempty requirements field. The new reader's backward compatibility is not a claim that old software reads new declarations. Coordinate public catalog validator/writer rollout; do not implicitly publish external catalogs from this task. A schema bump alone would not make an old reader compatible.

### 4.3 Package authority versus catalog preview

A catalog is useful preview information. The checked package's embedded manifest or `SKILL.md` is authoritative for the final advisory review.

- Do not reject merely because catalog and package requirement sets differ.
- Show a nonfatal “Package requirements differ from catalog preview” notice where available.
- Still reject original identity, version, source, compatibility, selected-loader, checksum, mod-ID, and actual loader metadata mismatches exactly as before.
- Skill community catalog schema stays unchanged in this implementation. Before download, show that requirements will be read from the package. Do not label an uninspected Skill package as declaring none.

## 5. Read-only status evaluation

`RequirementEvaluator` only joins declared direct IDs with supplied immutable facts. It does not import settings writers, package installers, transport, Rhino, or Minecraft APIs.

Statuses:

- **SATISFIED:** present and enabled in the evaluated local configuration; not a promise that later world/server permissions allow every operation.
- **DISABLED:** present and controlled by an existing disabled setting/policy.
- **MISSING:** required Extension/Skill is not installed or its known provider is absent.
- **UNAVAILABLE:** present but incompatible, failed, or unavailable in the evaluated scope.
- **UNKNOWN:** capability ID has no trusted state provider.
- **RESTART_REQUIRED:** Extension package is staged but not active.

The user-facing absence of unrestricted authorization can be reported as a disabled capability plus its explicit authorization/warning detail. Do not add a conflicting `NOT_AUTHORIZED` enum solely for this document. Explain that settings status is local; actual request authority is checked again by the existing runtime.

Evaluate direct requirements only. A→B→A does not recurse. Opening B shows B's own direct report. Do not add transitive repair, arbitrary depth limits, or automatic dependency installation.

The settings adapter uses trusted state:

- Tool facts from existing capability registry/policy, not arbitrary package labels.
- Stable setting IDs `openallay:unrestricted_javascript` and `openallay:experimental_commands` from their owning settings.
- Skill presence/deny state from current views/policy; preserve actual builtin033/command availability where already defined.
- Extension state from registration/community/staging, with staging never considered active.

The evaluator does not change `SkillCatalog`, `load_skill`, `installable()`, Extension registration, package `INCOMPATIBLE` state, or request tool maps. Requirement reporting is a separate presentation surface.

## 6. Checked installation preview

Shared in-progress handle contract:

```java
package dev.openallay.settings.requirement;

public interface PreparedPackageInstall extends AutoCloseable {
    RequirementKind kind();
    String id();
    String name();
    String version();
    String sha256();
    RequirementSet requirements();
    boolean catalogRequirementsDiffer();
    ToolResult<Boolean> commit();
    @Override void close();
}
```

Concrete handles:

- `skill/install/PreparedSkillInstall.java`
- `extension/install/PreparedExtensionInstall.java`
- `settings/requirement/RefreshingPreparedPackageInstall.java` wraps commit and refreshes backend state only after successful publication.

A prepared handle captures validated private bytes/files and metadata. Construction/inspection does not publish. Commit or close consumes it. Do not re-read an editable import source path at confirmation time. Digest, subject identity, and review generation identify the exact candidate. Replacing the candidate requires a fresh review.

Lifecycle:

```text
preparing -> ready
ready -> explicitly applying requirement changes -> re-evaluating -> ready
ready -> publishing -> completed/failed
ready -> cancelled
```

At `ready`, expose:

1. **Enable available requirements:** exact supported changes only; explicit dangerous consent when needed; remain in review and re-evaluate after successful settings save.
2. **Cancel:** discard the prepared candidate; do not replace the installed package.
3. **Continue anyway:** publish the already validated selected package unchanged; never enable its requirements. It remains available when every advisory row is unmet.

Release the active settings operation reservation while waiting for the player. Keep a reviewed candidate/token, not an indefinitely busy worker operation. Reject stale or consumed tokens. Cancel, close, screen replacement, and service shutdown must clean unpublished candidates. Late async preparation after cancellation must not publish or reopen an invalid review.

Retain existing installer checks:

- Skills: normalized private staging, symlink/path protection, one supported root `SKILL.md`, executable-file rejection, parser/legacy validation, atomic replacement and prior-package rollback.
- Extensions: select current loader before transport; validate checksum, original descriptor fields, mod IDs and loader metadata; atomically stage the stable managed JAR; report restart-required and never claim hot activation.

Programmatic convenience install APIs may compose prepare+commit without changing settings. All player-facing community/local/import/update paths must show the review before publication.

## 7. Explicit enablement and dangerous consent

`RequirementChange` describes only a real existing owner action. No author-defined callback, URL, class name, config path, or script is interpreted as an enable action.

- Disabled existing tool/capability: use the current owning validator/persistence API. A legacy dependency conflict remains a real failure; do not repair it by silently changing other toggles.
- Policy-disabled Skill: offer only if the real existing policy owner supports restoring that ID. Do not reintroduce generic top-level Skill toggles.
- Missing/unknown requirement: no fake Enable button. It can offer ordinary navigation to the existing installation/settings page, but no recursive download.
- Extension: no generic on/off API currently exists. An unavailable/staged/missing Extension must explain its state rather than pretend to activate.
- Unrestricted JavaScript: show the full JVM warning and require explicit confirmation. Continue anyway, cancel, catalog refresh, package parsing, and reading must never call `saveUnrestrictedJavascript(true)`.
- Existing normal capability enable buttons keep their normal behavior; the new review should not spread dangerous-consent boilerplate to harmless actions.

The unrestricted warning must cover arbitrary JVM code, file/network/process side effects, no rollback, and JVM-accessible credentials. Package metadata cannot replace the warning. A tooltip is not sufficient consent.

A setting save affects future local requests. It does not upgrade a running captured request or authorize server-model/callback Java access. Server settings remain read-only/isolated under existing decisions.

If an explicit save fails, keep the existing prior-state guarantees, show the unresolved row and actual failure, and leave Continue anyway available. If a player successfully saves a setting and later cancels installation, do not silently undo that saved setting; explain that only the package operation was cancelled.

## 8. UI and localization

Requirements belong in installed Skill and Extension details and in install preview. Keep instruction reading and local override editing available. Requirements must not be hidden behind debug mode.

Show kind, friendly name if known, exact ID, status, and actionable details. Unknown IDs remain visible. A staged package's requirements are shown alongside restart-required state. For a community Skill without package inspection, say requirements will be read before installation, not “No requirements.”

Use a shared projection and a native review screen or equivalent dedicated review UI. Keep package operations in service/backends; keep `OpenAllaySettingsScreen` limited to navigation/rendering/action dispatch. Support narrow and wide layouts and scrolling instead of arbitrary row caps.

Files:

- `common/src/main/java/dev/openallay/client/gui/settings/RequirementSettingsProjection.java`
- `common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java`
- review-screen implementation owned by UI worker
- `common/src/main/resources/assets/openallay/lang/en_us.json`
- `common/src/main/resources/assets/openallay/lang/zh_cn.json`

Recommended common prefix: `screen.openallay.settings.requirements.`. The implementation can reuse existing Cancel/action keys where appropriate; keep final key sets equal.

| Meaning | English | 简体中文 |
|---|---|---|
| Heading | Requirements | 使用要求 |
| Advisory explanation | You can continue without enabling these requirements. Unavailable actions will still fail. | 你可以不启用这些要求并继续；不可用的操作仍会失败。 |
| Scope | Status describes local settings. Runtime permissions still apply. | 此状态仅反映本地设置，实际操作仍受运行时权限限制。 |
| No declaration | No requirements declared | 未声明使用要求 |
| Before inspection | Requirements will be read from the package before installation. | 安装前将读取软件包中声明的使用要求。 |
| Capability | Capability | 能力 |
| Extension | Extension | 扩展 |
| Skill | Skill | 技能 |
| Satisfied | Available | 可用 |
| Disabled | Disabled | 已禁用 |
| Missing | Not installed or not present | 未安装或不存在 |
| Unavailable | Unavailable | 不可用 |
| Unknown | Unknown requirement | 未知要求 |
| Pending activation | Restart required | 需要重启 |
| Authorization detail | Not authorized | 未授权 |
| Enable | Enable %s | 启用 %s |
| Dangerous confirmation | Confirm enable | 确认启用 |
| Continue | Continue anyway | 仍然继续 |
| No grant | Continuing does not enable capabilities or grant permissions. | 继续不会启用能力或授予权限。 |
| Cancel | Cancel | 取消 |
| Preview difference | Package requirements differ from the catalog preview. | 软件包的使用要求与目录预览不同。 |
| Saved setting after cancel | Installation was cancelled. Settings you explicitly saved remain changed. | 已取消安装。你明确保存的设置更改将保留。 |

Reuse the existing full unrestricted warning translation, visibly, in its separate explicit confirmation. Do not repeat it in model prompts or every runtime invocation.

## 9. File-level tasks and verification

These tasks describe implementation acceptance boundaries. Workers may combine tiny edits, but should keep these testable ownership seams.

### Task A1 — Requirement model, strict codec, Skill adapter

**Owner:** metadata worker.

**Create:**

- `common/src/main/java/dev/openallay/requirement/RequirementSet.java`
- `common/src/main/java/dev/openallay/requirement/RequirementCodec.java`
- `common/src/main/java/dev/openallay/requirement/RequirementKind.java`
- requirement codec/model tests under `common/src/test/java/dev/openallay/requirement/`

**Modify:**

- `common/src/main/java/dev/openallay/skill/SkillMetadata.java`
- `common/src/test/java/dev/openallay/skill/AgentSkillsSubsetTest.java`

- [ ] Test absence/empty strings, all three whitespace-separated keys, exact IDs, duplicate/invalid syntax, unrelated metadata retention, immutable sorted sets, and unknown valid capability IDs.
- [ ] Implement `RequirementSet.EMPTY`, `isEmpty`, shared strict JSON/metadata codec, and derived Skill accessor using the agreed API above.
- [ ] Preserve Agent Skills top-level and string-map constraints and all legacy dependency behavior.
- [ ] Run `./gradlew :common:test --tests 'dev.openallay.requirement.*' --tests 'dev.openallay.skill.AgentSkillsSubsetTest'`.

### Task A2 — Optional Extension descriptor/package/catalog requirements

**Owner:** metadata worker.

**Modify:**

- `common/src/main/java/dev/openallay/extension/OpenAllayExtensionDescriptor.java`
- `common/src/main/java/dev/openallay/extension/install/ExtensionPackageManifestCodec.java`
- `common/src/main/java/dev/openallay/extension/catalog/ExtensionCatalogEntry.java`
- `common/src/main/java/dev/openallay/extension/catalog/ExtensionCatalogCodec.java`

**Tests:**

- `common/src/test/java/dev/openallay/extension/OpenAllayExtensionRegistryTest.java`
- codec-focused tests under `common/src/test/java/dev/openallay/extension/install/` and `common/src/test/java/dev/openallay/extension/catalog/`
- existing catalog-client last-valid-generation tests

- [ ] Prove old constructor callers compile and old documents decode with empty requirements.
- [ ] Test absent object/members, valid arrays, canonical omission of empty requirements, null, wrong types, duplicates, unknown nested fields, other unknown top-level fields, missing old required fields, and unsupported schemas.
- [ ] Propagate requirements through `descriptor()` and per-loader `descriptorFor()` without changing artifact selection.
- [ ] Preserve strict top-level schema validation and last-valid cache behavior.
- [ ] Run `./gradlew :common:test --tests 'dev.openallay.extension.*'`.

### Task A3 — Pure evaluator

**Owner:** metadata worker.

**Create:**

- `common/src/main/java/dev/openallay/requirement/RequirementAvailability.java`
- `common/src/main/java/dev/openallay/requirement/RequirementEnvironment.java`
- `common/src/main/java/dev/openallay/requirement/RequirementAssessment.java`
- `common/src/main/java/dev/openallay/requirement/RequirementStatus.java`
- `common/src/main/java/dev/openallay/requirement/RequirementReport.java`
- `common/src/main/java/dev/openallay/requirement/RequirementEvaluator.java`
- `common/src/test/java/dev/openallay/requirement/RequirementEvaluatorTest.java`

- [ ] Test every status, mixed kinds, known/unknown IDs, immutable inputs, deterministic ordering, direct self/cyclic references, and empty declarations.
- [ ] Assert no installer, settings writer, policy mutation, or runtime authority dependency enters the evaluator.
- [ ] Run `./gradlew :common:test --tests 'dev.openallay.requirement.*'`.

### Task B1 — Unpublished checked candidates

**Owner:** UI worker or its installer child.

**Create:**

- `common/src/main/java/dev/openallay/settings/requirement/PreparedPackageInstall.java`
- `common/src/main/java/dev/openallay/settings/requirement/RefreshingPreparedPackageInstall.java`
- `common/src/main/java/dev/openallay/skill/install/PreparedSkillInstall.java`
- `common/src/main/java/dev/openallay/extension/install/PreparedExtensionInstall.java`

**Modify:**

- `common/src/main/java/dev/openallay/skill/install/SkillPackageInstaller.java`
- `common/src/main/java/dev/openallay/extension/install/ExtensionPackageInstaller.java`
- `common/src/main/java/dev/openallay/settings/skill/SkillSettingsBackend.java`
- `common/src/main/java/dev/openallay/settings/extension/ExtensionSettingsBackend.java`

**Tests:** existing installer and both settings backend suites.

- [ ] Test prepare produces no discoverable/loader-visible publication; cancel preserves the previous package; close/double commit cannot publish.
- [ ] Test changing the source path after prepare cannot change reviewed bytes; replacing a candidate requires new review.
- [ ] Extract existing validation/capture from publication, without weakening path, parser, checksum, identity, compatibility, or loader checks.
- [ ] Replace whole-descriptor equality with original-field identity checks excluding advisory requirements.
- [ ] Test catalog/package advisory mismatch is nonfatal, actual package requirements win, and original identity mismatch remains fatal.
- [ ] Test unmet requirements do not affect commit on either local or community paths. Extension commit remains restart-required.
- [ ] Run `./gradlew :common:test --tests 'dev.openallay.skill.install.*' --tests 'dev.openallay.extension.install.*' --tests 'dev.openallay.settings.skill.*' --tests 'dev.openallay.settings.extension.*'`.

### Task B2 — Real settings environment and review ownership

**Owner:** UI worker.

**Create:**

- `common/src/main/java/dev/openallay/settings/requirement/RequirementSettingsEnvironment.java`
- `common/src/main/java/dev/openallay/settings/requirement/RequirementChange.java`
- `common/src/main/java/dev/openallay/settings/requirement/RequirementReview.java`
- settings-requirement tests under `common/src/test/java/dev/openallay/settings/requirement/`

**Modify:**

- `common/src/main/java/dev/openallay/settings/ClientSettingsService.java`
- `common/src/main/java/dev/openallay/settings/ClientSettingsSnapshot.java`
- `common/src/main/java/dev/openallay/settings/SettingsOperation.java`
- `common/src/main/java/dev/openallay/settings/ClientSettingsRuntime.java` only if wiring needs it
- `common/src/test/java/dev/openallay/settings/ClientSettingsServiceTest.java`
- `common/src/test/java/dev/openallay/settings/ClientSettingsRuntimeTest.java`

- [ ] Build local immutable facts from existing views/owners. Report staged Extensions as restart-required and unknown capabilities as unknown.
- [ ] Offer exact enable changes only for real supported owners; no Extension toggle or missing-package enable shortcut.
- [ ] Add review preparation to community install/update and local import. Use candidate identity/digest plus a fresh token/generation.
- [ ] Release busy reservation while awaiting the player. Re-evaluate after successful enable saves, without auto-publishing.
- [ ] Continue commits even if `report.allSatisfied()` is false. Assert it calls no settings save and grants no authority.
- [ ] Cancel/disconnect/close/replacement/late completion dispose unpublished candidates. Test stale and reused tokens.
- [ ] Test explicit enable success/failure, unrestricted consent required, partial saved settings followed by cancel, legacy policy conflicts, and no hidden dependency repair.
- [ ] Run `./gradlew :common:test --tests 'dev.openallay.settings.requirement.*' --tests 'dev.openallay.settings.ClientSettingsServiceTest' --tests 'dev.openallay.settings.ClientSettingsRuntimeTest'`.

### Task B3 — Details, review UI, and locales

**Owner:** UI worker or its screen child.

**Create/modify:**

- `common/src/main/java/dev/openallay/client/gui/settings/RequirementSettingsProjection.java`
- UI worker's dedicated native requirement-review surface
- `common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java`
- `common/src/main/java/dev/openallay/client/gui/settings/SkillSettingsProjection.java`
- `common/src/main/java/dev/openallay/client/gui/settings/ExtensionSettingsProjection.java`
- `common/src/main/java/dev/openallay/settings/skill/SkillSettingsView.java`
- `common/src/main/java/dev/openallay/settings/extension/ExtensionSettingsView.java`
- `common/src/main/resources/assets/openallay/lang/en_us.json`
- `common/src/main/resources/assets/openallay/lang/zh_cn.json`

**Tests:**

- `common/src/test/java/dev/openallay/client/gui/settings/RequirementSettingsProjectionTest.java`
- existing Skill/Extension settings projection tests
- `common/src/test/java/dev/openallay/client/gui/OpenAllaySettingsScreenProjectionTest.java`
- `common/src/test/java/dev/openallay/client/gui/SettingsLocalizationTest.java`

- [ ] Show requirement rows in installed details and before every package publication. Keep advisory state independent of package `installable`/`INCOMPATIBLE`.
- [ ] Show normal name and exact unknown IDs. For an uninspected community Skill show unknown-before-package-inspection, not an empty declaration claim.
- [ ] Keep Continue anyway active for missing/disabled/unavailable/unknown/restart-required states. Show Enable only for exact supported changes.
- [ ] Add full visible unrestricted warning confirmation without altering normal unrelated enable buttons.
- [ ] Add both locales, matching key sets/placeholders, keyboard/back navigation, wide/narrow layout, and scrolling for all rows.
- [ ] Run `./gradlew :common:test --tests 'dev.openallay.client.gui.*' --tests 'dev.openallay.settings.skill.*' --tests 'dev.openallay.settings.extension.*'`.

### Task C — Integration boundaries and loader verification

**Owner:** parent integration/review; coordinate test ownership with workers.

**Regression files:**

- `common/src/test/java/dev/openallay/skill/SkillRepositoryTest.java`
- `common/src/test/java/dev/openallay/skill/LoadSkillToolTest.java`
- `common/src/test/java/dev/openallay/capability/ClientCapabilityResolverTest.java`
- `common/src/test/java/dev/openallay/settings/capability/CapabilitySettingsBackendTest.java`
- `common/src/test/java/dev/openallay/client/ClientModelRuntimeRegistryTest.java`
- `common/src/test/java/dev/openallay/server/ServerGuideRuntimeTest.java`
- `common/src/test/java/dev/openallay/bridge/client/ClientPlacedToolExecutorTest.java`
- `common/src/test/java/dev/openallay/script/RhinoJavascriptRuntimeTest.java`
- `common/src/test/java/dev/openallay/agent/tool/LocalAgentToolExecutorTest.java`
- `common/src/test/java/dev/openallay/extension/OpenAllayExtensionRegistryTest.java`

- [ ] Add a valid ordinary Skill with all three unmet advisory kinds and no conflicting legacy dependencies. Assert repository reload, metadata listing, snapshot lookup, `load_skill`, and Continue installation succeed.
- [ ] Assert requirement declarations never mutate deny policy or request context; an actual denied tool still returns `tool_unavailable`.
- [ ] Assert reading the ordinary Skill does not enable Java; disabled execution fails through its real runtime path, authorized local execution still works, and no fallback executes.
- [ ] Assert server models/callbacks stay unrestricted=false even with local setting enabled and the Skill loaded.
- [ ] Assert active requests keep captured authority; only future requests observe a saved setting.
- [ ] Assert builtin033 authorization-specific guidance is unchanged and arbitrary new metadata cannot expose it as enabled.
- [ ] Assert a valid Extension with unmet advisory requirements still registers; duplicate/invalid actual contributions still fail.
- [ ] Run `./gradlew :common:test`.
- [ ] Run `./gradlew :fabric:build :neoforge:build`.
- [ ] Manual smoke on both loaders: local Skill directory/ZIP, community install/update, local Extension JAR/community update, all three review choices, staged restart state, missing/disabled/unknown requirements, and English/Chinese at wide/narrow sizes.

## 10. Acceptance matrix

| Case | Required result |
|---|---|
| Old metadata omits requirements | Same decoding and behavior as before. |
| All new requirement kinds unmet | Visible advisory rows; Continue installs; ordinary Skill can be read. |
| Capability known but disabled | Exact supported Enable action, no implicit mutation. |
| Unrestricted not authorized | Visible risk/consent before enable; Continue leaves it disabled. |
| Cancel before commit | No replacement published; prior valid package retained. |
| Successful explicit setting save then cancel | Package not installed; saved setting remains with clear explanation. |
| Catalog versus package advisory mismatch | Nonfatal notice; reviewed package's declaration used. |
| Bad checksum/identity/path/loader compatibility | Existing hard validation failure; no bypass. |
| Required Extension only staged | Restart required, never satisfied/active. |
| Cyclic Skill references | Direct reports only; no recursion or auto-repair. |
| Unknown valid capability ID | Visible unknown status; no registration/permission grant. |
| Malformed declaration shape/ID | Strict format error; do not confuse this with unmet availability. |
| Explicit legacy Tool/Skill deny | Existing enforcement preserved. |
| Loaded Skill attempts missing runtime function | Real failure, no fallback, no auto-enable, no fabricated success. |
| Server model/callback with local unrestricted on | No server/callback unrestricted authority granted. |
| Settings change while request runs | Active request unchanged; future request sees actual saved state. |

## 11. Documentation and release coordination

- Decision035 records final semantics. Do not re-open accepted schema choices as proposal blockers.
- Document the final three `requires-*` string keys and Extension typed requirements in `docs/development.md`.
- Explain optional schema expansion and old-reader limitations without adding cache migrations or accepting unknown versions.
- Distinguish legacy hard fields, advisory declarations, explicit deny policy, and real runtime permission checks.
- Coordinate any public schema/validator/package updates with their owners. Main-repository implementation does not authorize publishing catalogs or changing external repositories.
- Do not claim tests or loader/manual acceptance until the corresponding command/result exists.

Final review question: **Can package-provided requirement metadata or Continue anyway grant authority, hide an ordinary valid Skill, or silently repair missing runtime functionality?** The implementation must make each answer **no**.
