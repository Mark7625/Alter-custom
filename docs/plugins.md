# Plugins

How content plugins are discovered, loaded and wired in OpenRune, and the rules to follow so a
plugin cannot break the server it is dropped into.

Everything under `content/` is a plugin. There is no registry file, no manifest and no
`enabled = true` switch — a directory with a `build.gradle.kts` **is** a plugin, and the server
finds its code by classpath scan at boot.

---

## Quick mental model

1. **Gradle discovery.** `settings.gradle.kts` walks `content/` and includes every directory that
   has a `build.gradle.kts`. `server/shared/build.gradle.kts` then adds *all* of those projects as
   `api(...)` dependencies, so every plugin is on the server's runtime classpath automatically.
2. **Guice modules** (`PluginModule`) are loaded first, before the injector exists, and contribute
   bindings and hooks.
3. **Scripts** (`PluginScript`) are instantiated by Guice after the cache and map are loaded, and
   their `startup()` subscribes handlers to the event bus.
4. **Cache content** (`PluginPack`) is a *separate* build-time path: it packs new configs, db
   tables, interfaces, models, sprites and CS2 into the cache. It never runs on the game server.
5. **Names, not ids.** Content is referenced by RSCM strings (`"npc.hans"`, `"varp.playtime"`).
   Numeric ids for anything custom live in a plugin-local `gamevals.toml`.

---

## Boot order

From `server/app/src/main/kotlin/org/rsmod/server/app/GameServer.kt`:

| Phase | What happens | What you can rely on |
|---|---|---|
| `plugin-modules` | `PluginModuleLoader` ClassGraph-scans for `PluginModule` subclasses and instantiates them via a **no-arg constructor** | Nothing injected yet |
| `guice` | `GameServerModule` + every plugin module combined into one injector | — |
| `config` | `ServerConfig` read from `game.yml` | — |
| `cache` | `ServerCacheManager.init(revision)` | Cache types resolvable; RSCM strings resolve |
| `map` | Map decode; npc/obj spawns **queued**, not flushed | — |
| `scripts` | `PluginScriptLoader` scans for `PluginScript` subclasses, `injector.getInstance(...)` each, then calls `startup()` | Everything above |
| — | `EntityDelayedProcess.flush()` — map spawns actually fire | Your `NpcStateEvents.Create` handlers exist before any npc spawns |
| `services-start` | Network opens once `PluginScriptBootGate.markReady()` has run | No player can log in before your `startup()` ran |

Two consequences worth internalising:

- **`startup()` is registration, not gameplay.** It runs once, on one thread, with no players
  online. Subscribe handlers; do not touch player state.
- **Map spawns are deliberately deferred** so `onEvent<NpcStateEvents.Create>` in a plugin sees the
  world's initial spawns. If you register spawn handlers lazily (e.g. inside a login handler) you
  will miss them.

---

## The scan contract (easy to get wrong)

`server/shared/src/main/kotlin/org/rsmod/server/shared/PluginConstants.kt`:

```kotlin
val searchPackages = arrayOf("org.rsmod.api", "org.rsmod.content")
```

- Your classes **must** live under `org.rsmod.content.…`. A plugin in `com.myserver.foo` compiles,
  ships on the classpath, and is silently never loaded.
- `ScannerModule` additionally rejects `org.rsmod.content.*.integration` — that package name is
  reserved for test sources.
- `PluginScriptLoader` uses `getSubclasses(...)` (any depth) and skips abstract classes and
  interfaces. An abstract base script is fine; it just will not be instantiated itself.
- `PluginModuleLoader` uses `getSubclasses(...).directOnly()` and `clazz.getConstructor()`. A module
  must be a **direct** subclass of `PluginModule` with a **public no-arg constructor**. An
  intermediate abstract module class will not be found.
- Script loading is strict by default: a construction failure rethrows and kills boot. That is
  intentional — a half-registered plugin is worse than no server.

---

## Anatomy of a plugin

Minimal, complete plugin (`content/other/windmill`):

```
content/other/windmill/
├── build.gradle.kts
└── src/main/kotlin/org/rsmod/content/other/windmill/WindmillLadderScript.kt
```

```kotlin
// build.gradle.kts
plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
}
```

`api.pluginCommons` is an aggregate `api(...)` module: script DSL, player/npc api, combat, invtx,
config, repo, random, engine game/map/events. Depend on it and you have most of what content needs.
Add narrower modules (`projects.api.attr`, `projects.api.shops`, `projects.api.instances`) only when
you actually need them.

```kotlin
class WindmillLadderScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.qip_cook_ladder_top") { climbDown() }
        onOpLoc1("loc.qip_cook_ladder") { climbUp() }
        onOpLoc2("loc.qip_cook_ladder_middle") { climbUp() }
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        val dest = player.coords.translateLevel(1)
        anim("seq.human_reachforladder")
        delay(1)
        telejump(dest)
    }
}
```

That is the whole contract: extend `PluginScript`, override `ScriptContext.startup()`.

### Dependency injection

Constructor injection with `jakarta.inject.Inject`. Guice builds the script, so anything bound in
the injector is available:

```kotlin
class PlaytimeScript @Inject constructor(private val playerList: PlayerList) : PluginScript() { … }
```

`ScriptContext` itself carries only `eventBus`, `cheatCommandMap` and `engineQueueCache` — the DSL
functions are extensions on it.

### Cross-plugin dependencies

Plugins may depend on each other via `implementation(projects.content.…)`; several already do
(`skills.cooking → generic.genericLocs`, `areas.wilderness → interfaces.collectionLog`). Keep it
one-directional and prefer depending on a shared `…/utils` module over on a sibling feature.

---

## Referencing game content: RSCM

Every handler and helper takes a namespaced string resolved through `RSCM`. The valid prefixes are
the `RSCMType` enum (`or-cache/src/main/kotlin/dev/openrune/rscm/RSCM.kt`):

```
area bas category clientscript component content controller droptrigger currency dbcol dbrow
dbtable enum font headbar hitmark interface inv jingle loc mesanim midi models npc obj param
projanim queue seq spotanim stat synth timer varbit varcon varn varobj varp walktrigger
```

`"loc.qip_cook_ladder".asRSCM(RSCMType.LOC)` validates the prefix and fails loudly on a typo, at
boot, rather than silently binding to id 0. Prefer the string overloads for this reason.

Names resolve from, in order (`GameValProvider.sourceFiles`):

1. `.data/gamevals-binary/gamevals.dat` — dumped from the vanilla cache by `freshCache`
2. `.data/gamevals-binary/<generated>.dat`
3. every `gamevals.toml` under `content/`
4. every `gamevals.toml` under `api/`
5. `.data/gamevals/*.rscm`

So a plugin's own `gamevals.toml` is read directly from source at runtime — you do not need to
regenerate anything for the *server* to see a new name.

### Declaring custom ids

`src/main/resources/gamevals.toml`, one table per RSCM prefix:

```toml
[gamevals.varp]
spawn_menu = 65479

[gamevals.varbit]
spawn_include_nulls = 65450

[gamevals.interface]
spawn_menu = 1099
```

Rules:

- **Ids must be strictly greater than the vanilla max for that table.** The reserved ranges are in
  `.data/gamevals-binary/max-ids.toml` (`GameValMaxIdManifest`), regenerated by `freshCache` and
  committed so CI can detect collisions. In practice existing plugins count *down* from `65535`.
- The table name must be a real RSCM prefix; `PluginGamevalMerger` hard-fails otherwise.
- `-1` is a supported "unassigned" marker that the cache build fills in and writes back to your
  TOML — but no in-repo plugin uses it. Assigning by hand is the beaten path.
- `gradlew :or-cache:mergePluginGamevals` appends these into `.data/gamevals/*.rscm`. This is only
  needed for CS2/Neptune symbol resolution and release builds; the merger never overwrites an
  existing key, it only appends new ones.

---

## `ProtectedAccess` — the safety model

This is the single most important concept for writing correct content.

Most interaction handlers give you a `suspend ProtectedAccess.(Event) -> Unit` receiver rather than
a raw `Player`. `ProtectedAccess` is a **capability token**: holding one means this coroutine owns
the player's action slot right now.

```kotlin
public fun ScriptContext.onOpLoc1(
    type: String,
    action: suspend ProtectedAccess.(LocEvents.Op1) -> Unit,
): Unit = onProtectedEvent(type.asRSCM(RSCMType.LOC), action)
```

What it buys you:

- **Mutual exclusion.** `ProtectedAccessLauncher` refuses to start a second protected block while
  `player.isAccessProtected`, so two interactions cannot interleave and duplicate items.
- **Re-validation across suspension.** Every `delay`, dialogue and `playerWalk` suspends, and on
  resume re-acquires protected access. If the player logged out, died, was teleported, or started
  another interaction, it throws `ProtectedAccessLostException` and your handler unwinds. You do not
  have to re-check anything after a suspension — that is the point.
- **Correct UI handling.** `delay()` snapshots the open main modal and restores it on resume.

The rules that follow from this:

- **Never stash a `ProtectedAccess` reference** in a field, a map, or a lambda that outlives the
  handler. It is valid only for the current coroutine. Capture `player` (or a `PlayerUid`) instead.
- **Do all validation, then all mutation.** Anything before a `delay` may be stale after it; the
  exception protects the *player*, not your local variables.
- **`delay(n)` delays exactly `n` ticks** — this deliberately diverges from the "official"
  convention. `delay(0)` is rejected (`require(cycles > 0)`).
- **Use `arriveDelay()`** at the top of loc/npc handlers — it burns one tick only if the player
  moved last cycle, which is what the real game does.
- To *start* a protected block from unprotected code (a timer, an event, a command), inject
  `ProtectedAccessLauncher` and call `launch(player, busyText) { … }`. It returns `false` if the
  player was busy. Do not construct `ProtectedAccess` yourself; `launchLenient` is `@InternalApi`.

---

## The event DSL

All in `api/script` (and `api/script-advanced` for lower-level defaults). Roughly 200 entry points;
the naming is systematic:

| Family | Meaning |
|---|---|
| `onOpX1..5` | Player clicked option 1–5 on a loc / npc / obj / worn / held / player |
| `onApX1..5` | "Approach" variant — fired at range before walking into melee distance |
| `onOpXU` / `onApXU` | Use item **on** target |
| `onOpXT` | Cast spell / use interface component on target |
| `onOpContentX*` | Same, but keyed on a `content.*` group instead of a single type |
| `onOpLocCategoryX` | Keyed on a `category.*` |
| `onDefaultOpX` / `onUnimplementedOpX` | Fallback when nothing else handled it |
| `onAiOpPlayerX` / `onAiApPlayerX` | Npc-initiated interaction against a player |

Plus lifecycle and state:

`onGameStartup`, `onPlayerInit`, `onPlayerLogin`, `onPlayerLogout`, `onPlayerCoordsChanged`,
`onPlayerHitpointsChanged`, `onChangeStat`, `onAdvanceStat`, `onPlayerHit`, `onNpcHit`,
`onModifyNpcHit`, `onArea`/`onAreaExit`, `onZone`/`onZoneExit`, `onMapzone`/`onMapzoneExit`,
`onPlayerTimer`, `onPlayerSoftTimer`, `onPlayerQueue`, `onPlayerSoftQueue`, `onNpcTimer`,
`onNpcQueue`, `onIfOpen`/`onIfClose`/`onIfOverlayButton`/`onIfModalButton`, `onCommand`.

For anything without a wrapper, drop to `onEvent<T>` / `onProtectedEvent<T>`:

```kotlin
onEvent<NpcStateEvents.Create> { if (npc.type.id in bossIds) resetGorilla(npc) }
```

**Prefer content groups over ids.** `onOpContentLoc1("content.tree")` covers every tree the cache
tags, present and future; `onOpLoc1("loc.oak_tree")` covers one. Content/category/param tagging is
how this codebase avoids the id-list rot that plagues older frameworks.

### Commands

```kotlin
onCommand("playtime") {
    desc = "Show how long you have played for"      // required — build() errors without it
    requiredRights = Rights.ADMINISTRATOR           // optional gate
    cheat {
        player.mes("…")
    }
}
```

`CheatHandlerBuilder` already catches `NumberFormatException` / `IndexOutOfBoundsException` (→
"Invalid arguments!") and logs anything else, so argument parsing can be direct.

---

## State: where to put what

| Kind | Mechanism | Persists? | Use for |
|---|---|---|---|
| Varp / varbit | `by intVarp("varp.x")`, `by intVarBit("varbit.x")`, `by boolVarBit(...)` on `Player` or `ProtectedAccess` | Yes, saved with the account | Anything the client must see, or that must survive logout |
| Attribute | `AttributeKey<T>()` + `player.attr[…]` | Only if `persistenceKey != null` | Server-side flags; transient combat/admin state |
| Timer | `timer("timer.x", ticks)` / `softTimer(...)` | Per-session | Repeating per-player work |
| Queue | `queue`, `weakQueue`, `strongQueue`, `softQueue`, `longQueue` | Per-session | Deferred one-shot actions with interrupt semantics |

Idiomatic varp access, scoped to the plugin that owns it:

```kotlin
private var Player.playtime by intVarp("varp.playtime")
```

Attribute caveat from `AttributeKey`: do not use `Double`/`Float` for persistent keys — store scaled
`Int`s instead.

Prefer varps for anything that already exists in the cache; only invent an attribute when there is
no client-visible representation.

---

## `PluginModule` — bindings and hooks

Use a module when the engine needs to *call into* your plugin, rather than your plugin subscribing
to events. Hooks are contributed as Guice multibindings:

```kotlin
class SlayerModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(SlayerNpcKillHook::class.java)
        addSetBinding<NpcAttackValidateHook>(SlayerSuperiorAttackHook::class.java)
        addSetBinding<PlayerPostTickHook>(SlayerSuperiorEvents::class.java)
    }
}
```

Helpers available: `bindInstance<T>()`, `bindSingleton<T>(instance)`, `bindProvider<T>(…)`,
`bindBaseInstance<Base>(Impl::class.java)`, `newSetBinding<T>()`, `addSetBinding<T>(…)`.

Remember the loader constraints: direct subclass, public no-arg constructor, package under
`org.rsmod.content`.

---

## `PluginPack` — shipping new cache content

A plugin that needs data the vanilla cache does not have puts it in a **separate `pack`
submodule**. This is a hard convention, not a style choice: `or-cache` adds only projects whose
name ends in `-pack` to the cache-build classpath, so building the cache never has to compile your
game scripts or their api dependencies.

```
content/other/spawn/
├── build.gradle.kts                       # the plugin (api.pluginCommons)
├── src/main/resources/gamevals.toml       # id assignments
├── src/main/kotlin/…/SpawnMenuScript.kt
└── pack/
    ├── build.gradle.kts                   # or2 cache libs only
    ├── src/main/kotlin/…/pack/SpawnPluginPack.kt
    └── src/main/resources/pack/
        ├── configs/spawn.toml
        └── cs2/{script,symbols}/…
```

The directory **must** be named `pack` — `settings.gradle.kts` renames the Gradle project to
`<plugin>-pack` so the coordinates stay unique.

```kotlin
class SpawnPluginPack : PluginPack() {
    override fun interfaces(): List<InterfaceType> = listOf(buildSpawnMenuInterface())
}
```

Overridable surface: `dbTables()`, `interfaces()`, `extraTasks()`, `isEnabled(projectRoot)`,
`shouldAlwaysPack()`, `validate(projectRoot)`. Convention-located resource dirs, all optional:
`pack/configs`, `pack/models`, `pack/sprites`, `pack/cs2`.

`configs/*.toml` declares server-side types — invs, varps, varbits, npcs, locs, objs:

```toml
[[inventory]]
id = "inv.spawn_results"
size = 60
scope = "Temp"
stack = "Always"

[[varp]]
id = "varp.spawn_menu"
scope = "Perm"
transmit = "Never"

[[varbit]]
id = "varbit.spawn_include_nulls"
varp = "varp.spawn_menu"
startBit = 0
endBit = 0
```

Db tables are built with the `dbTable` DSL (see `ShootingStarsTable.kt`) and returned from
`dbTables()`.

After changing anything in a pack: `gradlew :or-cache:buildCache`. After adding gamevals used by
CS2: also `gradlew :or-cache:mergePluginGamevals`.

---

## Workflow

One-time: fetch and build the cache, generate `game.yml` and RSA keys.

```bash
./gradlew install
```

Boot the server (alias for `:server:app:run`).

```bash
./gradlew run
```

Rebuild the cache after touching any `pack/` module.

```bash
./gradlew :or-cache:buildCache
```

Append plugin gamevals into `.data/gamevals/*.rscm` (CS2 symbols, release builds).

```bash
./gradlew :or-cache:mergePluginGamevals
```

A new plugin needs **no** registration anywhere — create the directory with a `build.gradle.kts`
and re-sync Gradle.

### Testing

`base-conventions` pulls in `test-conventions`, so plain JUnit 5 `src/test` works in any plugin
and runs with `gradlew test`. Use it for the logic you can isolate — data tables, formatting,
label/encoding invariants (see `content/other/cheat-menu/src/test/…/CheatMenuDataTest.kt`).

Be aware: several plugins carry `src/integration` sources against
`org.rsmod.api.testing.GameTestState`, but **the `api:testing` module does not exist in this
repository** and no content `build.gradle.kts` applies `integration-test-suite`. Those sources are
not compiled or run today. Do not add new ones expecting them to execute.

### Formatting

Spotless + ktlint 1.5.0, ratcheted from `origin/main` — only files changed relative to main are
checked. Rule selection lives in `.editorconfig`. Run `gradlew spotlessApply` before committing.

---

## Safety checklist

Before shipping a plugin:

- [ ] Package is under `org.rsmod.content.…` (otherwise it is silently never loaded).
- [ ] `PluginModule`, if any, is a direct subclass with a public no-arg constructor.
- [ ] `startup()` only registers handlers — no player/world mutation, no blocking I/O, no
      `Thread.sleep`, no heavy work. It runs on the boot path and delays login for everyone.
- [ ] All type references are RSCM strings with the right prefix, so typos fail at boot.
- [ ] No `ProtectedAccess` stored beyond its handler; long-lived references use `Player`/`PlayerUid`.
- [ ] Validate-then-mutate around every `delay`/dialogue; assume anything can change across a
      suspension.
- [ ] Randomness goes through `GameRandom` (`random` on `ProtectedAccess`), never `Math.random()` or
      a private `Random` — this keeps content testable and reproducible.
- [ ] Custom ids are above the table max in `.data/gamevals-binary/max-ids.toml`, and live in the
      plugin's own `gamevals.toml`.
- [ ] Cache data is in a `pack/` submodule, not in the plugin module.
- [ ] Prefer `content.*` / `category.*` / params over hardcoded id lists.
- [ ] `gradlew spotlessApply` is clean, and the server boots (`gradlew run`) with the plugin present.

## Known sharp edges

Beyond `docs/quirks.md`:

- Boot is fail-fast for scripts. A single plugin that throws in its constructor takes the whole
  server down. That is the correct trade — but it means a plugin's constructor must not do
  anything that can fail on a fresh install.
- `PluginGamevalMerger` only ever *appends*. Renumbering a gameval leaves the stale id behind in
  `.data/gamevals/*.rscm`; delete the line by hand or the old name keeps resolving.
- `PluginPacks.discover` scans `dev.openrune.pack` and `org.rsmod.content` only — a pack class
  outside those packages is ignored with no error.
- The `-pack` project-name rewrite in `settings.gradle.kts` exists because Gradle would otherwise
  conflict-resolve every `org.rsmod:pack` down to one. Do not rename the directory.

## See also

- `docs/quirks.md` — engine-level design decisions and gotchas
- `docs/drops.md` — NPC loot tables (TOML + `@RegisterDropTable`)
- `docs/instances.md` — instanced bosses via `InstanceScript`
- `docs/doors.md`, `docs/gates.md`, `docs/boss-hp-bar.md`, `docs/ironman.md`
