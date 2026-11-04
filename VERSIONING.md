# Multi-version development

Convenient Crafting uses Stonecutter 0.9.8 to build Minecraft 1.21.1, 1.21.2, and 1.21.4 from one source tree. All targets require Java 21.

| Minecraft | NeoForge build dependency | Runtime range | Parchment |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.220 | [21.1.0,21.2.0) | 1.21.1 / 2024.11.17 |
| 1.21.2 | 21.2.1-beta | [21.2.1-beta,21.3.0) | 1.21.3 / 2024.12.07 |
| 1.21.4 | 21.4.161 | [21.4.161,21.5.0) | 1.21.4 / 2025.03.23 |

Each jar declares its exact Minecraft target. Do not broaden the range without testing every included version.

## Layout

- `settings.gradle`: supported versions and the VCS baseline (1.21.1).
- `stonecutter.gradle`: active IDE version and the `buildAll` task.
- `build.gradle`: shared ModDevGradle configuration.
- `gradle.properties`: shared mod identity and release version.
- `versions/<minecraft>/gradle.properties`: Minecraft, NeoForge, loader, and mappings dependencies.
- `src/main`: shared Java, templates, and resources.
- `versions/<minecraft>/src/main`: version-specific resource overrides, merged by Stonecutter.
- `compat/RecipeCompat.java`: recipe API adaptations using Stonecutter version conditions.
- `client/ClientRecipes.java`: client recipe snapshot with cache invalidation on synchronization.

Edit shared sources under `src/`, not the generated files under `versions/*/build/generated/stonecutter/`.

## Build and run

Windows commands (use `./gradlew` on Linux/macOS):

```powershell
# Compile, check, and collect all three release jars in build/libs/.
.\gradlew.bat buildAll

# Build or run one target explicitly; these do not switch the shared source tree.
.\gradlew.bat :1.21.1:build
.\gradlew.bat :1.21.2:build
.\gradlew.bat :1.21.4:build
.\gradlew.bat :1.21.1:runClient
.\gradlew.bat :1.21.2:runClient
.\gradlew.bat :1.21.4:runClient
.\gradlew.bat :1.21.4:runServer

# Change the IDE's active source version. Stonecutter rewrites its version comments.
.\gradlew.bat "Set active project to 1.21.4"

# Restore the 1.21.1 source baseline before committing.
.\gradlew.bat "Reset active project"
```

Run directories are isolated under `versions/<minecraft>/run/`. Do not copy worlds between versions just to test the mod. Minecraft artifact generation is serialized to avoid decompiling two versions concurrently.

Release jars are named `convenientcrafting-<mod_version>-neoforge-mc<minecraft_version>.jar`. Keep the target jars in the same release when appropriate, and tag each download only with its own Minecraft version. Updating the mod version does not require duplicating shared code or creating a branch per Minecraft version.

## Version adaptations

- From 1.21.2, recipe identifiers use resource keys; ingredients and result previews use the new placement/display APIs.
- On 1.21.4, NeoForge's `OnDatapackSyncEvent.sendRecipes` synchronizes built-in and configured recipe types on login and datapack reload. `RecipesReceivedEvent` replaces the client snapshot and invalidates recipe indexes, including same-size reloads.
- On 1.21.2, a target-local `recipe_catalog` payload sends the encoded catalogue in byte chunks of at most 64 KiB. Recipes are decoded only after vanilla tag sync, so newly introduced item tags exist before ingredient decoding. The client publishes a complete catalogue at once, invalidating same-size reloads and clearing unfinished data on logout. The old branch's 32,768-recipe catalogue limit is removed.
- The 1.21.2 target retains NeoForge 21.2.1-beta and the original port's Parchment 1.21.3 / 2024.12.07 parameter mappings. Its old single-target build and unlock-packet wire format are superseded by the shared build and separate catalogue payload; `codex/mc-1.21.2` remains a historical reference.
- Craft execution stays on the server and resolves the original server recipe by id; client data never supplies an authoritative result.
- Configured custom simple recipe types on 1.21.2 and later must expose usable placement ingredients and result displays through the new recipe APIs.
- Classification bags set their item registry keys before construction.
- Version-specific recipe JSON uses the modern ingredient format on 1.21.2 and later.
- On 1.21.4, version-specific item definitions select empty/filled bag models using the container component. The 1.21.1 and 1.21.2 targets retain legacy item predicates.
- Data generation uses `clientData` for 1.21.4 and `data` for 1.21.1/1.21.2.

## Adding another target

1. Add the version in `settings.gradle` and to `buildAll`/the CI matrix.
2. Add its dependency properties under `versions/<minecraft>/`.
3. Add localized Stonecutter conditions or version-specific overrides for changed APIs/resources.
4. Build every existing target to catch regressions.
5. Check a client and a dedicated server before publishing:
   - Login, disconnect/reconnect to another server, and `/reload` (including changed recipes with the same count).
   - Recipe search, craftability ordering, workstation unlocks, direct/batch/nested crafting, and missing-material trees.
   - Ingredient consumption, container remainders, full inventories, and armor/offhand handling.
   - Inventory/container sorting and Alt mineral compaction.
   - Bag recipes, item names, empty/filled models, pickup/storage, and optional bag configuration.
   - Configured additional recipe types and representative modpack recipes.

Successful compilation alone does not establish compatibility with every modpack or replace these gameplay checks.

## Automated checks

`build` and `buildAll` run NeoForge JUnit tests against real loaded server datapacks for all three targets. These check repeated shaped ingredients, shapeless output counts and tags, bag registration and conditional recipes, crafting remainders, and bag nesting/storage. The 1.21.4 target additionally checks that full workstation recipes are requested for client synchronization. Run a single target with `.\gradlew.bat :1.21.4:test`.

The 1.21.2 target also tests full-recipe codec round-tripping without premature registry/tag access, component-sensitive custom ingredients, catalogues over 32,768 entries, publication after tag updates, same-size/empty reloads, and incomplete/disconnected transfers. Shared tests exercise stonecutting outputs and smithing input matching/assembly on every target.
