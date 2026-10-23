# Settlements: The Culling

Minecraft 1.20.1, Forge 47.2.0, Villager Recruits 1.15.2, Wildfire Gender Mod 3.1+, and Java 17.

The Culling is the economy and interface cleanup release. Every political state has exactly one State Treasury. Taxes, deposits, traits, spouses, recruitment, mercenaries, aid, reparations, and faction founding all read from or write to that same balance.

Settlement-local Wealth, its migration code, and the Prosperity mechanic have been removed. Development now depends on population, Stability, housing, and capture objectives. Research speed comes from its technology duration and ruler traits.

The interface is arranged around reusable workspaces:

- Campaign dashboard
- Character workspace
- Settlement dashboard with Overview, Residents, Treasury, Effects, and Management tabs
- Military workspace with Forces, Levies, and Mercenaries tabs
- Faction workspace with Overview, Members, Settlements, and Ranks tabs
- Diplomacy workspace with Relations, Declare, Proposals, and Wars tabs
- Research
- World News
- In-world area editor

Character creation now moves directly from identity/body editing to trait selection. Main Seat selection lives directly on the Character Sheet. War aid and peace terms remain contextual editors attached to the selected war.

Run `BUILD-THE-CULLING.bat` on Windows or `./gradlew clean build` elsewhere. The output is `build/libs/settlements-3.0.0.jar`; clients and servers must use network protocol 43 and matching mod dependencies.

This release targets fresh worlds. Removed data formats are deliberately not migrated.
