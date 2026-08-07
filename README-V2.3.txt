LAST PAGANS SETTLEMENTS 2.3.0
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17

Settlements 2.3 is the multiplayer campaign overhaul. It replaces the old loose prototype flow with server-authoritative settlement permissions, guided settlement/faction diplomacy, negotiated peace, faction identity/ranks, physical housing and muster regions, dynamic settlement progression, devotion/religion, three-card traits, generated spouses/player marriage, faction-funded mercenaries, expanded levies, morale integration, protected admin polities, World News and admin/debug recovery tools.

INSTALL / BUILD
1. Extract this source into a completely fresh folder.
2. Run BUILD-V2.3.bat (Windows) or: gradlew clean build
3. Expected JAR: build/libs/settlementprototype-2.3.0.jar
4. Put the SAME 2.3.0 JAR on server and every client.
5. Remove older settlementprototype JARs before starting.
6. Back up the world before first launch.

IMPORTANT 2.3 BEHAVIOUR
- Settlement Manager is private. Foreign-faction settlements are not sent as manageable selections, and management packets are revalidated server-side.
- Settlement Wars are settlement-vs-settlement. Parent factions can Ignore (defender), Send Aid, or Join. Joining escalates the war.
- Faction Wars verify Villager Recruits ENEMY relation in both directions before the declaration is accepted.
- Peace is a negotiated terms system rather than a single peace button.
- Faction creation requires a held patterned banner and exposes Recruits team + unit colors.
- Professionals spawn unhired. Levies spawn already owned by the raising player.
- Default levy caps: 300/player, 500/faction. Values are configurable.
- Mercenary contracts are persistent and paid from faction wealth (independent mercs use settlement wealth).
- Admin/debug commands exist specifically to test multi-day mechanics without waiting real days.

MIGRATION
2.3 adds a separate SavedData extension. Existing 2.0.x settlement/character/campaign records remain loadable; new 2.3 fields default safely when absent. Always test a copy first.

KNOWN DESIGN BOUNDARIES
- Tactical freecam, Total-War unit overview, testudo/phalanx, and commander combat abilities are intentionally NOT part of Settlements; those were separated into a future Recruits-command addon.
- Children/heirs/fertility progression is intentionally out of scope.
- Islam permits up to four spouses in the 2.3 family system. Additional NPC wives use diminishing capital/City-State effects (50%, 25%, 10%). Ending the primary marriage ends the full marriage household; every active marriage must be at least 7 Minecraft days old first.
- Levy level caps are enforced. Optional armor-stand XP suppression is also hooked for levy-tagged recruits; real combat XP remains allowed below the configured cap.
- Very high Minecraft GUI scale can still reduce usable logical resolution. 2.3 layouts are substantially reorganized, but 1920x1080 users should prefer GUI scale 2-3 for the largest campaign screens.

See COMMANDS-v2.3.txt and TESTING-v2.3.txt before the multiplayer test.
