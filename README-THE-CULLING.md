# Settlements: The Culling

Minecraft 1.20.1, Forge 47.2.0, Villager Recruits 1.15.2, Wildfire Gender Mod 3.1+, and Java 17.

The Culling uses one State Treasury for each political state. Taxes, deposits, player-spouse effects, recruitment, mercenaries, direct aid, immediate peace payments, and faction founding all use that balance.

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

Character creation moves directly from identity/body editing to trait selection. Main Seat selection lives directly on the Character Sheet. Marriage is player-to-player only. Settlements attract immigrants automatically when housing, Stability, Food, and the settlement immigration toggle allow it.

War aid is a direct one-time transfer. Peace treaties can demand an immediate treasury payment but do not create recurring reparation schedules. Levy casualties and prolonged service use separate scalable pressure effects, and levies retain their ordinary Recruits XP without a separate Levy Tradition statistic.

Run `BUILD-THE-CULLING.bat` on Windows or `./gradlew clean build` elsewhere. The output is `build/libs/settlements-4.0.0.jar`; clients and servers must use network protocol 44 and matching mod dependencies.

Version 4.0.0 is a breaking save-format release. Back up a world before upgrading: removed-system data is discarded rather than converted, existing campaign modifiers from older formats are cleared, and old Settler Contract items disappear because their item registration was removed.
