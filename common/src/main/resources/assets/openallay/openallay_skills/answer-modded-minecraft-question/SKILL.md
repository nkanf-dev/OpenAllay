---
name: answer-modded-minecraft-question
description: Fallback multi-step workflow that correlates captured game data and indexed knowledge when no narrower recipe, guide, machine, progression, or diagnostic Skill matches.
allowed-tools: "openallay:run_javascript openallay:calculate_craftability"
---
Use this Skill only for a multi-step fallback that must coordinate more than
one data area and no narrower Skill matches. Never load it after a more specific
Skill. A simple direct JavaScript lookup does not match this Skill. Choose one
branch from the player's intent; do not run several branches speculatively.

Use one `run_javascript` program to join the relevant `mc` arrays. Inspect
`mc.capabilities` and `helpers.schema` before assuming unfamiliar mod-added
properties. Return compact answer rows, not whole datasets.

For an exact recipe result, preserve its sourceId, generation, and recipeId
unchanged. A `recipe_grid` component may copy that complete handle; never author
slots, coordinates, textures, GUI classes, or layouts.

1. Settings, installed mods, packs, F3, or player-visible state live under
   `mc.game` and `mc.player`.
2. Installed content lives in registry arrays and arbitrary `properties`.
3. Crafting and processing recipes live in `mc.recipes`; join them to registry
   rows by exact IDs.
4. Mechanics and progression documents live in `mc.knowledge`.
5. Use `calculate_craftability` only for deterministic overlapping inventory
   allocation after selecting an exact recipe.
6. Respect source completeness and evidence. Never invent an unavailable recipe
   or guide entry.
7. Stop after one materially corrected empty or partial result.

Answer in the player's language. Prefer concise ordered steps for procedures.
