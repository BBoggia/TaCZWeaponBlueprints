# Tiered Blueprint Analyzer preset

The mod already bundles this policy. To customize it, enable this datapack in a world using the active research profile
`taczweaponblueprints:duplicate_recovery`, then reload or restart. Use
`foundWeaponRecoveryMode = "PROTECTED_BLUEPRINT_ONLY"` for protected outputs.
The pack does not change global configuration.

This complete profile overrides other definitions at the same ID. Merge its
Analyzer extension into your own profile if you need to preserve custom settings.
Do not enable the legacy profile override at the same time. The optional
Analyzer override pack demonstrates exact-target and tag rules.

Disable this pack to restore the bundled tiered profile. To select guaranteed
analysis, use the legacy override instead. Existing analysis fragments and saved counts remain preserved.
