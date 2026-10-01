# Command request binding implementation plan

Goal: Keep the command setting, request-frozen authority, advertised guidance, and actual Rhino global consistent. Do not infer a disabled setting from the user report.

Confirmed defects: Client submit freezes only unrestricted mode. Commands freeze later during context capture. The request catalog is not selected from the actual bridge. Core guidance lists world but not top-level commands, and schema discovery covers mc only.

1. Add tests for enabled safe/unrestricted and disabled Rhino globals, frozen toggles, and matching request Skills.
2. Freeze and close command authority in the common client context provider.
3. Retain explicitly allowed command documents for future request selection. Filter command guidance by the bridge held by the actual run_javascript instance. Do not override explicit Skill denies.
4. Add prompt guidance that commands is separate from mc/schema and Java mode. State availability for each request.
5. Add settings save/reload and current-vs-next-request tests using the production shared command runtime.

No Gradle or game start in this worker. Parent schedules verification. No commits. Do not read credentials or environment. Scene-specific missing-binding cause remains unconfirmed until direct typeof commands evidence is available.
