# Phase 3C Player GUI Implementation Plan

**Goal:** Deliver one accessible, non-pausing, full-screen TomeWisp interface on
Fabric and NeoForge 26.2, opened by default `K` or bare `/guide`, as a pure
projection and intent sender over the existing GuideService.

**Architecture:** Common Minecraft client code owns a deterministic view model,
layout calculation, screen and source/tool detail state. Loader code owns only
key mapping registration and tick consumption. The screen subscribes while open
and reconstructs from immutable snapshots; closing only detaches its listener.

## Task 1: Pure view model and layout

1. Define transcript rows, session summaries, capability/empty state, tool and
   source details without retaining mutable Minecraft objects.
2. Test streaming/final reconciliation, selected-session busy state, concurrent
   session indicators, error/rate states, source/tool projections, and narrow
   versus normal layout bounds.
3. Keep reasoning and credentials absent from every visible projection.

## Task 2: Common screen

1. Implement a non-pausing `Screen` with session rail, top status, scrollable
   transcript, multiline-like composer, send/stop/retry, model selector, and
   detail drawer/overlay.
2. Bind lifecycle to GuideService subscription only; Escape/removal never
   cancels a request.
3. Use Minecraft widgets/components and scissoring; no web renderer or new UI
   framework. Provide narration labels and keyboard focus.

## Task 3: Loader opening and default K

1. Replace `gui_unavailable` openers with `Minecraft.setScreen` in both loaders.
2. Register a category-scoped default `K` key mapping and consume clicks on the
   client tick only when a player exists.
3. Keep loader source as thin adapters and compile both mappings against 26.2.

## Task 4: Behavioral verification

1. Test view model, layout and screen-independent intent decisions in common.
2. Run all common tests and both loader production builds.
3. Inspect both JARs for screen/view/key integration and scan tracked files/JARs
   for credentials.
4. Persist exact evidence and the no-graphical-run limitation. Do not claim
   screenshots or visual E2E because unattended execution must not launch GUI.

## Task 5: Phase 3 closeout

1. Update README, development guide, SKMB and product design with completed GUI
   behavior and remaining explicit acceptance gap.
2. Run final clean verification, review diff/history, push the branch with
   network retries, and integrate only after checks pass.
