# The Culling

## 4.0.0 campaign tree

- Replaced the flat campaign launcher with distinct Settlement and Realm branches rendered by one reusable branch screen.
- Removed cross-links that mixed character and military controls into settlement management.
- Made custom Casus Belli text appear verbatim in declaration summaries and removed the conflict implementation note.
- Removed settlement, character, and faction-rank renaming, along with the Main House field and its network/configuration paths.
- Settlement creation now derives IDs from names and adds `_2`, `_3`, and later suffixes when needed.
- Bumped the major version because this removes saved character/faction fields and commands; network protocol is now 44.

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
- The original Culling economy overhaul was introduced in semantic version `3.0.0` with network protocol 43.
