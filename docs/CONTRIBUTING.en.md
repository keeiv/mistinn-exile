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

## Using AI tools

AI-assisted contributions are treated like hand-written ones; no label needed, and they are not rejected for it.

One condition: **a pull request is merged only when it has no security issues.** Before sending one, check:

- You have read and understood every line you send and can explain why it is written that way.
- The server does not trust the client: grid actions are requests, checked on the server (see `GridService`). New packets range-check every field on the server.
- Nothing duplicates items: code that moves items into or out of the grid takes the same amount from the source.
- File paths, URLs and commands never use unchecked input.
- New dependencies exist, are maintained and have no known high-risk advisories. Tools sometimes invent package names; verify before installing.
- **Textures and models must be original.** Art from other games, resource packs, AI image generators or the web is not accepted. Sound effects may be made from verified CC0 recordings. List the sources of recordings and music by file in [CREDITS.md](../CREDITS.md). Commit finished files only (PNG, OGG, JSON) and keep working files (project files, generator scripts, layered images) outside the repository.

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
- Tier colours are fixed: N rust brown, I grey, II green, III blue, IV purple, V gold, VI red.
- Art follows vanilla items: each part outlined in its own darkest tone, light from the upper left, 4–5 tones per material; held models are vanilla extruded sprites.

## Tests

```powershell
.\build.ps1 runClientGameTest
```

The client game test opens a real game window, plays weapon swings, resting and respawning at a bell, death and residue, the grid inventory and tiers, and saves screenshots to `build/run/clientGameTest/screenshots`. Every check must pass; when you change what is on screen, look at the screenshots. New features add observable checks (which cell an item landed in, what survived death), not only screenshots.

Photos for the README and the website are staged in the game with photo mode:

```powershell
.\build.ps1 runClientGameTest -PphotoMode
```

To reshoot only some scenes, add `-PphotoSet=`, for example `-PphotoSet=death,executions` (choices: `title`, `places`, `hunter`, `structures`, `lighthouse`, `death`, `executions`, `story`, `inventory`).

Photo mode adds Sodium and Iris and shoots each scene at 1920×1080 on the highest settings into `build/run/clientGameTest/photos`. The shader pack is not shipped; put Complementary Reimagined r5.9.3 in `build/run/clientGameTest/shaderpacks` and enable it in `config/iris.properties` in the same run folder.

## Checks

Run `.\build.ps1 build` before committing. CI runs `./gradlew build` on Linux with JDK 25; the client game test needs a display and runs locally for now.

`build` also runs the code and comment style check (Checkstyle; the rules are in `config/checkstyle/checkstyle.xml`), and any one broken rule fails it: tabs for indentation, braces on every block, one statement a line, at most 200 columns; `final` parameters, no unused imports or locals; a Javadoc on every type whose first sentence ends in a full stop or a colon, a space after `//`, and no TODO left in a comment. To run only the style check: `.\build.ps1 checkstyleMain checkstyleClient checkstyleGametest`.

## Commits and pull requests

Commit messages and PR titles use Conventional Commits prefixes with a short Traditional Chinese summary: `feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `test:`, `chore:`. A PR describes the problem, the new behaviour and how it was verified (tests run, screenshots checked). Save-format changes explain compatibility and migration.

Commit only files related to the change. Never commit `build/`, `run/`, `.gradle/`, test worlds or any keys.

## Releases

Pushing a tag `vX.Y.Z` builds that commit and publishes the mod's JAR as a GitHub Release, with the changes since the last one. Set `version` in `gradle.properties` to the version being released and merge that to `main` through a PR; run the full client game test locally (CI has no display, and the release does not run it); then tag `main` and push the tag. The tag must match `version`, or the release fails.

After a release, the [launcher](https://github.com/keeiv/mistinn-launcher) brings out a build with the new mod embedded within the hour. With a `LAUNCHER_DISPATCH_TOKEN` secret in this repository (a token that may run the launcher repository's workflows), it is told the moment the release is out.

## Reporting problems

Report on the [MoriTeahouse site](https://moriteahouse.shizune.life/account) with the Minecraft and mod versions, steps to reproduce and `logs/latest.log`.

## License

The current version is **AGPL-3.0-only** by **Keeiv**. See [LICENSE](../LICENSE) and [NOTICE](../NOTICE). New contributions use the same license and keep contributors' copyright notices. The project's original textures, models and synthesized sounds share the code's license. See [CREDITS.md](../CREDITS.md) for the public-domain recordings and music.
