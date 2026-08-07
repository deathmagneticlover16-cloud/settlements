LAST PAGANS SETTLEMENTS — V1.1.0 CAMPAIGN RELEASE
Minecraft 1.20.1 | Forge 47.2.0 | Java 17 | Villager Recruits 1.15.2

BUILD
1. Extract this ZIP into a completely empty folder.
2. Keep the included Recruits JAR inside libs/.
3. Run BUILD-V1.1.bat, or run: gradlew.bat clean build
4. The built mod should appear at:
   build\libs\settlementprototype-1.1.0.jar

Install the new JAR on both client and server. Remove older Settlement Prototype / Last Pagans Settlements JARs first. Test on a copied world before using it on the main server.

KEYBINDS
M — Open Campaign menu
J — Open Character sheet
N — Open selected Settlement
The normal Recruits faction/diplomacy key is redirected to Campaign Diplomacy.

MAJOR V1.1 FEATURES
• Campaign overview and improved navigation between menus.
• Real expandable dropdowns in character creation.
• One-time character preview with Go Back before final confirmation.
• Player skin rendered as a 3D portrait.
• RP display names while UUIDs and scoreboard faction membership remain unchanged.
• Character sheet navigation and persistent spouse display.
• Text-based NPC spouse candidates and player-to-player marriage proposals.
• Formal diplomacy: relationship points, non-aggression pacts, defensive alliances, military alliances, calls to war, war declarations, and peace offers.
• War types: Raid, Settlement War, Faction War, and Independence War.
• Accidental or ordinary damage no longer automatically changes whole-faction diplomacy.
• Original Recruits diplomacy packets are rejected, and original diplomacy/treaty screens redirect to the Campaign menu.
• Settlement markers on the Recruits world map.
• Large settlement banners, custom local banners, faction-banner fallback, and independent settlement banners.
• Reusable military rosters made from configured Recruits spawn eggs.
• Global default roster, faction roster assignments, and rare settlement-specific unit overrides.
• Recruit, Shieldman, Bowman, Crossbowman, Horseman, Nomad, and Levy templates.
• Spawn-egg templates retain military configuration while unsafe copied ownership, UUID, position, faction, and old settlement data are removed.
• Existing food, wealth, stability, manpower, levy, settler contract, resident, housing, role, rank, and population-scaling systems remain included.
• Duplicate villager registration and duplicated Settler Contract redemption protection remain included.

SETTLEMENT BANNERS
Hold a normal Minecraft banner in your main hand:
  /settlements banner sethand <settlement_id>
Use the faction banner instead:
  /settlements banner faction <settlement_id>
Clear the settlement banner:
  /settlements banner clear <settlement_id>

A custom settlement banner is shown prominently. If the settlement belongs to a faction, the faction banner is shown separately as its political allegiance.

MILITARY ROSTERS
Create a reusable roster:
  /settlements admin roster create <roster_id> <display name>

Copy an existing roster before changing only a few units:
  /settlements admin roster copy <source_roster> <new_roster_id> <display name>

Hold a configured Villager Recruits spawn egg and save each template:
  /settlements admin roster sethand <roster_id> <unit_type>

Valid unit types:
  recruit, shieldman, bowman, crossbowman, horseman, nomad, levy

Make a roster the global fallback:
  /settlements admin roster default <roster_id>

Assign it to your current faction:
  /settlements admin roster assignfaction <roster_id>

Assign it to a specific Recruits faction ID:
  /settlements admin roster assignfaction <roster_id> <faction_id>

List rosters:
  /settlements admin roster list

Rare settlement-only override while holding an egg:
  /settlements admin sethand <unit_type> <settlement_id>

Template priority:
1. Settlement-specific override
2. Faction-assigned roster
3. Global default roster
4. Recruits' standard entity fallback

Professional units still spawn unhired and use Recruits' normal hiring mechanic after the settlement spends its strategic MP and wealth. Levies use their dedicated configured template, are named “Levyman,” and follow the existing hidden levy-mobilisation rules.

CHARACTERS AND MARRIAGE
  /character open
  /character create
  /character seat <settlement_id>
  /character marry <online_player>
  /campaign family

NPC spouses are text records for now; their listed bonuses are descriptive future effects and are not yet applied to settlement calculations. Player marriages are persistent and appear on both character sheets.

DIPLOMACY
Open with the Campaign menu, /campaign diplomacy, or the redirected Recruits faction key.

Only the Recruits faction leader or a server operator can formally send, accept, reject, or break treaties and declare war. Our saved campaign state is periodically enforced in both directions inside Recruits so recruit AI receives the correct Ally, Neutral, or Enemy state. Peace also clears the war relations of factions that joined through calls to war.

CURRENT WAR-SYSTEM BOUNDARY
V1.1 establishes formal wars, alliances, calls to war, and peace. Automatic siege victory detection, automatic occupation, annexation, claim transfer, detailed negotiated peace terms, and a selectable target-settlement picker are not yet automated. Settlement War and Independence War records already support a target settlement internally for later expansion.

MIGRATION
This release keeps the same mod ID and the existing settlement save. V1.0.x settlement, character, villager, contract, treasury, manpower, and population data should load normally. New campaign data is stored separately so old worlds do not need manual conversion.
