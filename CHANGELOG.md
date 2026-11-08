# Changelog

## 4.0.0

### System simplification

- Replaced NPC spouse candidates and multiple spouses with player-to-player marriage.
- Added automatic immigration with settlement controls and server-configured limits.
- Removed Settler Contracts, Levy Tradition, recurring war aid, and recurring reparations.
- Replaced discrete casualty and levy-service tiers with scalable pressure effects.
- Discarded removed-system data and pre-4.0 campaign modifiers instead of converting them.

### Performance, safety, and interface

- Consolidated periodic server work, removed redundant client polling, and cached map-marker snapshots and read-only data views.
- Removed duplicate packet queue hops, bounded variable-length network collections, and rate-limited every client action route.
- Bounded housing scans, skipped unloaded chunks, and reduced capture-zone and marker rebuild work.
- Replaced vanilla campaign buttons and the tax slider with the Settlements interface theme.
- Removed legacy branding, build scripts, unused conflict states, duplicate military paths, and obsolete helpers.
- Network protocol is 44.

## 3.0.0

### Economy

- Unified money sources and expenses under the State Treasury.
- Removed settlement-local Wealth, migration code, deposit history, and retired economy compatibility paths.
- Removed Prosperity; advancement now uses population, Stability, housing, and capture objectives.

### Interface

- Consolidated Settlement, Military, Faction, and Diplomacy into tabbed workspaces.
- Moved Main Seat selection into the Character Sheet.
- Simplified character creation and campaign navigation.
### Internals

- Removed retired save, command, API, and network formats.
- Network protocol is 43.
