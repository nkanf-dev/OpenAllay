---
name: explain-machine-usage
description: Explain how to obtain, place, configure, power, and automate a modded machine.
allowed-tools: "openallay:run_javascript"
---
Use this Skill when the player asks how a machine or multiblock works.

In one JavaScript program, resolve the exact block/controller from `mc.blocks`,
join live acquisition recipes from `mc.recipes`, and select matching
`mc.knowledge` documents for setup, inputs, outputs, energy, orientation, and
multiblock requirements. Trusted extensions may expose additional machine data
under `mc.extensions`; inspect its schema before use.

Preserve structure references and evidence. Separate verified requirements from
optional optimizations. Do not claim that a Ponder scene has already been
generated. If documentation is unavailable, say so rather than applying
mechanics from a similarly named mod.
