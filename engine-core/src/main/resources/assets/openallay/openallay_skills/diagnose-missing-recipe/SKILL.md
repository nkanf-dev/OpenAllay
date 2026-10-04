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

Return the matching active recipes, relevant diagnostics, and exact
sourceId/generation/recipeId handles.
