SETTLEMENTS 2.5.2 — TEST RELEASE
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17
Optional integration: History Stages 5.4.0+

Settlements 2.5.2 is the feature/fix consolidation build following 2.5.1. It keeps the research, independent-polity, faction-progression, taxation and Mercenary Rosters systems, and adds the latest population, research, trait, Roman character, city-upkeep and Bard work.

BUILD
1. Extract into a fresh folder.
2. Run BUILD-V2.5.2.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlements-2.5.2.jar
4. Install the SAME JAR on server and clients.
5. Remove older Settlements JARs before testing.
6. Old development-world compatibility is not a release requirement. Fresh test worlds are recommended.

IDENTITY
- Mod ID: settlements
- Package/resource namespace: ee.lastpagans.settlements / assets/settlements / data/settlements
- Network protocol: 33
- Prototype-era naming is not part of the active source.
- Physical campaign currency uses #settlements:currency.

POPULATION / SETTLER ATTRACTION
- The first configurable settlers still arrive after a newly founded settlement receives a valid Town Square.
- Later population growth uses Settler Contracts and active server time rather than Minecraft-day length.
- Contract frequency is now dynamic instead of one fixed interval.
- Stability, spare Housing and Food jointly determine Settler Attraction.
- Defaults range from roughly 12 active-server minutes for a barely eligible settlement to 2 minutes for an excellent settlement.
- Hard requirements such as free Housing, minimum Stability, Food reserve and pending-contract cap remain configurable.
- Residents shows attraction %, blockers and live time remaining.

CITY UPKEEP CHEST
- Manage > Locations can link a nearby inventory container as the City Upkeep Chest.
- Every maintenance sweep, edible items in the linked container can be converted into settlement Food when the stockpile is below its configured target.
- The default target is three days of civilian/professional/mercenary food upkeep.
- Consumption is capped per sweep and configurable.
- The system uses edible-item nutrition rather than a hardcoded bread list, so compatible modded food can work automatically.
- Treasury shows whether automatic upkeep is linked and can clear the link.

BARDS
- A managed recruit can be designated/undesignated as a Bard from the Military screen.
- A Bard stationed inside the settlement contributes a settlement-wide morale stack; a Bard campaigning outside town gives the same bonus to nearby managed soldiers within a configurable field radius. Stacking is capped.
- A Bard at the Town Square holding an item in #settlements:bard_instruments counts as performing and gives a configurable daily Stability bonus.
- The default bard-instrument tag contains minecraft:goat_horn and can be extended by datapacks.
- This is a Bard role/designation on a managed recruit, not a new Recruits entity class.

TRAITS / EVENTS
- Healthy now has a real mechanical effect: it reduces the chance of natural Plague in settlements ruled by that character.
- Natural Plague events now exist and use the existing Plague settlement modifier/news/toast system. Base chance, Healthy modifier and duration are configurable.
- Reserved now modifies Dynamic Reliability in both directions: treaty-breaking/reputation losses hurt less, but honorable/positive actions also earn less Reliability.
- Reserved scaling is configurable.

ROMAN CHARACTER SUPPORT
- Roman Paganism is a valid religion and participates in normal religion compatibility/Holy War/conversion logic as a pagan faith.
- Generated spouse candidates include a Roman culture/name pool.
- Roman Pagan characters strongly weight Roman spouse candidates while still allowing cross-cultural marriage candidates.
- Roman spouse names include gender-aware family-name forms.

RESEARCH / HISTORY STAGES FIXES
- Research is still optional and reflection-only: Settlements loads without History Stages.
- Technologies now respect History Stages pedestal/research tier requirements on the SERVER, not just visually.
- Settlement research tier maps to settlement development: Village 1, Town 2, City-State 3, Capital 4. A polity uses the best qualifying holding.
- History Stages MIN and EXACT pedestal-tier rules are respected.
- The Research screen has been rebuilt into a wider Total-War-inspired technology tree with dependency-depth columns, details panel and active-project strip.
- Active research progress, remaining time and availability refresh from the server approximately once per second while the screen is open.
- Completed research changes to DONE and dependent technologies can become available without closing/reopening the screen.
- Completion refreshes History Stages individual-stage cache, posts the IndividualUnlocked event, syncs the History Stages packet, and plays an unlock sound/Settlements toast.
- Only one active project exists per polity; independent holdings owned by one ruler share the same research queue.
- Scholarly/Prosperity research-speed systems remain active.

MILITARY POPULATION FIX
- Named Mercenary Roster soldiers no longer enter both the professional-unit registry and mercenary-company registry during EntityJoinLevelEvent.
- Existing in-memory/save overlap is repaired on settlement-data load.
- Named mercenaries count once as military population/food mouths and do not consume professional manpower.

FACTION CONFIGURATION
- Existing full-faction founding thresholds remain configurable: Town requirement, population, Stability, Prosperity and Wealth.
- Faction founding now also has a master enabled/disabled server-config toggle.
- Hidden independent backing factions remain technical Recruits infrastructure and do not become ordinary Settlements diplomatic factions.

INHERITED 2.5 SYSTEMS
- One-project research and optional History Stages integration.
- Named Mercenary Rosters with loyalty, poaching, wages and desertion.
- Hidden per-ruler independent backing factions for Recruits claims/units.
- Resource-gated real political faction founding.
- Full Low/Normal/High taxation.
- #settlements:currency abstraction.
- First settlers, housing, Food, Stability, Prosperity and manpower.
- Characters, three-trait creation, Main House, Main Seat, marriage, religion and Devotion.
- Reliability, gradual relation recovery, settlement wars, intervention, aid and structured peace treaties.
- Professional troops, levies, service-duration penalties, casualty effects and Mercenary Rosters.
- Multi-claim settlements and resident-villager claim confinement.
- Protected admin polities and server-authoritative political permissions.
- Replaceable GUI-art direction remains documented in GUI-ASSET-GUIDE.txt.

INTENTIONAL BOUNDARIES
- No Dynasty/children/heirs/fertility system.
- Tactical freecam, Total War battlefield formations/unit cards and commander-combat abilities belong to separate Recruits addons.

See CHANGELOG-v2.5.2.txt, COMMANDS-v2.5.2.txt, TESTING-v2.5.2.txt, VALIDATION-v2.5.2.txt and GUI-ASSET-GUIDE.txt.
