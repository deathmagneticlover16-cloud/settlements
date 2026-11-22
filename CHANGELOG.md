# Changelog

## 5.0.0

### Diplomacy

- Replaced the tabbed diplomacy menu with a map-centred faction workspace, conditional dossiers, contextual negotiations, correspondence, culture, relationship, and agreement overlays.
- Normalized conflicts to War and Raid, migrated legacy conflict records, required an eligible casus belli, and added Independence War tribute suspension.
- Added durable sent/received/history correspondence, withdrawal, peaceful independence and tribute requests, requests to join wars, and asynchronous multi-ally calls.
- Added all eight supplied culture dossiers, Celtic Paganism, faction culture selection, and server-derived signed relationship-factor arithmetic.

### Settlements and military

- Removed manual capital reassignment.
- Removed Shieldmen from all player-selectable military and template lists while retaining old-save decoding.
- Spawned mustered recruits only on safe blocks inside the configured Muster Ground and kept obstructed orders queued.
- Shortened the training confirmation to `X added to training queue.`

- Network protocol is 48.

## 4.2.2

- Prevented Recruits' per-player soldier registry from accepting null owner UUIDs.
- Removed legacy null-owner count entries before world serialization and added caller stack traces for rejected ownership operations.

## 4.2.1

- Fixed scaled mouse-coordinate handling in the Military screen click and scroll handlers.

## 4.2.0

### Recruitment and mustering

- Added persistent per-settlement troop reserves with native and foreign origin tracking, configurable capacities, costs, and training durations.
- Added a server-authoritative global FIFO training queue with individual completions and exact unfinished-order refunds.
- Added one-group timed mustering at the designated Muster Ground, with one directly owned recruit arriving per second and pending soldiers held against the deployment cap.
- Added configurable mercenary contract acceptance followed by automatic arrival through the same Muster Ground queue without a second hiring charge.
- Added player-wide world and campaign deployment accounting plus a passive barracks API for Garrison integration.

### Interface and lifecycle

- Rebuilt Military as full Overview, Train Troops, and Muster Troops workspaces with live origin bars, queue progress, cancellation, quantity controls, and active arrival status.
- Added acceptance and mustering progress to the existing mercenary contract screen.
- Removed Settlements-owned barracks rotation, rest, morale, and upkeep automation while retaining barracks geometry as an integration hook.
- Converted conquered trained reserves to foreign manpower and retired the legacy levy mobilization route.
- Network protocol is 47.

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
