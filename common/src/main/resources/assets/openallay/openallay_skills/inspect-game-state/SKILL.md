---
name: inspect-game-state
description: Use when troubleshooting requires correlating two or more Minecraft client, settings, HUD, or F3 areas.
metadata:
  openallay/version: "0.2.2"
allowed-tools: "openallay:run_javascript"
---
Use `mc.game` for client settings, HUD/F3, installed content, and world queries.
Relevant sections include:

- `mc.game.runtime`: version, loader, topology, and runtime identity.
- `mc.game.mods.installed`: installed mod metadata.
- `mc.game.options.values`: option values and key mappings across video, sound,
  controls, mouse, accessibility, language/chat, online/privacy, packs, and
  general settings.
- `mc.game.packs`: selected and available resource/data packs.
- `mc.game.shaders`: shader state and options.
- `mc.game.diagnostics`: section metadata; its `.values` array contains detached
  F3-style position, direction, dimension, biome, renderer, performance,
  target, and network values.
- `mc.game.player` and `mc.player`: the caller's visible state.
- `mc.game.worldQueries`: time, weather, difficulty, world border, and spawn.

Biome, coordinates, dimension, direction, yaw, and pitch are in diagnostics.
Return a compact correlation with authority/completeness metadata.
Use the core top-level `world` binding for spatial block/entity observations
when available.
