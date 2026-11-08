# Settlements

Settlements is a campaign-scale Minecraft Forge mod for settlement management, characters, factions, military systems, diplomacy, research, and world news.

## Requirements

- Minecraft 1.20.1
- Forge 47.2.0+
- Java 17
- Villager Recruits 1.15.2+
- Wildfire Gender Mod 3.1+
- History Stages 5.4.0+ (optional research integration)

Every political state uses one State Treasury. Development depends on population, Stability, housing, and capture objectives. Settlements attract immigrants automatically when housing, Stability, Food, and their manager-controlled immigration setting allow it. Marriage is player-to-player, war aid is an immediate transfer, and peace payments are immediate rather than recurring schedules.

The interface is organized into Campaign, Character, Settlement, Military, Faction, Diplomacy, Family, Research, and World News workspaces, plus a shared in-world area editor.

Use `gradlew.bat build` on Windows or `./gradlew build` elsewhere when a build is needed. The output is `build/libs/settlements-4.0.0.jar`.

Version 4.0.0 uses network protocol 44 and targets fresh worlds. Removed-system data is discarded rather than migrated.
