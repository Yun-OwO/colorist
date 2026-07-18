# Colorist — Native NeoForge 1.21.1 Port

A focused Kotlin port of the original [KubeJS Colorist mod](https://github.com/1026073226/colorist).
The core thesis — **"颜色即力量" (color is power)** — is preserved verbatim: Minecraft's 16
dyes plus a custom `soil_dye` are quantified to RGB, then translated into RPG-style attributes
(`r` / `g` / `b` / `yin` / `yang` / `level`) that drive combat, durability, and tooltip rendering.

This port implements the **core functionality** only. Per the porting specification
([DOCUMENTATION.md](DOCUMENTATION.md)), the following are **explicitly excluded**:

- **Section 17 — GUI interaction design** (Magic Table screen, Magic Book panel, Dye codex).
  The original mod's chat-bar feedback is retained instead.
- **Section 18 — Gameplay improvements and innovation suggestions** (color memory, paper
  quality tiers, environment interaction, color counter system, "color domain" dimension,
  real-time palette combat, color ecology, color runes, color alchemy, multiplayer color
  resonance, etc.).
- **LibGuiFoxified dependency** — required only by the excluded GUI layer; not declared.

The remaining Sections 1–14, 15, and 16 (porting architecture + prerequisite selection)
are implemented in full.

---

## Tech stack

| Component            | Version     | Notes                                                              |
|----------------------|-------------|-------------------------------------------------------------------|
| Minecraft            | 1.21.1      | NeoForge mappings via Parchment 2024.11.17                         |
| NeoForge             | 21.1.77     | Loaded via ModDevGradle                                           |
| Kotlin for Forge     | 5.7.0       | Required language provider — see [DOCUMENTATION.md §16.1](DOCUMENTATION.md) |
| Kotlin               | 2.1.0       | JVM target 21, `-Xjvm-default=all`                                |
| ModDevGradle         | 1.0.21      | NeoForge's recommended Gradle plugin                              |
| Java toolchain       | JDK 21      | Required by NeoForge 1.21.1                                        |

The mod is a single Kotlin object annotated with `@Mod("colorist")` and is loaded by KFF's
`KotlinLanguageProvider` (declared via `modLoader = "kotlinforforge"` in `neoforge.mods.toml`).

---

## Build prerequisites

- **JDK 21** — Eclipse Temurin or Microsoft OpenJDK 21 (NeoForge requirement).
- **Gradle 8.8+** — but you do **not** need a system install; the project uses the Gradle
  wrapper (`./gradlew` on Unix, `gradlew.bat` on Windows). The wrapper will auto-download
  the correct Gradle version.
- **Internet access** on first build — ModDevGradle downloads Minecraft artifacts, the
  Parchment mappings, and Kotlin for Forge from `maven.neoforged.net` and
  `thedarkcolour.github.io/KotlinForForge/`.

## Build instructions

```powershell
# Windows PowerShell — from the project root
.\gradlew build
```

```bash
# macOS / Linux — from the project root
./gradlew build
```

The built jar is written to:

```
build/libs/colorist-1.0.0.jar
```

Drop that jar into a NeoForge 1.21.1 `mods/` folder alongside [Kotlin for Forge 5.7.0+](https://modrinth.com/mod/kotlin-for-forge).

### Useful Gradle tasks

| Task                  | Purpose                                                       |
|-----------------------|--------------------------------------------------------------|
| `build`               | Compile, run data generation (if any), and package the jar   |
| `compileKotlin`       | Kotlin-only compile check (fast iteration)                   |
| `runClient`           | Launch a dev client with the mod loaded                      |
| `runServer`           | Launch a dev dedicated server with the mod loaded            |
| `--refresh-dependencies` | Force re-download of Minecraft / mappings artifacts       |

---

## Project structure

```
colorist/
├── build.gradle.kts                     # ModDevGradle + KFF wiring
├── settings.gradle.kts                  # Root project name + plugin portal
├── gradle.properties                    # Versions and mod metadata
├── gradle/wrapper/                      # Gradle wrapper bootstrap
├── DOCUMENTATION.md                     # Porting specification (source of truth)
├── README.md                            # This file
├── scripts/
│   └── generate_placeholder_textures.ps1  # Regenerates solid-color placeholder PNGs
└── src/main/
    ├── kotlin/com/colorist/
    │   ├── Colorist.kt                  # @Mod entrypoint; bus wiring
    │   ├── block/
    │   │   ├── MagicTableBlock.kt       # Right-click crafting station
    │   │   └── MagicCrystalOreBlock.kt  # Plain ore block
    │   ├── blockentity/
    │   │   └── MagicTableBlockEntity.kt # Stores the placed ItemStack
    │   ├── client/
    │   │   ├── ClientSetup.kt           # @EventBusSubscriber for client events
    │   │   ├── MagicBookClientHandler.kt# Particle VFX + crit sound on payload receipt
    │   │   └── TooltipHandler.kt        # Paper / book tooltip assembly
    │   ├── core/
    │   │   ├── AttrSystem.kt            # Attr data class + CODEC + aggregateAttrs
    │   │   ├── ColorSystem.kt           # Dye palette, hex/rgb, MERGE_COLOR
    │   │   ├── ValueCounter.kt          # Combat formulas (cost/atk/hp/br/bd)
    │   │   ├── TooltipFormatter.kt      # ▍-glyph bars + gradient text
    │   │   ├── ColoristDataComponents.kt# Codec/stream-codec specs for components
    │   │   └── ColorLog.kt              # SLF4J wrapper
    │   ├── event/
    │   │   ├── MagicBookServerEventHandler.kt # Right-click + per-tick cast driver
    │   │   ├── InventoryEventHandler.kt       # HP bonus polling on server tick
    │   │   └── BlockBreakEventHandler.kt      # Auto-return stored item on table break
    │   ├── item/
    │   │   └── MagicBookItem.kt         # Dynamic durability bar + channeled ray
    │   ├── network/
    │   │   └── ModPayloads.kt           # MagicStart/Stop/Crit CustomPacketPayloads
    │   ├── recipe/
    │   │   └── WashMagicPaperRecipe.kt  # ShapelessRecipe wrapper preserving `level`
    │   └── registry/
    │       ├── ModItems.kt              # 7 items + special-dye color lookup
    │       ├── ModBlocks.kt             # 2 blocks + their BlockItems
    │       ├── ModBlockEntities.kt      # MAGIC_TABLE BlockEntityType
    │       ├── ModCreativeTabs.kt       # Single "Colorist" creative tab
    │       ├── ModDataComponents.kt     # 5 DataComponentType entries
    │       └── ModRecipeSerializers.kt  # WASH_MAGIC_PAPER serializer
    └── resources/
        ├── META-INF/neoforge.mods.toml  # Mod metadata + KFF/NeoForge deps
        ├── pack.mcmeta                  # Resource pack manifest (format 34)
        ├── assets/colorist/
        │   ├── lang/{en_us,zh_cn}.json  # Localization
        │   ├── blockstates/             # magic_table, magic_crystal_ore
        │   ├── models/{block,item}/     # Model JSON
        │   └── textures/{block,item}/   # 16×16 placeholder PNGs (see below)
        └── data/
            ├── colorist/
            │   ├── recipe/              # 19 JSON recipes (dye crafting, magic_book, etc.)
            │   ├── loot_table/          # Block drops + injected entity loot tables
            │   ├── loot_modifiers/      # 6 GLM JSON files
            │   ├── neoforge/
            │   │   ├── global_loot_modifiers.json     # GLM registration manifest
            │   │   └── biome_modifier/add_magic_crystal_ore.json
            │   └── worldgen/
            │       ├── configured_feature/colorist_magic_crystal_ore.json
            │       └── placed_feature/colorist_magic_crystal_ore.json
            ├── minecraft/tags/blocks/   # mineable/pickaxe, needs_iron_tool
            └── forge/tags/blocks/ores.json  # legacy forge:ore tag (see Notes)
```

---

## KubeJS → Kotlin porting map

Every original KubeJS file from the source mod is represented in the port. The mapping is
1:1 where possible; KubeJS's `global` namespace is split into typed Kotlin objects.

| Original KubeJS file                 | Ported Kotlin file(s)                                              |
|--------------------------------------|-------------------------------------------------------------------|
| `startup_scripts/lib.js`             | `core/ColorSystem.kt`, `core/AttrSystem.kt`, `core/ValueCounter.kt`, `core/TooltipFormatter.kt` |
| `startup_scripts/item.js`            | `registry/ModItems.kt`                                            |
| `startup_scripts/block.js`           | `registry/ModBlocks.kt`, `block/MagicCrystalOreBlock.kt`          |
| `startup_scripts/magic_table.js`     | `block/MagicTableBlock.kt`, `blockentity/MagicTableBlockEntity.kt` |
| `startup_scripts/ore.js`             | (replaced by `data/colorist/worldgen/...` JSON + biome modifier) |
| `server_scripts/recipe.js`           | `data/colorist/recipe/*.json` + `recipe/WashMagicPaperRecipe.kt` |
| `server_scripts/loot.js`             | `data/colorist/loot_modifiers/*.json` + `loot_table/injections/*.json` |
| `server_scripts/block.js`            | `event/BlockBreakEventHandler.kt`                                 |
| `server_scripts/magic_book.js`       | `item/MagicBookItem.kt`, `event/MagicBookServerEventHandler.kt`, `network/ModPayloads.kt` |
| `server_scripts/inventory.js`        | `event/InventoryEventHandler.kt`                                  |
| `client_scripts/magic_book.js`       | `client/MagicBookClientHandler.kt`, `client/ClientSetup.kt`       |
| `client_scripts/tooltip.js`          | `client/TooltipHandler.kt`, `core/TooltipFormatter.kt`            |
| `assets/colorist/lang/zh_cn.json`    | `assets/colorist/lang/zh_cn.json` (preserved) + new `en_us.json`  |
| `datapacks/colorist/.../configured_feature/...` | `data/colorist/worldgen/configured_feature/colorist_magic_crystal_ore.json` + `placed_feature/...` |

### Architectural substitutions

| KubeJS construct                                | NeoForge 1.21.1 replacement                                  |
|-------------------------------------------------|-------------------------------------------------------------|
| `StartupEvents.registry("item"/"block")`        | `DeferredRegister.Items` / `DeferredRegister.Blocks`        |
| `e.create(id, "basic").maxStackSize(N)`         | `Item(Item.Properties().stacksTo(N))`                       |
| `.maxDamage(1000)` on the magic book            | `getMaxDamage(stack)` override returning `MAGIC_BOOK_MAX_DURABILITY` |
| `item.nbt.attr = {...}` (raw NBT)               | `DataComponentType<Attr>` (`MAGIC_PAPER_ATTR`)              |
| `item.nbt.attrs = [...]`                         | `DataComponentType<List<Attr>>` (`MAGIC_BOOK_ATTRS`)        |
| `player.sendData("colorist:magic_start", {r,g,b})` | `PacketDistributor.sendToPlayer(serverPlayer, MagicStartPayload(r,g,b))` |
| `NetworkEvents.dataReceived("colorist:magic_start")` | `registrar.playToClient(...)` in `ModPayloads`           |
| `blockEntity(e => e.initialData({item, nbt}))`  | `MagicTableBlockEntity` with `saveAdditional` / `loadAdditional` |
| `ServerEvents.recipes` with `.modifyResult`     | Custom `WashMagicPaperRecipe` wrapping `ShapelessRecipe` via Kotlin delegation |
| `ServerEvents.entityLootTables`                 | `GlobalLootModifier` JSON + `data/colorist/neoforge/global_loot_modifiers.json` |
| `BlockEvents.broken`                            | `@SubscribeEvent` on `BlockEvent.BreakEvent` (GAME bus)     |
| `PlayerEvents.inventoryChanged`                 | Polling on `ServerTickEvent.Post` (no exact NeoForge equivalent) |
| `PlayerEvents.respawned`                        | `PlayerEvent.PlayerRespawnEvent`                            |
| `ClientEvents.tick`                             | `ClientTickEvent.Post` (GAME bus)                           |
| `ItemEvents.tooltip`                            | `ItemTooltipEvent` (GAME bus, package `net.neoforged.neoforge.event.entity.player`) |
| Datapack `configured_feature` JSON              | Same JSON works; additionally wired via `biome_modifier/add_magic_crystal_ore.json` |

---

## What works

The core gameplay loop is fully implemented and compiles against NeoForge 21.1.77:

1. **Crafting** — All 19 recipes (dye expansion, special dyes, magic book, magic table,
   crying obsidian from TNT, wash-magic-paper, smelting ore → crystal).
2. **Magic Table interactions** — Place item, sneak-right-click to retrieve, dye a paper,
   inject crystal (+5 level), inject paper into book (max 12).
3. **Color & attribute system** — Dye palette, `MERGE_COLOR(c1, c2, 1/level)`, `CALC_ATTR`,
   `CALC_ATTRS` aggregation — all faithful to `lib.js`.
4. **Combat** — Magic book right-click triggers a 10-tick channeled ray; damage from
   `r^1.1/10 + level^0.8/5`; crit roll on `br`, crit multiplier `1 + bd`; cost deducted
   from first paper on cast end.
5. **HP bonus** — Server-tick polling grants the book's `hp` value to the carrier's
   `max_health` attribute; cleaned up on logout and respawn.
6. **Tooltips** — Paper and book tooltips with `▍`-glyph rainbow/yin-yang bars; Shift
   toggles detailed attribute breakdown.
7. **Networking** — `MagicStartPayload` / `MagicStopPayload` / `CritPayload` flow from
   server to client via `PayloadRegistrar`.
8. **Client VFX** — Multi-particle ray trail (`dripping_obsidian_tear`, `electric_spark`,
   `enchant`, alternating `sonic_boom` / `dripping_dripstone_lava` / `dripping_dripstone_water`),
   warden-death + amethyst sound design.
9. **World gen** — Magic crystal ore spawns in overworld biomes via `BiomeModifier`.
10. **Loot** — GLM injects colored papers and crystals into witch / creeper / skeleton /
    warden / enderman drops, plus amethyst block → ore bonus.
11. **Dynamic durability bar** — Cosmetic bar showing `1 - level / (cost * 100)`, colored
    with the book's aggregate color. The book never actually breaks.
12. **Localization** — English (`en_us.json`) and Chinese (`zh_cn.json`).

## Known limitations / notes

- **Placeholder textures.** All `*.png` files under `src/main/resources/assets/colorist/textures/`
  are 16×16 solid-color images generated by `scripts/generate_placeholder_textures.ps1`.
  They are sufficient to verify the mod loads and behaves correctly, but they are not the
  pixel-art textures from the original mod. Replace them with proper assets before shipping.
- **Chat-bar feedback.** Per the exclusion of Section 17, all magic-table interaction
  feedback is delivered via the action-bar (`player.displayClientMessage(..., true)`),
  mirroring the original mod's `player.tell(...)` UX. No GUI screen is opened.
- **`forge:ore` tag.** The legacy `data/forge/tags/blocks/ores.json` file is kept to match
  the documented tag (`DOCUMENTATION.md §4.1` says `forge:ore`). NeoForge 1.21.1 prefers
  the `c:` convention namespace; if a downstream mod needs the new tag, add
  `data/c/tags/blocks/ores.json` mirroring this file.
- **`PlayerEvents.inventoryChanged` has no exact NeoForge equivalent.** HP-bonus
  recomputation therefore polls on `ServerTickEvent.Post` every 10 ticks (twice per
  second). The cost is trivial: `O(players × inventory size × constant)` per pass.
- **Magic book never breaks.** The durability bar is purely a visual indicator of
  `level / (cost * 100)` — this matches the original KubeJS behavior where
  `setDamageValue` was used to render a fake bar.

---

## Running the mod

1. Install [NeoForge 1.21.1](https://neoforged.net/) (client or server).
2. Install [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) version 5.7.0 or newer.
3. Drop `build/libs/colorist-1.0.0.jar` into the `mods/` folder.
4. Launch the game. The "Colorist" creative tab contains every item.

### Quick sanity check in-game

```
/give @s colorist:magic_book
/give @s colorist:magic_paper
/give @s colorist:magic_crystal
/give @s minecraft:red_dye 64
/place structure colorist:magic_table   # or craft one
```

Right-click the table with the paper to place it; right-click with the red dye to stain
the paper; right-click with the magic crystal to raise its level by 5. Sneak-right-click
to retrieve. Hold the magic book and right-click to fire the ray.

---

## License

LGPL-2.1, matching the original Colorist mod and Kotlin for Forge.
