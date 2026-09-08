# Station setup and research access

Open the server's blueprint settings and expand **Stations & Research Access**.
Changing these settings requires operator permission level 2. They apply to
all players on that server. Client and server mod versions must match.

## Analyzer setup

**Tiered Analyzers** uses the physical station's tier and the weapon's tier to
choose a reward table. **Single Analyzer** uses the weapon's same-tier table at
any Analyzer: a Tier 3 weapon uses the Tier 3 / Tier 3 row even at the basic
station. Explicit disabled rows, origin restrictions, costs and reconstruction
rules still apply. Minimum Analyzer-tier requirements are satisfied in Single
Analyzer mode. These modes do not change weapon tier assignments.

**Single Analyzer: Research Data rewards** chooses which station tier's Data
reward tables to use. It defaults to Tier 1 rewards. Notes, Reports and Dossiers
keep their configured target pools; this choice changes rewards, not target
selection. It has no effect in Tiered Analyzers mode.

Datapack authors can continue overriding reward tables, costs, fragment counts
and eligibility. The station setting chooses which table is evaluated; it does
not replace that table's contents. Legacy analysis profiles keep legacy behavior.

## Research access

- **Tiered Research Benches:** Enforce the required bench tier, independently of
  the research portion of the existing progression preset.
- **Single Research Bench:** Any Research Bench supports every research tier.
- **Portable Research — no bench required:** Use the Journal / Research key to
  open the Tech Tree from gameplay. The J button in the tree opens the Journal.
  Crafting and Analyzer actions still require their own stations.
- **Use existing progression preset:** Preserve the previous preset and custom
  research-tier enforcement behavior. Existing configuration files migrate to
  this compatibility choice; fresh installations default to Tiered Benches.

Single and Portable modes satisfy normal research-tier checks and explicit
bench-tier progression requirements by providing Tier 3 research access. Other
requirements, including advancements, custom milestones, costs and prerequisite
blueprints, still apply. Crafting Workbench requirements are independent.

## Changing modes

The server closes open research stations and reloads data to refresh recipes
when these choices change. If the reload fails, fix the reported data error and
run `/reload`. Single modes keep only basic station recipes; Portable mode omits
Research Bench recipes. Existing blocks and items remain usable, and datapacks
can supply additional recipes without the bundled station condition.

Portable research uses an authenticated server menu. Walking away is allowed;
changing dimension, dying, losing portable access or disabling research ends its
validity. A client cannot authorize research by inventing a station or session.

Research Designer captures include these choices for offline analysis and
installation context. Older captures retain their previous station behavior.
Scenario checks still report external progression gates as unassessed when they
require world information.
