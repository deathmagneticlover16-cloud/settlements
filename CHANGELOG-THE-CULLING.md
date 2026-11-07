# The Culling

## 4.0.0 — system simplification

- Removed NPC spouse candidates, refresh history, quality rolls, multiple spouses, and diminishing spouse effects. Player marriage, divorce, political marriage alliances, and live player-spouse trait effects remain.
- Repurposed Charismatic from NPC candidate quality to a +5 diplomatic-relations trait.
- Removed the Settler Contract item, queue, redemption ledger, model, command action, and GUI controls.
- Added automatic immigration using the existing housing, Stability, Food, attraction, and active-server timing rules. Each settlement has a manager-controlled immigration toggle, and server configuration supplies a global switch and physical-villager cap.
- Removed Levy Tradition storage, XP bonuses, administration, and UI. Recruits XP and military traits remain.
- Replaced five casualty tiers with one quadratic casualty effect and four service tiers with one gradually scaling service-pressure effect.
- Removed recurring war aid, cancellation records, schedules, and ledger UI. Direct treasury, Food, and manpower aid remains.
- Removed daily reparations, schedules, missed-payment notifications, and treaty controls. Immediate treasury peace terms remain.
- Reviewed diplomatic agreements and retained NAPs, defensive alliances, military alliances, and marriage alliances because their triggers and obligations differ.
- Confirmed the retired Prosperity mechanic has no runtime, configuration, command, API, or GUI implementation.
- Made this a clean save-format break: removed-system data is discarded instead of converted, and pre-4.0 campaign modifiers are cleared on load.
- Bumped the semantic version to `4.0.0` and network protocol to `44` for removed gameplay and packet formats.

## 3.0.0 — original Culling release

## Economy

- Replaced the old faction-wealth backing field with a direct `StateTreasuries` store.
- Removed settlement-local Wealth fields, serialization, mutations, commands, and migration code.
- Routed every money source and expense through the state treasury.
- Renamed player and administrator terminology from Wealth to State Treasury.
- Removed rolling deposit history and old economy compatibility paths.

## Prosperity removal

- Removed Prosperity storage, serialization, calculation, modifiers, UI, API fields, commands, configuration, textures, and regression assumptions.
- Removed Prosperity requirements from advancement and faction founding.
- Removed Prosperity tax efficiency and research speed bonuses.
- Advancement now uses population, Stability, housing, and capture objectives.

## Interface

- Removed the standalone character preview page; creation proceeds directly to traits.
- Moved Main Seat selection into the Character Sheet.
- Retained Settlement, Military, Faction, and Diplomacy as shared tabbed workspaces instead of separate top-level pages.
- Renamed money fields consistently and removed duplicate state-balance presentations.

## Cleanup

- Removed historical documentation and active compatibility code for retired formats.
- Removed missing-body, old-faith, old-contract, old-mercenary, and one-click-war compatibility branches.
- Bumped the mod to semantic version `3.0.0` because this release intentionally breaks removed save, command, API, and network formats; network protocol is now 43.
