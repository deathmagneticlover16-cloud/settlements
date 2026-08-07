LAST PAGANS SETTLEMENTS 2.0.2 SOURCE
Minecraft 1.20.1 | Forge 47.2.0 | Java 17 | Villager Recruits 1.15.2

BUILD
1. Extract this archive into an empty folder.
2. Confirm Java 17 is selected.
3. Run BUILD-V2.0.2.bat or gradlew.bat clean build.
4. The expected file is build\libs\settlementprototype-2.0.2.jar.
5. Remove all older settlementprototype JARs.
6. Install the same 2.0.2 JAR on the dedicated server and every multiplayer client.
7. Test with a copied world first.

THIS HOTFIX ADDRESSES
- Missing faction creation after the original Recruits faction screen was intercepted.
- Missing administrator settlement-stat controls.
- Full-body 3D character rendering and later dynasty/main-house editing.
- Overlapping text and controls on major campaign screens.
- Settlement switching when a player can access more than one settlement.
- Oversized banners.
- Linking multiple Recruits claims into one logical large settlement.

FACTION CREATION
Independent players can open Faction and enter a name. The mod calls Recruits' own faction creation method so the resulting faction remains compatible with Recruits teams, claims, banners and soldiers. The campaign layer then creates its faction profile and world-news entry.

MULTI-CLAIM SETTLEMENTS
A settlement retains one primary claim for backwards compatibility and stores additional claim links. Every linked claim resolves to the same settlement. This does not physically rewrite or merge Recruits' claim objects; it creates a settlement-level union of them. Recruits still renders and manages each underlying claim separately.

CHARACTER IDENTITY
The original character name, faith, title and form of address remain fixed after confirmation. Dynasty and Main House are intentionally editable later from the Character sheet because they may be acquired or founded during roleplay.

SAVE COMPATIBILITY
2.0.2 adds optional fields and lists. Existing 2.0.0/2.0.1 data should load without manual conversion. Always retain a world backup before updating.

See CHANGELOG-v2.0.2.txt and COMMANDS-v2.0.2.txt for details.
