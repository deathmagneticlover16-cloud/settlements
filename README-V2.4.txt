LAST PAGANS SETTLEMENTS 2.4.0 — RELEASE CANDIDATE
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17

Settlements 2.4 is the consolidation/release-hardening pass after the large 2.3 campaign overhaul. It keeps settlement wars, escalation, negotiated peace, factions/ranks, characters/traits, family, religion, housing, prosperity, levies, mercenaries, morale, World News and admin recovery while filling scope gaps and bugs found during real multiplayer testing.

BUILD
1. Extract into a fresh folder.
2. Run BUILD-V2.4.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlementprototype-2.4.0.jar
4. Install the SAME 2.4.0 JAR on the server and every client.
5. Remove older settlementprototype JARs.
6. Back up the world before first test.

IMPORTANT 2.4 CHANGES
- Settlement diplomacy now uses real political roles, not operator permission. OP status no longer makes foreign settlements become "your settlements" or makes an operator count as both sides of a peace negotiation.
- Operator permission also no longer masquerades as faction leadership in normal faction/diplomacy screens. Admin intervention belongs to explicit admin/recovery tools.
- Protected admin polities are completely absent from ordinary diplomacy and remain controllable through admin/recovery tools.
- Same-parent Settlement Wars remain local conflicts; the shared parent faction cannot ambiguously Join/Aid both sides.
- Relations can improve gradually over time according to treaty state.
- Dropdown menus render/input above other campaign controls.
- War aid has separate Daily/One-time scheduling for Manpower, Wealth and Food.
- Dynamic Reliability reacts to aid, mercenary payroll, treaty behavior and marriage-alliance breakage.
- Your faction Reliability is visible on the Character Sheet.
- Main Seat can be chosen directly from the Character Sheet and is validated server-side against settlements you personally own.
- Dynasty was removed. Main House remains as the useful roleplay identity field; old Dynasty save data is ignored and disappears on the next save.
- Full settlement taxation is active: Low / Normal / High policy, daily population/prosperity-scaled revenue, Stability/Prosperity trade-offs, persistence, Treasury controls and Effects integration.
- Registered civilian residents are territorially bound to any of the settlement's linked Recruits claims; multi-claim settlements remain fully valid.
- Mercenary status/next payment are exposed in military UI.
- Diplomacy trait relation effects are functional.
- Devotion has donations and ruler-tier benefits.
- Same-faction outsiders receive a limited public settlement snapshot rather than private management data.
- Manage > Members supports Citizen/Co-owner changes in GUI.
- Peace treaties can explicitly renounce linked claims.
- Effects and recurring-aid ledgers scroll.
- Admin recovery/debug suite is substantially expanded.
- Villager population removal, Recruits 25-color faction mapping and the production levy-training mixin include their previous 2.3 hotfixes.

NETWORK
Protocol 28. Client/server mismatch is intentionally rejected.

COMPATIBILITY
2.4 continues using the same SavedData lineage as 2.3; new values default safely. Existing Dynasty NBT is simply ignored. Always test a world copy first.

KNOWN DESIGN BOUNDARIES
- No children/heirs/fertility system.
- No tactical freecam, testudo/phalanx, Total-War unit overview or commander-combat abilities; those belong to the separate Recruits addons.
- A standalone Independence War declaration flow is still reserved for a future sovereignty/vassal system. Recognize Independence remains a negotiated peace term.

See CHANGELOG-v2.4.txt, COMMANDS-v2.4.txt and TESTING-v2.4.txt.
