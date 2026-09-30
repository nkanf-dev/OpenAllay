# SKMB-2026-09-30-035: Advisory Skill and Extension requirements

Status: accepted by the user's explicit instructions in this implementation task.

## Selected behavior

Skill extra metadata and Extension descriptors declare useful capabilities and
dependencies. These declarations support player understanding, not a new grant
or hard gate. Missing requirements do not prevent installation, activation,
reading or explicit use. The UI offers enable available requirements, cancel,
and Continue anyway. Continue anyway does not enable settings, change deny
policy, install packages, or claim that missing runtime functions will work.

Skill metadata uses scalar extra keys `openallay/requires-capabilities`,
`openallay/requires-extensions`, and `openallay/requires-skills`, with whitespace
separated exact IDs. Empty/absent means no advisory declaration. Validate known
key syntax; unknown ordinary metadata remains preserved. Existing required-mods
and allowed-tools contracts are unchanged, not silently converted into this
new advisory scheme.

Extension descriptor, package and catalog optional `requirements` contains
`capabilities`, `extensions`, and `skills` arrays of unique nonblank exact IDs.
Absent keys mean empty lists. Unknown requirement object members are rejected;
unknown capability IDs are retained and shown as unknown/unavailable rather
than authorizing anything. Optional schema expansion is explicit: existing
schema1 package/schema2 catalog inputs still decode identically without this
field; other unknown top-level fields and unknown schema versions still fail.
New writers omit empty requirements so old metadata retains its representation.
Existing Java descriptor constructors remain available with empty requirements.

Differences between advisory catalog declarations and package declarations do
not become a new hard identity mismatch. The selected artifact's checked
manifest is the authority for its advisory display after staging; exact source,
version, compatibility, checksum and mod identity validation remain unchanged.
Do not install unreviewed candidate content merely to discover requirements.
Preview parses downloaded/local staged metadata and presents requirements before
publication; cancel discards staging, Continue anyway publishes the already
validated candidate unchanged. Changed/replaced candidates require fresh preview.

## UI and settings

Show declared needs in Skill/Extension list details and installation preview.
Evaluate against immutable current settings/catalog state: satisfied, disabled,
missing, unavailable, unknown or restart-required as applicable. Names/IDs are
visible even if unknown. Local and community package paths receive equivalent
preview behavior. Capability/Extension absence cannot fabricate success.

Enable is an explicit player action listing the exact changes. Reuse existing
setting validators and persistence. The published Builder capability ID
`unrestricted-javascript` is an explicit compatibility alias for
`openallay:unrestricted_javascript`. Both evaluate the same persistent setting;
reports and changes preserve the declared ID. Both require the same explicit JVM
consent and route to the existing unrestricted setting owner. No other ID spelling
is normalized or granted, and unrelated unknown IDs remain unknown.
Enabling unrestricted JavaScript must show
its actual JVM authority warning, not grant it as a hidden transitive step.
Dependencies not installed/unsupported cannot be enabled; show that fact and
leave Continue anyway available. No automatic recursive downloads, transitive
privilege grant, infinite dependency walk or silent setting fallback.

Settings writes affect future requests. Active requests retain frozen authority.
A successful enable action re-evaluates the preview; failures retain prior state
where existing atomic setting contracts require and display exact unresolved
requirements. Continuing without changes never adds capabilities to a request.

Keep requirements UI-first. Do not inject repeated server-rule, moral or generic
risk reminders into prompts or every invocation. Runtime enforcement remains
in code: server-model/callback JVM isolation, permission checks, actual missing
API failures, cancellation and evidence boundaries are unchanged. A Skill can
explain APIs but cannot grant them. The existing builtin unrestricted guidance
still describes only genuinely enabled request authority under033; arbitrary
advisory metadata does not automatically expose that builtin as enabled.

## States and tests

Preview states: preparing -> ready -> applying requirements/re-evaluating ->
ready, or publishing/cancelled/failed. Confirm tokens bind candidate identity
and preview generation. No mutation at preview, cancel or Continue anyway
except the explicit requested installation/enable of the selected package.

Tests cover metadata roundtrip/unknown fields/version rejection, requirements
never changing runtime availability, installed/missing/disabled/unknown needs,
Continue anyway on unmet requirements, cancellation with no publication,
changed candidate/preview races, explicit grant success/failure, English and
Simplified Chinese UI keys and loader-equivalent artifact metadata.
