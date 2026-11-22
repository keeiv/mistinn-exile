<div align="center">

**EN** | [繁中](../README.md)

<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="../assets/logo/mistinn-wordmark-en-light.png">
    <img src="../assets/logo/mistinn-wordmark-en.png" alt="Mistinn: Exile" width="330">
  </picture>
</h1>

**A hunter sent by Rust Anchor Station walks into the forbidden zone at the edge of the world.**

A souls-like Minecraft Java mod: salt for currency, residue where you fall, golden bells to rest at,<br>
damage by body part, gear in seven tiers, a grid inventory inspired by extraction shooters, and a keeper atop a lighthouse.

[![CI](https://github.com/keeiv/mistinn-exile/actions/workflows/ci.yml/badge.svg)](https://github.com/keeiv/mistinn-exile/actions/workflows/ci.yml)
![Minecraft](https://img.shields.io/badge/minecraft-26.3-62B47A)
![Fabric](https://img.shields.io/badge/fabric_loader-0.20.1-DBD0B4)
![Java](https://img.shields.io/badge/java-25-E76F00)
![License](https://img.shields.io/badge/license-AGPL--3.0--only-F99549)

[MoriTeahouse](https://moriteahouse.shizune.life) ·
[Features](#features) ·
[Playing](#playing) ·
[Phase 2 design](phase2-design.md) (Chinese) ·
[Phase 3 design](phase3-design.md) (Chinese) ·
[Contributing](CONTRIBUTING.en.md)

</div>

![A hunter in the Rust God's Skull, Face and Scales, Bellkeeper greaves and iron boots, the Rust God's Harness on the chest and Reliquary on the back, with a netherite hammer, standing on a drop station platform at dusk](images/hunter.jpg)

> [!NOTE]
> This is a translation of the [Chinese README](../README.md), which remains the primary version. Pages marked (Chinese) are only available in Chinese for now.

## Contents

- [Features](#features)
- [Playing](#playing)
- [Story mode](#story-mode)
- [World and structures](#world-and-structures)
- [Body parts](#body-parts)
- [Vanilla monsters and executions](#vanilla-monsters-and-executions)
- [Death and the end of the mission](#death-and-the-end-of-the-mission)
- [Swings, stamina and rolls](#swings-stamina-and-rolls)
- [The fog-shore lighthouse and the Snuffer](#the-fog-shore-lighthouse-and-the-snuffer)
- [The fog-bell station and the Sunken Bell](#the-fog-bell-station-and-the-sunken-bell)
- [Stats and skills](#stats-and-skills)
- [Achievements](#achievements)
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
| Salt | The currency. Defeating a hostile creature gives salt equal to its maximum health; others give a fifth (at least 1). Salt found in structures is worth 10 each when used. |
| Residue and gravestones | When you fall, all the salt you carry becomes residue, and everything else you carry (gear, hotbar, pockets, chest rig and backpack; not the safe box) goes into the same gravestone instead of onto the ground; right-click it to take it all back. Fall again first and the old residue fades: the salt is lost and the things spill where the stone stood. |
| Death scene | Killed by a creature, every other creature vanishes; the hunter drops to one knee, topples onto one side and turns to smoke. Other deaths are a plain fall. Then the mission ends: the cause, a line from Rust Anchor, and the choice to return or to rest. |
| Story mode | A prologue and a first chapter with objectives, radio calls, pixel-art story cards and in-world scenes, with a mission journal on `K`. Later chapters are still being made. |
| Hunter Training | An optional course for a new singleplayer world. Follow the instructor's radio, the glowing arrows and Rust Anchor's staff (report to Steelyard, the quartermaster, first; right-click staff to talk) through equipment, heavy blows, sprinting, rolling and sliding, reading a monster's windup, crossing the listening pen without a sound, medicine, crafting, the guide and your stats, the bell and residue, radio repair, and a keeper rehearsal (the lent slide, practice stat points and achievements earned in training are taken back when you leave), all at Rust Anchor Station, a base built over the sea round a giant anchor. Board the drop pod to choose Story or Free exploration. |
| Golden bells | Ring one to set your respawn point. Each bell has its own one-hour cooldown in real time, shared by everyone. The border world has no such metal, so they cannot be crafted. |
| Body parts | Hearts are replaced by six parts (head, torso, both arms, both legs), 80 health each (up to 120 with constitution); three destroyed parts kill. Each part is protected only by the gear that covers it, and a destroyed one has lasting effects. |
| Bleeding and broken bones | Blows can open a bleeding wound that slowly kills the part until a tourniquet stops it; falls, explosions and crushing blows can break a limb, which then works as if destroyed (a broken leg only dives) until surgery sets it; a splint holds it for a while. |
| Medicine and repair | Potent injector, surgery kit, painkillers, tourniquet, splint, and tier III–VI armour and helmet repair kits; each takes a few seconds with its own motion and sounds, and a heavy hit or a roll breaks it off. |
| Crafting | Every medical item, repair kit, piece of gear and weapon can be crafted or smithed. Permanent golden bells are found in the world. Keepers drop the Rust God's salt block, an ingredient in tier VI gear. |
| One-time bells | Fragile bells cast from gold ore (ore or raw gold) and an iron chain. Each rings once and breaks after calling its first hunter back. A bell that has already rung drops nothing when broken. |
| Minimap | Built in, north up; shows your heading, your bell and where you fell, pinned to the edge when off the map. |
| Weapons | Six branches (blade, greatsword, spear, dagger, hammer, scythe) in five materials (wood, stone, iron, diamond, netherite), so there is something to fight with from day one; four named weapons: Anchor Blade, Sunken Bell Greatsword, Mistwalker Spear and Salt Fang; and three special weapons that only answer a hunter who has earned them: the Snuffer's Hook, the Mist-Lantern Staff and the Tide-Bell Maul. Each has vanilla-style art and a held model, its own swing and hit sounds, and its own first- and third-person swing. |
| Swings | Every blow has a wind-up, a strike, an overshoot and a recovery, and leaves a cut of vanilla-style pixel light where it lands, turned to the blow: the blade's diagonal, the greatsword's chop, the spear's line, the dagger's two crossing cuts, the hammer's sweep, the scythe's rising reap. Hold use to charge a **heavy blow** that hits everything in its reach. Third person twists and steps into it. In first person the view swings with the blow and jolts on a hit. |
| Vanilla monsters | Their voices are built from CC0 recordings, lower and more savage; their movement and poses are remade for each rig. They stalk low once they have a hunter, and every melee blow has a windup you can see and hear, and roll away from. A hunter whose legs are both destroyed is executed: see [Vanilla monsters and executions](#vanilla-monsters-and-executions). |
| Stamina and rolls | Swings, sprints and rolls cost stamina. The legs determine how far a roll goes and the arms shape its motion. Badly injured legs can force a dive, whose landing strains the upper body. |
| Title screen | An original misty shore with a dead lighthouse: drifting fog, glinting water and a last ember in the lamp room. |
| Hunter's Guide | Placed in your hotbar the first time you join. A guide screen of its own: eleven pages on the world, the mission and the rules, the stats, the skill tree and the achievements. |
| Achievements | Sixteen of them, from the first hunt, a monster down with each of the six branches and three hit by one heavy blow, to bringing down the Snuffer and the Sunken Bell. A notice slides in at the top right and pays some salt; all are listed on the guide's deeds page. See [Achievements](#achievements). |
| World and structures | You always start at a broken or counterfeit drop station; drop stations, salt cairns, anchor wrecks and mist watchposts with supplies are scattered across the world. |
| Tiers | N and I–VI; names show as "◆ name" in the tier colour, and inventory cells sit on the tier colour. |
| Gear | A helmet, mask, body armour, chest rig and backpack per tier, plus leggings and boots from N to V; each with its own durability, protection, capacity, layout and a two-line story. |
| Grid inventory | Four pockets, the chest rig's and backpack's grids, and a safe box kept through death. Every item takes a footprint by kind and can be rotated. Packs, rigs and some medical supplies have sprites drawn for their footprints. Packs and rigs also show on the body. Survival and adventure use the mod's own inventory screen and seal off the 27 vanilla slots. |
| Fog-shore lighthouses | Dead lighthouses on the coast; the lamp room at the top is held by the first keeper, **the Snuffer**, whose every move strikes a set part of the body. The fight has its own music, which turns more frantic in its dark half. |
| Fog-bell stations | Bell towers on reefs in the shallows off the misty shore, held by the second keeper, **the Sunken Bell**. Each toll of its bell rolls a ring out along the floor and thickens the mist by a layer; a fully charged heavy blow rings its own bell and thins the mist again. See [The fog-bell station and the Sunken Bell](#the-fog-bell-station-and-the-sunken-bell). |
| Stats and skills | Bring back a keeper's rust god salt to learn the **slide**, the first major skill, on the guide's skills page; after that, salt buys strength, speed, constitution and intellect a point at a time. See [Stats and skills](#stats-and-skills). |
| Languages | Traditional Chinese and English. |

### Weapons

Each of the six branches has its own swing, footprint and feel; the material sets damage, durability and tier.

| Branch | Swing | Attacks per second | Reach | Footprint | Recipe (`X` material, `#` stick, `.` empty) |
| --- | --- | --- | --- | --- | --- |
| Blade | Diagonal slash, upper right to lower left | 1.6 | 3 | 1×3 | `..X` / `.X.` / `#..` |
| Greatsword | Raised over the shoulder, then brought down | 0.7 | 3.5 | 1×4 | `.XX` / `.XX` / `#..` |
| Spear | Draw back, then thrust | 1.1 | 4 | 1×4 | `..X` / `.#X` / `#..` |
| Dagger | Two crossing cuts in one swing | 2.6 | 2.6 | 1×2 | `X` / `#` |
| Hammer | Wound up at the side, then swept across; extra knockback | 0.8 | 3 | 2×3 | `XXX` / `XXX` / `.#.` |
| Scythe | Two-handed, reaped up from lower left to upper right | 1.0 | 3.8 | 2×4 | `XXX` / `..#` / `.#.` |

| Material | Tier | Durability | Blade | Greatsword | Spear | Dagger | Hammer | Scythe |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Wood | N | 59 | 4 | 7 | 4.5 | 2.5 | 6 | 5.5 |
| Stone | N | 131 | 5 | 8 | 5.5 | 3.5 | 7 | 6.5 |
| Iron | I | 250 | 6 | 9 | 6.5 | 4.5 | 8 | 7.5 |
| Diamond | IV | 1561 | 7 | 10 | 7.5 | 5.5 | 9 | 8.5 |
| Netherite | V | 2031 | 8 | 11 | 8.5 | 6.5 | 10 | 9.5 |

Materials match vanilla tools (any planks, cobblestone-like stones, iron ingots, diamonds); netherite weapons are upgraded from diamond ones at a smithing table. Named weapons:

| Weapon | Swing | Damage | Attacks per second | Reach |
| --- | --- | --- | --- | --- |
| Anchor Blade | Diagonal slash, upper right to lower left | 7 | 1.6 | 3 |
| Sunken Bell Greatsword | Raised over the shoulder, then brought down; tolls on hit | 12 | 0.7 | 3.5 |
| Mistwalker Spear | Draw back, then thrust | 8 | 1.1 | 4.5 |
| Salt Fang | Two crossing cuts in one swing | 5 | 2.6 | 2.6 |

Damage is the tooltip value (including the 1 point of a bare hand).

### Special weapons

Three special weapons only answer a hunter who meets their requirements. Short of them, the weapon hits at half strength, swings slowly and gives no heavy blow; its tooltip lists the requirements, green when met and red when not.

| Weapon | Branch | Requires | Special heavy blow | Found |
| --- | --- | --- | --- | --- |
| Snuffer's Hook | Scythe (8 damage) | The Snuffer brought down, strength 3 | After the reap, the farthest foe it caught is hooked to your feet and its attack broken off | Dropped by the Snuffer |
| Mist-Lantern Staff | Spear (6 damage) | Intellect 4 | The lantern bursts ahead: everything within 3.5 blocks is hurt and set alight, for (6 + intellect) × (1 + the charge) | `..L` / `.#.` / `#..` (L soul lantern) |
| Tide-Bell Maul | Hammer (13 damage) | Strength 5, constitution 3 | The bell tolls: everything within 4.5 blocks all around is thrown up | `IBI` / `.#.` / `.#.` (I iron block, B bell) |

## Playing

You need Minecraft Java Edition 26.3, [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer, [Fabric API](https://modrinth.com/mod/fabric-api) and Java 25. Put `mistinn-<version>.jar` and Fabric API in your `mods` folder and enter a survival world.

> [!NOTE]
> Only Minecraft 26.3 is supported. Creative mode keeps the vanilla inventory; the grid inventory is active in survival and adventure.

Existing Bell Salve stacks turn into the same amount of raw gold when the save is loaded.

### Playing with friends

The host enters a Story or Free exploration world, presses Esc, and chooses **World & Multiplayer → Open Room**. Enter a host IP or hostname your friends can reach, then copy the connection address. Guests use the same Mistinn version and paste the address in **Multiplayer → Direct Connection**. The host can close the room from the same screen, and the world stays on their computer.

Friends on different networks can connect over a reachable public address or a shared private virtual network. For a public IPv4 connection, forward the chosen TCP port (default `25565`) to the host computer and allow it through the firewall. A shared private virtual network also works when the host's ISP uses CGNAT. Guests need a verified Minecraft account to join the room. [Minecraft Java multiplayer help](https://help.minecraft.net/hc/en-us/articles/32899741198989)

Story worlds remember whether the keeper has fallen and the salt block has been turned in. Friends who join later can continue their own objectives when they reach those steps.

## Story mode

When creating a new world from the world list, choose **Story**, **Free exploration**, or **Hunter Training**. Free exploration has no mission and otherwise plays the same. Story mode has the prologue, *The Drop*, and chapter 1, *Lights Out*, so far.

Hunter Training creates a save with standard survival settings and sends you to Rust Anchor Station, a base built over the sea round a giant anchor standing slanted in the water. You wake in the barracks. The docks, the market and mess, the forge, the drill yard, the infirmary, the workshop, the archive, the bell tower and memorial wall, the radio room and the lamp hall spread over three decks, quieter the higher you go, with the drop crane alone at the top by the fog. Fifteen staff keep their posts: right-click them to talk and ask questions, and they call out as you pass; dock hands carry cargo up and down the quay, and Helm speaks over the station speakers now and then; near the forge you hear the hammer, and on the quay barrels, chains and water against the boats. Fall into the sea and you are fished out onto the nearest deck. At the end of the course the whole station gathers on the drop platform to see your bunkmate, number 3411, go down first. Then walk into the drop pod and press the console button to take your seat, and choose where to deploy in the same save: the station bell rings three times, with every ring there are fewer people on the deck, and the screen goes dark. Practice salt and gear stay at the range.

![Rust Anchor Station at dusk: the giant anchor slanted in the sea, the three decks round it and the drop crane at the edge of the fog](images/rust-anchor-station.jpg)

- **Guaranteed places**: a new story world builds an intact drop station about 150 to 240 blocks from spawn, looking for flat land first. It searches from the station for a beach to build the lighthouse on, or uses open ground farther out if it finds no beach.
- **Objectives**: shown in the top-left corner with a hint and the distance, lit up when they change. The amber diamond on the minimap is where to go.
- **The radio**: the drop station's radio has burned out. Mend it by using it with 4 redstone, and Helm, Rust Anchor's operator, starts calling; the lines appear as typed subtitles.
- **Story cards**: chapters open and close on full-screen pixel art; click to continue, `Esc` to skip.
- **Scenes**: when the Snuffer falls, black bars close in and the view leaves you to circle it while it says its last words; `Esc` skips.
- **Mission journal** (`K`): the objectives so far and every call you have heard.

| Chapter | Objectives |
| --- | --- |
| Prologue: The Drop | Gather 10 salt → find an intact drop station → repair its radio |
| Chapter 1: Lights Out | Reach the fog-shore lighthouse → rest at the bell by its door → climb to the lamp room → bring down the Snuffer → hand the Rust God's salt block in at a drop station |

## World and structures

Rust Anchor drops its hunters into the zone from beyond the edge. Where you wake is not a real drop station but one of two: a long-abandoned **broken drop station**, or a **counterfeit drop station** someone built in Rust Anchor's image. You always start on its platform, never inside a wall, with a *Hunter's Guide* in your hotbar.

| Structure | Where | What it holds |
| --- | --- | --- |
| Drop station | On land, uncommon | A one-time bell; a supply barrel with stone and iron weapons, rations and tier I–II gear |
| Salt cairn | Beaches and badlands | A chest on top: salt, quartz, low-tier backpacks, sometimes a Salt Fang |
| Anchor wreck | Beaches | A huge anchor half sunk in the sand, a chest buried beside it: iron weapons, tier I–III armour, sometimes an Anchor Blade |
| Mist watchpost | On land, rare | The lookout's chest at the top: iron and diamond weapons, tier II–IV gear, sometimes a Mistwalker Spear or a one-time bell |

| | |
| --- | --- |
| ![An intact drop station on the plains at dusk](images/drop-station.jpg) | ![A mist watchpost at the forest's edge](images/watchpost.jpg) |
| ![An anchor wreck half sunk in a beach at dawn](images/anchor-wreck.jpg) | ![A salt cairn in the badlands](images/salt-cairn.jpg) |

## Body parts

![The lamp room at night: the Snuffer's lantern burns cold blue, the lamp stands lit again](images/snuffer-dark.jpg)

| Part | Health | Protected by |
| --- | --- | --- |
| Head (with the face) | 80 | Crown: helmet; face: mask |
| Torso | 80 | Body armour |
| Left and right arm | 80 each | Nothing |
| Left and right leg | 80 each | Leggings and boots |

- **Death**: any three parts destroyed. Damage to a destroyed part spreads, weakened, over the others, so you cannot become unkillable.
- **Where hits land**: by height and direction. High from the front is the face, from behind the crown; the middle is the torso or the arm on that side; low is the leg on that side. Falls land on both legs, drowning and starving on the torso, fire and explosions over the whole body.
- **Recovery**: living parts that are no longer losing blood mend slowly while your food is at 6 or more. Potions and golden apples mend the most hurt living parts first. A surgery kit brings back one destroyed part at a time.
- **Destroyed parts**: legs slow you and cut your jump; arms weaken blows, attack speed and mining; the torso makes you hungry faster; the head darkens the edges of your view and stops the minimap.
- **HUD**: a small figure where the hearts were, each part green to red by its health; a bleeding part flashes red, a broken one shows a crack, and tourniquets and splints show too. The inventory lists each part's wounds and the time left on them.

### Bleeding and broken bones

| Wound | From | What it does | What to do |
| --- | --- | --- | --- |
| Bleeding | Creatures' blows, arrows | The part loses 0.5 a second until it is destroyed, and does not mend by itself | A **tourniquet** stops it. After 3 minutes under a tourniquet, the part starts to die. Only a surgery kit takes it off |
| Broken bone (arms and legs) | Falls, explosions, very heavy blows | The limb works as if destroyed before it is: a broken leg cannot roll, only dive, and slows you; a broken arm weakens blows and mining | A **splint** holds it for 4 minutes, then it is broken again; a **surgery kit** sets it |

- A **surgery kit** treats one part at a time, in order of priority: destroyed parts, broken bones, tourniquets, then bleeding. A destroyed part comes back with 24 health.
- **Painkillers** temporarily suppress the effects of destroyed parts.

## Vanilla monsters and executions

| | |
| --- | --- |
| ![At sunset on the grass by a drop station, a husk twists the hunter's arm up behind their back and bends to bite the shoulder](images/execution-grapple.jpg) | ![A warden has slammed the hunter into the ground and bends over them, fists swinging](images/execution-fist-slam.jpg) |
| ![A ravager has thrown the hunter down on the grass and bites into their belly, shaking them](images/execution-charge.jpg) | ![An enderman holds the hunter's head in both hands and lifts them off the ground, about to break the neck](images/execution-neck-twist.jpg) |
| ![A skeleton stands with its bow drawn and looses an arrow at the hunter kneeling across from it](images/execution-headshot.jpg) | ![The mission-end screen after an execution: the word executed in crimson, and below it the heart bitten open with a knife in it](images/mission-end-execution.jpg) |

- **Hearing and smell**: what a hunter does makes noise. A hostile monster nearby with nothing to chase freezes, turns its head toward the sound, then walks over to look, and gives up if it finds nothing. Heard is not seen: it only locks on once it sees you.

  | Action | Carries (blocks) |
  | --- | --- |
  | Swing, roll, dive | 10 |
  | Sprinting, slide | 12 |
  | Starting a treatment | 16 |
  | Heavy blow | 18 |
  | Resting at a golden bell | 24 |

  Crouching halves swings, rolls, slides and sprinting; treatment and the bell are as loud crouched. A bleeding hunter gives off a scent every two seconds that monsters within 20 blocks smell without seeing them, 4 blocks further for each destroyed part. Creatures that only fight when provoked (endermen, zombified piglins, wolves, polar bears) are left alone, and piglins, hoglins, wardens, breezes and the creaking hunt the vanilla way.
- **Hunting as a pack**: when three or more melee monsters hunt the same hunter, two at most go in to strike and the rest spread round you about 4.5 blocks off, circling and watching. A striker that has struck, or taken too long, gives its turn to whichever has worked furthest round behind you, so the blows keep coming, often from where you are not looking. One or two monsters each hunt as they please; once both your legs are gone they stop taking turns, and whoever can reach you goes in.
- **Still while watched**: the walking dead (zombies, husks, drowned and zombie villagers) hunting you from 12 to 32 blocks away stop dead and stare back while you look straight at them; the moment you turn away they come on, faster than they walk. A stare holds one for three seconds at most, after which it comes on whether you look or not. Within 12 blocks they hunt as before. No other monster does this.
- **Stalking and windups**: a monster that has a hunter closes in low. Before each melee blow it winds up (zombies and spiders quickly, wardens and ravagers slowly) with a cry, then lunges. A roll or a step back during the windup avoids it; backing off too far is chased a short way.
- **Voices**: 35 creatures (zombies, piglins, the warden, endermen, spiders, wolves with a set for each of their seven variants, polar bears and more) speak with CC0 recordings from OpenGameArt, layered and reshaped. Skeletons and the creaking have no throat; their windups are bone and wood.
- **Executions**: when both legs are destroyed, a monster in reach pins the hunter and kills them. Each family has its own paired move:

| Move | Who | How |
| --- | --- | --- |
| Grapple and bite | Zombies, zombie villagers, husks, drowned, zombified piglins | Twists the wrist behind the back until it breaks, forces the hunter down, bites the shoulder and tears |
| Neck pounce | Spiders, cave spiders, wolves, silverfish, endermites, phantoms, and every young monster | Knocks the hunter flat and rides them, biting the throat |
| Leap and slam | Slimes, magma cubes, sulfur cubes | Leaps high and flattens the hunter, bounces, slams again |
| Fist slam | The warden, the creaking | The warden lifts the hunter overhead and slams them down; the creaking knocks them down; both arms are broken, then the ribs |
| Charge and maul | Ravagers, hoglins, zoglins, polar bears | Throws the hunter down, bites into the belly, shakes and drags them |
| Stomp and stab | Vindicators, piglins, piglin brutes, vexes | Kicks the hunter down, stands on the chest and drives the weapon in |
| Slash | Wither skeletons, and skeletons without a bow | Cuts down both arms, holds the forehead and cuts the throat |
| Neck twist | Endermen | Wrenches both wrists, lifts the hunter by the head and breaks the neck |
| Headshot | Skeletons, strays, bogged and parched with a bow | Stands still, aims, and puts an arrow through the head |

- Another hunter's blow on the monster saves the victim; the victim's own blows do not. Where the monster has no room to stand (a corridor, a cave) there is no execution and the blow is an ordinary one.
- Bodies do not pass through each other: every move is measured against every monster, tick by tick, and no model box goes more than 0.1 blocks into another.

## Death and the end of the mission

![Struck down on a drop station platform at dusk, a hunter on one knee, a hand on the floor, the pod behind](images/death.jpg)

- **Killed by a creature** (keepers too): every other creature vanishes and the view swings out in front of and above the hunter, who drops to one knee, holds themselves up on a shaking hand, then topples onto one side and turns to smoke, leaving the residue.
- **Any other death** (hunger, a fall, drowning, fire): a stagger, then a fall face down, and the same smoke.
- **The end of the mission**: the view darkens to red words saying the mission is terminated, the cause of death and a line from Rust Anchor that fits it. *Return to the mission* respawns you at your golden bell; *Rest* saves and goes back to the title screen. Below them, a heart run through by a short knife slowly stops; after an execution the heart is bitten open, and the word *executed* in the cause is crimson.
- The gravestone rises as the body turns to smoke. If something was put down on that spot in the meantime, it rises nearby instead and the minimap mark follows.

## Swings, stamina and rolls

| | |
| --- | --- |
| ![A hunter swings a netherite hammer, the body dragged round by its weight](images/swing.jpg) | ![A hunter halfway through a sideways roll on a drop station platform](images/roll.jpg) |

- **Swings**: every blow has four beats: the wind-up strains harder towards its end and the arms shake, the strike breaks loose and accelerates, the weapon's weight carries the body past the line, and the recovery hauls it back. Heavy weapons wind up and recover longer.
- **Sword qi**: every blow leaves a cut of vanilla-style pixel light where it lands, turned to the blow and mirrored for the left hand.
- **Heavy blows**: with a weapon in hand, **hold use** to charge (a gauge appears under the crosshair) and **let go** to strike. A heavy blow hits everything in its reach, not only what the crosshair is on, for the weapon's damage × (1 + the charge), and 4% more per point of strength; at a full charge a bell chimes, and the blow breaks off the attack a creature it hits was winding up. Let go under 30% and nothing happens. You walk slowly and cannot sprint while charging, and stamina does not come back; letting go costs the swing's stamina × (1 + the charge) and 2 durability. Speed makes the charge quicker.

| Branch | Full charge | Heavy blow |
| --- | --- | --- |
| Dagger | 0.6 s | Two crossing cuts across 90° in front, 0.6× each |
| Blade | 0.8 s | A diagonal cut across 110° in front |
| Spear | 0.9 s | Straight through a whole line, 2 blocks farther than usual |
| Scythe | 1 s | A reap across 200° in front that drags what it catches toward you |
| Hammer | 1.2 s | A sweep across 180° in front, with heavier knockback |
| Greatsword | 1.3 s | A chop across 50° in front, 1 block farther, and the ground shakes where it lands (half damage) |

| | |
| --- | --- |
| ![Heavy blows seen from behind: the scythe and the hammer drawn far back to the right, the other hand off the haft, then swept round to the left with a low cut of light](images/heavy-blows.png) | ![Heavy blows' sword qi in first person: the blade's diagonal, the greatsword's chop and its ground wave, the dagger's crossing cuts, the scythe's low sweep](images/sword-qi.png) |

- **View**: in first person the view turns, dips and tilts with the swing and jolts on a hit (harder for a heavy blow). Press `J` to turn this off; the setting is kept in `config/mistinn-client.properties`.
- **Stamina**: 100 at most (3 more per point of constitution). Swings (misses too), sprinting and rolls cost it, and it starts coming back a second after you stop spending. At zero you cannot sprint or swing.

| Weapon | Stamina per swing |
| --- | --- |
| Dagger | 7 |
| Blade | 11 |
| Spear | 13 |
| Greatsword | 22 |
| Hammer | 24 |
| Scythe | 15 |
| Anything else | 4 |

- **Roll** (Left Alt): 22 stamina, in the direction you are moving, or a step back if you are not. You cannot be hit for 10 ticks on whole legs, fewer as they are hurt, and the roll gets shorter too.
- **The arms shape the roll**: a whole arm tucks in and pushes off; a destroyed arm hangs and is flung about, and the roll leans to that side; with both arms destroyed you roll over a shoulder.
- **Dive**: an unsplinted broken leg or two destroyed legs without painkillers force a dive. It costs 28 stamina and gives no protection from blows. The usual landing spreads 12 points of damage over the living parts of the upper body.
- A destroyed torso slows stamina recovery; destroyed legs make sprinting cost more.

## The fog-shore lighthouse and the Snuffer

Rust Anchor says the coasts at the world's edge are lined with dead lighthouses, each held by a keeper. Now and then a beach has a **fog-shore lighthouse**. A golden bell waits in a stone shrine by the door. The spiral stair climbs past the supplies to the lamp room, where the Snuffer waits.

| | |
| --- | --- |
| ![The fog-shore lighthouse and the keeper's cottage](images/lighthouse.jpg) | ![The Snuffer with its long hook by the rail of the lamp room](images/snuffer.jpg) |

| Move | Tell | Where it lands |
| --- | --- | --- |
| Sweep | The lantern flares, it leans back, and metal scrapes | Torso, or the arm on that side |
| Slam | Salt crackles underfoot and grains rise where it will land | The shockwave takes both legs |
| Hook | The hook is drawn back and the chain snaps taut | Drags you in, then strikes your face; only a mask helps |

- 300 health, 8 armour, and a boss health bar.
- Below half health every lamp in the room goes out and you are in darkness. Right-click the four lamp stands to light them; with all four lit it staggers for five seconds and takes half again as much damage, then snuffs them out one by one.
- It drops **the Rust God's salt block**, 3000 salt, and a 30% chance of one piece of tier III–IV gear.
- In the lamp room, or within 32 blocks once it is wounded, the keeper's fight music plays. Below half health, a low bell marks the blackout and leads into the dark track. The music stops when the fight is over.

## The fog-bell station and the Sunken Bell

Now and then, in the shallows off the misty shore, a **fog-bell station** stands on a reef: in the old days a bell was rung here in the mist to guide the boats in. A golden bell waits in a stone shrine where the boats tied up. The square tower has three floors, the lowest half under water, a stair climbing its walls and stores that get better the higher they are; on top, in an open belfry, a great bell hangs, and the second keeper, **the Sunken Bell**, waits beneath it.

The Sunken Bell is a drowned bell-ringer, his head and shoulders sealed in a cracked bronze bell grown over with verdigris and barnacles, the bell's chained tongue in his fists. He is shorter than the Snuffer but far broader, and thinks with the same brain that looks ahead and learns you.

- 360 health and 10 armour; he cannot be pushed, and has a boss bar.
- **Toll**: he strikes his own bell with the clapper and a ring rolls out along the floor. Standing on the floor where it passes takes a blow to the head; jump it or roll through it. The bell hums before a toll.
- **Mist layers**: each toll thickens the mist of his room by a layer, and how far you see shrinks from 16 blocks down to 4.5, the map with it; a layer thins after fifteen quiet seconds. At the fourth layer he gives the **deep toll**: three tolls, each ring further than the last.
- **Ringing it back**: a fully charged heavy blow rings his own bell at him and thins the mist a layer; three in a row throw him off balance for five seconds, taking half again as much damage, and thin the mist by two more.
- Below half health the bell cracks: his tolls come double and the mist is slower to thin, and he leaps to bring the bell down over you: shut in under it you can neither see nor move, and he tolls with you inside. Roll out before he lands.
- Once you are up in the belfry, the mist closes the stair behind you until he falls.
- He drops the **Rust God's salt block**, 3500 salt, and a 30% chance of a piece of tier III–IV gear.

## Stats and skills

| | |
| --- | --- |
| ![The skills page of the Hunter's Guide: the slide on the left, four rows of minor nodes for strength, speed, constitution and intellect, and the major nodes not yet open on the right](images/guide-skills.png) | ![The stats page of the Hunter's Guide: the four stats with what they do now, and the salt and rust god salt carried](images/guide-stats.png) |

A hunter starts growing once a keeper falls and its rust god salt comes home. Open the **Hunter's Guide** on its Skills page: major nodes are unlocked with a rust god salt, the first being the **slide**; after that, salt buys minor nodes one at a time, each adding to one of four stats. Every node needs the one before it; click once to choose a node and again to learn it.

| Stat | Per point (0 to 20) |
| --- | --- |
| Strength | Melee damage +3% |
| Speed | Moving +1%, swings +1.5%, roll speed +1.5%; one more tick of roll dodge every 5 points |
| Constitution | Each body part holds 2 more (up to 120), stamina 3 more (up to 160), bleeding and fractures 3% less likely |
| Intellect | Treatment and repair take 2.5% less time (never less than half), surgery kits and injectors heal 3% more |

- **Major nodes** take one rust god salt, from the hands, the hotbar or the grid. Later keepers guard the other major nodes; the guide shows them as not yet open.
- **Minor nodes** cost 200 salt for the first and 100 more for each one after. Each of the four branches has five.
- **Slide** (crouch while running): feet first for about five blocks, slowing as it goes; 18 stamina, a 1.2 second cooldown, and it breaks off a treatment in hand. Every hostile creature in the way is swept off its feet for 3 damage, once each. The slide gives no dodge, and two dead legs cannot slide.
- Skills are saved with the hunter and kept through death. The guide's Stats page shows what each stat does for you now, and the salt and rust god salt you carry.

## Achievements

| | |
| --- | --- |
| ![The deeds page of the Hunter's Guide: how many are earned and the salt they paid, then the achievements one per row, earned ones bright and the rest dim](images/guide-achievements.png) | ![The achievement notice at the top right of the screen: the emblem and name on a strip of paper](images/achievement-toast.png) |

When you do something worth remembering, a notice slides in at the top right, a bell chimes and some salt is paid (the first time only). Every achievement is listed on the **deeds** page of the Hunter's Guide: earned ones bright, the rest dim, with the count and the salt earned at the top. Achievements are saved and kept through death; bringing down the Snuffer is also what the Snuffer's Hook asks for.

| Achievement | How | Salt |
| --- | --- | --- |
| First Hunt | Bring down a monster yourself | 50 |
| Armory | Bring one down with each of the six branches: blade, greatsword, spear, dagger, hammer, scythe | 600 |
| Three in One | Hit three or more with one heavy blow | 200 |
| Broken Off | Break off a monster's attack with a full heavy blow | 200 |
| Close Shave | Roll through an attack | 150 |
| Swept Away | Hit a monster with a slide | 150 |
| First Skill | Learn the slide in the guide | 100 |
| Mastery | Bring any stat to 5 | 300 |
| All Round | At least 1 in all four stats | 200 |
| Chosen | Land a special weapon's own heavy blow | 400 |
| Snuffer Slain | The Snuffer falls with you near | 1000 |
| Sunken Bell Silenced | The Sunken Bell falls with you near | 1200 |
| Steady Hands | Save a destroyed part with a surgery kit | 150 |
| The Bell | Rest at a golden bell | 50 |
| Gathered Back | Take your residue back from your gravestone | 100 |
| Salt Merchant | Carry 5000 salt | – |

## Controls

| Input | Action |
| --- | --- |
| Use a golden bell | Ring it to set your respawn point |
| Right-click a gravestone | Take back the salt and things in the residue |
| `M` | Minimap zoom (1, 2 or 4 blocks per pixel) |
| `E` | Inventory (gear, chest rig, backpack and body) |
| Right-click the helmet slot | Switch to the mask slot |
| Right-click a lamp stand | Light it |
| Use a drop station radio | Mend it with 4 redstone; in the story, hand in the Rust God's salt block |
| `K` | Mission journal |
| Left Alt | Roll or dive, depending on leg injuries and painkillers |
| Crouch while running | Slide (once learned) |
| Hold use with a weapon | Charge; let go for a heavy blow |
| Use the Hunter's Guide | Open the guide: pages, stats and skills |
| `J` | Toggle the first-person swing view |
| Click a cell | Pick up or put down |
| Drag | Move between cells, or between the grid and the hotbar or a chest |
| `R` while carrying | Rotate |
| `Q` over an item | Drop it |
| Shift-click | Grid item to the hotbar; chest item into the grid |

## Tiers and gear

| Tier | Colour | Helmet | Mask | Body armour | Leggings | Boots | Chest rig | Backpack |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| N | Rust brown | Rustleather Hood | Rustleather Face Wrap | Rustleather Vest | Rustleather Leg Wraps | Rustleather Boots | Rustleather Bandolier | Rust-Buckle Sack |
| I | Grey | Dockhand Helm | Dockhand Goggle Mask | Dockhand Plate | Dockhand Leg Guards | Dockhand Work Boots | Dockhand Belt Rig | Dock Canvas Bag |
| II | Green | Tidewatch Helm | Tidewatch Mask | Tidewatch Cuirass | Tidewatch Greaves | Tidewatch Waders | Tidewatch Chest Rig | Tidewatch Pack |
| III | Blue | Anchorguard Helm | Anchorguard Jaw Guard | Anchorguard Plate | Anchorguard Greaves | Anchorguard War Boots | Anchorguard Chest Rig | Anchorguard Rucksack |
| IV | Purple | Mistwalker Visor | Mistwalker Filter Mask | Mistwalker Harness | Mistwalker Greaves | Mistwalker Climbing Boots | Mistwalker Chest Rig | Mistwalker Pack |
| V | Gold | Bellkeeper Crown | Bellkeeper Faceplate | Bellkeeper Mail | Bellkeeper Greaves | Bellkeeper Iron Boots | Bellkeeper Tactical Vest | Bellkeeper Frame |
| VI | Red | Rust God's Skull | Rust God's Face | Rust God's Scales | — | — | Rust God's Harness | Rust God's Reliquary |

Helmets last 70–640, masks 50–480, body armour 110–940, leggings 90–610, boots 60–430; higher tiers protect more and armour and leggings get heavier. A helmet only covers the crown and a mask the face; body armour covers the torso, leggings and boots the legs, and nothing covers the arms. Vanilla items follow their material (iron I, gold II, emerald III, diamond IV, netherite V) or vanilla rarity. Salt repairs gear at an anvil.

## Grid inventory

| Backpack | Layout (top pockets + main) | Cells | Speed |
| --- | --- | --- | --- |
| N | 3×2 | 6 | — |
| I | 4×3 | 12 | — |
| II | 1×2, 1×2 + 3×4 | 16 | −1% |
| III | 1×2, 2×2, 1×2 + 4×4 | 24 | −1% |
| IV | 1×2, 2×2, 1×2 + 4×5 | 28 | −1% |
| V | 1×2, 3×2, 1×2 + 5×5 | 35 | −2% |
| VI | 1×2, 3×2, 1×2 + 5×6 | 40 | −2% |

| Chest rig | Layout | Cells |
| --- | --- | --- |
| N | 1×2 ×2 | 4 |
| I | 1×2 ×4 | 8 |
| II | 2×2 + 1×2 ×4 | 12 |
| III | 2×2 ×2 + 1×2 ×4 | 16 |
| IV | 2×2 ×2 + 1×3 ×4 | 20 |
| V | 2×2 ×3 + 1×3 ×4 | 24 |
| VI | 2×3 ×2, 2×2 + 1×3 ×4 | 28 |

Pockets are four 1×1 cells and the safe box is 2×2. Pickups fill the pockets, then the chest rig, then the backpack. On death the pockets, chest rig, backpack and mask go into the gravestone with everything else you carry; the safe box stays. Footprints: gems and ingots 1×1, sticks 1×2, tools and swords 1×3, ore and other blocks 2×2, armour 2×2 or 2×3; the injector, painkillers and tourniquet 1×1, the surgery kit and splint 1×2, and repair kits grow with their tier (armour: III 1×2, IV 1×3, V/VI 2×2; helmet: III/IV 1×2, V 1×3, VI 2×2). A backpack's contents live in the backpack item, so they travel with it. The backpack and chest rig you wear show on your body.

## Building from source

JDK 25 is required. From a OneDrive folder or a path with non-ASCII characters use `build.ps1`; otherwise `./gradlew build` works.

```powershell
.\build.ps1 build               # mistinn-<version>.jar
.\build.ps1 runClient           # development client
.\build.ps1 runClientGameTest   # a real client plays the core loop and saves screenshots
```

Textures and models are original work by MoriTeahouse. Sound effects combine edited CC0 recordings with noise synthesis made for the mod. The repository holds the finished PNG, OGG and JSON files. See [CREDITS.md](../CREDITS.md) for the audio sources.

## What comes next

1. **The rest of the story**: chapter 2, *The Salt Road*, then the keepers of the Sunken Bell and the Rust Wheel, and the Mist Inn with the endings.
2. **More of the Sunken Bell**: its own spoils and its chapter of the story ([design, in Chinese](phase3-design.md)).
3. **The Rust Wheel**: a rolling, ramming machine with its weak point on the axle at its back, built on the same keeper moves and brain.
4. **Later**: co-op.

## Contributing

Bug fixes, features, tests, art and documentation are welcome; read the [contributing guide](CONTRIBUTING.en.md) first.

> [!IMPORTANT]
> AI-assisted contributions are treated like hand-written ones. Pull requests must pass security review, and all textures and models must be original to the project.

## License

Copyright (c) 2026 Keeiv.

Licensed under the **GNU Affero General Public License v3.0 only (AGPL-3.0-only)**; see [LICENSE](../LICENSE) and [NOTICE](../NOTICE). The original textures and models share the code's license. The sound effects use edited CC0 recordings, some layered with the project's own noise synthesis. The score is arranged from CC0 instrument and metal percussion samples. Every sound file's sources are listed in [CREDITS.md](../CREDITS.md).

> [!NOTE]
> The wordmark is drawn with [Fusion Pixel Font](https://github.com/TakWolf/fusion-pixel-font) (SIL Open Font License 1.1). Minecraft is a trademark of Mojang Studios; this mod is not affiliated with Mojang or Microsoft.
