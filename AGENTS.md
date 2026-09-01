# [TaCZ] Weapon Research & Blueprints Instructions

Follow the workspace writing standard at
`../Content&DocumentationStandards/WRITING_STANDARD.md` when it is available.
If this repository is checked out separately, apply the rules summarized here.

## Public names

- Product: **[TaCZ] Weapon Research & Blueprints**
- Dependency: **Timeless and Classics Zero (TaCZ)**; use **TaCZ** afterward.
- Player workstation: **Blueprint Analyzer**. Do not call it the Blueprint
  Recycler in public copy even when code or translation keys retain that name.
- Other named features: **Research Bench**, **Blueprint Journal**,
  **Research Points (RP)**, **Research Data**, and **Tech Tree**.

## Audience rules

- CurseForge copy explains exploration, blueprint learning, research,
  recycling, requirements, and modpack support before implementation details.
- README content may include operator and pack-author information, but long
  data-format, protocol, topology, and validation contracts belong in `docs/`.
- Changelog bullets record player-, operator-, pack-author-, or API-visible
  changes. Phase completion, fixture counts, audit identities, and test report
  paths belong in development documentation.
- `server-authoritative`, `atomic transaction`, `deterministic`, and
  `shortest-path research` are approved terms. Use other engineering terms only
  when the target audience needs the exact concept.

## Local development notes

- Store agent journals, phased implementation plans, numbered phase reports,
  and temporary evidence under `.local/agent-development-notes/`.
- That directory is intentionally ignored and must never be required by a
  build, test, README link, release checklist, or published document.
- Durable behavior and integration contracts belong in normally named files
  under `docs/` without phase or step numbering.
- Before adding a Markdown file containing `phase-<number>` or
  `step-<number>` in its name, move it to the local-note directory instead.

## Release consistency

- `gradle.properties` is the source for the version and metadata description.
- `CHANGELOG.md` is the release-history source.
- `docs/curseforge-description.md` is evergreen mod-page copy.
- `docs/releases/<version>.md` contains release-specific copy and is reused on
  every distribution platform.
- Verify public dependency and compatibility statements against build metadata
  and `META-INF/mods.toml`.

## Commit examples

```text
feat(research): add whole-path blueprint unlocking
fix(ui): keep the selected research route visible
docs(curseforge): simplify research progression overview
chore(release): prepare 1.3.1
```
