# Scoped Extension Bindings Implementation Plan

> **For agentic workers:** Execute this isolated core worktree in small reviewable steps. Do not run Gradle, launch a game, modify runtime settings, or commit until the parent permits it.

**Goal:** Let safe Agent JavaScript call only an Extension's declared host methods without granting Agent Java/JVM access.

**Architecture:** Registry-owned immutable method declarations are exposed through the existing Rhino `require` function as closed read-only objects with `BaseFunction` methods. Every call uses the same worker-local Extension invocation context as its lifecycle participant. Each Extension declares its own exact capabilities. Independent user grants are frozen per client-local request; installation, server callbacks, and the unrestricted-Java toggle never grant them.

**Tech Stack:** Existing Rhino, Gson JSON leaves, the existing invocation scope lifecycle, and atomic settings persistence.

---

- [ ] Add `ExtensionCapability`, `JavascriptHostValueType`, `JavascriptHostMethod`, and `JavascriptHostBinding` public contracts. Keep old four- and five-argument contribution constructors. Advance the independently released Extension API to 0.2.2.
- [ ] Capture and validate binding declarations and capability ownership atomically in `OpenAllayExtensionRegistry`. Make owner-scoped invocation contexts share cancellation/evidence lifecycle without sharing another Extension's grants.
- [ ] Bind namespaced methods through Rhino `require`. Validate exact argument count, scalar types, finite numeric values, closed JSON arguments/results, and cancellation before/after handlers. Never wrap Java objects or forward JavaScript callbacks.
- [ ] Persist independent default-off grants in one latest-only exact-shape settings document. Freeze user grants at client request start and freeze no grants for server callbacks. Add one generic Extension capability control per declared scope; installing a package does not enable it.
- [ ] Add focused tests for safe calls, unrestricted independence, readonly detached values, callback/reflection rejection, per-owner grants, server isolation, request freezing, cancellation, and worker-local cleanup. Ask the parent to run the focused Gradle gate before integration.
