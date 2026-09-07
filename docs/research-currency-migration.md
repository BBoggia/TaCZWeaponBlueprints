# Research currency migration

Server operators can inspect saved Research Points (RP), retain banked research,
or explicitly withdraw RP into the selected physical currency. Upgrading does
not withdraw RP, exchange existing items, or reissue previously claimed rewards.

## Configuration and upgrades

Physical Scrap and Research Data analysis are the defaults. New files and older
files missing the economy selectors use those defaults; explicitly saved
selectors take precedence. Other configuration values and saved RP are preserved.
A saved RP balance never selects a mode implicitly. To preserve the old economy
from first startup, stop the server and explicitly set these root fields in
`config/taczweaponblueprints/blueprint.toml` before starting the new build:

```toml
researchCurrencyKind = "BANKED_RP"
researchDataBehavior = "LEGACY_CURRENCY"
```

The preset command below is available in the new build. It can be run from the
server console after updating and before players join. Older releases do not
have this command. See [configuration](research-currency-configuration.md) for
other combinations, including banked RP with Data analysis.

To keep banked research explicitly, run:

```mcfunction
/gg progression currency legacy apply
```

This writes `researchCurrencyKind = "BANKED_RP"` and
`researchDataBehavior = "LEGACY_CURRENCY"`, republishes the settings, and reports
whether the saved values were verified. It preserves the currency item ID and
all other settings. Existing items, learned blueprints, discoveries, fragment
archives, recipes, and award claims remain intact. Server startup reports the
active modes and the inspection/withdrawal commands.

## Inspect and grant

```mcfunction
/gg progression currency inspect PlayerName
/gg progression currency give PlayerName 100
```

Inspection reports the active currency and available amount, the separate saved
banked RP balance, and the Data mode. Currency grants create new value; they do
not debit saved RP. The existing `points give` command still grants banked RP.
All commands require operator permission level 2. Player-targeting commands use
online players; `give` also accepts a player selector. `legacy apply` is a
server-wide settings change and has no player target.

## Withdraw saved RP

Select `PHYSICAL_ITEM` and a valid `researchCurrencyItem` in the server Blueprint
configuration first. Then preview a requested amount:

```mcfunction
/gg progression currency withdraw preview PlayerName 100
```

The preview reports the destination item, amount, initial and retained RP, and a
confirmation command containing a unique token. It changes no items or balance.
The same operator must confirm within 60 seconds of server ticks:

```mcfunction
/gg progression currency withdraw confirm <token>
```

Each RP delivers one selected currency item. One request accepts 1–2,304 units,
subject to the actual item stack limit and available inventory space. The whole
requested amount must fit in the main inventory and hotbar; withdrawal does not
use armor, offhand, an open container, or dropped overflow. To withdraw less,
make a new preview for that smaller amount. No automatic partial withdrawal is
performed. Alternate currency items receive the same 1:1 conversion rate.

The confirmation rechecks the live player identity, saved balance, inventory,
and active currency policy. Changes invalidate the preview before payment.
Switching the currency away and back also invalidates it. A confirmation is
consumed before its transaction runs, including stale or failed attempts. A new
preview for the same player replaces the previous one. Disconnect and server
shutdown clear pending previews; they are never persisted. Only 256 pending
previews are retained, with the oldest evicted when necessary.

The successful transaction delivers items and subtracts exactly that amount of
banked RP. The report includes retained RP, and the server log records the token,
operator, player UUID, requested amount, and transaction result. Knowledge,
archives and award ledgers are not participants in the withdrawal and remain
unchanged. Online-player commands reject offline names; offline balances stay in
their saved data until the player connects. A repeated confirmation cannot
perform a second withdrawal. A new preview is a new explicit withdrawal.

## Failure and recovery

Ordinary delivery failures restore the transaction's inventory and RP changes.
If another mod prevents verified restoration, the command reports a distinct
restoration failure; inspect the inventory and saved RP before another attempt.
The transaction does not invoke gameplay award claims or mint a pending balance.

These guarantees apply to an executing server transaction. They do not make
Minecraft's separate player/world saves atomic across a process crash. Back up
world and player data together before a bulk administrative migration. Restoring
an old backup intentionally restores its old RP and items. A fresh withdrawal
can then spend that restored RP; no claim of administrator-rollback duplication
protection is made. Changing the configured item does not transform old physical
currency, and there is no automatic item-to-RP deposit.

## Compatibility

Player data stays at version 5 and network protocol at 62. Existing player readers
and item identity rules remain in use. Clone and reconnect copy the saved state;
neither performs migration. Historical configuration values remain readable.
Banked-only Java award APIs continue to reject physical mode explicitly; use the
active-currency API described in [currency integrations](research-currency-integrations.md).
