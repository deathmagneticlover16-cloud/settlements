SETTLEMENTS 2.5.3 — TEST RELEASE
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17
Optional integration: History Stages 5.4.0+

Settlements 2.5.3 is a focused identity/destructive-management update on top of 2.5.2.

BUILD
1. Extract into a fresh folder.
2. Run BUILD-V2.5.3.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlements-2.5.3.jar
4. Install the SAME JAR on server and clients.
5. Remove older Settlements JARs before testing.
6. Old-world compatibility is not a release requirement; use a fresh test world when practical.

IDENTITY
- Mod ID: settlements
- Network protocol: 34
- Physical campaign currency uses #settlements:currency.
- Character renaming defaults to 10 tagged currency items and is configurable under economy.characterRenameCurrencyCost.
- Main House remains free to edit.

PERMANENT SETTLEMENT DELETION
- Available only to the settlement's actual owner from Manage > Identity.
- Requires two-step confirmation.
- This is intentionally destructive and cannot be undone.
- Settlement-owned stats/resources/registries are erased and linked Recruits claims are deleted.
- Active-war and protected settlements are blocked from normal deletion.
- Admins retain explicit admin recovery/reset tooling; OP status is not treated as political ownership by this GUI action.

See CHANGELOG-v2.5.3.txt, TESTING-v2.5.3.txt, VALIDATION-v2.5.3.txt and GUI-ASSET-GUIDE.txt.
