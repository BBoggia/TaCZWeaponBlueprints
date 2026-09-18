# Legacy Research Note exchange

Install this directory as a datapack only if you intentionally use legacy Data
currency redemption. It replaces the default Novice Research Note sale with an
explicitly opted-in row: 4 currency items per Note, with the default minimum
charge of 3 after discounts. Other Data rows retain their defaults.

Use a physical research currency and the same villager currency. With the
bundled one-item Research Note redemption reward, the minimum charge of 3 is
greater than its redemption value of 1. If your reward definitions differ, the
runtime checks their actual grouped reward total. Lowering the price until its
minimum reaches the reward suppresses the sale. Banked RP redemption cannot
qualify for this example; Emeralds and RP are different units.

This flag does not switch Data behavior. In analysis mode the ordinary protected
analysis-pool checks still apply. Use `/gg villager_trades inspect
taczweaponblueprints:ballistics_researcher/note` to review the effective row.
