# Mature Forge ecosystem backport — first anchor implementation plan

**Goal:** Adapt OpenAllay to high-value legacy Forge ecosystems without copying the feature engine, SDK or Builder. First executable milestone: Minecraft 1.19.2 Forge. Then 1.18.2, 1.16.5 and 1.12.2; 1.7.10 is conditional on a real supported runtime route.

**Architecture:** Keep the Java 17 Minecraft-free engine and Rhino, Java 8 public SDK and universal Builder unchanged. Use actual loader identities and small typed native bindings. Use Forge for older anchors; no automatic Fabric matrix. Java 8-era game loaders require a separately proven supported modern-JVM route before implementation.

**Verification and storage:** Remote Gradle/game checks only. First assemble one target/loader, collect all compilation errors once, fix owners together and run one representative native operation. Preserve unaffected 0.4.2 behavior evidence. No release by default. No large local runtime, cache or artifact copies. Commit and push coherent batches; preserve unique evidence before task cleanup.

## 1. First anchor source changes
- [ ] Explicit Forge target/profile and module selection: official Forge 1.19.2-43.5.0, Java 17, native MCP/game facts. Reuse existing Forge-namespaced 1.20.1 bindings where APIs match. Use checked-in ModDevGradle legacy Forge support; no isolated build unless a real blocker appears.
- [ ] World adapter bindings under adapters/minecraft/src/targets/1.19.2. Preserve exact actor/world/dimension identity, loaded chunks, owner-thread scheduling, state serialization, operation feedback and cancellation.
- [ ] Common native bindings for Minecraft 1.19.2. Adapt PoseStack-based GUI, widget access, resource IDs, NBT/registry/item/recipe APIs, captures and E2E world construction. Preserve feature behavior in engine-core.
- [ ] Lightweight profile/source-closure and launcher contract checks. Validate the actual selected source list, loader descriptor identity, packet scheduling and build command before remote CI allocation.

## 2. One useful CI lane
- [ ] Push source, dispatch only 1.19.2 Forge assembly with recorded exact source and pinned Builder source.
- [ ] Capture all compiler/linkage failures from the target closure; fix smallest actual owners, not sequential dependency guesses.
- [ ] Run the exact packaged JAR with the normal official target loader and Java 17. Verify startup and one real native read/write plus core lifetime/rejection checks relevant to changed boundaries.
- [ ] Record nonfatal copy/style/static findings without blocking. Keep passed job/attempt/source/JAR SHA receipts. Do not rerun unaffected mainline/UI/persistence suites.

## 3. Remaining anchors and finish
- [ ] Reuse verified native families descending to 1.18.2. Determine supported Java 17+ host route for 1.16.5 and Java 8-era 1.12.2 before changing engine ABI.
- [ ] Prefer real maintained ecosystem loaders/runtime additions over an expensive feature-runtime fork, with exact documented target requirements. Never claim stock Forge support from an alternative loader result.
- [ ] Add 1.7.10 only if supported runtime/ecosystem return warrants the cost. Skip cold versions and low-return loader combinations.
- [ ] Update documentation to distinguish implemented/accepted targets from published 0.4.2 support. Default no new release. Small verified source commits and preservation-first task cleanup.
