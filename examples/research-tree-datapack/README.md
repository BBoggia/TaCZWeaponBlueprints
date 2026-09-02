# Research Tree datapack example

This example shows how a pack author can define a weapon-only Tech Tree,
research rules, an automatic placement profile, and optional authored entries
for an add-on gun pack.

The placeholder namespace `example_guns` is not real content. Replace every
placeholder item or blueprint ID with IDs from the packs installed on your
server. Run `/gg research export` in a test world to obtain the loaded catalog
and its exact IDs.

## Try the example

1. Copy this directory into a test world's `datapacks` directory.
2. Replace the `example_guns` IDs and translation keys with valid values.
3. Run `/reload`, then `/gg research status` to verify the selected profile and
   tree.
4. Use `/gg research inspect <blueprint_id>` to check individual selections.

Do not ship the example unchanged. Invalid placeholder IDs are rejected, and a
rejected reload keeps the last working research data active.

See [Research Tree datapack authoring](../../docs/research-tree-authoring.md)
for the supported formats, automatic-versus-authored behavior, validation
rules, and migration guidance.
