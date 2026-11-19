<div align="center">

**EN** | [繁中](../README.md)

<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="../assets/logo/mistinn-wordmark-light.png">
    <img src="../assets/logo/mistinn-wordmark.png" alt="Mistinn: Exile" width="320">
  </picture>
</h1>

**A hunter sent by Rust Anchor Station walks into the forbidden zone at the edge of the world.**

A souls-like Minecraft Java mod: salt for currency, residue where you fall, golden bells to rest at,<br>
gear in seven tiers, and a grid inventory inspired by extraction shooters.

[![CI](https://github.com/keeiv/mistinn-exile/actions/workflows/ci.yml/badge.svg)](https://github.com/keeiv/mistinn-exile/actions/workflows/ci.yml)
![Minecraft](https://img.shields.io/badge/minecraft-26.3-62B47A)
![Fabric](https://img.shields.io/badge/fabric_loader-0.20.1-DBD0B4)
![Java](https://img.shields.io/badge/java-25-E76F00)
![License](https://img.shields.io/badge/license-AGPL--3.0--only-F99549)

[MoriTeahouse](https://moriteahouse.shizune.life) ·
[Features](#features) ·
[Playing](#playing) ·
[Phase 2 design (Chinese)](phase2-design.md) ·
[Contributing](CONTRIBUTING.en.md)

</div>

## Contents

- [Features](#features)
- [Playing](#playing)
- [Controls](#controls)
- [Tiers and gear](#tiers-and-gear)
- [Grid inventory](#grid-inventory)
- [Building from source](#building-from-source)
- [What comes next](#what-comes-next)
- [Contributing](#contributing)
- [License](#license)

## Features

| Area | What it does |
| --- | --- |
| Salt | The currency. Defeating a hostile creature gives salt equal to its maximum health; others give a fifth (at least 1). |
| Residue and gravestones | When you fall, all the salt you carry becomes residue held by a gravestone where you fell; hit it to take the salt back. Fall again first and the old residue fades. |
| Golden bells | Save points. Rest at one to heal, eat and set it as your respawn point. The border world has no such metal, so they cannot be crafted. |
| One-time bells | Cast from gold ore (ore or raw gold) and an iron chain; brings you back once, then cracks. |
| Minimap | Built in, north up; shows your heading, your bell and where you fell, pinned to the edge when off the map. |
| Weapons | Anchor Blade, Sunken Bell Greatsword, Mistwalker Spear and Salt Fang, each with vanilla-style art and held model, its own swing and hit sounds, and its own first- and third-person swing. |
| Tiers | N and I–VI; names show as "◆ name" in the tier colour, and inventory cells sit on the tier colour. |
| Gear | A helmet, body armour and backpack per tier, each with its own durability, protection, capacity, layout and a two-line story. |
| Grid inventory | Four pockets, a layout set by the backpack, and a safe box kept through death; every item takes a footprint by kind and can be rotated. |
| Languages | Traditional Chinese and English. |

> [!TIP]
> The easiest way to play is the **Mistinn Launcher**: it installs the game, Java, Fabric, this mod and a performance pack, and tunes the settings to your computer as Smooth, Normal or Ultra.

### Weapons

| Weapon | Swing | Damage | Attacks per second | Reach |
| --- | --- | --- | --- | --- |
| Anchor Blade | Diagonal slash, upper right to lower left | 7 | 1.6 | 3 |
| Sunken Bell Greatsword | Raised over the shoulder, then brought down; tolls on hit | 12 | 0.7 | 3.5 |
| Mistwalker Spear | Draw back, then thrust | 8 | 1.1 | 4.5 |
| Salt Fang | Two crossing cuts in one swing | 5 | 2.6 | 2.6 |

## Playing

You need Minecraft Java Edition 26.3, [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer, [Fabric API](https://modrinth.com/mod/fabric-api) and Java 25. Put `mistinn-<version>.jar` and Fabric API in your `mods` folder and enter a survival world.

> [!NOTE]
> Only Minecraft 26.3 is supported. Creative mode keeps the vanilla inventory; the grid inventory is active in survival and adventure.

## Controls

| Input | Action |
| --- | --- |
| Use a golden bell | Rest, set respawn point |
| Hit a gravestone | Take back the salt in the residue |
| `M` | Minimap zoom (1, 2 or 4 blocks per pixel) |
| `E` | Inventory and grid |
| Click a cell | Pick up or put down |
| `R` while carrying | Rotate |
| `Q` over an item | Drop it |
| Shift-click | Grid item to the hotbar; chest item into the grid |

## Tiers and gear

| Tier | Colour | Helmet | Body armour | Backpack |
| --- | --- | --- | --- | --- |
| N | Rust brown | Rustleather Hood | Rustleather Vest | Rust-Buckle Sack |
| I | Grey | Dockhand Helm | Dockhand Plate | Dock Canvas Bag |
| II | Green | Tidewatch Helm | Tidewatch Cuirass | Tidewatch Pack |
| III | Blue | Anchorguard Helm | Anchorguard Plate | Anchorguard Rucksack |
| IV | Purple | Mistwalker Visor | Mistwalker Harness | Mistwalker Pack |
| V | Gold | Bellkeeper Crown | Bellkeeper Mail | Bellkeeper Frame |
| VI | Red | Rust God's Skull | Rust God's Scales | Rust God's Reliquary |

Helmets last 70–640, body armour 110–940; higher tiers protect more and body armour gets heavier (0% to −6% movement speed). Vanilla items follow their material (iron I, gold II, emerald III, diamond IV, netherite V) or vanilla rarity. Salt repairs gear at an anvil.

## Grid inventory

| Backpack | Layout | Cells | Speed |
| --- | --- | --- | --- |
| N | 3×2 | 6 | — |
| I | 4×3 | 12 | — |
| II | 3×5 | 15 | −1% |
| III | 2×2, 2×2, 4×3 | 20 | −1% |
| IV | 4×6 | 24 | −1% |
| V | 2×3, 2×3, 4×5 | 32 | −2% |
| VI | 2×4, 2×4, 5×6 | 46 | −2% |

Pockets are four 1×1 cells and the safe box is 2×2. On death the pockets and the whole backpack drop; the safe box stays. Footprints: gems and ingots 1×1, sticks 1×2, tools and swords 1×3, ore and other blocks 2×2, armour 2×2 or 2×3. A backpack's contents live in the backpack item, so they travel with it.

## Building from source

JDK 25 is required. From a OneDrive folder or a path with non-ASCII characters use `build.ps1`; otherwise `./gradlew build` works.

```powershell
.\build.ps1 build               # mistinn-<version>.jar
.\build.ps1 runClient           # development client
.\build.ps1 runClientGameTest   # a real client plays the core loop and saves screenshots
```

All textures and sounds are generated from scratch: `python tools/make_assets.py` (numpy, Pillow, soundfile) and `python tools/make_wordmark.py`.

## What comes next

1. **Phase 2: the forbidden zone and the Lighthouse Keepers.** Fog-shore lighthouses, the first keeper, creatures of the mist and tiered loot ([design, in Chinese](phase2-design.md)).
2. **More grid inventory**: chest rigs, side-by-side dragging with chests, backpacks worn on the model.
3. **Later**: levelling with salt at bells, more weapons and charged attacks, stamina and dodge rolls, co-op.

## Contributing

Bug fixes, features, tests, art and documentation are welcome; read the [contributing guide](CONTRIBUTING.en.md) first.

> [!IMPORTANT]
> AI-assisted contributions are treated like hand-written ones. A pull request is merged only without security issues, and **every asset must be generated by this repository's scripts**; assets from other games or packs are not accepted.

## License

Copyright (c) 2026 Keeiv.

Licensed under the **GNU Affero General Public License v3.0 only (AGPL-3.0-only)**; see [LICENSE](../LICENSE) and [NOTICE](../NOTICE). Generated textures, models and sounds share the code's license.

> [!NOTE]
> The wordmark is drawn with [Fusion Pixel Font](https://github.com/TakWolf/fusion-pixel-font) (SIL Open Font License 1.1). Minecraft is a trademark of Mojang Studios; this mod is not affiliated with Mojang or Microsoft.
