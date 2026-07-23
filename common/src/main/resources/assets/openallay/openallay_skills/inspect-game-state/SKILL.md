---
name: inspect-game-state
description: Multi-section troubleshooting that correlates two or more Minecraft client, settings, and F3 areas; direct one-field lookups do not need this Skill.
allowed-tools: "openallay:run_javascript"
---
Use this Skill for multi-step diagnosis or correlation across Minecraft menus,
the player's own UI, HUD/F3, installed-content screens, and closed
non-mutating queries. Do not load it for an obvious direct lookup such as
“list my installed mods” or “what biome am I in”; query `mc.game` directly.

Use one JavaScript program to select and correlate only the required detached
sections:

- `mc.game.runtime`: version, loader, topology, and runtime identity.
- `mc.game.mods`: installed mod metadata.
- `mc.game.options`: option values and key mappings across video, sound,
  controls, mouse, accessibility, language/chat, online/privacy, packs, and
  general settings.
- `mc.game.packs`: selected and available resource/data packs.
- `mc.game.shaders`: shader state and options. Treat an unavailable integration
  as unavailable; do not infer shader state from installed files.
- `mc.game.diagnostics`: detached F3-style position, direction, dimension,
  biome, renderer, performance, target, and network values.
- `mc.game.player` and `mc.player`: only the caller's visible state.
- `mc.game.worldQueries`: time, weather, difficulty, world border, and spawn.

Biome, coordinates, dimension, direction, yaw, and pitch come from diagnostics
and never require server command permission. Return a compact correlation and
retain authority/completeness metadata. If a section is partial or unavailable,
say what is unavailable and stop rather than guessing.

Recipes and Guides are separate deep-content domains; load their Skills for
those questions. Nearby blocks/entities, maps, structures, another container's
contents, arbitrary paths/classes, raw commands, and every write or world
interaction are outside this Skill.
