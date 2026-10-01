---
name: inspect-game-state
description: Use when troubleshooting requires correlating two or more Minecraft client, settings, HUD, or F3 areas.
metadata:
  openallay/version: "0.2.2"
allowed-tools: "openallay:run_javascript"
---
Use this Skill for multi-step diagnosis or correlation across Minecraft menus,
the player's own UI, HUD/F3, installed-content screens, and closed
non-mutating queries. Do not load it for an obvious direct lookup such as
“list my installed mods” or “what biome am I in”; query `mc.game` directly.

Select and correlate only the required sections:

- `mc.game.runtime`: version, loader, topology, and runtime identity.
- `mc.game.mods.installed`: installed mod metadata. This direct exact-path
  lookup does not require schema probing.
- `mc.game.options.values`: option values and key mappings across video, sound,
  controls, mouse, accessibility, language/chat, online/privacy, packs, and
  general settings.
- `mc.game.packs`: selected and available resource/data packs.
- `mc.game.shaders`: shader state and options. Treat an unavailable integration
  as unavailable; do not infer shader state from installed files.
- `mc.game.diagnostics`: section metadata; its `.values` array contains detached
  F3-style position, direction, dimension, biome, renderer, performance,
  target, and network values.
- `mc.game.player` and `mc.player`: only the caller's visible state.
- `mc.game.worldQueries`: time, weather, difficulty, world border, and spawn.

Biome, coordinates, dimension, direction, yaw, and pitch come from diagnostics
and never require server command permission. Return a compact correlation and
retain authority/completeness metadata. If a section is partial or unavailable,
say what is unavailable and stop rather than guessing.

Recipes and Guides are separate deep-content domains; load their Skills for
those questions. Spatial block/entity observation is also outside this
troubleshooting Skill: use the core top-level `world` binding directly when that
request capability is available. Maps, structures,
another container's contents, arbitrary paths/classes, raw commands, and every
write or world interaction remain outside this Skill.
