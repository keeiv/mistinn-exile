<div align="center">

**EN** | [繁中](../CONTRIBUTING.md)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="../assets/logo/mistinn-wordmark-en-light.png">
  <img src="../assets/logo/mistinn-wordmark-en.png" alt="Mistinn: Exile" width="226">
</picture>

</div>

# Contributing

Bug fixes, features, tests, art and documentation are welcome. Contributors use forks and pull requests; maintainers may commit agreed changes to `main` directly.

## Scope

Small fixes, balance tweaks and documentation can go straight to a PR. New dependencies, save-format changes (player data, backpack contents, block entities), new core systems and changes to established rules (the death penalty, tier colours, grid rules) need an issue first that explains the purpose, compatibility and how old saves migrate.

## Before you start

- **Check the issues first.** Before building a feature, see whether someone has already said they are doing it in an issue, and whether a pull request for it has already been merged. If someone is on it, talk it over in that issue instead of doing it twice.
- **Bugs start as issues.** Open an issue with steps to reproduce. A fix sent as a pull request with no issue is not turned away, but it is looked at later.
- **Report typos in an issue.** Typos, punctuation and small wording slips go in an issue and the team fixes them together. Send a pull request only when a whole passage is wrong.

## Know the world first

The code here is written after the story. Names like `line.fallen()`, `line.walker()`, `home.lit()` and `seaReached()` are not arbitrary: each one stands for something in the world — the tillers who fell, the one walking the way now, a hub woken by three crystal cores, the moment the sea is seen. Reading only the code, it is easy to make a change that is right as code and wrong in the world: treating the fallen as a plain death counter, say, or lighting facilities in a homestead nobody has woken.

So before contributing gameplay, story, creatures, structures or anything a player sees:

- Read the README's sections on Story mode, Terra, the sky rifts and the Path of Longing, and the matching pages of the in-game hunter's guide.
- Play the part you are changing: fight that boss, walk that chapter, wake that hub.
- Check the glossary below for what a name you are touching means in the world.

When you change things:

- Name new classes, methods and variables with the world's words, not neutral stand-ins (do not rename `fallen` to `deaths` or `walker` to `currentPlayer`).
- Write comments that explain *why* in the world's terms, so the next person can read what the code means in the story.
- New story, lines and text must follow the established tone and setting, and must not borrow characters, lines, lyrics or names from other works.
- When unsure about a piece of the setting, ask in an issue before writing it.

| Word in the code | What it is in the world |
|---|---|
| hunter | a hunter, the player, dropped into the mist by Rust Anchor |
| salt | salt, the world's currency; on death it becomes residue, left at a gravestone |
| bell | a golden bell: ring it to rest and set your respawn |
| keeper | a keeper, a boss holding a lighthouse or stronghold; the first is the Snuffer |
| domain | a place of change: a Terra highland (TERRA) or a sky rift (RIFT) |
| tiller | a crystal tiller, a native of the Terra highlands |
| settlement / homestead | the tillers' settlement; once its hub is woken (`lit()`) it is every hunter's homestead |
| hub / cores | the hub at the settlement's middle, woken by three crystal cores |
| elder / crown | the crown elder, the dragon keeping the highland's heart and letting no one leave |
| drake | a crystal drake |
| siege | a crystal tide: lizards swarming a woken homestead by night |
| squall | a crystal squall, a storm of crystal grit over the highland |
| rift / herald / hound | a sky rift, a rift herald, a rift hound |
| path / line | the Path of Longing and how far it has come |
| walker | the tiller walking the way now (the next takes over each time one falls) |
| fallen | the tillers who fell, in the order they fell; when twelve have fallen the way is cut |
| sea / `seaReached()` | out of the highland, the sea seen: the way is walked (ended) |

## Horror and heavy content

> [!CAUTION]
> **Adding gore, suicide or jump scares without reason is strictly forbidden. Such content with no reason and no context will not be accepted.**

The horror, death and heavy themes in this work each have a place and a reason in the story. If your contribution includes such content:

- Say in the pull request where it comes in the story, why it must be there, and how it fits what is already established.
- Jump scares and sudden images or sounds must all keep to the content level in Mistinn Settings: none of them at Standard or Gentle.
- Heavy lines in the story come with a gentle version, and heavy story pictures go on the list left out at Gentle (see "What counts as heavy" below).
- Suicide and self-harm appear only where the story truly needs them: never describing methods or details, and never glorifying them.

### What counts as heavy

Whether a part of the story is heavy depends on what the reader sees and feels, not on the words it uses. Words like corpse, bone, coffin, slay or death are not heavy in themselves; a name like 憧憬的屍之道 (the Path of Longing) reads as a slightly over-the-top title, not as something heavy.

Three things are heavy:

1. **Death and wounds seen plainly**: the dead, blood and flesh, wounds, or the dead being handled, written or drawn so the reader can picture them.
2. **Staying inside someone giving themselves up**: a person harming or ending themselves, or giving up on living, with the telling left there and never turning.
3. **Fear coming for the reader**: something closing in, watching, remembering you, with no way out.

Not heavy: names and titles, game terms (execution, fracture, bleeding, necrosis), grief and missing someone, simply saying that someone died, and turns of the fourth wall made with warmth or a joke.

Gentle deals only with the parts of the story (story cards, the radio, the quotes on the death screen) that are one of these three:

- **Lines**: a gentle version under the same key with `.light`. The fact stays and the eye moves off it: say what happened, not what it looked like; the fourth wall stays, told so it does not frighten.
- **Pictures**: story pictures of the first kind are not shown at Gentle; the card keeps only its words. The list is `HEAVY` in `Haunting`.
- **Never changed**: the names of items, creatures, places, chapters, modes and achievements, item text, the bestiary and the guide.

## Using AI tools

**AI-assisted programming is allowed**, on these terms:

- **Say so.** Tick whether you used AI in the "AI assistance" part of the pull request description. CI fails an outside contributor's pull request that ticks neither.
- **AI is not a co-author.** A commit message's co-author trailers must not name an AI tool. CI checks that every co-author is a person's GitHub account: to credit a co-author, use their GitHub noreply address (`ID+login@users.noreply.github.com`).
- **You are responsible.** You need not understand every line an AI wrote, but what you send is yours: if it breaks something or does harm, the responsibility lies with whoever sent it.

**A pull request is merged only when it has no security issues**, whoever or whatever wrote it. Before sending one, check:

- The server does not trust the client: grid actions are requests, checked on the server (see `GridService`). New packets range-check every field on the server.
- Nothing duplicates items: code that moves items into or out of the grid takes the same amount from the source.
- File paths, URLs and commands never use unchecked input.
- New dependencies exist, are maintained and have no known high-risk advisories. Tools sometimes invent package names; verify before installing.

Comments are for future maintainers: say why the code does what it does; do not leave conversations with tools or edit history.

## Development setup

JDK 25; the Gradle wrapper downloads Gradle.

```powershell
git clone https://github.com/keeiv/mistinn-exile.git
cd mistinn-exile
.\build.ps1 build
```

Use `build.ps1` from OneDrive or non-ASCII paths; elsewhere `./gradlew build` works.

## Code and assets

- Clear English names; comments and doc comments in English.
- Player-facing text lives in `assets/mistinn/lang`, in both `zh_tw.json` and `en_us.json`. Gear gets a two-line story (`.lore.1`, `.lore.2`).
- Server logic in `src/main`, screens and animation in `src/client`; shared code never references client classes.
- Use a mixin only when no event fits; keep injection points small and say why.
- New items get a tier in `Tiers`, a footprint in `ItemSizes`, and a place in the creative tab.
- Textures, models and sound follow the [art standards](#art-standards) below.

## Art standards

### Sources

- **Original work only.** No textures, models, sounds or music taken from other games, resource packs or the web.
- **AI-generated art is banned outright**: images, model textures, music, sound and voice alike, such as music generation services like Suno and image generators of every kind.
- **Art made by a program is judged case by case.** Art drawn or synthesised step by step by a script (`.py` and the like) is accepted or not by the developers and the core team on how it turns out and how it looks. There is no formal standard for looks; the team's judgement decides.
- Sound effects may be made from verified CC0 recordings; list the sources of recordings and music by file in [CREDITS.md](../CREDITS.md).
- Commit finished files only (PNG, OGG, JSON); keep working files (project files, generator scripts, layered images) outside the repository.

### Item and block textures

- Follow vanilla: each part outlined in its own darkest tone, light from the upper left, 4–5 tones per material; held models are vanilla extruded sprites.
- Tier colours are fixed: N rust brown, I grey, II green, III blue, IV purple, V gold, VI red.
- Textures are square; only animated textures with a `.mcmeta` may be strips.

### Models

- New creatures get models of their own, not a vanilla mob with a new texture.
- A Java block model spans -16 to 32 (at most 3×3×3 blocks), and each element turns about one axis in 22.5° steps; bigger facilities are split into several blocks (the hub and its spire, the three-piece relay pylon).
- The crystal tillers' facilities use off-white panels `#d9dcd5`, dark grey frames `#40454c`, yellow-and-black hazard `#e3b437`/`#24272b` and crystal teal `#62e3d2`. Hazard stripes go on edges and floors, not stacked row after row up a wall.
- Parts that light up have a lit and a dark texture, switched by the block's `lit` state.

### Story cards, the guide and sound

- Story cards are 320×180 opaque RGB pixel art, with ordered (Bayer) dithering for fog, sky and soft light, and the bottom 50 pixels kept calm for subtitles.
- A guide page fits in 16 lines at 128 pixels wide.
- Sound is OGG: positional effects in mono, music in stereo and seamlessly looping. Every sound event except music has a subtitle.

### Review

- **Every piece of art from contributors outside the developers and the core team is reviewed by hand by the developers and the core team.** When a pull request carries art, CI labels it `needs-art-review` and blocks the merge until a team member approves the pull request at its latest commit; anything pushed after the approval needs approving again.
- Attach in-game screenshots of the art to the pull request; for a facility that lights up, one lit and one dark.
- **Approved:** the code and the art are merged together.
- **Not approved:** if the code passes review, the code alone is merged. A developer takes the art out of the pull request; it is then left to the core team, or you may send a fix in a new pull request within 14 days. Keep "Allow edits by maintainers" ticked on your pull request so the art can be taken out.

## Tests

```powershell
.\build.ps1 runClientGameTest
```

The client game test opens a real game window, plays weapon swings, resting and respawning at a bell, death and residue, the grid inventory and tiers, and saves screenshots to `build/run/clientGameTest/screenshots`. Every check must pass; when you change what is on screen, look at the screenshots. New features add observable checks (which cell an item landed in, what survived death), not only screenshots.

Playing with friends is normally tested over a direct connection: the room test logs guests in from this computer and checks the invite and the room key. To test it through the real relay as well (needs the internet), add `-PliveRelay`; the test then installs e4mc and logs guests in from outside through the relay:

```powershell
.\build.ps1 runClientGameTest -PonlyTest=MistinnRoomTest -PliveRelay
```

Photos for the README and the website are staged in the game with photo mode:

```powershell
.\build.ps1 runClientGameTest -PphotoMode
```

To reshoot only some scenes, add `-PphotoSet=`, for example `-PphotoSet=death,executions` (choices: `title`, `places`, `hunter`, `structures`, `lighthouse`, `death`, `executions`, `story`, `inventory`).

Photo mode adds Sodium and Iris and shoots each scene at 1920×1080 on the highest settings into `build/run/clientGameTest/photos`. The shader pack is not shipped; put Complementary Reimagined r5.9.3 in `build/run/clientGameTest/shaderpacks` and enable it in `config/iris.properties` in the same run folder.

## Checks

Run `.\build.ps1 build` before committing. CI runs `./gradlew build` on Linux with JDK 25; the client game test needs a display and runs locally for now.

Every pull request also has three checks that must pass:

- **Art review**: art from contributors outside the developers and the core team passes only once a team member has approved the pull request at its latest commit. A change touching more files than can be listed always goes to a person.
- **AI disclosure**: for contributors outside the developers and the core team, one box under "AI assistance" in the description is ticked.
- **Co-authors**: every co-author named in the commit messages is a person's GitHub account.

An outside pull request linked to no issue is labelled `no-linked-issue` and looked at later; it is not blocked.

`build` also runs the code and comment style check (Checkstyle; the rules are in `config/checkstyle/checkstyle.xml`), and any one broken rule fails it: tabs for indentation, braces on every block, one statement a line, at most 200 columns; `final` parameters, no unused imports or locals; a Javadoc on every type whose first sentence ends in a full stop or a colon, a space after `//`, and no TODO left in a comment. To run only the style check: `.\build.ps1 checkstyleMain checkstyleClient checkstyleGametest`.

## Commits and pull requests

Commit messages and PR titles use Conventional Commits prefixes with a short Traditional Chinese summary: `feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `test:`, `chore:`. A PR follows the template: the problem and the new behaviour, the related issue, AI assistance, art, and how it was verified (tests run, screenshots checked). Save-format changes explain compatibility and migration.

Commit only files related to the change. Never commit `build/`, `run/`, `.gradle/`, test worlds or any keys.

## Releases

Pushing a tag `vX.Y.Z` builds that commit and publishes the mod's JAR as a GitHub Release, with the changes since the last one. Set `version` in `gradle.properties` to the version being released and merge that to `main` through a PR; run the full client game test locally (CI has no display, and the release does not run it); then tag `main` and push the tag. The tag must match `version`, or the release fails.

After a release, the [launcher](https://github.com/keeiv/mistinn-launcher) brings out a build with the new mod embedded within the hour. With a `LAUNCHER_DISPATCH_TOKEN` secret in this repository (a token that may run the launcher repository's workflows), it is told the moment the release is out.

## Reporting problems

Open a GitHub issue for bugs and typos, with the Minecraft and mod versions, steps to reproduce and `logs/latest.log`. Players may also report on the [MoriTeahouse site](https://moriteahouse.shizune.life/account).

## License

The current version is **AGPL-3.0-only** by **Keeiv**. See [LICENSE](../LICENSE) and [NOTICE](../NOTICE). New contributions use the same license and keep contributors' copyright notices. The project's original textures, models and synthesized sounds share the code's license. See [CREDITS.md](../CREDITS.md) for the public-domain recordings and music.
