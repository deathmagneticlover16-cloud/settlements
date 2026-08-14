SETTLEMENTS 2.5.1 — TEST RELEASE
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17
Optional integration: History Stages 5.4.0+

Settlements 2.5.1 deepens the campaign layer with technology research, real independent-polity backing factions, resource-gated faction founding, and named mercenary companies with loyalty and poaching. It inherits the 2.4.1 taxation, first-settler, diplomacy, character, religion, levy, claim, reliability, GUI and release-hardening work.

BUILD
1. Extract into a fresh folder.
2. Run BUILD-V2.5.1.bat, or run: gradlew clean build
3. Expected JAR: build/libs/settlements-2.5.1.jar
4. Install the SAME JAR on server and clients.
5. Remove older Settlements JARs before testing.
6. Fresh worlds are strongly recommended. Backward compatibility with old development saves is not a goal.

IDENTITY
- Mod ID: settlements
- Package/resource namespace: ee.lastpagans.settlements / assets/settlements / data/settlements
- Network protocol: 32
- Prototype-era naming is not part of the active source.

2.5 RESEARCH
- Campaign > Research opens the technology screen.
- History Stages is optional. Without it, Settlements still loads; the Research screen explains that technology research is unavailable.
- With History Stages installed, its individual technologies/stages are discovered dynamically.
- Only one technology can be actively researched by a polity at a time.
- Research progresses using active server ticks, not Minecraft day length. Sleeping, /time set and seasonal daylight changes therefore do not shortcut research. Active projects pause if History Stages is absent.
- History Stages prerequisites are respected.
- Completion is persisted by Settlements and synchronized to eligible online members; completed stages are re-synchronized on login.
- Independent rulers share one research polity across all of their independent holdings. Founding/joining a proper faction merges completed research into the political faction and carries an active project if the receiving faction has no active project.
- Scholarly is a positive character trait. Default: +15% research speed. It also retains a small native Settlements economy benefit when History Stages is absent.
- Prosperity can provide a configurable additional research-speed bonus.

2.5 INDEPENDENT POLITIES
- An independent ruler now receives a hidden technical Villager Recruits faction/team for claims and native military ownership.
- The backing faction is intentionally hidden from Settlements political diplomacy: the player remains an Independent ruler until a real faction is founded or joined.
- Independent settlements owned by the same ruler share the backing identity.
- Claims and loaded military units migrate to the proper political faction when the ruler forms/joins one.
- Full faction founding is a progression step instead of a free technical action. Defaults require an established Town/City-State, 75 population, 60 Stability, 40 Prosperity and 500 settlement Wealth, plus Villager Recruits' own faction-creation requirements and a unique patterned banner. All Settlements thresholds are configurable.

2.5 MERCENARY ROSTERS
- Military > Mercenary Rosters opens a dedicated company market.
- Named companies are server-data-driven through config/settlements-mercenary-rosters.json.
- Each company defines a custom item icon, configured unit-template roster, composition, hire price, upkeep interval and starting loyalty.
- Only one active instance of each named company exists globally.
- Company loyalty/trust is persistent per employer.
- A loyal, paid company may refuse to betray its current employer.
- A company willing to defect demands a loyalty/reputation-based buyout premium.
- Missed wages reduce trust and employer Reliability. Three missed company payments cause desertion.
- Paid service improves company trust and can improve faction Reliability.
- Ending a paid contract honorably improves company trust toward that employer.
- Bidder Reliability affects hiring price.
- Mercenary companies are polity-level contracted forces and do not consume settlement manpower. They still count as military population and food mouths.
- Mercenary company configuration reload: /settlements admin mercenary reload_rosters

POPULATION GROWTH
- New settlements still receive the configurable first two settlers.
- Further residents arrive through Settler Contracts.
- Contracts now use active server time rather than Minecraft-day count. Default interval: 5 minutes while eligible.
- Default eligibility: free housing, Stability 60+, and at least 20 Food. A generated contract consumes 10 Food.
- Residents displays the exact reason growth is currently blocked or the remaining contract timer.
- All major contract pacing/requirement values are configurable under the residents config section.

INHERITED IMPORTANT SYSTEMS
- Full Low/Normal/High taxation and Treasury controls.
- Currency abstraction through #settlements:currency.
- Two configurable first settlers can arrive automatically after settlement founding.
- Dynamic Reliability and gradual peacetime relation recovery.
- Settlement Wars, escalation, aid and structured peace treaties.
- Characters, traits, marriage, Main House, Main Seat, religion, Devotion and Holy Sites.
- Population, food, housing, Prosperity, Stability and manpower.
- Professional units, muster grounds, levies, service-duration penalties and casualty effects.
- Multi-claim settlements and claim-bound civilian villagers.
- Protected admin polities and server-authoritative permissions.
- Replaceable-GUI-art direction documented in GUI-ASSET-GUIDE.txt.

CURRENCY
All physical currency logic uses the item tag #settlements:currency. No campaign economy feature should require one hardcoded coin. The bundled settlements:silver_coin is only the default tag member.

INTENTIONAL BOUNDARIES
- No children/heirs/fertility/dynasty generation.
- Tactical freecam, Total War unit controls/formations and commander battlefield mechanics belong to separate Recruits addons.

See CHANGELOG-v2.5.1.txt, COMMANDS-v2.5.1.txt, TESTING-v2.5.1.txt and GUI-ASSET-GUIDE.txt.
