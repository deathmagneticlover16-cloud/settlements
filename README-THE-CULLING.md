# Settlements: The Culling

Minecraft 1.20.1, Forge 47.2.0, Villager Recruits 1.15.2, Wildfire Gender Mod 3.1+, and Java 17.

The Culling is the economy and interface cleanup release. Every political state has exactly one State Treasury. Taxes, deposits, traits, spouses, recruitment, mercenaries, aid, reparations, and faction founding all read from or write to that same balance.

Settlement-local Wealth, its migration code, and the Prosperity mechanic have been removed. Development now depends on population, Stability, housing, and capture objectives. Research speed comes from its technology duration and ruler traits.

The interface starts with a two-branch campaign tree:

- The Settlement branch contains local overview and management, founding, and research.
- The Realm branch contains character, family, faction, military, diplomacy, and world news.

The branch screens share one implementation while their leaves remain focused workspaces. Settlement names generate stable internal IDs automatically; duplicate names receive `_2`, `_3`, and later suffixes without exposing that bookkeeping in the normal interface.

Character creation moves directly from identity/body editing to trait selection. Names are fixed after creation, Main House has been removed, and Main Seat selection lives directly on the Character Sheet. War aid and peace terms remain contextual editors attached to the selected war.

Run `BUILD-THE-CULLING.bat` on Windows or `./gradlew clean build` elsewhere. The output is `build/libs/settlements-4.0.0.jar`; clients and servers must use network protocol 44 and matching mod dependencies.

This release targets fresh worlds. Removed data formats are deliberately not migrated.
