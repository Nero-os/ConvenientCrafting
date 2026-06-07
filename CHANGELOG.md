# Changelog

## 1.1.0

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
