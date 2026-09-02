# Capability-v3 automatic weapon scoring

Capability v3 is the packaged automatic-placement scoring model. It is a
versioned, non-authoritative interpretation of TaCZ gun data: research rules
still own cost, visibility, eligibility, and authored prerequisites, while an
exact authored Tech Tree placement still owns its position.

## Evidence and normalization

The runtime adapter and offline reference generator capture the same bounded
evidence. Both paths deserialize TaCZ's `GunData` and use one normalizer, so
omitted fields receive TaCZ's real defaults instead of different offline
guesses. In addition to the mechanical-v2 fields, v3 records projectile count,
damage retention, projectile gravity, explosion damage/radius/fuse and
knockback, entity ignition, tactical reload, per-fire-mode adjustments, burst
count/BPM/minimum trigger interval, heat duty-cycle data, and charge behavior.
TaCZ explosion delay is already expressed in seconds and describes the
automatic mid-air fuse, not impact latency. It remains diagnostic evidence and
is not scored because neither a shorter nor a longer fuse is universally
stronger. Entity ignition duration is also expressed in seconds. TaCZ divides
shotgun damage across its spawned pellets, so projectile count never multiplies
impact damage or sustained DPS.

Every supported fire mode is evaluated independently. Additive damage, RPM,
projectile-speed, armor-ignore, headshot, and aimed-inaccuracy adjustments are
applied before the strongest valid capability is selected. Burst cadence uses
the larger of its minimum start-to-start interval and the time needed to emit
the burst; it is not unconditionally layered over automatic RPM. Charge
profiles distinguish AUTO/DELAY maximum charge from HOLD's minimum firing
threshold, account for post-shot charge loss, and respect whether recharging
can overlap the ordinary fire cooldown. Heat-limited sustained pressure uses
TaCZ's RPM interpolation, average heated aimed-inaccuracy modifier, and its documented
`cooling_multiplier * seconds^2` recovery curve plus the overheat lock.

Unknown values remain missing instead of being invented. Missing applicable
metrics use the pinned reference median, reduce confidence, and appear in
diagnostics. Non-applicable explosion metrics do not penalize ordinary guns.
Script-controlled weapons retain a 50% confidence ceiling and explicit review
reasons.

## Capability packages and progression

Percentile-normalized metrics feed six explainable packages:

- lethality;
- sustained pressure;
- precision and reach;
- area control (explosive weapons only);
- handling; and
- versatility.

Armor ignore and entity penetration are deliberately separate. Armor ignore
contributes to lethality; `pierce` is the number of targets a projectile can
continue through and contributes to versatility.

Combat strength rewards the strongest supported capability without letting a
single statistic erase every tradeoff: 55% strongest combat package, 25%
second strongest, and 20% mean combat package. The raw final blend is 80%
combat, 15% handling, and 5% versatility. A pinned linear calibration maps the
otherwise compressed raw blend onto the 0–100 progression range:

```text
progression = clamp(round((raw - 25) * 5 / 3), 0, 100)
```

This calibration affects vertical progression only. Branch identity remains a
strength-relative role vector derived from v3-normalized metrics, keeping
weapon role separate from aggregate strength. Its stable topology slots blend
related v3 metrics, so explosion damage/radius/control, damage retention,
gravity, projectile count, charge, and target penetration participate instead
of silently falling back to the older mechanical subset.

## Versioning, selection, and rollback

The model and reference identities are `tacz-gun-capability-v3` and
`tacz-1.1.8-capability-v3`. The checked-in 53-gun reference is pinned by source
and metric SHA-256 fingerprints and loaded through a strict bounded parser.
Regenerate and compare it with:

```text
./gradlew generateCapabilityWeaponReference
./gradlew generateCapabilityComparisonReport
```

Automatic-placement format 4 adds `scoring_model`. Existing format 1–3 files
and format-4 files that omit the field use `mechanical_v2`, preserving custom
datapack behavior. The packaged profile opts into `capability_v3`. Rollback is
therefore a datapack-only change: set `"scoring_model": "mechanical_v2"` and
reload. No player knowledge, RP, item, packet, or save migration is involved.
Capability-reference or per-weapon v3 scoring failures are isolated from the
mechanical-v2 publication, and diagnostics keep distinct default v2 and v3
placement plans. A v3-selected profile treats a missing v3 score as unavailable
evidence and uses its existing conservative handling rather than corrupting the
v2 rollback path.

Operator status and per-blueprint inspection expose the selected model,
formula, and reference. Catalog exports include v3 score, confidence, suggested
tier, warnings, and authored-tier divergence. An authored position differing
from the v3 suggestion by at least two legacy tiers is review-marked but is
never moved automatically. The bundled M320 was separately reviewed and
authored into Advanced rank 5 behind the AUG and M870; that deliberate datapack
migration fixes the original early-launcher exception without weakening
authored authority for third-party trees.

The comparison report uses format 2 and records formula and reference identity
in four separate fields. Reference and comparison writers use same-directory
temporary files followed by an atomic replace where the filesystem supports it.
