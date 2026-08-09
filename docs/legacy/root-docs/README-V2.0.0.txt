LAST PAGANS SETTLEMENTS 2.0.0
Minecraft 1.20.1 | Forge 47.2.0 | Java 17 | Villager Recruits 1.15.2

Settlements 2.0.0 is a source release for the campaign layer built around Villager Recruits. It upgrades the old prototype into separate character, settlement, faction, military, diplomacy, family and world-news systems.

BUILD
1. Extract the ZIP into a completely empty folder.
2. Keep the bundled Recruits JAR in libs/.
3. Run BUILD-V2.0.bat or: gradlew.bat clean build
4. The expected JAR is build\libs\settlementprototype-2.0.0.jar
5. Install the same JAR on the server and every client.
6. Remove all older settlementprototype JARs first.
7. Back up the world before the first 2.0.0 launch.

MAIN FEATURES

CHARACTERS
- First-join character creation with true expandable dropdowns.
- Head-and-shoulders live portraits rendered from the player's Minecraft skin.
- Catholic, Orthodox, Baltic Pagan, Finnic Pagan, Norse Pagan, Slavic Pagan, Islamic, Jewish and custom/other faith choices.
- Character sheets display faction rank, spouse, holdings and icon-based traits.
- Trait tooltips explain exact positive, negative or mixed effects.
- Roleplay names appear as [Faction Rank] Character Name while account names and UUIDs remain unchanged.

FACTIONS
- Faction and Diplomacy now have separate screens.
- Faction overview, leader, members, settlements, realm totals, roster, allies, enemies and active wars.
- Five permission tiers with faction-customizable visible titles, such as [Jarl], [Hersir] or [Commoner].
- Leaders can assign member tiers and rename every displayed rank from the Faction screen.
- The normal Recruits faction key opens the Settlements Faction screen.

SETTLEMENTS
- Town Square is the civilian location for contract settlers.
- Muster Ground is the required military spawn point.
- Barracks provide a military home, return order, rest/morale support and an optional Recruits upkeep/supply position.
- Independent settlements and faction settlements can use custom local banners.
- Faction settlements may instead inherit their faction banner.
- Population, contract and villager registration use one UUID-authoritative registry.
- A villager cannot be counted repeatedly, including contract-spawned villagers.
- The Effects page uses icons, sources, durations and exact modifier descriptions.

MILITARY
- Dedicated Military screen for Recruit, Shieldman, Bowman, Crossbowman, Horseman and Nomad recruitment.
- Levy recruitment remains separate and uses two weaker levymen per manpower point.
- Reusable global rosters, faction roster assignments and settlement overrides.
- Configured Recruits spawn eggs are stored as templates; unsafe UUID, owner, position, team and old settlement data are sanitized before spawning.
- Troops form in safe rows around the Muster Ground instead of appearing in the Town Square.
- Loaded settlement troops can be ordered back to their Barracks.
- Military summaries show manpower, professionals, levies, average Recruits morale, mercenary count and wage obligations.

MORALE AND MERCENARIES
- Villager Recruits' own morale value is used rather than a competing morale system.
- Settlement stability, ruler/spouse traits, Barracks rest and unpaid wages gradually influence morale.
- Only spawn-egg templates explicitly marked as mercenaries require recurring payment.
- Mercenary wages are paid from settlement wealth at the configured interval.
- Missed wages reduce morale; after three missed payments the unit deserts and leaves settlement/faction tracking.
- Ordinary recruits remain free of the selective mercenary wage system.

DIPLOMACY
- Reworked guided Diplomacy screen with separate Relations, Declare Conflict, Proposals and Active Wars pages.
- Declarations clearly identify attacker, defender, exact settlement/faction target, conflict tier, war goal and immediate consequences.
- Supported player-facing tiers: Raid, Skirmish, Settlement War and Faction War.
- War goals are restricted to the selected tier.
- Treaties, alliances, calls to war, peace proposals, reliability and faith relationship modifiers are persisted server-side.
- The Active Wars page lets authorized officers choose an ally to call; defensive allies may only be called when their partner is defending.
- Recruits ally/enemy states are synchronized from the campaign system.
- Ordinary accidental damage does not declare faction war.
- Original Recruits diplomacy-change packets are blocked so the campaign system stays authoritative.

FAMILY
- Player-to-player marriage proposals remain supported.
- Five AI spouse candidates are generated dynamically from Baltic, Finnic, Norse, Germanic and Slavic first/last-name pools.
- Generated spouses have faith, culture, three traits and real settlement/morale effects.
- Candidate sets persist and may refresh after seven Minecraft days, preventing unlimited immediate rerolls.

WORLD NEWS
- Separate World News tab with filters for wars, diplomacy, settlements, factions, characters, economy, religion and military events.
- Public and faction-restricted visibility is enforced server-side.
- War declarations, peace, treaties, marriages, settler arrivals and mercenary desertions can create news records.
- Up to 1,000 records are retained.

WORLD MAP
- Settlement markers continue to appear on the Villager Recruits world map.
- Settlement markers use cached server snapshots rather than entity scans each frame.

IMPORTANT LIMITATIONS
- This release does not include the abandoned tactical bird's-eye/RTS command system.
- It does not include the abandoned Total War-style unit-card overview.
- Independence War exists in the save model for later settlement-sovereignty work, but the 2.0.0 declaration screen intentionally does not allow it yet. The current faction/team model cannot represent a settlement rebelling against its own parent faction without misleading allegiance behaviour.
- Raid, Skirmish and Settlement War record a specific settlement target, but Villager Recruits battlefield allegiance is still team/faction based. Synchronizing hostility therefore affects the defending faction's Recruits team while the campaign record preserves the narrower target and war goal.
- Automatic siege resolution, occupation, annexation, vassalage and negotiated territorial peace terms are not included yet.
- Faction membership creation/invitation/removal still relies on Villager Recruits' underlying faction data; Settlements 2.0.0 adds the campaign overview and rank/title layer.

SAVE COMPATIBILITY
- Existing V1 settlement and character data is retained.
- Old faith labels migrate as follows: Pagan -> Finnic Paganism; Christian -> Catholic Christianity.
- Campaign 2.0.0 data is stored separately from settlement data to reduce migration risk.
- Always test a copied world first.
