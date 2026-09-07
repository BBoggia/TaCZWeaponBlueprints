# Share and install a custom Tech Tree

Open **Project → Export** in the Research Designer. Review the required
profile, dependencies, settings, and compatibility notes, then choose **Export
files**. The Designer validates the draft and reads the generated resource bytes
back through the normal research codecs before reporting a local export.
Unresolved errors and unfinished field inputs block export.

Exports appear under `taczweaponblueprints/designer/exports/<name>/` in the game
folder. Choose a new name for each export; existing exports are never overwritten.

| File | Purpose |
| --- | --- |
| `datapack.zip` | Installable research definitions and `pack.mcmeta`. |
| `editable-project.zip` | Portable source project, current recovery checkpoint, and captured context when present. |
| `export-review.json` | Required profile, dependencies, captured settings, source assumptions, validation notices, and datapack checksum. |
| `INSTALL.txt` | Installation and activation instructions. |

The datapack uses Minecraft 1.20.1 pack format 15. It contains only definitions
owned by the project's namespace. TaCZ gun packs, external definitions, and other
mods remain dependencies. Supply custom artwork or translations in a separate
resource pack. The server does not need the editable project bundle.

## Import an editable project

In the project chooser, select **Import project**, then **Open folder**. Put the
editable project ZIP in that imports folder and enter its filename, including
`.zip`. Select **Review bundle**, inspect its title and contents, then select
**Import as new project**. If the ZIP changes after review, review it again before
importing; this includes changes to recovery text or captured context.

Import creates a new local project identity. Stable node, blueprint, and resource
IDs retain their meaning. The current source and recovery checkpoint are included;
session Undo history, camera position, and synthetic player scenarios are not.
Captured context travels with the bundle, so reopening does not need the original
host's capture folder. Captures can become stale when server settings or content
change. Project bundles reveal full authored information, including hidden
blueprints; share them with that in mind.

Unsupported versions, unexpected files, duplicate entries, invalid checksums,
unsafe paths, symlinks, and oversized archives are rejected. Import never extracts
archive paths into arbitrary locations or overwrites an existing project.

## Activate the datapack on a server

A successful export does not mean a server has activated it.

1. Back up the destination world. Install matching versions of [TaCZ] Weapon
   Research & Blueprints, Timeless and Classics Zero (TaCZ), and the dependencies
   listed in the review.
2. Copy `datapack.zip` to the world's `datapacks` folder. Enable it and check
   `/datapack list enabled`.
3. Resolve conflicting resource IDs and pack priority. Remove superseded copies
   of the same project. A higher-priority pack can replace its definitions.
4. Set `activeResearchProfile` to the exact profile shown in the review and match the
   required settings. Review changed presets, exemptions, tags, and dependencies.
5. Reload datapacks, inspect server logs, and check the Research Bench in a test
   world. Invalid or conflicting research data can leave the last valid
   publication active; the absence of a crash does not prove activation.

External progression gates, inventory delivery, and survival resource supply are
not proven by detached export validation. Test them on the destination server.
The Designer only writes local exports; it never installs a pack into the
connected world or changes server settings.

## Archive contract

Generated definition JSON uses canonical object ordering; ZIP entries are sorted,
with fixed timestamps and uncompressed storage. Re-exporting identical semantic
resource content produces identical datapack bytes. Project/capture identity and
other review metadata are kept outside the datapack.

Bundles use a versioned manifest with SHA-256 checksums. Import review records
the SHA-256 of the entire archive, and import checks that same digest before
writing the new project. Informational compiler notices do not block export;
errors do.

ZIP input and total expanded data are limited to 96 MiB, individual entries to 32 MiB, and entry count
to 16,384. Exported research definitions have a stricter 2,000,000-byte limit.
Multi-disk and ZIP64 archives are unsupported. These limits protect local import;
checksums detect corruption and are not a signature of the author's identity.

Files are written into a temporary export directory and flushed before the final
directory is published. A failed write does not report success or expose a partly
written final export. A process crash may leave a hidden temporary directory,
which is not treated as an export.
