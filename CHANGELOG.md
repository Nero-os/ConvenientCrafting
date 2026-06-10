# Changelog

## 1.3.0 - 2026-06-10

### Added

- Added brewing stand recipe support in the convenient crafting panel.
- Added vanilla brewing batch behavior: one brewing ingredient can process up to three matching input potions into up to three result potions.
- Added `enabledBuiltinRecipeTypes` config so modpack users can choose which vanilla workstation recipe types are available in convenient crafting.
- Added Shift-click batch crafting for the convenient crafting `>` button.
- Added the standard button click sound when pressing the convenient crafting `>` button.
- Added item name tooltips when hovering required ingredients in the convenient crafting recipe list.
- Added item tooltips when hovering recipe result icons in the convenient crafting recipe list.

### Changed

- Vanilla crafting, smithing, and brewing recipe types are enabled by default through config.
- Server-to-client recipe unlock syncing now includes enabled built-in recipe types so multiplayer clients follow the server config.
- Convenient crafting panel now keeps its original default size on normal windows and shrinks only when needed to fit smaller game windows.
- Convenient crafting panel layout is now more compact, with tighter recipe rows and page-aligned adaptive ingredient columns.

### Fixed

- Fixed the Convenient Crafting creative mode tab icon showing as a missing-texture block by using a vanilla crafting table icon until custom mod items are added.
- Removed unused template example items from creative tab display to avoid missing-model entries in creative search.
- Added localized names and tooltips for the in-game NeoForge configuration screen, and removed unused template demo config entries.

## 1.2.0 - 2026-06-08

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

## 1.1.0 - 2026-06-07

### Added

- Added smithing table recipe support in the convenient crafting panel.
- Added support for using materials from the currently opened container, such as chests, when crafting from the panel.
- Added mouse wheel page navigation for the recipe list.
- Added right-click clearing for the recipe search box.
- Added Chinese Javadocs and detailed Chinese comments for core recipe and inventory logic.

### Changed

- Crafting now prefers player inventory materials first, then falls back to materials in the opened container.
- Closing the convenient crafting panel now also closes the underlying opened container to avoid leaving the container menu active.
- Recipe matching now uses the actual matched smithing inputs so armor trims and netherite upgrades produce correct results.

### Fixed

- Fixed garbled mod display name in the NeoForge mod list.
- Fixed inventory space checks so consumed player-inventory ingredients are counted as freed slots before adding the result.

## 1.0.0 - 2026-06-06

Initial release by Nero-os.

### Added

- Added inventory sorting with a smart auto-sort button on the inventory screen. It automatically stacks items and categorizes them, such as tools, blocks, and food.
- Added portable crafting: press `G` to open a quick crafting panel anywhere, using items directly from your inventory.

### Technical Details

- Built for Minecraft 1.21.1 with NeoForge.
- Client-side only; no server-side installation required for basic features.
