# Progression Gate criterion API

Progression Gates let integrations record durable player milestones without
directly teaching a blueprint. A completed criterion only satisfies the matching
gate condition; normal Tech Tree paths, Research Bench tiers, Research Points
(RP), and material costs remain separate requirements.

The API is server-only and player-scoped. Call it from the Minecraft server
thread with a `ServerPlayer` and a namespaced criterion ID.

## Recording progress

Use `com.gamergaming.taczweaponblueprints.api.ProgressionCriteria`:

```java
ResourceLocation trial = new ResourceLocation("example", "rifle_trial");

// Idempotent: raises progress to at least 1.
ProgressionCriteria.ChangeResult granted =
        ProgressionCriteria.grant(player, trial);

// Idempotent for the same or higher saved value.
ProgressionCriteria.ChangeResult qualified =
        ProgressionCriteria.grant(player, trial, 3);

// Adds progress and saturates at the hard saved-data limit.
ProgressionCriteria.ChangeResult advanced =
        ProgressionCriteria.increment(player, trial, 1);
```

`grant` treats its value as a minimum. Calling `grant(player, trial, 3)` when the
player already has 5 keeps the value at 5 and returns `UNCHANGED`. `increment`
always adds its positive amount unless the counter has reached its limit.

Inspect progress without changing it:

```java
ProgressionCriteria.Inspection inspection =
        ProgressionCriteria.inspect(player, trial);
if (inspection.successful()) {
    int current = inspection.value();
}
```

Check the returned status instead of assuming a change committed. Calls reject
client-side players, off-thread access, unavailable player data, invalid amounts,
full criterion storage, canceled events, and stale transitions. A successful
no-op reports `UNCHANGED`; `changed()` is true only for `APPLIED`.

## Clearing progress

Gameplay integrations should grant progress rather than revoke it. The explicit
administrative method exists for operator tools and migration or recovery code:

```java
ProgressionCriteria.clearAdministratively(player, trial);
```

Command implementations should use `clearFromCommand`, which requires permission
level 2 and verifies that the command source belongs to the player's server.
Clearing one criterion does not affect blueprints, discoveries, RP, fragments,
advancements, or other criteria.

## Forge events

Every real transition posts two server-side Forge events:

- `ProgressionCriterionChangeEvent.Pre` is cancellable and fires after capacity
  preflight but before player data changes.
- `ProgressionCriterionChangeEvent.Post` is immutable and fires only after the
  exact transition commits.

Both events expose the player, criterion ID, operation, operand, previous value,
and resulting value. Listeners cannot rewrite those values. If a listener changes
the same criterion during `Pre`, the outer request becomes stale and does not
silently commit a different transition.

Idempotent `UNCHANGED` calls do not post events. Canceled and failed calls do not
post `Post`.

## Advancement and workstation conditions

Vanilla advancement conditions are read from the player's current advancement
progress when a gate is evaluated. The evaluator checks only the advancement IDs
in that blueprint's applicable gate policy, up to the policy limit of 64 total
conditions. Historic advancements therefore work after login or datapack reload,
and revoked advancements stop satisfying later checks without a second saved
ledger or a world-wide background scan.

Workbench conditions use the authoritative workstation context supplied by the
open server menu. A context for research cannot satisfy a crafting condition, and
a client-provided tier is never authoritative.

Gate evaluation returns only unmet groups and disclosure-safe hints. Conditions
marked `hidden` retain their translation key and broad condition type, but never
expose their criterion or advancement ID, current counter, target counter, or
required tier through the result.

Server integrations can evaluate the current revision-matched policy with
`ProgressionGateEvaluator.evaluateBlueprint`:

```java
ProgressionGateEvaluation result = ProgressionGateEvaluator.evaluateBlueprint(
        player,
        blueprintId,
        ResearchInteractionMode.RESEARCH,
        authoritativeWorkbenchContext);

if (result.satisfied()) {
    // The Progression Gates passed. Other research requirements remain separate.
}
```

An unavailable policy, player capability, server thread, or advancement state
returns a typed blocked result with no policy details. This is a query only; it
does not teach a blueprint or consume progress.

## Operator commands

All commands require permission level 2:

```text
/gg progression criteria inspect <player> <criterion>
/gg progression criteria grant <targets> <criterion> [value]
/gg progression criteria increment <targets> <criterion> <amount>
/gg progression criteria reset <targets> <criterion>
```

The existing `/gg progression reset <targets> criteria` command clears every
custom criterion for the selected players. Neither command can revoke a vanilla
advancement.

## Limits and compatibility

- Each player can retain at most 4,096 custom criterion IDs.
- Values range from 1 to 1,000,000,000; zero is stored as absence.
- IDs use canonical Minecraft resource locations and are limited to 256
  characters.
- Criterion changes use compare-and-set commits and schedule one coalesced player
  progression update.
- The API does not scan all players or the world every tick.

Future Weapon Trials should call this API with their own stable criterion IDs.
They should not write player NBT, open Research Bench menus, or teach blueprints
directly.

## Weapon Trials integration contract

A trials module should own a stable, namespaced criterion ID for each durable
objective family. Record progress only when the server has authoritatively
accepted the gameplay event. Use `grant` for one-time or threshold achievements
and `increment` for counters:

```java
public static final ResourceLocation RIFLE_TRIAL =
        new ResourceLocation("example_weapon_trials", "rifle_hits");

// Called from the server-side hit handler after the hit is accepted.
ProgressionCriteria.ChangeResult result =
        ProgressionCriteria.increment(player, RIFLE_TRIAL, 1);
if (!result.successful()) {
    // Log or defer according to result.status(); do not award the hit twice.
}
```

The integration must not cache a Research Bench decision. Criterion changes
schedule a coalesced progression update, while the next server-side research or
crafting evaluation reads the current criterion value and the current policy.
This keeps trials independent from datapack reloads, workstation changes, and Tech
Tree layout changes.

Treat `APPLIED` as a committed mutation and `UNCHANGED` as a successful,
idempotent no-op. Do not retry `increment` after an ambiguous caller-side error:
the API returns `APPLIED` even if a post-commit observer fails. Calls must remain
on the owning Minecraft server thread, and trial listeners must not use the
administrative clear method during normal play.
