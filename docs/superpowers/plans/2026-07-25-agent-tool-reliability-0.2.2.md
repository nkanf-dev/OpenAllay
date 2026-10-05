# Agent Tool Reliability 0.2.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce real-client JavaScript correction calls, make command/world
results accurately described, clarify cancelled exports, and publish OpenAllay
0.2.2.

**Architecture:** Keep `run_javascript` as the single general Agent capability.
Teach exact interaction recipes through the descriptor-derived core contract,
system prompt, and vertical command Skill; keep runtime state semantics
unchanged. Format cancellation explicitly at the privacy-safe export boundary.

**Tech Stack:** Java 25, JUnit 5, KubeJS Rhino, Minecraft 26.2, Gradle,
GitHub Actions, Modrinth v2 API.

---

### Task 1: Lock the guidance contract with tests

**Files:**
- Modify: `common/src/test/java/dev/openallay/agent/AgentSystemPromptTest.java`
- Modify: `common/src/test/java/dev/openallay/script/schema/CoreJavascriptContractTest.java`
- Modify: `common/src/test/java/dev/openallay/skill/BundledSkillsTest.java`

- [ ] Add assertions for installed-instance scope, retry workflow continuity,
  command feedback truth, one player-relative world example, direct
  `commands.run`, and the prohibition on calling `commands` as a function.
- [ ] Run:
  `./gradlew :common:test --tests 'dev.openallay.agent.AgentSystemPromptTest' --tests 'dev.openallay.script.schema.CoreJavascriptContractTest' --tests 'dev.openallay.skill.BundledSkillsTest'`
  and verify that the new assertions fail.

### Task 2: Implement the canonical Agent guidance

**Files:**
- Modify: `common/src/main/java/dev/openallay/agent/AgentSystemPrompt.java`
- Modify: `common/src/main/java/dev/openallay/script/schema/CoreJavascriptContract.java`
- Modify: `common/src/main/resources/assets/openallay/openallay_skills/run-game-commands/SKILL.md`
- Modify: `common/src/main/resources/assets/openallay/openallay_skills/run-game-commands/references/commands.md`

- [ ] Add compact rules for retained retry workflow, installed-instance scope,
  feedback-versus-verification wording, and stopping after a complete scan.
- [ ] Add one syntactically valid relative `world.inspect` example using
  `mc.player.position` and `roots: ["player", "world"]`.
- [ ] Put direct synchronous `commands.run` usage before command discovery and
  state that `commands` is never called as a function.
- [ ] Run the Task 1 test command and verify that it passes.

### Task 3: Mark incomplete exported responses

**Files:**
- Modify: `common/src/test/java/dev/openallay/client/gui/export/GuideSessionExporterTest.java`
- Modify: `common/src/main/java/dev/openallay/client/gui/export/GuideSessionExporter.java`

- [ ] Add one cancelled snapshot whose partial assistant text must be followed
  by `[This request ended before the response completed.]`.
- [ ] Assert that a completed snapshot has no interruption marker.
- [ ] Implement the marker after the request timeline without adding Tool
  inputs, outputs, raw failures, or diagnostics.
- [ ] Run:
  `./gradlew :common:test --tests 'dev.openallay.client.gui.export.GuideSessionExporterTest'`
  and verify that it passes.

### Task 4: Run focused regression and package gates

**Files:**
- Verify only.

- [ ] Run:
  `./gradlew :common:test --tests 'dev.openallay.agent.AgentSystemPromptTest' --tests 'dev.openallay.script.schema.CoreJavascriptContractTest' --tests 'dev.openallay.skill.BundledSkillsTest' --tests 'dev.openallay.script.command.JavascriptCommandBridgeTest' --tests 'dev.openallay.world.JavascriptWorldBridgeTest' --tests 'dev.openallay.client.gui.export.GuideSessionExporterTest'`
- [ ] Run `./gradlew clean :common:test :fabric:build :neoforge:build`.
- [ ] Run `./scripts/verify-distribution.sh` and
  `./scripts/verify-sqlite-packaging.sh`.

### Task 5: Prepare and publish 0.2.2

**Files:**
- Modify: `gradle.properties`
- Modify: `README.md`
- Modify: `README.zh-CN.md`
- Modify: `docs/development.md`

- [ ] Set `version=0.2.2` and update player/developer text that names 0.2.1 as
  the current build while preserving the 0.2.x line.
- [ ] Add concise README wording for reliable command feedback and focused
  world verification.
- [ ] Review `git diff --check`, stage only this release scope, and commit with
  a conventional message.
- [ ] Push `main`, wait for the Quality workflow, create annotated tag
  `v0.2.2`, and push the tag.
- [ ] Wait for the Release workflow and verify both the GitHub Release and the
  two Modrinth version files before reporting completion.
