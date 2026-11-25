# Security Audit — Settlements 6.0.1

Date: 2026-11-25

Scope: the Forge 1.20.1 Settlements source tree, including the integrated Campaign Map, Villager Recruits and Wildfire integration points, commands, persistence, military/economic transactions, and all registered network messages.

## Findings and fixes

### High — campaign army packet flooding

- Affected path: every `PLAY_TO_SERVER` message registered by `CampaignNetwork`, especially group-catalog enumeration, army creation, movement, deployment, flags, renaming, and commander changes.
- Impact: a modified client could repeatedly scan Recruits entities/groups, schedule transitions, dirty saves, and broadcast army snapshots, consuming CPU and network bandwidth.
- Fix: all campaign serverbound routes now share the per-player global/action budget used by Settlements. Expensive catalog and formation operations have longer cooldowns. The limiter is safe under concurrent callbacks, bounds route-map growth, and is cleared on logout/server stop.

### High — stale-balance double spending

- Affected path: `CampaignSystemsData.spendTreasury`, `StateTreasury`, and `CampaignManager.transferFactionAid`.
- Impact: concurrent callbacks could validate the same balance and both spend it. War aid also validated treasury, manpower, food, and recipient capacity separately from mutation.
- Fix: treasury check-and-deduct is synchronized and rejects negative amounts. War aid validates and commits its compound resource transfer under the same authoritative data lock, uses long arithmetic for totals, and rechecks the spend at commit time.

### High — non-atomic faction founding charge

- Affected path: `CampaignManager.createFactionInternal` to Recruits `FactionEvents.createTeam`.
- Impact: the faction was created before the State Treasury charge, and the result of the later spend was ignored. A changed balance or concurrent callback could create a faction without paying.
- Fix: the cost is reserved first. It is refunded only when Recruits confirms that no faction was created. If Recruits throws after creating the faction, normal Settlements setup continues without issuing a duplicating refund.

### High — campaign soldier recovery duplication window

- Affected path: `RecruitsCampaignGroupProvider.deploy` after campaign packing and an interrupted/cross-file save.
- Impact: Minecraft entity-region saves and mod `SavedData` are not one atomic file. A crash could leave both a live world recruit and its packed campaign copy.
- Fix: deployment checks every packed entity UUID across loaded server levels. If the original entity is alive, the packed duplicate is consumed and deployment accounting is reconciled instead of spawning a clone.

### Medium — campaign authority and value validation gaps

- Affected path: `CampaignNetwork` and `CampaignArmyData`.
- Impact: malformed coordinates/aspect ratios, inaccessible commanders/groups, excessive group lists, or unbounded army creation could corrupt state or create avoidable server load.
- Fix: authenticated sender ownership is rechecked at execution; coordinates and flag ratios must be finite and in range; commanders/groups must exist and be owned; groups are deduplicated; army and group limits are enforced in the authoritative data layer as well as packet handlers; compound creation is serialized.

### Medium — strategic intelligence disclosure across distance/dimensions

- Affected path: `CampaignNetwork.forObserver` and full/update army synchronization.
- Impact: enemy commander UUID/appearance, food, and fatigue were sent even when strength was redacted; matching coordinates in another dimension could grant close-range intelligence.
- Fix: non-overworld observers receive no proximity intelligence, commander details require identification range, and food/fatigue require exact observation range. Owners continue to receive their full records.

### Medium — integer overflow in military ledgers

- Affected path: reserve totals, pending deployment totals, training queue totals, native/foreign MP refunds, Silver refunds, and deployment totals.
- Impact: sufficiently large or corrupted values could wrap negative, bypass capacity checks, or destroy/refund incorrect resources.
- Fix: aggregate calculations use `long`, externally stored integer values saturate, queue order sizes are bounded, and refund multiplication cannot wrap.

### Medium — unbounded/corrupt persistent army and military data

- Affected path: `CampaignArmyData.load`, `CampaignArmy.load`, and `MilitaryData.load`.
- Impact: malformed or oversized lists could create excessive memory/CPU use during world load; NaN positions/fatigue could poison movement; duplicate memberships could corrupt associations.
- Fix: total records, queues, armies, groups, paths, and packed entities are bounded. Invalid records are skipped, duplicate memberships are removed, non-finite positions are reset, fatigue/aspect values are sanitized, and loaded queue sizes follow runtime limits.

### Medium — cross-thread mutable campaign army state

- Affected path: campaign army ticking, packets, entity integration callbacks, save serialization, and public army accessors.
- Impact: optimization mods dispatching callbacks concurrently could cause lost updates, concurrent modification, or duplicate membership.
- Fix: campaign army compound operations and serialization are synchronized on the authoritative `CampaignArmyData` instance. Packet formation creation holds that lock across validation and membership assignment.

### Low — rejection log amplification and insufficient accountability

- Affected path: rejected packets, unauthorized settlement actions, and blocked Recruits/Wildfire compatibility packets.
- Impact: invalid traffic was mostly silent, while the blocked Recruits diplomacy path could send a chat response for every malicious packet.
- Fix: rate-limited security logging records authenticated name/UUID, action, target, and reason without dumping payloads. Unauthorized settlement operations are logged. Compatibility responses/reassertions are rate-limited.

### Medium — province mutation authorization lived only in command registration

- Affected path: public `CampaignOwnershipData#setOwner` and `setController` methods.
- Impact: an alternate server-side entry point could call the data mutators without passing through Brigadier's operator requirement.
- Fix: ownership/controller mutation now requires a `CommandSourceStack` and independently verifies permission level 2 inside the authoritative data layer. Command registration checks remain in place as the first layer.

## Existing controls verified

- Every Settlements and Campaign Map message is registered with an explicit network direction.
- Serverbound handlers derive identity from `NetworkEvent.Context#getSender`; client-supplied ownership is not authoritative.
- Settlements serverbound handlers enqueue state changes on the server thread; Campaign Map uses Forge main-thread consumers.
- Variable-sized packet collections use explicit decode limits. Text fields use bounded `readUtf` lengths.
- Character body values reject non-finite input and are server-sanitized; trait offers are recomputed server-side.
- Settlement, faction, diplomacy, research, military, mercenary, and area mutations revalidate current server-side authority and resources.
- Administrative commands use server command permission checks. Campaign army player commands recheck ownership; operator bypass is explicit.
- Recruits diplomacy packets are intercepted so its alternate client channel cannot bypass Settlements diplomacy rules.
- Icy's Better Horses remains mandatory with version range `[2.1.6,2.2.0)`.

## Compatibility and gameplay changes

- Mod version: `6.0.1`.
- Settlements protocol: `49` (was `48`).
- Integrated Campaign Map protocol: `8` (was `7`).
- Client and server must both update.
- Existing save IDs and normal save formats are retained. Oversized/corrupt records beyond safety limits are ignored rather than loaded.
- A player may own at most 64 strategic armies; each army may contain at most 128 groups. These limits prevent unbounded persistent/network state.
- Normal economic prices, military costs, diplomacy rules, dependencies, and intended administrative permissions are unchanged.

## Verification

- Source inspection covered all registered packet directions and handlers, the alternate Recruits/Wildfire packet mixins, state-changing campaign/settlement managers, strategic army persistence, military persistence, commands, and cross-mod army packing/deployment.
- `git diff --check` passes.
- Regression checks were added for negative treasury spending, concurrent double-spend resistance, and saturating military reserve arithmetic.
- Per the user's corrected instruction, Gradle compilation and automated execution were **not run**. Minecraft was not launched.

## Remaining concerns

- TickWeave and other optimization mods can alter execution assumptions outside Settlements. Critical treasury and campaign-army compounds are protected, but runtime stress testing with the server's exact modpack is still required.
- Minecraft does not provide one atomic transaction spanning entity-region files, third-party Recruits data, and Settlements `SavedData`. UUID reconciliation prevents the identified campaign deployment duplication case, but a full write-ahead transaction log across mods is not available through their public APIs.
- The security of Forge, Minecraft packet framing, Villager Recruits, Wildfire Gender, Icy's Better Horses, History Stages, Immersive Paintings, RTS Camera, and any scripting/permission mods is outside this repository. Their exposed interactions were audited where Settlements calls or intercepts them.
- No runtime/in-game audit or fuzzing was performed. Server administrators should test disconnects and forced shutdowns during faction creation, army packing/deployment, mercenary hiring, peace application, and war aid before production deployment.
