# Changelog

## 1.5.0 - 2026-06-16

### Added

- Added JEI-style advanced search syntax to the convenient crafting search box: mod filters with `@`, exclusions with `-`, quoted phrases, tooltip filters with `#`, item tag filters with `$`, creative tab filters with `%`, ingredient filters with `~`, and multi-search with `|`.
- Added recipe-result and ingredient interactions in the convenient crafting list: hover an item and press `R`, or left-click it, to search for its recipes.
- Added usage lookup interactions in the convenient crafting list: hover an item and press `U`, or right-click it, to search for recipes that use it as an ingredient.
- Added search navigation history for recipe and usage lookups, allowing `Backspace` to return to the previous lookup when the search box is not focused.
- Added administrator commands to unlock recipe types for players: `/convenientcrafting unlock <targets> all` and `/convenientcrafting unlock <targets> <recipe_type_id>`.
- Added stonecutter recipe support to the convenient crafting panel, including config defaults, workstation unlocks, craftability checks, direct crafting, and nested crafting candidates.
- Added nested crafting support for brewing recipes, including recursive intermediate potion brewing and missing-material tree display.
- Added a shared recipe type adapter registry so built-in workstation recipe types keep separate indexing and duplicate-detection rules.

### Changed

- Recipe and usage lookup searches now automatically quote item names containing spaces so multi-word item names search correctly.
- Refreshing or closing the convenient crafting panel now clears recipe and usage lookup history for the current panel session.
- Creative-mode players can now craft any visible convenient crafting recipe directly without materials, with all recipe buttons shown as available.
- Recipe duplicate keys now include the recipe type id, preventing different workstation types with similar ingredients and results from hiding each other.
- Nested crafting preflight now stops expanding ingredients that already appear higher in the current material chain, reducing recursive loops and cleaner missing-material trees.
- Convenient crafting now warms up a small amount of craftability data before sorting so craftable recipes remain prioritized without restoring full upfront scans.
- Background recipe index preloading now processes larger batches so the index is ready sooner after entering a world.

### Fixed

- Fixed the convenient crafting page reset button requiring a second click to return to the first page and potentially crashing when clicked repeatedly.
- Fixed repeated ancestor ingredients still appearing as red leaves in nested crafting missing-material trees.
- Fixed the foreground convenient crafting screen accidentally finalizing an empty recipe queue while a background recipe index was still loading, which could leave only brewing recipes visible.
- Fixed brewing missing-material trees only showing the missing input potion and skipping the missing brewing ingredient.
- Fixed craftable recipe states staying stale after crafting by refreshing when the client inventory changes, with a short timeout fallback.

## 1.4.0 - 2026-06-15

### Added

- Added Seed Bag, Dye Bag, and Mineral Bag items with category-limited 9-slot storage, empty/filled states, fullness bars, item models, localization, and creative tab entries.
- Added leather/string and vanilla bundle upgrade recipes for Seed Bag, Dye Bag, and Mineral Bag.
- Added `convenientcrafting:minerals` item tag so modpacks can extend which ore and mineral items the Mineral Bag accepts.
- Added automatic pickup, inventory sorting, and convenient crafting support for materials stored inside category bags.
- Added Alt inventory sorting support for compacting mineral nuggets, ingots, and blocks using standard repeated-material crafting recipes.
- Added middle-click inventory sorting from the player inventory screen.
- Added standard copy, paste, cut, select-all, and keyboard navigation support to the convenient crafting search box.
- Added mouse-drag support for the nested missing-material tree scrollbar.

### Changed

- Convenient crafting now treats bag contents as available materials for crafting, smithing, brewing, configured simple recipes, and nested crafting checks.
- Improved convenient crafting performance in large modpacks with incremental recipe loading, reusable recipe indexes, cached material/craftability checks, current-page rendering, and cached page navigation views.
- Automatic recipe index preloading now runs only during normal gameplay with no screen open, uses smaller background batches, and stops before menu/save screens.
- Client recipe index caches are preserved across ordinary screen changes and cleared only when leaving a world.
- Alt inventory sorting now includes Mineral Bag contents in material compaction and then packs compacted mineral results back into available bag space.
- Alt mineral compaction now caches detected repeated-material mineral recipes so large modpacks do not rescan every recipe on each sort.
- Recipe type unlocks are now triggered when players obtain or craft workstation items instead of relying on periodic inventory scans.
- Multi-recipe results now keep showing a craftable recipe variant when at least one variant can be crafted, preventing the craft button from flickering off during cycling.
- Inventory sorting now preserves item component data, preventing container-like items such as bags from losing their stored contents during sorting.
- Convenient Crafting creative tab now uses the Seed Bag as its icon and displays all bag items.
- Inventory sort button now uses a compact custom icon-style button with a tooltip instead of a text glyph.

### Fixed

- Fixed player inventory sorting writing tools and other items into armor or offhand slots.
- Fixed workstation unlocks sometimes being missed when a player picked up and quickly dropped the workstation item.
- Fixed convenient crafting search text editing missing common desktop-style operations.
- Fixed nested missing-material scrollbar only responding to the mouse wheel.
- Fixed the inventory sort button position by anchoring it to the actual inventory screen origin.
- Fixed the inventory sort button staying visually highlighted after being clicked.
- Fixed Alt-click nested crafting sometimes failing without opening the missing-material tree.
- Added recursion depth, search step, candidate count, and missing-tree row limits to nested crafting so complex modpacks cannot stall the integrated server.

## 1.3.0 - 2026-06-10

### Added

- Added brewing stand recipe support in the convenient crafting panel.
- Added vanilla brewing batch behavior: one brewing ingredient can process up to three matching input potions into up to three result potions.
- Added `enabledBuiltinRecipeTypes` config so modpack users can choose which vanilla workstation recipe types are available in convenient crafting.
- Added Shift-click batch crafting for the convenient crafting `>` button.
- Added the standard button click sound when pressing the convenient crafting `>` button.
- Added Alt-click nested crafting for the convenient crafting `>` button, including a preflight raw-material check and a scrollable tree-style missing-material popup.
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

SmartBundle.
Initial release by Nero-os.

### Added

- Added inventory sorting with a smart auto-sort button on the inventory screen. It automatically stacks items and categorizes them, such as tools, blocks, and food.
- Added portable crafting: press `G` to open a quick crafting panel anywhere, using items directly from your inventory.

### Technical Details

- Built for Minecraft 1.21.1 with NeoForge.
- Client-side only; no server-side installation required for basic features.
