SETTLEMENTS 2.5.4 — TEST RELEASE
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17
Optional integration: History Stages 5.4.0+

Settlements 2.5.4 is a military-UI and research-tree usability update on top of 2.5.3.

BUILD
1. Extract into a fresh folder.
2. Run BUILD-V2.5.4.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlements-2.5.4.jar
4. Install the SAME JAR on server and clients.
5. Remove older Settlements JARs before testing.

MILITARY SCREEN
- The old overcrowded Military page is replaced by three internal pages: Forces, Levies and Support.
- Forces contains manpower, troop counts, morale and professional recruitment.
- Levies contains levy capacity, muster/cooldown/service state, requirements and levy controls.
- Support contains Mercenary Rosters and Bard controls/status.
- This deliberately separates unrelated information instead of squeezing the entire military system into one panel.

RESEARCH
- Settlements reads History Stages INDIVIDUAL stages as campaign technologies.
- History Stages individual-stage dependencies become technology-tree links in Settlements.
- Prerequisite arrows are drawn between technologies, Rome-II-style: left prerequisite -> right descendant.
- Prerequisites display their readable technology names instead of raw stage IDs where possible.
- Only one project can be actively researched per polity at a time.
- Settlements runs the campaign timer; completing it grants the matching History Stages individual stage to polity members.
- See HISTORY-STAGES-RESEARCH-GUIDE.txt before building a custom tech tree.

IDENTITY
- Mod ID: settlements
- Network protocol: 35
- Physical campaign currency uses #settlements:currency.

See CHANGELOG-v2.5.4.txt, TESTING-v2.5.4.txt, VALIDATION-v2.5.4.txt, HISTORY-STAGES-RESEARCH-GUIDE.txt and GUI-ASSET-GUIDE.txt.
