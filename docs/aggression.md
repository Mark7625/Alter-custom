# Monster aggression

Which monsters attack players on sight, and when they stop.

## Rules (as in OSRS)

- An aggressive monster attacks players whose combat level is at most twice its own. A level 28
  Hill Giant attacks players up to level 56; level 63 and above monsters attack everyone.
- Inside the Wilderness the level rule is ignored: aggressive monsters attack every player.
- After ten minutes in the same map square a player is *tolerated* and monsters that use the level
  rule leave them alone until they leave the square and come back. Monsters that hunt everyone
  regardless of level (bosses, God Wars creatures, the always-aggressive kinds) never tire.

## Where it lives

| Piece | Location |
|---|---|
| Which monsters are aggressive | `content/other/npc-aggression/src/main/resources/npc-aggression.toml`, generated from the wiki infobox `aggressive` field of every attackable npc name |
| Hand corrections | `npc-aggression-overrides.toml` next to it (`aggressive = false` silences a generated entry) |
| Assigning hunt modes at boot | `NpcAggressionScript`: `stalk.constant_melee` for melee monsters, `stalk.constant_ranged` for ranged and magic ones, skipped for npcs whose own config already sets a hunt mode |
| Level rule and Wilderness override | `NpcPlayerHuntProcessor` (engine), driven by the hunt mode's `checkNotTooStrong` |
| Tolerance timer | `AggressionTolerance` (engine), ticked from `PlayerPostTickProcess` |

The hunt modes themselves are defined in `.data/raw-cache/server/hunt.toml`; the `stalk.*` modes
carry the level check and tolerance, the `aggressive_*` modes do not.

## Regenerating the list

The generator queries the wiki API for the wikitext of every attackable npc name from
`osrs-dumps/dump.npc` (batches of 50 titles, one request every half second) and reads the
`aggressive`/`id` pairs from each `Infobox Monster` version. Any value starting with "Yes" counts
as aggressive; the rest of the value is kept in `note` when it names a condition the server does
not model yet (an item that pacifies the monster, a quest that removes the aggression).
