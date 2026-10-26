# LightSetPlugin

Paper plugin that builds dynamic spotlights and other lighting out of `minecraft:light` blocks, with brightness effects, groups, a particle beam and a visible model for every source.

## Requirements

- Minecraft 1.21.11, Paper, Java 21
- No other plugins. Companion client mod: none.

## Build and run

```bash
./gradlew build      # build/libs/LightSetPlugin-<version>.jar
./gradlew runServer  # Paper 1.21.11 test server
```

The version is stored only in `gradle.properties` (`2.0.0`).

## Architecture

```text
net.dvmn2.lightsetplugin
├── LightSetPlugin        composition root
├── Lang                  RU/EN MiniMessage texts
├── Permissions           permission constants
├── PluginKeys            NamespacedKey factory
├── LightSetSettings      config snapshot (record)
├── LightManager          sources and the update loop
├── LightGeometry         ray tracing and shapes
├── LightBlockManager     light blocks in the world
├── ModelManager          item_display models
├── EffectCalculator      flicker / pulse / blink
├── BeamRenderer          particle beam
├── PersistenceManager    dirty flags and save scheduling
├── LightListener         chunk and entity load events
├── command/              Brigadier tree
├── model/                domain data and result enums
├── storage/              stores, atomic writes
│   └── dto/              JSON DTOs
└── util/                 stateless helpers
```

| Class | Responsibility |
|---|---|
| `LightSetPlugin` | wiring, enable/disable order, `reloadAll()` |
| `LightManager` | owns all `LightSource`s; per-tick loop; every command mutation goes through it |
| `LightGeometry` | computes the positions and brightness factors of a source; never loads chunks |
| `LightBlockManager` | merges contributions of sources (highest level wins), places/removes light blocks within a per-tick budget, adopts blocks left by a previous run |
| `ModelManager` | spawns and removes item displays; removes orphans |
| `PersistenceManager` | dirty flags, async writes, final synchronous save |
| `LightSetSettings` | immutable config snapshot, parsed by `parse(...)` |
| `Lang` | the only holder of static mutable state (current language) |

### Threading

- All Bukkit API calls run on the main thread, including every manager.
- Only file writes run asynchronously: snapshots are made on the main thread (`LightManager#snapshotLights`, `LightBlockManager#snapshotPlaced`) and written by `LightStore#write` / `PlacedStore#write`.
- A ticket per snapshot (`LatestWriter`) guarantees that an older snapshot never overwrites a newer one.

### Lifecycle

Enable: `saveDefaultConfig` → settings → language → stores → managers → load registry and sources → `LightManager#start` → `PersistenceManager#start` → listener → command → one log line.

Disable: cancel the update loop and remove models (`LightManager#shutdown`) → synchronous save of both files (`PersistenceManager#shutdown`). Light blocks stay in the world and are recorded in `placed.bin`. `PersistenceManager#shutdown` does nothing if `start()` was never reached, so a failed start cannot erase data.

Reload (`/lightset reload`): `reloadConfig` → new settings snapshot → models and geometry of every source are rebuilt. `lights.json` is not re-read.

### Update model

Each source caches a `Geometry` (packed positions and factors `0..1`). Brightness, effect and on/off changes recompute only the light levels. Moving, rotating or changing the cone marks the geometry dirty and queues a new ray trace (`performance.source-updates-per-tick` per tick). A source is recalculated again when a chunk near it loads, if its last result was truncated by an unloaded chunk.

## Public API

None.

## Protocol

None.

## Data and files

Files are in `plugins/LightSetPlugin/`.

| File | Format | Version | Content |
|---|---|---|---|
| `config.yml` | YAML | n/a | settings, see `MODRINTH.md` |
| `lights.json` | JSON (Gson, `LightsFileDto`) | `1` | light sources |
| `placed.bin` | gzip, big-endian | `1` | registry of light blocks placed by the plugin |

`lights.json`:

```json
{
  "version": 1,
  "lights": [
    {
      "name": "stage1",
      "type": "spot",
      "world": "world",
      "x": 10.5, "y": 70.5, "z": -3.5,
      "yaw": 90.0, "pitch": 20.0,
      "brightness": 15, "angle": 40.0, "range": 250, "rays": 120,
      "enabled": true, "effect": "none", "group": "stage", "beam": true
    }
  ]
}
```

Unknown fields are ignored and missing fields get defaults. Invalid entries are skipped with a warning naming the light.

`placed.bin` (all numbers big-endian, the whole stream is gzip-compressed):

```text
int     magic      0x4C535043
int     version    1
int     worldCount 0..1024
repeat worldCount:
  UTF   world
  int   count      0..10000000
  long  position[count]   packed X(26) Z(26) Y(12), see Positions
```

If a file cannot be read it is renamed to `<name>.corrupt` and the plugin starts with empty data.

Version 2.0.0 does not read the files of 1.x (`plugins/LightSet/`): there is no migration.

## Conventions and constraints

- The registry in `placed.bin` is flushed every 5 seconds when it changes (not every 30 s): it is the crash-recovery data, and a lost entry leaves a light block that nothing will remove.
- Models are non-persistent entities marked with the PDC key `lightsetplugin:display` (value: source name). A marked display that no source owns is removed at start and on `EntitiesLoadEvent`.
- Light blocks are placed without physics updates. A position occupied by a non-air block is skipped.
- Chunks are never loaded or generated by the plugin; positions in unloaded chunks are deferred until the chunk loads.
- User text is inserted into messages only through `Placeholder.unparsed`.
- `LightSource` keeps runtime state (geometry, applied levels) next to settings for speed; the DTO classes in `storage.dto` hold only persistent fields.

## License

All-Rights-Reserved.
