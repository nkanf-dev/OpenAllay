---
name: diagnose-missing-recipe
description: Use when an expected crafting or processing recipe is absent in the active pack.
metadata:
  openallay/version: "0.2.1"
allowed-tools: "openallay:run_javascript"
---
Resolve the natural/localized name against registry IDs, aliases, and display
names, then filter `mc.recipes` by exact inputs and outputs. Correlate
`mc.recipeCatalog.providers`, diagnostics, and groups before concluding
absence. Search `mc.knowledge` for
progression changes, disabled recipes, alternate machines, quest gates, or
replacement items.

Return the observed matching recipes, relevant diagnostics, and exact
sourceId/generation/recipeId handles. Do not infer that a recipe exists from
documentation when the active recipe catalog does not contain it.
If the available evidence remains empty or partial, report that limitation.
