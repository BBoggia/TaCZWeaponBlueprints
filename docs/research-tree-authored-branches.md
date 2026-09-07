# Authored Tech Tree branches

Pack authors can give the Tech Tree named specialties, nested sub-branches, and
stable sibling ordering. The client places the regions and routes the actual
prerequisite connections between them. Branch membership is presentation data:
it does not grant research, create prerequisites, change costs, or override
an automatic weapon-placement profile.

The [example datapack](../examples/authored-branches-datapack/README.md) contains
a shared foundation, two specialties, a nested split, a shared later unlock,
and a cross-branch OR requirement.

## Definitions and membership

Use tree format **3** in `research_tech_trees/<tree>.json`. All format-2 tree
fields retain their meaning. Add an optional `branches` array:

```json
{
  "id": "example:smg",
  "domain": "weapons",
  "parent": "example:foundation",
  "title": "Submachine guns",
  "translation_key": "tree.example.smg",
  "order": 10,
  "anchor": "tacz:uzi",
  "label_visibility": "anchor_revealed"
}
```

The object above is one array element. `id`, `domain`, `title`, `order`, and
`label_visibility` are required. `parent`, `translation_key`, and `anchor` are
optional. Omit `parent` for a root region. A translation key is resolved on the
client; provide that translation in a resource pack. The literal title remains
required for authoring and diagnostics.

Use placement-bundle format **3** in
`research_tech_tree_entries/<bundle>.json` for membership:

```json
{
  "format": 3,
  "tree": "example:nested",
  "priority": 0,
  "entries": [],
  "branch_memberships": [
    {"branch": "example:smg", "members": ["tacz:uzi"]}
  ]
}
```

A presentation-only bundle can have empty `entries`. Existing placement entries
and these membership records can also share one bundle. Membership uses exact
blueprint IDs from `/gg research export`; it does not use selectors, priorities,
or fallback matching. Several bundles can extend one branch with disjoint
members. Every blueprint has at most one direct owner in a tree. A parent
contains its own direct members and all child regions, so ancestors should not
repeat their descendants' members.

Branches can belong to Weapons, Ammo, or Attachments. A child must have the same
domain as its parent, and each member must resolve to that domain. Authored-only
members need a valid placement. Automatic weapons need a known catalog entry;
branch membership alone does not make an excluded automatic candidate eligible.
An optional anchor must be a member of that branch or one of its descendants.
It controls label disclosure and supplies a public representative when revealed;
it does not pin a node to coordinates or add a progression relationship.

## Layout and ordering

Sibling regions sort by `order`, then stable branch ID. Order is an integer from
0 through 1,000,000. It is an ordering hint, not an X coordinate. Parent regions
contain their children, and sibling regions receive separate space. Prerequisite
rank, node dimensions, and route clearance still constrain placement. A shared
later unlock has one owner; dependencies from other branches remain real
cross-region connections.

If a domain has published authored branches, its unassigned visible nodes use
an **Other research** region. If it has no published branch metadata, the client
uses the existing inferred layout. Merely upgrading a format number does not
change that layout. Branch labels and anchor changes refresh presentation;
changes to IDs, parents, order, or membership rebuild the scene geometry.

## Disclosure

Choose a label rule explicitly for every declaration:

| Rule | Label publication |
| --- | --- |
| `public_when_populated` | Publish title and translation key once any node in the subtree is present in the player's public graph. This explicitly allows the label even when those nodes are anonymous. |
| `anchor_revealed` | Require an anchor. Publish the label only when that anchor's identity is revealed in the player's public graph. Otherwise show a neutral **Branch** label. |

Branch IDs and parent structure are public whenever the subtree is populated,
even under `anchor_revealed`. Use neutral IDs if names would reveal a secret.
Only permitted public node identities, including anonymous placeholders, appear
in membership. Hidden nodes and entirely hidden subtrees are removed. Hidden or
anonymous anchors are removed, and cannot unlock anchor-gated labels. Published
counts and progress derive from the filtered membership, never private totals.
The operator export deliberately contains full declarations and catalog IDs;
it is not a player-disclosure view.

## Validation and limits

Validation rejects unknown fields, duplicate branch IDs, duplicate ownership,
missing branches or parents, cross-domain parents or membership, empty declared
subtrees, missing catalog members, and anchors outside their subtree. Cycles and
excessive nesting are rejected before publication. Errors identify the branch
or member; cross-file ownership errors also identify the entry bundle and tree.
Normal resource-loading diagnostics identify the file being decoded.

Limits per tree are 128 branches, 16 levels of nesting including the root, and
4,096 directly assigned nodes. Titles contain at most 80 characters; translation
keys and resource IDs at most 256. Titles must be trimmed and contain no control
characters. Translation keys cannot contain whitespace or control characters.
Each bundle has at most 128 membership records and 4,096 member references.
An empty parent is valid when a descendant has members; an empty subtree is not.
Unknown catalog entries cannot be used as speculative future membership.

## Migration, synchronization, and inspection

Tree formats 1 and 2 and placement-bundle formats 1 and 2 remain valid. Their
branch fields must be absent, including empty arrays. Upgrade only the files
that use new fields to format 3. Research-profile and research-rule versions are
separate; the example uses profile format 1 and rule format 2. Player data and
saved client settings require no migration.

The wire protocol is **56**. Clients and servers must use matching builds.
Branch parents use branch ordinals; anchors and members use graph-node ordinals.
Band references also use the existing band table's ordinals. The bounded tree
record fits the 900,000-byte chunk envelope and retains atomic publication
reassembly. Malformed indices, duplicate ownership, and invalid containment
are rejected before the client accepts a publication.

`/gg research status` reports authored branch and direct-member counts for the
active tree. The current export format **22** retains the `authored_branches`
field introduced in format 21, sorted by ID, with
the tree declarations and sorted direct `members`. The existing catalog,
prerequisite, cost, automatic-placement, and crafting-policy fields retain their
meaning. Export summaries for automatic prerequisite branches describe gameplay
generation; `authored_branches` describes visual containment.
