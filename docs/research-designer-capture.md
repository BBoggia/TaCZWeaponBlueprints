# Research Designer capture and custom-tree conversion

This document describes the common capture API and local context library.
Use **Customize server tree** or **Empty tree with server weapons** in the [Research Designer workspace](research-designer-workspace.md)
to create a local project. Export validation and server activation are separate features.

## Operator access and transfer

Full capture requires permission level 2, including in a singleplayer world.
The server checks permission before reading any source, before each chunk, and
before accepting completion. It also cancels outstanding work after permission
loss, disconnect, or timeout. Ordinary Research Bench and Journal packets do
not grant access to this context. Denied replies contain no content IDs, rules,
costs, fingerprints, or content totals.

Network protocol **63** adds separate capture request/reply packets. Clients
and servers must use matching builds. Requests carry a UUID, operation, and
chunk index. A receiver must acknowledge each 24 KiB chunk; the last chunk is
followed by an explicit completion request. The client checks the complete byte
checksum before asking for completion, and exposes a capture only after the
server's final authorization reply.

One player may hold one transfer; at most two transfers are retained by the
server. Starts are limited to one per five seconds per connected operator.
Captures are limited to 32 MiB, 4,096 catalog entries, and 1,000 conversion
notices. Oversized captures fail; they are never truncated. Transfers expire
after 30 seconds without activity or three minutes overall. Out-of-order or
mixed chunks fail closed. Errors are bounded status codes, without server
exception messages or resource details.

## Coherent, detached context

`ResearchDesignerCaptureService` runs on the server thread and acquires the
existing `CURRENT_ONLY` policy publication. It never rebuilds policy, reloads
packs, installs definitions, or writes server files. It checks the publication
identity, active profile, settings, recipe identity, and registry facts again
after assembling the document. A changing context is retried once; otherwise
the client receives a retry status.

The context contains:

- The selected profile, tree, rules, placement bundles, automatic settings,
  legacy groups, referenced blueprint tags, and applicable Research Data and
  reconstruction definitions. Definitions come from the loaded typed values;
  original JSON formatting, omissions normalized by codecs, and overridden
  lower-priority pack files are not recoverable from this capture.
- Canonical catalog IDs, recipe associations, output counts, local art
  references, and per-blueprint research, crafting, and automatic evidence.
  Entries hidden from ordinary players remain present in the operator context.
- Raw Blueprint settings plus the resolved progression, access, balance,
  award, and feature snapshots. These are activation assumptions, not changes
  to the running server.
- Recipe serializer IDs and payloads, output stack data, gun/ammo associations,
  automatic classifications and prerequisite plans, relevant item/tag facts
  and advancement criteria, and installed mod versions. These are
  captured dependency facts, never automatically executed or installed.
- Pack provenance labels without filesystem paths. The scope is the selected
  research resource closure; unrelated pack files and player data are absent.

The capture keeps its own identity and revision evidence. Its semantic hash
normalizes object-key order and decimal representation while retaining ordered
arrays. Session IDs, revision counters, and provenance labels are excluded. Source definitions,
content, recipes, relevant settings, and reference versions contribute to the
hash. This detects changed dependency context; it is not proof of policy equivalence or a signature from a trusted server.

## Explicit conversion

`ResearchDesignerConversion.createCustom` creates a new project in the chosen
namespace. Blueprint IDs remain unchanged. It preserves profile policy, makes
the tree authored, and materializes catalog nodes, research costs, generated
prerequisite groups, placement ranks/order, research tiers, and resolved fragment
thresholds. It selects research, progression, crafting, and Analyzer overrides
independently, matching their respective runtime selection paths.

Inherited visibility remains inherited so discovering a blueprint can still
reveal it. Disabled domains remain disabled. Generated prerequisites are
materialized without a temporary player's discovery filter; current exemption,
hard-block, and entry-point replacement rules still apply. The ordinary
empty-player policy projection remains separate evidence.

Authored branch membership is retained. Automatic branch positions become
named custom branches; transition spacing is re-laid out. Blueprint IDs are
never inferred from item names, and missing recipes do not create new guns.
Unplaced, omitted, exempt, and missing references are reported. Unplaced nodes
remain incomplete draft nodes until the author resolves them.

Profile-specific Research Data and reconstruction definitions are copied to
owned IDs and retargeted to the new profile. Global resources and blueprint tags
remain fingerprinted dependencies. This avoids replacing global behavior for
other profiles. Captured source rules and automatic profiles stay in the
context rather than competing with the new project's exact rules.

Conversion is **not an export certificate**. Projects retain a required runtime
equivalence check and the original context fingerprint. Detached compiler
validation assesses graph structure, references, scope/priority effects,
gates, economy behavior, and configuration assumptions before export.

## Offline context and local artwork

`ResearchDesignerCaptureStore` explicitly saves immutable captures under
`taczweaponblueprints/designer/captures/<capture UUID>.json` in the local game
directory. Save As projects can reuse that context. Loading verifies both the
UUID and project fingerprint; missing, changed, oversized, or linked files do
not silently fall back to current server state. Offline work can retain old
context as evidence while a newer capture marks it stale.

The client clears pending and cached privileged context on disconnect or
permission loss. An explicitly saved local project/context remains a local
file. Capture itself does not save either one automatically.

Art lookup uses the local TaCZ pack and resource manager, with a blueprint-item
fallback for missing content. Capture never downloads art or decodes arbitrary
server textures. The screen's renderer remains responsible for its existing
image-size bounds and resource-reload handling.


Captured registry facts now include positioned automatic candidates and weapon
scoring evidence for detached progression and crafting comparisons. These are
value records; no recipe is executed while reopening the context. Older captures
remain readable, but comparisons requiring these additional facts explicitly
request a new capture. Captured nullable weapon measurements remain absent when
the source could not provide trustworthy values.
