# Settlements

Settlements is a campaign-scale Minecraft Forge mod for settlement management, characters, factions, military systems, diplomacy, research, and world news.

## Requirements

- Minecraft 1.20.1
- Forge 47.2.0+
- Java 17
- Villager Recruits 1.15.2+
- Wildfire Gender Mod 3.1+
- History Stages 5.4.0+ (optional research integration)

Every political state uses one State Treasury. Development depends on population, Stability, housing, and capture objectives. The Town Square is the settlement's single Main objective. Settlements retain their founding faith through conquest and convert gradually where the rules permit it. Marriage is read-only for players and is officiated or dissolved by administrators after roleplay ceremonies.

The campaign shell links Realm Overview, Character (including Marriage), Settlements, Military, Realm/Faction, Diplomacy, Research, and Chronicle. Diplomacy uses one bilateral relationship score. Player factions can form real vassalage contracts with 5/10/25/50% tribute taken once from actual post-stability tax receipts. Press `M` to open the integrated province campaign map, manage persistent strategic armies, and inspect political ownership and control.

The campaign map requires Icy's Better Horses `2.1.6` (and remains incompatible with `2.2.0+`). Immersive Paintings and Recruits RTS Camera integrations are optional. Remove the old standalone Campaign Map JAR when installing Settlements 6.0.1 because its code and saved-data support are now included here.

Use `gradlew.bat build` on Windows or `./gradlew build` elsewhere when a build is needed. The output is `build/libs/settlements-6.0.1.jar`.

Version 6.0.1 uses Settlements network protocol 49 and integrated campaign-map protocol 8. Legacy conflict kinds load as War or Raid; clients and servers must use the same version.
