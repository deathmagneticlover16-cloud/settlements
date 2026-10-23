# The Culling

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
