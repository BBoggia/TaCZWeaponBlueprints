# Research economy compatibility inputs

These inputs were frozen on 2026-09-05 before implementing physical Scrap and randomized Research Data. Tests read them independently of the current writers. Do not regenerate them to make a failing compatibility test pass.

- `player-v5.snbt` and `analysis-inventory.snbt` are unchanged outputs from the completed disposable dedicated-server Analyzer acceptance run. They contain 137 RP, learned CZ75 knowledge, ordinary AA12 fragments, all four AWM analysis classes, and finite award history. Recent unlocks include targets from earlier fixture preparation; they are retained deliberately.
- `ordinary-item-tags.snbt` retains the older ordinary fragment, blueprint, and weapon-origin samples.
- `profile-legacy-v4.json` and `profile-tiered-v5.json` are copies of the production legacy and packaged opt-in tiered profiles.
- `awards/` contains all 15 production award definitions, including deterministic Note/Report/Dossier values of 1/3/6.
- `config-default-v4.toml` was serialized once from the pre-change configuration. `config-custom-v4.toml` copies only representative scalar settings from the development configuration. Neither is loaded as the user's live configuration by these tests.
- `preview-*-v59.hex` and `result-*-v59.hex` were produced once by protocol-59 writers from constructed representative quotes/results. They are binary payloads encoded as hexadecimal, not newly captured network traffic. Tests decode their player-visible meaning. When the protocol intentionally changes, preserve these bytes and explicitly adapt the compatibility test boundary; they do not require mixed-version connections.
- `pinned-economy.json` captures the current production tree builder using the existing pinned TaCZ 1.1.8 recipe/capability test inputs: 172 catalog entries, 53 enabled weapons, and 398 RP of enabled weapon costs. It is not a measurement of every installed content pack or an authoritative live-world export. The existing `DefaultTaCZResearchTreeTest` validates that builder independently.

`manifest.json` records source-byte checksums. Ordinary test/build execution never writes these inputs. Capture utilities and native screenshots are local development evidence and are not required by tests or the build.

The tests preserve old data meaning; deliberate new defaults belong in separate tests. Future readers should still preserve old balances, claims, and fragment permissions even when physical currency is active. The target implementation contract is in `docs/research-currency-contract.md` at the repository root.
