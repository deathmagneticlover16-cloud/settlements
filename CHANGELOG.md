# Changelog

## 3.0.0

### Economy

- Unified money sources and expenses under the State Treasury.
- Removed settlement-local Wealth, migration code, deposit history, and retired economy compatibility paths.
- Removed Prosperity; advancement now uses population, Stability, housing, and capture objectives.

### Interface

- Consolidated Settlement, Military, Faction, and Diplomacy into tabbed workspaces.
- Moved Main Seat selection into the Character Sheet.
- Simplified character creation and campaign navigation.
- Replaced vanilla campaign buttons and the tax slider with a compact Settlements visual theme.
- Added custom treasury and Stability iconography and reduced instructional text.

### Performance and safety

- Consolidated periodic server work, removed redundant client polling, and cached map-marker snapshots and read-only data views.
- Removed duplicate packet queue hops, bounded every variable-length network collection, and rate-limited client actions.
- Bounded region and housing scans, skipped unloaded chunks, and reduced capture-zone and marker rebuild work.
- Cleared transient player/server caches on logout and shutdown.

### Internals

- Removed retired save, command, API, and network formats.
- Removed unused conflict states, duplicate military paths, obsolete helpers, and legacy branded build documents.
- Network protocol is 43.
