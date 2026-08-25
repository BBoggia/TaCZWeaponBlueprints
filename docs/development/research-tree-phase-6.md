# Research Tree Phase 6: Default Content and Authoring Support

Date: 2026-08-24

Phase 6 turns the Research Bench canvas into a useful default progression. The
pack now targets every gun in the bundled TaCZ 1.1.8-hotfix catalog exactly once
and organizes them into seven independent branches: pistols, SMGs, shotguns,
rifles, snipers, machine guns, and launchers.

Known TaCZ guns request `full` visibility so the server-wide maximum can select
any of the five disclosure tiers while keeping the complete progression
present on the canvas before discovery. Costs rise from 4 RP at roots
to 12 RP for the deepest weapons. The definitions use only datapack resources;
servers can replace or extend them without a code build.

Unknown content-pack blueprints are deliberately not classified. They inherit
the active profile, retain an empty prerequisite list, and remain accessible
through the established discovery fallback. This prevents a new add-on from
being silently locked behind a fictional dependency.

`BlueprintResearchDiagnostics.audit` now resolves the authored profile against
the live catalog and reports assignment coverage, roots, leaves, connected
components, independent entries, missing prerequisites, hidden prerequisite
paths, and competing rule selections. Reload logging reports structural
problems, while `/gg research status` and `/gg research inspect` expose bounded
operator views. `/gg research export` atomically writes a sorted authoring
catalog inside the world directory.

The standalone example datapack and `docs/research-tree-authoring.md` document
profile creation, exact branches, precedence, third-party fallback, validation,
and the authoring workflow.
