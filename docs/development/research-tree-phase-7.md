# Research Tree Phase 7: Hardening, QA, and Release Preparation

Date: 2026-08-24

Phase 7 hardens the completed Research Bench feature line without changing its
datapack formats or persistent player schema.

Every client-requested menu action now passes through one pure
`ResearchBenchActionValidator` before the menu mutates state. Selection is
accepted only in Browse; research and filling require the exact prepared
selection; recycling requires the exact physical duplicate ID; and mode changes
reject unrelated blueprint payloads. Existing transaction services still
re-resolve policy and perform their own atomic validation at commit time.

`ResearchTreeNavigator` adds deterministic keyboard traversal. Up and Down
prefer actual prerequisite/dependent edges, while Left and Right traverse the
current visual tier. Search results can be cycled with Up/Down and activated
with Enter. Mouse behavior and server-authoritative selection remain unchanged.

Hardening coverage now includes conflicting synchronization chunks, duplicate
node ordinals, completed-total mismatches, every menu-action/mode combination,
keyboard traversal of branches and merges, and a five-second ceiling for laying
out the configured 4,096-node maximum on the test environment.

The wire protocol remains `11`. No player-world migration is required: learned
blueprint IDs, discovered IDs, Research Points, and legacy recipe aliases retain
their existing serialized forms. Both sides must update together because older
clients do not understand the current menu, preview, and tree packet shapes.

`docs/research-tree-manual-qa.md` separates the remaining real-client checks
from automated evidence and includes GUI scales, small windows, localization,
mouse and keyboard input, narration, two-player state, reloads, content-pack
removal, transactions, model orientation, and the release screenshot shot list.
The CurseForge-ready copy is maintained in `docs/curseforge-description.md`.
