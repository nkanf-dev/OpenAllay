---
name: search-guide-books
description: Find and synthesize relevant entries from Patchouli and other indexed in-game guide books.
allowed-tools: "openallay:run_javascript"
---
Use one JavaScript program to resolve useful exact item/block/effect IDs from
registry arrays, then search `mc.knowledge` by those IDs, localized names, title,
body, namespace, and mechanic terms. Rank compact candidate records before
returning complete bodies; do not return the entire guide corpus.

Enumerate only matches within the captured evidence scope. Preserve sourceId,
documentId, structureRef, provenance, and evidence. Treat missing, malformed,
config-gated, and unsupported entries as unavailable; never reconstruct their
contents from guesses. Trusted structure data may be present under
`mc.extensions`; use it only when its explicit reference matches the document.
