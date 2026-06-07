# Changelog

## 1.1.0

### Added

- Added smithing table recipe support in the convenient crafting panel.
- Added support for using materials from the currently opened container, such as chests, when crafting from the panel.
- Added mouse wheel page navigation for the recipe list.
- Added right-click clearing for the recipe search box.
- Added configurable extra recipe type support through `additionalRecipeTypes` for modpack authors.
  Examples include `create:mechanical_crafting` and `malum:spirit_infusion`.
- Added recipe type unlock rules through `recipeTypeUnlockItems`.
- Added permanent per-player recipe type unlock tracking after players obtain the required workstation item.
- Added server-to-client unlock syncing so recipe visibility follows the server configuration in multiplayer.
- Added chat notifications when obtaining a workstation unlocks a new convenient crafting recipe type.
- Added grouped multi-recipe display for items with multiple matching recipes, with automatic cycling between variants.
- Added cycling display for alternative ingredient choices, such as any plank type in vanilla recipes.
- Added Chinese Javadocs and detailed Chinese comments for core recipe and inventory logic.

### Changed

- Crafting now prefers player inventory materials first, then falls back to materials in the opened container.
- Recipe list now deduplicates recipes with the same output and matching ingredient set.
- Recipe ingredient previews now show the actual matched inventory or container item when a recipe accepts alternatives.
- Recipe ingredient previews now smoothly scroll on hover when a recipe has more materials than can fit in the row.
- Closing the convenient crafting panel now also closes the underlying opened container to avoid leaving the container menu active.
- Recipe matching now uses the actual matched smithing inputs so armor trims and netherite upgrades produce correct results.

### Fixed

- Fixed garbled mod display name in the NeoForge mod list.
- Fixed inventory space checks so consumed player-inventory ingredients are counted as freed slots before adding the result.
- Fixed alternative ingredients appearing as missing when the player had a valid substitute item, such as birch planks for wooden shovel recipes.
