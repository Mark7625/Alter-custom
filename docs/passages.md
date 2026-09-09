# Generic passages

Doors, gates, ladders, staircases, trapdoors, cave mouths, stiles and the Wilderness ditch that
no dedicated script claims are handled by `content/generic/generic-locs` under `locs/passages`.

The data-driven [door](doors.md), [gate](gates.md) and ladder scripts keep priority: the generic
handler is registered on the *default* loc op events, which the engine only fires when a loc op
has no type, content-group or category handler.

## What it does

| Loc name / op | Behaviour |
|---|---|
| `Door`, `Gate`, `Large door`, ... with `Open` / `Close` (or any wall-shaped loc) | Finds the open/closed twin by name, size and first op within a few ids. A closed panel with a matching panel beside it is one half of a double door or a two-panel gate and both halves swing together, using the `DoubleDoorScript` and `PicketGate` geometry. Opened panels are remembered until they reset, so closing puts the originals back exactly. No twin: the door is removed for 500 cycles instead. |
| `Trapdoor` with `Open` | Swaps in the open trapdoor (twin with `Climb-down`) or climbs straight down. |
| `Ladder`, `Staircase`, `Stairs`, `Rope`, ... with `Climb-up` / `Climb-down` / `Climb` | Moves one level, or between the surface and the dungeon copy of the map 6400 tiles north. Lands on the nearest free tile so a blocked ladder tile puts the player beside it. `Climb` asks which way. |
| `Cave`, `Cave entrance`, `Tunnel`, `Hole`, `Passage`, ... with `Enter` / `Exit` / `Leave` | Same surface/dungeon swap as ladders. |
| `Stile`, `Fence` with `Climb-over` | Hops to the tile past the far edge. |
| `loc.ditch_wilderness_cover` `Cross` | `WildernessDitchScript`: jumps the ditch to whichever side the player is not on. |
| Passages listed in `LinkedPassages` (e.g. the Wilderness Slayer Cave entrances) | `LinkedPassageScript`: explicit two-way links for caves whose far side is not 6400 tiles away. Add an entry there when the generic rule strands a player. |

Anything else still gets the engine's "Nothing interesting happens." message. A guessed landing
is refused when nothing (no wall, loc or blocked tile) exists within 8 tiles of it, since that is
the black filler of a map square rather than a room.

The rules themselves (`Passages.kt`) are pure functions with unit tests; the script only wires
them to the map and the loc registry. When a specific door, ladder or cave needs exact behaviour,
give it a content group or a `loc.toml` entry and the generic path stops applying to it.

## Teleport jewellery

`content/travel/jewellery` covers amulets of glory, rings of dueling and wealth, games, skills,
passage and digsite necklaces, combat bracelets, burning amulets and slayer rings. Destinations,
charge sequences and Wilderness limits live in `JewelleryTeleports.kt`; a destination with
`coords = null` is one whose content is not on the server yet and tells the player so.
