---
name: explain-machine-usage
description: Use when a player asks how to obtain, place, configure, power, or automate a modded machine.
metadata:
  openallay/version: "0.2.1"
allowed-tools: "openallay:run_javascript"
---
Use this Skill when the player asks how a machine or multiblock works.

Resolve the exact block/controller from `mc.blocks`, join live acquisition
recipes from `mc.recipes`, and select matching
`mc.knowledge` documents for setup, inputs, outputs, energy, orientation, and
multiblock requirements. Extension-provided machine data is relevant only when
its declared source and exact machine identity match.

Preserve structure references and separate required setup from optional
optimizations.
