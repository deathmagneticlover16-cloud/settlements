LAST PAGANS SETTLEMENTS 2.4.0
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17

Settlements 2.4 is the completion/consolidation release following the enormous 2.3 campaign overhaul. It keeps 2.3's settlement wars, escalation, negotiated peace, factions/ranks, traits, family, religion, housing, prosperity, levies, mercenaries, morale, World News and admin framework, while filling the gaps found by comparing the shipped source against the original 2.3 scope and live multiplayer feedback.

BUILD
1. Extract into a fresh folder.
2. Run BUILD-V2.4.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlementprototype-2.4.0.jar
4. Install the SAME 2.4.0 JAR on the server and every client.
5. Remove older settlementprototype JARs.
6. Back up the world before first test.

IMPORTANT CHANGES FROM 2.3
- Relations can now improve gradually over time according to diplomatic state.
- Dropdown menus render/input above other campaign controls.
- War aid has separate daily/one-time scheduling for Manpower, Wealth and Food.
- Reliability has more real behavioral hooks (aid, mercenary payroll, marriage-alliance breakage).
- Mercenary status/next payment are exposed in military data/UI.
- Diplomacy trait relation effects are functional.
- Protected admin polities are hardened server-side.
- Devotion has donations and ruler-tier benefits.
- Same-faction outsiders receive a limited public settlement snapshot rather than private management data.
- Manage > Members supports Citizen/Co-owner changes in GUI.
- Peace treaties can explicitly renounce linked claims.
- Effects and recurring-aid ledgers scroll.
- Admin recovery/debug suite is substantially expanded.

NETWORK
Protocol 27. Client/server mismatch is intentionally rejected.

COMPATIBILITY
2.4 continues using the same SavedData lineage as 2.3; new values default safely. Always test a world copy first.

KNOWN DESIGN BOUNDARIES
- No children/heirs/fertility system.
- No tactical freecam, testudo/phalanx, Total-War unit overview or commander-combat abilities; those belong to the separate Recruits addons.
- A standalone Independence War declaration flow is still reserved for a future sovereignty/vassal system. Recognize Independence remains a negotiated peace term.

See CHANGELOG-v2.4.txt, COMMANDS-v2.4.txt and TESTING-v2.4.txt.
