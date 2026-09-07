SETTLEMENTS 2.6.0 — TEST BUILD
Minecraft 1.20.1 / Forge 47.2.0 / Villager Recruits 1.15.2 / Java 17

Continued from recovery HEAD 6e24ea4 on settlements-2.6.0-foundations.
The full Git history is retained. This is intended for a fresh test world;
old development/test-world compatibility is not guaranteed.

BUILD
Run BUILD-V2.6.0.bat, or gradlew.bat clean build.
Keep libs/recruits-1.20.1-1.15.2.jar in place.
Expected output: build/libs/settlements-2.6.0.jar.
The build includes dependency-free regression checks via regressionChecks.
Install the same Settlements build on clients and server (network protocol 38).

2.6 FEATURES
- One spendable State Treasury per polity. Settlements contribute tax income;
  military recruitment, mercenary upkeep and other expenses use that treasury.
- A draggable 50–150% tax slider; keyboard changes also commit to the server.
- Capture Zones: Village requires 1 Main; Town requires 1 Main + 1 Secondary;
  City/Major City requires 1 Main + 2 Secondaries. Objectives gate advancement.
- Capture changes military occupation; legal ownership is resolved separately.
- Research tiers: Village I, Town II, City III, Major City IV.
  Designating a Capital is political and does not promote settlement rank.
- Faction creation has identity, government, capital and banner-design pages.
  The banner editor offers registered patterns, base/layer dyes, a live preview,
  up to six layers, undo-last and clear. Drafts survive page changes and errors.
- Dye colors are enumerated dynamically and saved by name. Dye Depot 1.3.0
  is optional. Its extra colors appear when installed on both client and server.
  Settlements map markers retain the selected dye RGB; Recruits team/chat and
  unit colors use the nearest color in Recruits' fixed palettes.
- Bard gameplay has been removed. The old 2.5.4 documents are under docs/legacy.

OPTIONAL INTEGRATIONS
History Stages supplies technologies; see HISTORY-STAGES-RESEARCH-GUIDE.txt.
Dye Depot is not bundled and is not a build-time dependency.
RTS integrations may use api/SettlementsCaptureApi, CaptureZoneView and capture/
occupation events. Control survives contested state via CaptureZoneView.captured().
RTS camera and battle controls belong in the separate RTS mod.

SCOPE
Year/Serene Seasons Plus, League Alliances, Culture, Trade Agreements and
Administrative Capacity remain deferred; this build does not add those systems.
See TESTING-v2.6.0.txt and VALIDATION-v2.6.0.txt for validation limits.
