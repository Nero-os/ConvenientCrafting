# Convenient Crafting

**Convenient Crafting** is a lightweight survival-workflow quality-of-life mod for Minecraft 1.21.1 on NeoForge. Its main feature is a portable crafting assistant: open it anywhere, search recipes, check craftability, craft directly, and let the mod gather materials from the places you are already using.

Press **G** to open the compact crafting assistant, then craft from materials in your inventory, the container you are currently using, and optionally supported category bags.

## What It Does

Convenient Crafting is built around the portable crafting assistant. The goal is to keep crafting fast without turning the mod into a large content expansion:

- Craft without placing a workstation first
- Search recipes by name, id, mod, tooltip, tag, ingredient, or creative tab
- Filter to only recipes you can currently craft
- Click or press hotkeys on displayed items to look up recipes and usages
- Craft once, batch craft, or try nested crafting for missing intermediate parts
- Use materials from your inventory, opened containers, and supported category bags

Inventory/container sorting and category bags are included as material-preparation helpers. They are useful when your materials are scattered, but they are not the main gameplay loop of the mod.

It is especially useful while mining, building, exploring, working from storage rooms, or playing larger modpacks where recipe lists can get noisy.

## Supported Recipe Types

The convenient crafting panel supports several vanilla workstation recipe types out of the box:

- Crafting table recipes
- Smithing table recipes
- Brewing stand recipes
- Stonecutter recipes

Recipe types are handled separately, so a stonecutter recipe and a crafting table recipe with similar ingredients will not hide each other in the recipe list.

Modpack authors can also enable compatible custom recipe types through the config when those recipes are safe to craft directly from ingredients and a fixed result.

## Search and Recipe Lookup

The search box supports JEI-style advanced filters:

- `@modid` for mod id filters
- `-term` for exclusions
- `"quoted text"` for exact phrases
- `#term` for tooltip text
- `$tag` for item tags
- `%tab` for creative tabs
- `~ingredient` for ingredient searches
- `|` for multi-search

You can also interact with items directly in the recipe list:

- Hover an item and press `R`, or left-click it, to search for recipes that make it
- Hover an item and press `U`, or right-click it, to search for recipes that use it
- Press `Backspace` to return through recipe and usage search history when the search box is not focused

## Batch and Nested Crafting

Convenient Crafting can do more than single-click crafting:

- Click `>` to craft once
- Shift-click `>` to batch craft
- Alt-click `>` to try nested crafting

Nested crafting simulates the required crafting chain before it starts. If the final item cannot be made, the mod shows a scrollable missing-material tree so you can see which raw materials are still missing.

The nested crafting search includes safety limits for recursion depth, search steps, candidate recipes, and missing-material rows, helping it stay responsive in large modpacks.

## Brewing and Stonecutting

Brewing recipes follow vanilla brewing behavior: one brewing ingredient can process up to three matching input potions in one convenient crafting action.

Stonecutter recipes are supported as their own workstation type, with separate config defaults, unlock handling, craftability checks, direct crafting, and nested crafting candidates.

## Category Bags

Category bags are optional material-preparation tools. Convenient Crafting includes three small category-limited bags:

- Seed Bag
- Dye Bag
- Mineral Bag

Each bag provides 9 slots for its category. Depending on config, bag contents can be used by the portable crafting assistant, nested crafting checks, brewing, smithing, configured simple recipes, automatic pickup, and inventory sorting.

The Mineral Bag also supports a `convenientcrafting:minerals` item tag so modpacks can extend which ore and mineral items it accepts.

## Inventory Sorting

Sorting is a lightweight material-preparation feature. The mod includes a compact sort button for inventory and supported container screens. Sorting can merge stackable items, group similar items, preserve item component data, and optionally work with supported bag contents.

Middle-click sorting is also supported from the player inventory and supported container screens.

## Performance

Convenient Crafting is designed to stay responsive in large modpacks:

- Incremental recipe loading
- Reusable recipe indexes
- Cached material and craftability checks
- Current-page rendering
- Cached page navigation views
- Background recipe index preloading during normal gameplay
- Cached nested-crafting candidate lookups

Recipe index caches are kept across ordinary screen changes and cleared when leaving a world.

## Modpack Configuration

The common config file is located at:

`.minecraft/config/convenientcrafting-common.toml`

Useful options include:

- `enabledBuiltinRecipeTypes` for vanilla workstation recipe types
- `additionalRecipeTypes` for compatible custom recipe types
- `recipeTypeUnlockItems` for workstation unlock rules

Server-side unlock syncing keeps multiplayer clients aligned with the server config. Administrators can also unlock recipe types for players with:

- `/convenientcrafting unlock <targets> all`
- `/convenientcrafting unlock <targets> <recipe_type_id>`

## Controls

| Key / Action | Function |
| --- | --- |
| `G` | Open the convenient crafting panel |
| Click `>` | Craft once |
| Shift-click `>` | Batch craft |
| Alt-click `>` | Try nested crafting |
| Hover item + `R` | Search recipes for the item |
| Hover item + `U` | Search usages for the item |
| Left-click recipe-list item | Search recipes for the item |
| Right-click recipe-list item | Search usages for the item |
| Middle-click inventory/container | Sort inventory or container |

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.220+
- Multiplayer friendly
- Datapack compatible
- Modpack configurable
- Built to stay lightweight and vanilla-friendly

Convenient Crafting reduces repetitive crafting and inventory busywork so you can spend more time building, exploring, and actually playing.
