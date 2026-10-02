# Unrestricted Java Access Implementation Plan

> **For agentic workers:** Implement independent facade/tests in the isolated worktree. Root serializes Gradle verification. Do not attach to, restart, or change the user's running client/world. No release/tag is authorized by this task.

**Goal:** Let an already-authorized client-local JavaScript request inspect and access Java members through reliable native entrypoints rather than Rhino's public-member wrapper conventions.

**Architecture:** Extend the existing `Java` object only in unrestricted mode. A small Java-host facade resolves actual classes/loaders, walks declared members and invokes exact signatures through standard reflection. Parameter conversion and result wrapping use the checked Rhino APIs. It does not alter Rhino's shared class caches, transform game classes, attach an agent, or propagate JVM authority to ordinary/scoped Extension execution.

**Tech Stack:** Java 25 reflection, existing Rhino BaseFunction/Wrapper/TypeInfo conversion, Gson/closed metadata views, Gradle wrapper/JUnit.

## Contract

- Keep `Java.type(name)` compatible.
- `Java.classOf(value)` returns the actual class wrapper without calling a guest-visible `getClass`.
- `Java.inspect(target)` returns detached class/member metadata, not automatically read private values.
- `Java.get(target, fieldName)` and `Java.set(target, fieldName, value)` find the nearest declared field in the target hierarchy. A class target operates on static members; an instance target operates on its bound object. Explicit declaring-class forms can disambiguate shadowed fields if needed, without mutable global selection state.
- `Java.invoke(target, methodName, parameterTypes, arguments)` resolves the exact declared signature through the target hierarchy. Explicit parameter types avoid a second handwritten overload engine. Varargs use their actual array signature and existing Rhino array conversion.
- `Java.construct(type, parameterTypes, arguments)` invokes the exact declared constructor.
- Member/type resolution uses the actual target class loader, including primitive/array types. No single-argument `Class.forName` through guest reflection.
- Standard `trySetAccessible`/reflection rules govern access. Named-module closure, final/static-final rules and target exceptions are reported honestly; no Unsafe or dynamic attach fallback.
- Ordinary JS and scoped Extension callbacks remain without this surface. Live game operations still run on their actual owning thread; this facade does not guess or reroute user code.
- Data/result context projection remains unchanged. No credential/result scanning is reintroduced.

## Implementation and verification

- [x] Write native regression cases for the current `.class`/hidden `getClass`/caller-sensitive reflection failures using synthetic classes and actual Rhino execution.
- [x] Implement the facade and install it through the existing unrestricted bridge. Keep native errors short and useful with actual access/error categories.
- [x] Test private/protected/package fields, superclass shadowing, static/instance methods, private constructors, overloaded exact signatures, primitive/array/varargs conversion, null and missing members, module access denial, target exceptions and worker cancellation.
- [x] Test safe/unrestricted order isolation: shared Rhino caches must not acquire private access and safe `Java` remains absent.
- [x] Document the exact methods and runnable generic examples in the unrestricted Skill reference only. Keep the system prompt capability-neutral.
- [ ] Run focused tests, full common tests and both loader builds. Preserve unrelated source changes and user saves/config/history/exports.
- [ ] Commit/push in coherent verified batches if delivery proceeds; no product version/tag/publication without a new request.

## Evidence

Latest request 15 in the exported session-3 snapshot failed at wrapper entrypoints: `Java.type(...).class` is not a Rhino property, guest `Class.forName` triggers a caller-sensitive restricted-lookup error, and `Minecraft.getInstance().getClass()` is not exposed by this fork's wrapper. These are not proof that a particular private member or named-module operation was attempted and denied. Dynamic Mixin is not the first fix for these failures.
