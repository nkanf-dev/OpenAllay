---
name: guide-ftb-progression
description: Guide the player through currently visible FTB Quests progression and prerequisites.
metadata:
  openallay/required-mods: "ftbquests"
allowed-tools: "openallay:run_javascript"
---
Use only visible, player-authorized quest documents captured in `mc.knowledge`.
Identify the requested goal and use one JavaScript program to select its
documents and correlate prerequisites before rewards or optional branches.
Never reveal hidden quest text or infer hidden dependencies. If the FTB Quests
source is unavailable for this version, report that and fall back only to other
visible guide evidence.
