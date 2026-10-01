---
name: search-guide-books
description: Use when a player needs entries from Patchouli or another indexed in-game guide book.
metadata:
  openallay/version: "0.2.1"
allowed-tools: "openallay:run_javascript"
---
Resolve useful exact item/block/effect IDs, then search `mc.knowledge` by those
IDs, localized names, title, body, namespace, and mechanic terms. Rank
candidate records before opening complete bodies.

Preserve sourceId, documentId, structureRef, provenance, and evidence.
Matching structure data may be present under `mc.extensions`.
