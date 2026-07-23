# Rhino Agent runtime live-provider acceptance

- Date: 2026-07-24
- Model: `deepseek-v4-flash`
- Protocol: OpenAI-compatible Chat Completions
- Credential source: interactive environment input; never written to a file or
  report
- Mode: non-streaming provider response, production Agent loop and Tool
  continuation
- Test: `LiveJavascriptAgentAcceptanceTest`
- Result: passed

## Highest-damage sword

The model loaded `analyze-game-data`, loaded
`references/examples.md`, and used JavaScript over the complete captured item
array. It returned:

```text
example:obsidian_sword
damage: 14
```

The accepted run used two set-level JavaScript programs and no per-item Tool
loop. Between them the provider attempted an irrelevant craftability check with
an invalid synthetic generation, received a bounded failure, and recovered.
The production prompt was tightened after this observation so craftability is
reserved for explicit inventory-sufficiency questions. A prior successful run
used one program after the same Skill/reference preflight.

## Minimum-material container

The model loaded `analyze-game-data`, loaded
`references/examples.md`, filtered all container outputs, summed consumed
ingredient units, and returned:

```text
recipe: example:small_pouch
output: example:small_pouch
material units: 2
```

The accepted run used one ranking program. It did not issue one Tool call per
recipe or item.

## Runtime and context safeguards exercised

The accepted build uses KubeJS-Mods Rhino in a fresh denied-host context,
selects the smallest documented data roots, caches one detached projection for
the request, keeps canonical results behind request-local handles, and sends
only the bounded structured preview to the provider. Skill instructions and
references use snapshot-bound progressive cursors when they exceed one chunk.

## Provider observation

Two streaming attempts from the user-authorized endpoint ended with
`model_transport_error`; a direct authenticated model-list and streaming probe
both succeeded, and the same two Agent scenarios passed with provider streaming
disabled. This is retained as provider/stream transport evidence, not reported
as a Rhino or Tool failure.
