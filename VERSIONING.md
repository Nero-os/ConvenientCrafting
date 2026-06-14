# Versioning and Porting Guide

Convenient Crafting is currently maintained as a Minecraft 1.21.1 NeoForge mod.

## Current target

| Field | Value | Source |
| --- | --- | --- |
| Minecraft compile target | `1.21.1` | `minecraft_version` |
| Minecraft declared range | `[1.21.1]` | `minecraft_version_range` |
| NeoForge compile target | `21.1.220` | `neo_version` |
| NeoForge declared range | `[21.1.0,21.2.0)` | `neo_version_range` |
| Java target | `21` | `build.gradle` |
| Parchment mappings | `1.21.1 / 2024.11.17` | `parchment_*` |

The declared Minecraft range is intentionally exact. Do not broaden it until the mod has been compiled and tested on every Minecraft version included by the range.

## Recommended branch model

Use one long-lived branch per Minecraft version:

| Branch | Purpose |
| --- | --- |
| `main` | Current stable development target. |
| `mc/1.21.1` | Optional release maintenance branch for Minecraft 1.21.1. |
| `mc/1.21.x` | Port branch for a newer 1.21 patch line after choosing a concrete target version. |

Keep release jars separate per Minecraft branch. A jar built for one Minecraft version should not be uploaded as compatible with another version until it has been tested there.

## Port checklist

When starting a new Minecraft/NeoForge target:

1. Create a new branch from the closest working version.
2. Update `gradle.properties`:
   - `minecraft_version`
   - `minecraft_version_range`
   - `neo_version`
   - `neo_version_range`
   - `parchment_minecraft_version`
   - `parchment_mappings_version`
3. Refresh Gradle and run `.\gradlew.bat build`.
4. Fix compile errors caused by Minecraft or NeoForge API changes.
5. Run a client and check:
   - inventory sort button and keybind;
   - craft helper screen;
   - nested crafting;
   - recipe unlock synchronization;
   - dye bag and seed bag pickup/storage behavior.
6. Run a dedicated server and check that the mod loads without client-only class errors.
7. Update README badges and supported version text for the branch.
8. Publish the jar only for the tested Minecraft and NeoForge range.

## Known API areas to review

These code areas are likely to need attention during ports:

| Area | Why it matters |
| --- | --- |
| `DataComponents` and `ItemContainerContents` | Item data storage changed significantly between Minecraft versions. |
| `CustomPacketPayload`, `StreamCodec`, and NeoForge payload registration | Network APIs are version-sensitive. |
| `RecipeHolder`, recipe lookup, and recipe result APIs | Recipe manager APIs often change between Minecraft releases. |
| Client screen classes and button APIs | GUI constructors and rendering helpers can shift between versions. |
| Creative tab registration | Registry and tab event APIs can change with NeoForge updates. |

For backports to Minecraft 1.20.1, expect a real code port rather than a version-property change.
