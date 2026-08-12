SETTLEMENTS 2.4.1 — RELEASE CANDIDATE
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17

Settlements 2.4.1 is the release-cleanup branch of the campaign mod. It carries forward settlement management, population, claims, economy, factions, diplomacy, characters, religion, warfare, levies, mercenaries and admin recovery while removing prototype-era naming and test-build baggage.

BUILD
1. Extract this source into a fresh folder.
2. Run BUILD-V2.4.1.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlements-2.4.1.jar
4. Install the SAME 2.4.1 JAR on the server and every client.
5. Remove every older Settlements / Settlement Prototype JAR before testing.
6. A fresh test world is recommended.

WHY THIS IS 2.4.1
Two different development checkpoints were previously shipped with the visible version 2.4.0. The early 1973819 test checkpoint still contained Dynasty and did not contain taxation; the later 16f1434 checkpoint removed Dynasty and implemented taxation. 2.4.1 gives the corrected branch an unambiguous version and canonical Settlements identity.

IDENTITY / DATA BREAK
- Canonical mod id: settlements
- Canonical Java/resource namespace: ee.lastpagans.settlements / assets/settlements / data/settlements
- New SavedData identifiers are used for the cleaned release architecture.
- Old prototype/test-world save compatibility is intentionally NOT guaranteed.
- Prototype naming is removed from active source/resources.
- Network protocol: 29. Mismatched clients/servers are rejected.

2.4.1 HIGHLIGHTS
- Dynasty is removed. Main House remains as the roleplay family/house identity field.
- Full taxation is present in Settlement > Treasury: Low / Normal / High tax policy, projected and last daily revenue, population/prosperity-scaled collection, persistence and Stability/Prosperity consequences.
- Currency is data-driven through the item tag #settlements:currency instead of being locked to one item.
- The bundled settlements:silver_coin is included in the default currency tag, but datapacks may add to or replace that tag.
- currencyItemWealthValue controls how much settlement Wealth one physical currency item represents.
- Religious donations consume an item from #settlements:currency as well.
- Newly founded settlements can receive their first settlers automatically. By default two villagers arrive 10–60 seconds after founding once the Town Square is valid, are registered as residents, and the owner receives: “The first settlers have reached your village.”
- First-settler spawning/count/delay are configurable.
- Settlement diplomacy uses real political roles rather than OP permission. Being an operator does not make every settlement yours or put you on both sides of a peace negotiation.
- Protected admin polities remain outside ordinary diplomacy and management.
- Same-parent Settlement Wars stay local and do not let the shared parent join both sides.
- Relations can improve gradually during peace and treaty maintenance.
- Dynamic Reliability responds to aid, mercenary payroll, treaties and marriage-alliance behavior and is visible on the Character Sheet.
- Main Seat can be selected from personally owned settlements on the Character Sheet.
- War aid independently supports One-time / Daily Manpower, Wealth and Food.
- Dropdowns render/input above the rest of campaign GUI controls.
- Settlement Manage is split into Locations / Identity / Claims / Members.
- Same-faction outsiders receive a limited public settlement snapshot instead of private management data.
- Registered civilian villagers are kept within any of their settlement's linked Recruits claims.
- Population cleanup handles villager death/removal without treating chunk unload as a death.
- Recruits' real 25-color unit palette is used by the faction creator.
- Mercenary status/payment information, devotion effects, detailed peace terms, scrollable Effects/aid ledgers and expanded admin recovery/debug tools remain available.

CURRENCY DATAPACK EXAMPLE
The built-in file is:
data/settlements/tags/items/currency.json

Default:
{
  "replace": false,
  "values": [
    "settlements:silver_coin"
  ]
}

A server datapack can replace this tag with its own coin/item list. Deposits accept any tagged item. Withdrawals prefer settlements:silver_coin when it remains in the tag; otherwise Settlements deterministically chooses a tagged currency item.

GUI ART
See GUI-ASSET-GUIDE.txt. The long-term art workflow is intentionally resource-based under assets/settlements/textures/gui/ so programmer-art panels/buttons/icons can be replaced with PNG assets without changing campaign logic. Not every existing fill/button has been converted to a PNG yet; the guide distinguishes current behavior from the intended texture workflow.

INTENTIONAL BOUNDARIES
- No children/heirs/fertility/dynasty-generation system.
- No tactical freecam, testudo/phalanx, Total-War unit overview or commander-combat abilities; those belong to separate Recruits addons.
- Direct Independence War declaration remains reserved for a later sovereignty/vassal system. Recognize Independence remains a peace term.

See CHANGELOG-v2.4.1.txt, COMMANDS-v2.4.1.txt, TESTING-v2.4.1.txt and GUI-ASSET-GUIDE.txt.
