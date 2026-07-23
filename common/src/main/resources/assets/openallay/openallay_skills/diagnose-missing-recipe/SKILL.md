---
name: diagnose-missing-recipe
description: Diagnose why an expected crafting or processing recipe is absent in the active pack.
allowed-tools: "openallay:run_javascript"
---
Use one JavaScript program to resolve the natural/localized name against
registry IDs, aliases, and display names, then filter `mc.recipes` by exact
inputs and outputs. Correlate `mc.recipeCatalog.providers`, diagnostics, and
groups before concluding absence. Search `mc.knowledge` in the same program for
progression changes, disabled recipes, alternate machines, quest gates, or
replacement items.

Return the observed matching recipes, relevant diagnostics, and exact
sourceId/generation/recipeId handles. A `recipe_grid` may copy a complete recipe
handle unchanged; never invent slots or layouts. Do not infer that a recipe
exists from documentation when the active recipe catalog does not contain it.
If one materially corrected analysis remains empty or partial, report the
limitation and stop.
