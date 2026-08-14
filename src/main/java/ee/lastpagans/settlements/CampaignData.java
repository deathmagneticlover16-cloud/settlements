package ee.lastpagans.settlements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Campaign-layer data for Settlements diplomacy, marriage, banners, military templates and world news.
 */
public final class CampaignData extends SavedData {
    private static final String DATA_NAME = "settlements_campaign";

    private final Map<String, SettlementVisualRecord> settlementVisuals = new HashMap<>();
    private final Map<String, RosterRecord> rosters = new LinkedHashMap<>();
    private final Map<String, String> factionRoster = new HashMap<>();
    private String defaultRoster = "default";

    private final Map<FactionPair, DiplomacyRecord> diplomacy = new HashMap<>();
    private final Map<UUID, DiplomacyProposal> proposals = new LinkedHashMap<>();
    private final Map<UUID, WarRecord> wars = new LinkedHashMap<>();
    private final Map<String, Integer> factionReliability = new HashMap<>();

    private final Map<UUID, MarriageRecord> marriages = new HashMap<>();
    private final Map<UUID, List<MarriageRecord>> additionalNpcMarriages = new HashMap<>();
    private final Map<UUID, UUID> pendingPlayerMarriageProposals = new HashMap<>();
    private final Map<UUID, List<NpcSpouse>> spouseCandidates = new HashMap<>();
    private final Map<UUID, Long> spouseCandidateGeneratedDay = new HashMap<>();

    private final Map<String, FactionProfile> factionProfiles = new HashMap<>();
    private final List<WorldNewsEntry> worldNews = new ArrayList<>();

    public CampaignData() {
        rosters.put("default", new RosterRecord("default", "Default Roster"));
    }

    public static CampaignData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(CampaignData::load, CampaignData::new, DATA_NAME);
    }

    public static CampaignData load(CompoundTag tag) {
        CampaignData data = new CampaignData();
        data.rosters.clear();

        for (Tag raw : tag.getList("SettlementVisuals", Tag.TAG_COMPOUND)) {
            SettlementVisualRecord record = SettlementVisualRecord.fromNBT((CompoundTag) raw);
            if (!record.settlementId.isBlank()) data.settlementVisuals.put(record.settlementId, record);
        }
        for (Tag raw : tag.getList("Rosters", Tag.TAG_COMPOUND)) {
            RosterRecord roster = RosterRecord.fromNBT((CompoundTag) raw);
            if (!roster.id.isBlank()) data.rosters.put(roster.id, roster);
        }
        if (data.rosters.isEmpty()) data.rosters.put("default", new RosterRecord("default", "Default Roster"));
        data.defaultRoster = SettlementData.normalizeId(tag.getString("DefaultRoster"));
        if (!data.rosters.containsKey(data.defaultRoster)) data.defaultRoster = data.rosters.keySet().iterator().next();

        for (Tag raw : tag.getList("FactionRosters", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String faction = entry.getString("Faction");
            String roster = SettlementData.normalizeId(entry.getString("Roster"));
            if (!faction.isBlank() && data.rosters.containsKey(roster)) data.factionRoster.put(faction, roster);
        }
        for (Tag raw : tag.getList("Diplomacy", Tag.TAG_COMPOUND)) {
            DiplomacyRecord record = DiplomacyRecord.fromNBT((CompoundTag) raw);
            if (!record.a.isBlank() && !record.b.isBlank()) data.diplomacy.put(FactionPair.of(record.a, record.b), record);
        }
        for (Tag raw : tag.getList("Proposals", Tag.TAG_COMPOUND)) {
            DiplomacyProposal proposal = DiplomacyProposal.fromNBT((CompoundTag) raw);
            data.proposals.put(proposal.id, proposal);
        }
        for (Tag raw : tag.getList("Wars", Tag.TAG_COMPOUND)) {
            WarRecord war = WarRecord.fromNBT((CompoundTag) raw);
            data.wars.put(war.id, war);
        }
        for (Tag raw : tag.getList("Reliability", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String faction = entry.getString("Faction");
            if (!faction.isBlank()) data.factionReliability.put(faction, clamp(entry.getInt("Value"), -100, 100));
        }
        for (Tag raw : tag.getList("Marriages", Tag.TAG_COMPOUND)) {
            MarriageRecord marriage = MarriageRecord.fromNBT((CompoundTag) raw);
            if (marriage.playerId != null) data.marriages.put(marriage.playerId, marriage);
        }
        for (Tag raw : tag.getList("AdditionalNpcMarriages", Tag.TAG_COMPOUND)) {
            MarriageRecord marriage = MarriageRecord.fromNBT((CompoundTag) raw);
            if (marriage.playerId != null && marriage.kind == MarriageKind.NPC)
                data.additionalNpcMarriages.computeIfAbsent(marriage.playerId, key -> new ArrayList<>()).add(marriage);
        }
        for (Tag raw : tag.getList("MarriageProposals", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Target") && entry.hasUUID("Proposer")) {
                data.pendingPlayerMarriageProposals.put(entry.getUUID("Target"), entry.getUUID("Proposer"));
            }
        }
        for (Tag raw : tag.getList("SpouseCandidateSets", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Player")) continue;
            UUID player = entry.getUUID("Player");
            List<NpcSpouse> values = new ArrayList<>();
            for (Tag candidateRaw : entry.getList("Candidates", Tag.TAG_COMPOUND)) {
                values.add(NpcSpouse.fromNBT((CompoundTag) candidateRaw));
            }
            if (!values.isEmpty()) data.spouseCandidates.put(player, values);
            data.spouseCandidateGeneratedDay.put(player, entry.getLong("GeneratedDay"));
        }
        for (Tag raw : tag.getList("FactionProfiles", Tag.TAG_COMPOUND)) {
            FactionProfile profile = FactionProfile.fromNBT((CompoundTag) raw);
            if (!profile.factionId.isBlank()) data.factionProfiles.put(profile.factionId, profile);
        }
        for (Tag raw : tag.getList("WorldNews", Tag.TAG_COMPOUND)) {
            WorldNewsEntry entry = WorldNewsEntry.fromNBT((CompoundTag) raw);
            if (!entry.headline.isBlank()) data.worldNews.add(entry);
        }
        data.trimWorldNews();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag visuals = new ListTag();
        settlementVisuals.values().forEach(record -> visuals.add(record.toNBT()));
        tag.put("SettlementVisuals", visuals);

        ListTag rosterList = new ListTag();
        rosters.values().forEach(roster -> rosterList.add(roster.toNBT()));
        tag.put("Rosters", rosterList);
        tag.putString("DefaultRoster", defaultRoster);

        ListTag assignments = new ListTag();
        factionRoster.forEach((faction, roster) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Faction", faction);
            entry.putString("Roster", roster);
            assignments.add(entry);
        });
        tag.put("FactionRosters", assignments);

        ListTag diplomacyList = new ListTag();
        diplomacy.values().forEach(record -> diplomacyList.add(record.toNBT()));
        tag.put("Diplomacy", diplomacyList);

        ListTag proposalList = new ListTag();
        proposals.values().forEach(proposal -> proposalList.add(proposal.toNBT()));
        tag.put("Proposals", proposalList);

        ListTag warList = new ListTag();
        wars.values().forEach(war -> warList.add(war.toNBT()));
        tag.put("Wars", warList);

        ListTag reliability = new ListTag();
        factionReliability.forEach((faction, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Faction", faction);
            entry.putInt("Value", value);
            reliability.add(entry);
        });
        tag.put("Reliability", reliability);

        ListTag marriageList = new ListTag();
        marriages.values().forEach(marriage -> marriageList.add(marriage.toNBT()));
        tag.put("Marriages", marriageList);
        ListTag additionalMarriageList = new ListTag();
        additionalNpcMarriages.values().forEach(values -> values.forEach(marriage -> additionalMarriageList.add(marriage.toNBT())));
        tag.put("AdditionalNpcMarriages", additionalMarriageList);

        ListTag marriageProposals = new ListTag();
        pendingPlayerMarriageProposals.forEach((target, proposer) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", target);
            entry.putUUID("Proposer", proposer);
            marriageProposals.add(entry);
        });
        tag.put("MarriageProposals", marriageProposals);

        ListTag candidateSets = new ListTag();
        spouseCandidates.forEach((player, candidates) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putLong("GeneratedDay", spouseCandidateGeneratedDay.getOrDefault(player, 0L));
            ListTag values = new ListTag();
            candidates.forEach(candidate -> values.add(candidate.toNBT()));
            entry.put("Candidates", values);
            candidateSets.add(entry);
        });
        tag.put("SpouseCandidateSets", candidateSets);

        ListTag profiles = new ListTag();
        factionProfiles.values().forEach(profile -> profiles.add(profile.toNBT()));
        tag.put("FactionProfiles", profiles);

        ListTag news = new ListTag();
        worldNews.forEach(entry -> news.add(entry.toNBT()));
        tag.put("WorldNews", news);
        return tag;
    }

    public SettlementVisualRecord visualFor(String settlementId) {
        String id = SettlementData.normalizeId(settlementId);
        return settlementVisuals.computeIfAbsent(id, SettlementVisualRecord::new);
    }

    public RosterRecord createRoster(String rawId, String rawName) {
        String id = SettlementData.normalizeId(rawId);
        if (id.isBlank() || rosters.containsKey(id)) return null;
        RosterRecord roster = new RosterRecord(id, SettlementData.cleanText(rawName, id, 48));
        rosters.put(id, roster);
        setDirty();
        return roster;
    }

    public RosterRecord getRoster(String rawId) {
        return rosters.get(SettlementData.normalizeId(rawId));
    }

    public RosterRecord copyRoster(String sourceId, String rawNewId, String rawName) {
        RosterRecord source = getRoster(sourceId);
        String newId = SettlementData.normalizeId(rawNewId);
        if (source == null || newId.isBlank() || rosters.containsKey(newId)) return null;
        RosterRecord copy = new RosterRecord(newId, SettlementData.cleanText(rawName, newId, 48));
        source.templates.forEach((type, stack) -> copy.templates.put(type, stack.copy()));
        rosters.put(newId, copy);
        setDirty();
        return copy;
    }

    public Collection<RosterRecord> getRosters() {
        return List.copyOf(rosters.values());
    }

    public void setDefaultRoster(String rosterId) {
        String id = SettlementData.normalizeId(rosterId);
        if (rosters.containsKey(id)) {
            defaultRoster = id;
            setDirty();
        }
    }

    public String getDefaultRosterId() {
        return defaultRoster;
    }

    public void assignFactionRoster(String factionId, String rosterId) {
        String roster = SettlementData.normalizeId(rosterId);
        if (factionId == null || factionId.isBlank() || !rosters.containsKey(roster)) return;
        factionRoster.put(factionId, roster);
        setDirty();
    }


    public String assignedRosterId(SettlementData.SettlementRecord settlement) {
        return factionRoster.getOrDefault(settlement.getFactionTeam(), defaultRoster);
    }

    public String assignedRosterName(SettlementData.SettlementRecord settlement) {
        RosterRecord roster = rosters.get(assignedRosterId(settlement));
        return roster == null ? "Default Roster" : roster.getName();
    }

    public ItemStack resolveUnitTemplate(SettlementData.SettlementRecord settlement, UnitType type) {
        SettlementVisualRecord visual = visualFor(settlement.getId());
        ItemStack settlementTemplate = visual.getUnitTemplate(type);
        if (!settlementTemplate.isEmpty()) return settlementTemplate;

        String assigned = factionRoster.getOrDefault(settlement.getFactionTeam(), defaultRoster);
        RosterRecord roster = rosters.get(assigned);
        if (roster != null) {
            ItemStack template = roster.getTemplate(type);
            if (!template.isEmpty()) return template;
        }
        RosterRecord fallback = rosters.get(defaultRoster);
        return fallback == null ? ItemStack.EMPTY : fallback.getTemplate(type);
    }

    public DiplomacyRecord relation(String firstFaction, String secondFaction) {
        FactionPair key = FactionPair.of(firstFaction, secondFaction);
        return diplomacy.computeIfAbsent(key, pair -> new DiplomacyRecord(pair.first, pair.second));
    }

    public Collection<DiplomacyRecord> getDiplomacy() {
        return List.copyOf(diplomacy.values());
    }

    public DiplomacyProposal createProposal(
            ProposalType type,
            String fromFaction,
            String toFaction,
            UUID actor,
            UUID linkedWar,
            long day
    ) {
        DiplomacyProposal proposal = new DiplomacyProposal(
                UUID.randomUUID(), type, fromFaction, toFaction, actor, linkedWar, day
        );
        proposals.put(proposal.id, proposal);
        setDirty();
        return proposal;
    }

    public DiplomacyProposal getProposal(UUID id) {
        return proposals.get(id);
    }

    public List<DiplomacyProposal> proposalsFor(String factionId) {
        return proposals.values().stream()
                .filter(proposal -> proposal.status == ProposalStatus.PENDING)
                .filter(proposal -> factionId.equals(proposal.toFaction))
                .sorted(Comparator.comparingLong(DiplomacyProposal::createdDay).reversed())
                .toList();
    }

    public void resolveProposal(UUID id, ProposalStatus status) {
        DiplomacyProposal proposal = proposals.get(id);
        if (proposal == null || proposal.status != ProposalStatus.PENDING) return;
        proposal.status = status;
        setDirty();
    }

    public WarRecord createWar(
            WarType type,
            String attackerFaction,
            String defenderFaction,
            String targetSettlement,
            UUID declaredBy,
            long day
    ) {
        return createWar(type, WarGoal.defaultFor(type), attackerFaction, defenderFaction, targetSettlement, declaredBy, day);
    }

    public WarRecord createWar(
            WarType type,
            WarGoal goal,
            String attackerFaction,
            String defenderFaction,
            String targetSettlement,
            UUID declaredBy,
            long day
    ) {
        WarRecord war = new WarRecord(
                UUID.randomUUID(),
                type,
                goal == null || !goal.appliesTo(type) ? WarGoal.defaultFor(type) : goal,
                attackerFaction,
                defenderFaction,
                SettlementData.normalizeId(targetSettlement),
                declaredBy,
                day
        );
        wars.put(war.id, war);
        if (type != WarType.SETTLEMENT_WAR && attackerFaction != null && defenderFaction != null
                && !attackerFaction.isBlank() && !defenderFaction.isBlank() && !attackerFaction.equals(defenderFaction)) {
            DiplomacyRecord relation = relation(attackerFaction, defenderFaction);
            relation.state = TreatyState.WAR;
            relation.relationship = clamp(relation.relationship - 40, -100, 100);
            relation.lastChangedDay = day;
        }
        setDirty();
        return war;
    }

    public WarRecord createSettlementWar(String attackerSettlement, String defenderSettlement,
                                         String attackerFaction, String defenderFaction, WarGoal goal,
                                         String customCasusBelli, UUID declaredBy, long day) {
        WarRecord war = new WarRecord(UUID.randomUUID(), WarType.SETTLEMENT_WAR,
                goal == null || !goal.appliesTo(WarType.SETTLEMENT_WAR) ? WarGoal.CAPTURE_SETTLEMENT : goal,
                attackerFaction == null ? "" : attackerFaction, defenderFaction == null ? "" : defenderFaction,
                SettlementData.normalizeId(defenderSettlement), declaredBy, day);
        war.attackerSettlement = SettlementData.normalizeId(attackerSettlement);
        war.customCasusBelli = SettlementData.cleanText(customCasusBelli, "", 100);
        war.attackerFactionJoined = false;
        war.defenderFactionJoined = false;
        wars.put(war.id, war);
        setDirty();
        return war;
    }

    public WarRecord getWar(UUID id) {
        return wars.get(id);
    }

    public void setWarCustomCasusBelli(UUID id, String text) {
        WarRecord war = id == null ? null : wars.get(id);
        if (war == null) return;
        war.customCasusBelli = SettlementData.cleanText(text, "", 100);
        setDirty();
    }

    public Collection<WarRecord> getWars() { return List.copyOf(wars.values()); }

    public List<WarRecord> activeWarsFor(String factionId) {
        return wars.values().stream()
                .filter(war -> war.status == WarStatus.ACTIVE)
                .filter(war -> war.involves(factionId))
                .sorted(Comparator.comparingLong(WarRecord::startedDay).reversed())
                .toList();
    }

    public void endWar(UUID warId, long day, String terms) {
        WarRecord war = wars.get(warId);
        if (war == null || war.status != WarStatus.ACTIVE) return;
        war.status = WarStatus.ENDED;
        war.endedDay = day;
        war.peaceTerms = SettlementData.cleanText(terms, "Peace agreed.", 180);
        boolean parentFactionsWereBelligerents = war.type != WarType.SETTLEMENT_WAR
                || (war.attackerFactionJoined && war.defenderFactionJoined);
        if (parentFactionsWereBelligerents && !war.attackerFaction.isBlank() && !war.defenderFaction.isBlank()
                && !war.attackerFaction.equals(war.defenderFaction)) {
            relation(war.attackerFaction, war.defenderFaction).setState(TreatyState.NEUTRAL, day);
        }
        setDirty();
    }

    public int getFactionReliability(String factionId) {
        return factionReliability.getOrDefault(factionId, 0);
    }

    public void changeFactionReliability(String factionId, int delta) {
        factionReliability.put(factionId, clamp(getFactionReliability(factionId) + delta, -100, 100));
        setDirty();
    }

    public MarriageRecord getMarriage(UUID playerId) {
        return marriages.get(playerId);
    }

    public List<MarriageRecord> getAdditionalNpcMarriages(UUID playerId) {
        return List.copyOf(additionalNpcMarriages.getOrDefault(playerId, List.of()));
    }

    public int spouseCount(UUID playerId) {
        return (marriages.containsKey(playerId) ? 1 : 0) + additionalNpcMarriages.getOrDefault(playerId, List.of()).size();
    }

    public static int spouseEffectPercent(int spouseIndex) {
        return switch (Math.max(0, spouseIndex)) {
            case 0 -> 100;
            case 1 -> SettlementConfig.SECOND_SPOUSE_EFFECT_PERCENT.get();
            case 2 -> SettlementConfig.THIRD_SPOUSE_EFFECT_PERCENT.get();
            default -> SettlementConfig.LATER_SPOUSE_EFFECT_PERCENT.get();
        };
    }

    public static int scaledSpouseEffect(int value, int spouseIndex) {
        return Math.round(value * spouseEffectPercent(spouseIndex) / 100.0F);
    }

    public boolean marryAdditionalNpc(UUID playerId, NpcSpouse spouse, long day) {
        if (playerId == null || spouse == null || !marriages.containsKey(playerId)) return false;
        MarriageRecord primary = marriages.get(playerId);
        if (primary != null && primary.kind == MarriageKind.NPC && primary.spouseId.equals(spouse.id())) return false;
        List<MarriageRecord> values = additionalNpcMarriages.computeIfAbsent(playerId, key -> new ArrayList<>());
        if (values.size() >= 3) return false;
        if (values.stream().anyMatch(value -> value.spouseId.equals(spouse.id()))) return false;
        values.add(MarriageRecord.forNpc(playerId, spouse, day));
        List<NpcSpouse> candidates = spouseCandidates.get(playerId);
        if (candidates != null) candidates.removeIf(value -> value.id().equals(spouse.id()));
        setDirty();
        return true;
    }

    public void marryNpc(UUID playerId, NpcSpouse spouse, long day) {
        marriages.put(playerId, MarriageRecord.forNpc(playerId, spouse, day));
        List<NpcSpouse> candidates = spouseCandidates.get(playerId);
        if (candidates != null && spouse != null) candidates.removeIf(value -> value.id().equals(spouse.id()));
        pendingPlayerMarriageProposals.remove(playerId);
        setDirty();
    }

    public boolean proposePlayerMarriage(UUID proposer, UUID target) {
        if (proposer == null || target == null || proposer.equals(target)) return false;
        if (marriages.containsKey(proposer) || marriages.containsKey(target)) return false;
        pendingPlayerMarriageProposals.put(target, proposer);
        setDirty();
        return true;
    }

    public UUID pendingMarriageProposer(UUID target) {
        return pendingPlayerMarriageProposals.get(target);
    }

    public boolean acceptPlayerMarriage(UUID target, long day) {
        UUID proposer = pendingPlayerMarriageProposals.remove(target);
        if (proposer == null || marriages.containsKey(target) || marriages.containsKey(proposer)) return false;
        MarriageRecord first = MarriageRecord.forPlayer(proposer, target, day);
        MarriageRecord second = MarriageRecord.forPlayer(target, proposer, day);
        marriages.put(proposer, first);
        marriages.put(target, second);
        setDirty();
        return true;
    }

    public void rejectPlayerMarriage(UUID target) {
        if (pendingPlayerMarriageProposals.remove(target) != null) setDirty();
    }

    public void divorce(UUID playerId) {
        MarriageRecord marriage = marriages.remove(playerId);
        additionalNpcMarriages.remove(playerId);
        if (marriage != null && marriage.kind == MarriageKind.PLAYER && marriage.spousePlayerId != null) {
            marriages.remove(marriage.spousePlayerId);
            additionalNpcMarriages.remove(marriage.spousePlayerId);
        }
        setDirty();
    }

    public void clearFamilyData(UUID playerId) {
        divorce(playerId);
        pendingPlayerMarriageProposals.remove(playerId);
        pendingPlayerMarriageProposals.entrySet().removeIf(entry -> playerId.equals(entry.getValue()));
        spouseCandidates.remove(playerId);
        spouseCandidateGeneratedDay.remove(playerId);
        setDirty();
    }

    public void resetPlayerData(UUID playerId) {
        if (playerId == null) return;
        clearFamilyData(playerId);
        for (FactionProfile profile : factionProfiles.values()) profile.memberRanks.remove(playerId);
        setDirty();
    }

    public void resetMarriage(UUID playerId) { clearFamilyData(playerId); }

    public void resetFactionRank(UUID playerId) {
        boolean changed = false;
        for (FactionProfile profile : factionProfiles.values()) changed |= profile.memberRanks.remove(playerId) != null;
        if (changed) setDirty();
    }

    public List<NpcSpouse> candidatesFor(UUID playerId, long day) {
        return candidatesFor(playerId, day, List.of(), List.of(), 0, "");
    }

    public List<NpcSpouse> candidatesFor(UUID playerId, long day, List<String> rejectedNames, List<String> seenNames, int qualityBonus) {
        return candidatesFor(playerId, day, rejectedNames, seenNames, qualityBonus, "");
    }

    public List<NpcSpouse> candidatesFor(UUID playerId, long day, List<String> rejectedNames, List<String> seenNames, int qualityBonus, String preferredFaith) {
        List<NpcSpouse> values = spouseCandidates.get(playerId);
        if (values == null || values.isEmpty()) {
            values = generateCandidates(playerId, day, 10, rejectedNames, seenNames, qualityBonus, preferredFaith);
            spouseCandidates.put(playerId, values);
            spouseCandidateGeneratedDay.put(playerId, day);
            setDirty();
        }
        return List.copyOf(values);
    }

    public boolean canRefreshCandidates(UUID playerId, long day) {
        return day - spouseCandidateGeneratedDay.getOrDefault(playerId, Long.MIN_VALUE / 2) >= 7L;
    }

    public List<NpcSpouse> refreshCandidates(UUID playerId, long day) {
        return refreshCandidates(playerId, day, List.of(), List.of(), 0, "");
    }

    public List<NpcSpouse> refreshCandidates(UUID playerId, long day, List<String> rejectedNames, List<String> seenNames, int qualityBonus) {
        return refreshCandidates(playerId, day, rejectedNames, seenNames, qualityBonus, "");
    }

    public List<NpcSpouse> refreshCandidates(UUID playerId, long day, List<String> rejectedNames, List<String> seenNames, int qualityBonus, String preferredFaith) {
        if (!canRefreshCandidates(playerId, day)) return candidatesFor(playerId, day, rejectedNames, seenNames, qualityBonus, preferredFaith);
        List<NpcSpouse> values = generateCandidates(playerId, day, 10, rejectedNames, seenNames, qualityBonus, preferredFaith);
        spouseCandidates.put(playerId, values);
        spouseCandidateGeneratedDay.put(playerId, day);
        setDirty();
        return List.copyOf(values);
    }

    public List<NpcSpouse> forceRefreshCandidates(UUID playerId, long day) {
        return forceRefreshCandidates(playerId, day, List.of(), List.of(), 0, "");
    }

    public List<NpcSpouse> forceRefreshCandidates(UUID playerId, long day, List<String> rejectedNames, List<String> seenNames, int qualityBonus) {
        return forceRefreshCandidates(playerId, day, rejectedNames, seenNames, qualityBonus, "");
    }

    public List<NpcSpouse> forceRefreshCandidates(UUID playerId, long day, List<String> rejectedNames, List<String> seenNames, int qualityBonus, String preferredFaith) {
        List<NpcSpouse> values = generateCandidates(playerId, day, 10, rejectedNames, seenNames, qualityBonus, preferredFaith);
        spouseCandidates.put(playerId, values);
        spouseCandidateGeneratedDay.put(playerId, day);
        setDirty();
        return List.copyOf(values);
    }

    public long nextCandidateRefreshDay(UUID playerId) {
        return spouseCandidateGeneratedDay.getOrDefault(playerId, 0L) + 7L;
    }

    public NpcSpouse getCandidate(UUID playerId, String candidateId) {
        if (candidateId == null) return null;
        return spouseCandidates.getOrDefault(playerId, List.of()).stream()
                .filter(candidate -> candidate.id().equals(candidateId))
                .findFirst().orElse(null);
    }

    public boolean rejectCandidate(UUID playerId, String candidateId) {
        List<NpcSpouse> values = spouseCandidates.get(playerId);
        if (values == null || candidateId == null) return false;
        boolean changed = values.removeIf(value -> candidateId.equals(value.id()));
        if (changed) setDirty();
        return changed;
    }

    public FactionProfile factionProfile(String factionId) {
        String id = factionId == null ? "" : factionId;
        return factionProfiles.computeIfAbsent(id, FactionProfile::new);
    }

    public int factionRankTier(String factionId, UUID playerId, boolean factionLeader) {
        if (factionLeader) return 4;
        return factionProfile(factionId).memberRanks.getOrDefault(playerId, 0);
    }

    public String factionRankTitle(String factionId, UUID playerId, boolean factionLeader) {
        FactionProfile profile = factionProfile(factionId);
        int tier = factionRankTier(factionId, playerId, factionLeader);
        return profile.rankTitles.getOrDefault(tier, CampaignDefinitions.defaultRankTitle(tier));
    }

    public void setFactionMemberRank(String factionId, UUID playerId, int tier) {
        if (factionId == null || factionId.isBlank() || playerId == null) return;
        factionProfile(factionId).memberRanks.put(playerId, clamp(tier, 0, 4));
        setDirty();
    }

    public void clearFactionMemberRank(String factionId, UUID playerId) {
        if (factionId == null || factionId.isBlank() || playerId == null) return;
        FactionProfile profile = factionProfiles.get(factionId);
        if (profile != null && profile.memberRanks.remove(playerId) != null) setDirty();
    }

    public void setFactionRankTitle(String factionId, int tier, String title) {
        if (factionId == null || factionId.isBlank()) return;
        factionProfile(factionId).rankTitles.put(clamp(tier, 0, 4), SettlementData.cleanText(title, CampaignDefinitions.defaultRankTitle(tier), 24));
        setDirty();
    }

    public void addNews(NewsCategory category, NewsImportance importance, String headline, String body,
                        long day, String factionId, String settlementId, NewsVisibility visibility) {
        worldNews.add(0, new WorldNewsEntry(UUID.randomUUID(), category, importance,
                SettlementData.cleanText(headline, "World event", 80),
                SettlementData.cleanText(body, "", 240), day,
                factionId == null ? "" : factionId,
                SettlementData.normalizeId(settlementId), visibility));
        trimWorldNews();
        setDirty();
    }

    public List<WorldNewsEntry> newsFor(String viewerFaction, boolean administrator) {
        return worldNews.stream()
                .filter(entry -> administrator || entry.visibility == NewsVisibility.PUBLIC
                        || (entry.visibility == NewsVisibility.FACTION && !viewerFaction.isBlank() && viewerFaction.equals(entry.factionId)))
                .sorted(Comparator.comparingLong(WorldNewsEntry::day).reversed())
                .toList();
    }

    private void trimWorldNews() {
        while (worldNews.size() > 1000) worldNews.remove(worldNews.size() - 1);
    }

    private static List<NpcSpouse> generateCandidates(UUID playerId, long day, int count,
                                                       List<String> rejectedNames, List<String> seenNames, int qualityBonus, String preferredFaith) {
        String[] cultures = {"Baltic", "Finnic", "Norse", "Germanic", "Slavic", "Roman"};
        String[][] firstNames = {
                {"Aino", "Linda", "Maia", "Leelo", "Ilmar", "Jaan", "Lembit", "Marek"},
                {"Kyllikki", "Aila", "Tuuli", "Vaino", "Tapio", "Mikko", "Kauko", "Sampo"},
                {"Astrid", "Sigrid", "Freydis", "Ingrid", "Ragnar", "Leif", "Eirik", "Harald"},
                {"Adelheid", "Helena", "Gertrud", "Mathilde", "Konrad", "Otto", "Heinrich", "Bruno"},
                {"Miroslava", "Milena", "Zora", "Danica", "Bogdan", "Milos", "Radomir", "Vladan"},
                {"Julia", "Aurelia", "Claudia", "Marcella", "Marcus", "Lucius", "Gaius", "Titus"}
        };
        String[][] lastNames = {
                {"Tamm", "Varik", "Kask", "Saar", "Lepp", "Raud", "Vaher", "Mets"},
                {"Korhonen", "Virtanen", "Maki", "Laine", "Niemi", "Kivi", "Aho", "Jarvi"},
                {"Eiriksson", "Haraldsdottir", "Ulfsson", "Ragnarsdottir", "Bjornsson", "Sigurdsdottir"},
                {"Falk", "Stein", "Wulf", "Hartmann", "Lind", "Bauer", "Keller", "Brandt"},
                {"Dragovic", "Mirovic", "Radic", "Kovac", "Veselin", "Dabrowski", "Novak", "Vukic"},
                {"Valerius", "Cassius", "Flavius", "Aemilius", "Aurelius", "Claudius", "Cornelius", "Julius"}
        };
        String[][] religions = {
                {"Baltic Paganism", "Finnic Paganism", "Catholic Christianity"},
                {"Finnic Paganism", "Orthodox Christianity", "Catholic Christianity"},
                {"Norse Paganism", "Catholic Christianity"},
                {"Catholic Christianity", "Orthodox Christianity"},
                {"Slavic Paganism", "Orthodox Christianity", "Catholic Christianity"},
                {"Roman Paganism", "Catholic Christianity", "Orthodox Christianity"}
        };
        List<String> traitPool = CampaignDefinitions.playableTraits().stream().map(CampaignDefinitions.TraitDefinition::id).toList();
        long seed = day * 341873128712L;
        if (playerId != null) seed ^= playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
        java.util.Random random = new java.util.Random(seed);
        List<NpcSpouse> result = new ArrayList<>();
        List<String> generatedNames = new ArrayList<>();
        int qualityRolls = 1 + Math.max(0, Math.min(2, qualityBonus));
        for (int i = 0; i < count; i++) {
            String gender = i < count / 2 ? "Female" : "Male";
            int culture = 0;
            String first = "";
            String last = "";
            String fullName = "";
            boolean acceptedName = false;
            // Rejected people should stay gone. Previously-seen names have a 75% reroll chance, so they
            // remain possible without dominating every weekly candidate refresh.
            for (int attempt = 0; attempt < 64; attempt++) {
                boolean romanPreferred = preferredFaith != null && preferredFaith.equalsIgnoreCase("Roman Paganism");
                culture = romanPreferred && random.nextDouble() < 0.70D ? 5 : random.nextInt(cultures.length);
                int firstIndex = "Female".equals(gender) ? random.nextInt(4) : 4 + random.nextInt(Math.max(1, firstNames[culture].length - 4));
                first = firstNames[culture][firstIndex];
                last = lastNames[culture][random.nextInt(lastNames[culture].length)];
                if (culture == 5 && "Female".equals(gender) && last.endsWith("us")) last = last.substring(0, last.length() - 2) + "a";
                fullName = first + " " + last;
                if (containsIgnoreCase(rejectedNames, fullName) || containsIgnoreCase(generatedNames, fullName)) continue;
                if (containsIgnoreCase(seenNames, fullName) && random.nextDouble() < 0.75D) continue;
                acceptedName = true;
                break;
            }
            if (!acceptedName) {
                // The finite default name pool can theoretically be exhausted after many years of rejections.
                // Keep the candidate generic while guaranteeing that a deliberately rejected person never returns.
                int suffix = 2;
                String base = first + " " + last;
                fullName = base;
                while (containsIgnoreCase(rejectedNames, fullName) || containsIgnoreCase(generatedNames, fullName))
                    fullName = base + " " + suffix++;
            }
            generatedNames.add(fullName);
            String faith = religions[culture][random.nextInt(religions[culture].length)];

            List<String> traits = List.of();
            int bestScore = Integer.MIN_VALUE;
            for (int roll = 0; roll < qualityRolls; roll++) {
                List<String> shuffled = new ArrayList<>(traitPool);
                java.util.Collections.shuffle(shuffled, random);
                List<String> attempt = List.copyOf(shuffled.subList(0, Math.min(3, shuffled.size())));
                int score = spouseTraitScore(attempt);
                if (score > bestScore) { bestScore = score; traits = attempt; }
            }
            int stability = CampaignDefinitions.totalEffect(traits, CampaignDefinitions.EffectKind.STABILITY);
            int wealth = CampaignDefinitions.totalEffect(traits, CampaignDefinitions.EffectKind.WEALTH);
            int morale = CampaignDefinitions.totalEffect(traits, CampaignDefinitions.EffectKind.MORALE);
            String id = "generated_" + Long.toUnsignedString(random.nextLong(), 36);
            String effect = describeSpouseEffects(stability, wealth, morale);
            result.add(new NpcSpouse(id, fullName, gender, faith, cultures[culture], traits, effect, stability, wealth, morale));
        }
        return result;
    }

    private static int spouseTraitScore(List<String> traits) {
        int score = 0;
        for (String id : traits) {
            CampaignDefinitions.TraitDefinition definition = CampaignDefinitions.trait(id);
            if (definition == null) continue;
            score += switch (definition.tone()) {
                case POSITIVE -> 2;
                case MIXED -> 1;
                case NEUTRAL -> 0;
                case NEGATIVE -> -2;
            };
        }
        return score;
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        if (values == null || wanted == null) return false;
        return values.stream().anyMatch(value -> value != null && value.equalsIgnoreCase(wanted));
    }

    private static String describeSpouseEffects(int stability, int wealth, int morale) {
        List<String> parts = new ArrayList<>();
        if (stability != 0) parts.add((stability > 0 ? "+" : "") + stability + " daily stability");
        if (wealth != 0) parts.add((wealth > 0 ? "+" : "") + wealth + " daily wealth");
        if (morale != 0) parts.add((morale > 0 ? "+" : "") + morale + " recruit morale");
        return parts.isEmpty() ? "No direct settlement modifier" : String.join(", ", parts);
    }

    public enum UnitType {
        RECRUIT("Recruit"),
        SHIELDMAN("Shieldman"),
        BOWMAN("Bowman"),
        CROSSBOWMAN("Crossbowman"),
        HORSEMAN("Horseman"),
        NOMAD("Nomad"),
        LEVY("Levy");

        private final String displayName;

        UnitType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public static UnitType parse(String raw) {
            if (raw == null) return null;
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if ("RECRUIT_SHIELDMAN".equals(normalized) || "SHIELD".equals(normalized)) normalized = "SHIELDMAN";
            if ("CROSSBOW".equals(normalized)) normalized = "CROSSBOWMAN";
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public enum BannerMode {
        FACTION,
        CUSTOM,
        NONE
    }

    public enum TreatyState {
        NEUTRAL("Peace"),
        NON_AGGRESSION("Non-aggression pact"),
        DEFENSIVE_ALLIANCE("Defensive alliance"),
        MILITARY_ALLIANCE("Military alliance"),
        WAR("War");

        private final String displayName;

        TreatyState(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isAlliance() {
            return this == DEFENSIVE_ALLIANCE || this == MILITARY_ALLIANCE;
        }
    }

    public enum ProposalType {
        NON_AGGRESSION,
        DEFENSIVE_ALLIANCE,
        MILITARY_ALLIANCE,
        PEACE,
        CALL_TO_WAR
    }

    public enum ProposalStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELLED
    }

    public enum WarType {
        RAID("Raid"),
        SKIRMISH("Skirmish"),
        SETTLEMENT_WAR("Settlement War"),
        FACTION_WAR("Faction War"),
        INDEPENDENCE_WAR("Independence War");

        private final String displayName;

        WarType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum WarStatus {
        ACTIVE,
        ENDED
    }

    public enum MarriageKind {
        NPC,
        PLAYER
    }

    public enum WarGoal {
        SEIZE_WEALTH("Seize Wealth", WarType.RAID),
        STEAL_SUPPLIES("Steal Supplies", WarType.RAID),
        PUNITIVE_RAID("Punitive Raid", WarType.RAID),
        BORDER_CLASH("Border Clash", WarType.SKIRMISH),
        FORCE_WITHDRAWAL("Force Withdrawal", WarType.SKIRMISH),
        CAPTURE_SETTLEMENT("Capture Settlement", WarType.SETTLEMENT_WAR),
        FORCE_TRIBUTE("Force Tribute", WarType.SETTLEMENT_WAR),
        LIBERATE_SETTLEMENT("Liberate Settlement", WarType.SETTLEMENT_WAR),
        CONQUER_TERRITORY("Conquer Territory", WarType.FACTION_WAR),
        RECOVER_TERRITORY("Recover Lost Territory", WarType.FACTION_WAR),
        FORCE_VASSALAGE("Force Vassalage", WarType.FACTION_WAR),
        PUNITIVE_WAR("Punitive War", WarType.FACTION_WAR),
        HOLY_WAR("Holy War", WarType.FACTION_WAR),
        CUSTOM("Custom Casus Belli", null),
        GAIN_INDEPENDENCE("Gain Independence", WarType.INDEPENDENCE_WAR),
        SUPPRESS_REBELLION("Suppress Rebellion", WarType.INDEPENDENCE_WAR);

        private final String displayName;
        private final WarType type;

        WarGoal(String displayName, WarType type) {
            this.displayName = displayName;
            this.type = type;
        }

        public String displayName() { return displayName; }
        public WarType type() { return type; }
        public boolean appliesTo(WarType warType) { return this == CUSTOM || type == warType; }

        public static WarGoal defaultFor(WarType type) {
            for (WarGoal goal : values()) if (goal.type == type) return goal;
            return CONQUER_TERRITORY;
        }
    }

    public enum NewsCategory {
        WARS, DIPLOMACY, SETTLEMENTS, FACTIONS, CHARACTERS, ECONOMY, RELIGION, MILITARY
    }

    public enum NewsImportance { MAJOR, NORMAL, MINOR }
    public enum NewsVisibility { PUBLIC, FACTION, ADMIN }

    public static final class SettlementVisualRecord {
        private final String settlementId;
        private BannerMode bannerMode = BannerMode.FACTION;
        private CompoundTag customBanner = new CompoundTag();
        private final Map<UnitType, CompoundTag> unitOverrides = new EnumMap<>(UnitType.class);

        private SettlementVisualRecord(String settlementId) {
            this.settlementId = SettlementData.normalizeId(settlementId);
        }

        private static SettlementVisualRecord fromNBT(CompoundTag tag) {
            SettlementVisualRecord record = new SettlementVisualRecord(tag.getString("Settlement"));
            try {
                record.bannerMode = BannerMode.valueOf(tag.getString("BannerMode"));
            } catch (IllegalArgumentException ignored) {
                record.bannerMode = BannerMode.FACTION;
            }
            if (tag.contains("CustomBanner", Tag.TAG_COMPOUND)) record.customBanner = tag.getCompound("CustomBanner").copy();
            for (Tag raw : tag.getList("UnitOverrides", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                UnitType type = UnitType.parse(entry.getString("Type"));
                if (type != null && entry.contains("Stack", Tag.TAG_COMPOUND)) {
                    record.unitOverrides.put(type, entry.getCompound("Stack").copy());
                }
            }
            return record;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Settlement", settlementId);
            tag.putString("BannerMode", bannerMode.name());
            tag.put("CustomBanner", customBanner.copy());
            ListTag overrides = new ListTag();
            unitOverrides.forEach((type, stack) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("Type", type.name());
                entry.put("Stack", stack.copy());
                overrides.add(entry);
            });
            tag.put("UnitOverrides", overrides);
            return tag;
        }

        public BannerMode getBannerMode() {
            return bannerMode;
        }

        public void setBannerMode(BannerMode mode) {
            bannerMode = Objects.requireNonNull(mode);
        }

        public ItemStack getCustomBanner() {
            return customBanner.isEmpty() ? ItemStack.EMPTY : ItemStack.of(customBanner.copy());
        }

        public void setCustomBanner(ItemStack stack) {
            CompoundTag tag = new CompoundTag();
            ItemStack copy = stack.copy();
            copy.setCount(1);
            copy.save(tag);
            customBanner = tag;
            bannerMode = BannerMode.CUSTOM;
        }

        public void clearCustomBanner() {
            customBanner = new CompoundTag();
            bannerMode = BannerMode.NONE;
        }

        public ItemStack getUnitTemplate(UnitType type) {
            CompoundTag tag = unitOverrides.get(type);
            return tag == null || tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag.copy());
        }

        public void setUnitTemplate(UnitType type, ItemStack stack) {
            CompoundTag tag = new CompoundTag();
            ItemStack copy = stack.copy();
            copy.setCount(1);
            copy.save(tag);
            unitOverrides.put(type, tag);
        }

        public void clearUnitTemplate(UnitType type) {
            unitOverrides.remove(type);
        }
    }

    public static final class RosterRecord {
        private final String id;
        private String name;
        private final Map<UnitType, CompoundTag> templates = new EnumMap<>(UnitType.class);

        private RosterRecord(String id, String name) {
            this.id = SettlementData.normalizeId(id);
            this.name = SettlementData.cleanText(name, id, 48);
        }

        private static RosterRecord fromNBT(CompoundTag tag) {
            RosterRecord record = new RosterRecord(tag.getString("Id"), tag.getString("Name"));
            for (Tag raw : tag.getList("Templates", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                UnitType type = UnitType.parse(entry.getString("Type"));
                if (type != null && entry.contains("Stack", Tag.TAG_COMPOUND)) {
                    record.templates.put(type, entry.getCompound("Stack").copy());
                }
            }
            return record;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Name", name);
            ListTag list = new ListTag();
            templates.forEach((type, stack) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("Type", type.name());
                entry.put("Stack", stack.copy());
                list.add(entry);
            });
            tag.put("Templates", list);
            return tag;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setTemplate(UnitType type, ItemStack stack) {
            CompoundTag tag = new CompoundTag();
            ItemStack copy = stack.copy();
            copy.setCount(1);
            copy.save(tag);
            templates.put(type, tag);
        }

        public void clearTemplate(UnitType type) {
            templates.remove(type);
        }

        public ItemStack getTemplate(UnitType type) {
            CompoundTag tag = templates.get(type);
            return tag == null || tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag.copy());
        }

        public List<UnitType> configuredTypes() {
            return templates.keySet().stream().sorted(Comparator.comparingInt(UnitType::ordinal)).toList();
        }
    }

    public static final class DiplomacyRecord {
        private final String a;
        private final String b;
        private int relationship;
        private TreatyState state = TreatyState.NEUTRAL;
        private long lastChangedDay;

        private DiplomacyRecord(String a, String b) {
            FactionPair pair = FactionPair.of(a, b);
            this.a = pair.first;
            this.b = pair.second;
        }

        private static DiplomacyRecord fromNBT(CompoundTag tag) {
            DiplomacyRecord record = new DiplomacyRecord(tag.getString("A"), tag.getString("B"));
            record.relationship = clamp(tag.getInt("Relationship"), -100, 100);
            try {
                record.state = TreatyState.valueOf(tag.getString("State"));
            } catch (IllegalArgumentException ignored) {
                record.state = TreatyState.NEUTRAL;
            }
            record.lastChangedDay = tag.getLong("LastChangedDay");
            return record;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("A", a);
            tag.putString("B", b);
            tag.putInt("Relationship", relationship);
            tag.putString("State", state.name());
            tag.putLong("LastChangedDay", lastChangedDay);
            return tag;
        }

        public String firstFaction() { return a; }
        public String secondFaction() { return b; }
        public int relationship() { return relationship; }
        public TreatyState state() { return state; }
        public long lastChangedDay() { return lastChangedDay; }

        public void setState(TreatyState value, long day) {
            state = value;
            lastChangedDay = day;
        }

        public void changeRelationship(int delta) {
            relationship = clamp(relationship + delta, -100, 100);
        }

        public boolean passiveImprove(int cap, long day) {
            if (lastChangedDay >= day || relationship >= cap) return false;
            relationship = clamp(relationship + 1, -100, Math.min(100, cap));
            lastChangedDay = day;
            return true;
        }
    }

    public static final class DiplomacyProposal {
        private final UUID id;
        private final ProposalType type;
        private final String fromFaction;
        private final String toFaction;
        private final UUID actor;
        private final UUID linkedWar;
        private final long createdDay;
        private ProposalStatus status = ProposalStatus.PENDING;

        private DiplomacyProposal(UUID id, ProposalType type, String fromFaction, String toFaction, UUID actor, UUID linkedWar, long createdDay) {
            this.id = id;
            this.type = type;
            this.fromFaction = fromFaction;
            this.toFaction = toFaction;
            this.actor = actor;
            this.linkedWar = linkedWar;
            this.createdDay = createdDay;
        }

        private static DiplomacyProposal fromNBT(CompoundTag tag) {
            UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
            ProposalType type;
            ProposalStatus status;
            try { type = ProposalType.valueOf(tag.getString("Type")); } catch (IllegalArgumentException ignored) { type = ProposalType.NON_AGGRESSION; }
            try { status = ProposalStatus.valueOf(tag.getString("Status")); } catch (IllegalArgumentException ignored) { status = ProposalStatus.PENDING; }
            DiplomacyProposal proposal = new DiplomacyProposal(
                    id,
                    type,
                    tag.getString("From"),
                    tag.getString("To"),
                    tag.hasUUID("Actor") ? tag.getUUID("Actor") : null,
                    tag.hasUUID("War") ? tag.getUUID("War") : null,
                    tag.getLong("CreatedDay")
            );
            proposal.status = status;
            return proposal;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Type", type.name());
            tag.putString("From", fromFaction);
            tag.putString("To", toFaction);
            if (actor != null) tag.putUUID("Actor", actor);
            if (linkedWar != null) tag.putUUID("War", linkedWar);
            tag.putLong("CreatedDay", createdDay);
            tag.putString("Status", status.name());
            return tag;
        }

        public UUID id() { return id; }
        public ProposalType type() { return type; }
        public String fromFaction() { return fromFaction; }
        public String toFaction() { return toFaction; }
        public UUID actor() { return actor; }
        public UUID linkedWar() { return linkedWar; }
        public long createdDay() { return createdDay; }
        public ProposalStatus status() { return status; }
    }

    public static final class WarRecord {
        private final UUID id;
        private final WarType type;
        private final WarGoal goal;
        private final String attackerFaction;
        private final String defenderFaction;
        private final String targetSettlement;
        private String attackerSettlement = "";
        private String customCasusBelli = "";
        private boolean attackerFactionJoined;
        private boolean defenderFactionJoined;
        private final UUID declaredBy;
        private final long startedDay;
        private WarStatus status = WarStatus.ACTIVE;
        private long endedDay;
        private String peaceTerms = "";
        private final List<String> supportingAttackers = new ArrayList<>();
        private final List<String> supportingDefenders = new ArrayList<>();

        private WarRecord(UUID id, WarType type, WarGoal goal, String attackerFaction, String defenderFaction, String targetSettlement, UUID declaredBy, long startedDay) {
            this.id = id;
            this.type = type;
            this.goal = goal;
            this.attackerFaction = attackerFaction;
            this.defenderFaction = defenderFaction;
            this.targetSettlement = targetSettlement;
            this.attackerFactionJoined = type != WarType.SETTLEMENT_WAR;
            this.defenderFactionJoined = type != WarType.SETTLEMENT_WAR;
            this.declaredBy = declaredBy;
            this.startedDay = startedDay;
        }

        private static WarRecord fromNBT(CompoundTag tag) {
            WarType type;
            WarGoal goal;
            WarStatus status;
            try { type = WarType.valueOf(tag.getString("Type")); } catch (IllegalArgumentException ignored) { type = WarType.FACTION_WAR; }
            try { goal = WarGoal.valueOf(tag.getString("Goal")); } catch (IllegalArgumentException ignored) { goal = WarGoal.defaultFor(type); }
            if (!goal.appliesTo(type)) goal = WarGoal.defaultFor(type);
            try { status = WarStatus.valueOf(tag.getString("Status")); } catch (IllegalArgumentException ignored) { status = WarStatus.ACTIVE; }
            WarRecord war = new WarRecord(
                    tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
                    type,
                    goal,
                    tag.getString("Attacker"),
                    tag.getString("Defender"),
                    SettlementData.normalizeId(tag.getString("TargetSettlement")),
                    tag.hasUUID("DeclaredBy") ? tag.getUUID("DeclaredBy") : null,
                    tag.getLong("StartedDay")
            );
            war.attackerSettlement = SettlementData.normalizeId(tag.getString("AttackerSettlement"));
            war.customCasusBelli = SettlementData.cleanText(tag.getString("CustomCasusBelli"), "", 100);
            war.attackerFactionJoined = tag.contains("AttackerFactionJoined", Tag.TAG_BYTE) ? tag.getBoolean("AttackerFactionJoined") : type != WarType.SETTLEMENT_WAR;
            war.defenderFactionJoined = tag.contains("DefenderFactionJoined", Tag.TAG_BYTE) ? tag.getBoolean("DefenderFactionJoined") : type != WarType.SETTLEMENT_WAR;
            war.status = status;
            war.endedDay = tag.getLong("EndedDay");
            war.peaceTerms = tag.getString("PeaceTerms");
            for (Tag raw : tag.getList("SupportingAttackers", Tag.TAG_STRING)) war.supportingAttackers.add(raw.getAsString());
            for (Tag raw : tag.getList("SupportingDefenders", Tag.TAG_STRING)) war.supportingDefenders.add(raw.getAsString());
            return war;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Type", type.name());
            tag.putString("Goal", goal.name());
            tag.putString("Attacker", attackerFaction);
            tag.putString("Defender", defenderFaction);
            tag.putString("TargetSettlement", targetSettlement);
            tag.putString("AttackerSettlement", attackerSettlement);
            tag.putString("CustomCasusBelli", customCasusBelli);
            tag.putBoolean("AttackerFactionJoined", attackerFactionJoined);
            tag.putBoolean("DefenderFactionJoined", defenderFactionJoined);
            if (declaredBy != null) tag.putUUID("DeclaredBy", declaredBy);
            tag.putLong("StartedDay", startedDay);
            tag.putString("Status", status.name());
            tag.putLong("EndedDay", endedDay);
            tag.putString("PeaceTerms", peaceTerms);
            ListTag attackers = new ListTag(); supportingAttackers.forEach(value -> attackers.add(net.minecraft.nbt.StringTag.valueOf(value))); tag.put("SupportingAttackers", attackers);
            ListTag defenders = new ListTag(); supportingDefenders.forEach(value -> defenders.add(net.minecraft.nbt.StringTag.valueOf(value))); tag.put("SupportingDefenders", defenders);
            return tag;
        }

        public UUID id() { return id; }
        public WarType type() { return type; }
        public WarGoal goal() { return goal; }
        public String attackerFaction() { return attackerFaction; }
        public String defenderFaction() { return defenderFaction; }
        public String targetSettlement() { return targetSettlement; }
        public String attackerSettlement() { return attackerSettlement; }
        public String defenderSettlement() { return targetSettlement; }
        public String customCasusBelli() { return customCasusBelli; }
        public String casusBelliName() { return customCasusBelli.isBlank() ? goal.displayName() : customCasusBelli; }
        public boolean attackerFactionJoined() { return attackerFactionJoined; }
        public boolean defenderFactionJoined() { return defenderFactionJoined; }
        public String escalationName() {
            if (type != WarType.SETTLEMENT_WAR) return type.displayName();
            if (attackerFactionJoined && defenderFactionJoined) return "Faction vs Faction";
            if (attackerFactionJoined) return "Faction vs Settlement";
            if (defenderFactionJoined) return "Settlement vs Faction";
            return "Settlement vs Settlement";
        }
        public void joinParentFaction(boolean attackerSide) { if (attackerSide) attackerFactionJoined = true; else defenderFactionJoined = true; }
        public UUID declaredBy() { return declaredBy; }
        public long startedDay() { return startedDay; }
        public WarStatus status() { return status; }
        public long endedDay() { return endedDay; }
        public String peaceTerms() { return peaceTerms; }
        public List<String> supportingAttackers() { return List.copyOf(supportingAttackers); }
        public List<String> supportingDefenders() { return List.copyOf(supportingDefenders); }

        public boolean involves(String factionId) {
            return attackerFaction.equals(factionId)
                    || defenderFaction.equals(factionId)
                    || supportingAttackers.contains(factionId)
                    || supportingDefenders.contains(factionId);
        }

        public void addSupporter(String factionId, boolean attackerSide) {
            List<String> side = attackerSide ? supportingAttackers : supportingDefenders;
            if (!side.contains(factionId)) side.add(factionId);
        }
    }

    public record NpcSpouse(
            String id,
            String name,
            String gender,
            String faith,
            String culture,
            List<String> traits,
            String effect,
            int stabilityBonus,
            int wealthPerDay,
            int moraleBonus
    ) {
        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Name", name);
            tag.putString("Gender", gender);
            tag.putString("Faith", faith);
            tag.putString("Culture", culture);
            ListTag traitList = new ListTag();
            traits.forEach(value -> traitList.add(net.minecraft.nbt.StringTag.valueOf(value)));
            tag.put("Traits", traitList);
            tag.putString("Effect", effect);
            tag.putInt("StabilityBonus", stabilityBonus);
            tag.putInt("WealthPerDay", wealthPerDay);
            tag.putInt("MoraleBonus", moraleBonus);
            return tag;
        }

        private static NpcSpouse fromNBT(CompoundTag tag) {
            List<String> traits = new ArrayList<>();
            for (Tag raw : tag.getList("Traits", Tag.TAG_STRING)) traits.add(raw.getAsString());
            return new NpcSpouse(
                    tag.getString("Id"),
                    tag.getString("Name"),
                    tag.getString("Gender").isBlank() ? "Unspecified" : tag.getString("Gender"),
                    tag.getString("Faith"),
                    tag.getString("Culture"),
                    List.copyOf(traits),
                    tag.getString("Effect"),
                    tag.getInt("StabilityBonus"),
                    tag.getInt("WealthPerDay"),
                    tag.getInt("MoraleBonus")
            );
        }

        public String traitsText() {
            return traits.stream().map(value -> {
                CampaignDefinitions.TraitDefinition definition = CampaignDefinitions.trait(value);
                return definition == null ? value : definition.name();
            }).reduce((a, b) -> a + ", " + b).orElse("None");
        }
    }

    public static final class MarriageRecord {
        private final UUID playerId;
        private MarriageKind kind;
        private UUID spousePlayerId;
        private String spouseId = "";
        private String spouseName = "";
        private String spouseFaith = "";
        private String spouseCulture = "";
        private final List<String> spouseTraits = new ArrayList<>();
        private String spouseEffect = "";
        private int stabilityBonus;
        private int wealthPerDay;
        private int moraleBonus;
        private long marriedDay;

        private MarriageRecord(UUID playerId) {
            this.playerId = playerId;
        }

        private static MarriageRecord forNpc(UUID playerId, NpcSpouse spouse, long day) {
            MarriageRecord record = new MarriageRecord(playerId);
            record.kind = MarriageKind.NPC;
            record.spouseId = spouse.id;
            record.spouseName = spouse.name;
            record.spouseFaith = spouse.faith;
            record.spouseCulture = spouse.culture;
            record.spouseTraits.addAll(spouse.traits);
            record.spouseEffect = spouse.effect;
            record.stabilityBonus = spouse.stabilityBonus;
            record.wealthPerDay = spouse.wealthPerDay;
            record.moraleBonus = spouse.moraleBonus;
            record.marriedDay = Math.max(0L, day);
            return record;
        }

        private static MarriageRecord forPlayer(UUID playerId, UUID spousePlayerId, long day) {
            MarriageRecord record = new MarriageRecord(playerId);
            record.kind = MarriageKind.PLAYER;
            record.spousePlayerId = spousePlayerId;
            record.marriedDay = Math.max(0L, day);
            return record;
        }

        private static MarriageRecord fromNBT(CompoundTag tag) {
            MarriageRecord record = new MarriageRecord(tag.hasUUID("Player") ? tag.getUUID("Player") : null);
            try { record.kind = MarriageKind.valueOf(tag.getString("Kind")); } catch (IllegalArgumentException ignored) { record.kind = MarriageKind.NPC; }
            if (tag.hasUUID("SpousePlayer")) record.spousePlayerId = tag.getUUID("SpousePlayer");
            record.spouseId = tag.getString("SpouseId");
            record.spouseName = tag.getString("SpouseName");
            record.spouseFaith = tag.getString("SpouseFaith");
            record.spouseCulture = tag.getString("SpouseCulture");
            for (Tag raw : tag.getList("SpouseTraitList", Tag.TAG_STRING)) record.spouseTraits.add(raw.getAsString());
            if (record.spouseTraits.isEmpty() && tag.contains("SpouseTraits", Tag.TAG_STRING)) {
                for (String value : tag.getString("SpouseTraits").split(",")) {
                    String id = CampaignDefinitions.normalize(value);
                    if (!id.isBlank()) record.spouseTraits.add(id);
                }
            }
            record.spouseEffect = tag.getString("SpouseEffect");
            record.stabilityBonus = tag.getInt("StabilityBonus");
            record.wealthPerDay = tag.getInt("WealthPerDay");
            record.moraleBonus = tag.getInt("MoraleBonus");
            record.marriedDay = tag.getLong("MarriedDay");
            return record;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            if (playerId != null) tag.putUUID("Player", playerId);
            tag.putString("Kind", kind.name());
            if (spousePlayerId != null) tag.putUUID("SpousePlayer", spousePlayerId);
            tag.putString("SpouseId", spouseId);
            tag.putString("SpouseName", spouseName);
            tag.putString("SpouseFaith", spouseFaith);
            tag.putString("SpouseCulture", spouseCulture);
            ListTag traits = new ListTag();
            spouseTraits.forEach(value -> traits.add(net.minecraft.nbt.StringTag.valueOf(value)));
            tag.put("SpouseTraitList", traits);
            tag.putString("SpouseEffect", spouseEffect);
            tag.putInt("StabilityBonus", stabilityBonus);
            tag.putInt("WealthPerDay", wealthPerDay);
            tag.putInt("MoraleBonus", moraleBonus);
            tag.putLong("MarriedDay", marriedDay);
            return tag;
        }

        public UUID playerId() { return playerId; }
        public MarriageKind kind() { return kind; }
        public UUID spousePlayerId() { return spousePlayerId; }
        public String spouseId() { return spouseId; }
        public String spouseName() { return spouseName; }
        public String spouseFaith() { return spouseFaith; }
        public String spouseCulture() { return spouseCulture; }
        public List<String> spouseTraitIds() { return List.copyOf(spouseTraits); }
        public String spouseTraits() {
            return spouseTraits.stream().map(value -> {
                CampaignDefinitions.TraitDefinition definition = CampaignDefinitions.trait(value);
                return definition == null ? value : definition.name();
            }).reduce((a, b) -> a + ", " + b).orElse("None");
        }
        public String spouseEffect() { return spouseEffect; }
        public int stabilityBonus() { return stabilityBonus; }
        public int wealthPerDay() { return wealthPerDay; }
        public int moraleBonus() { return moraleBonus; }

        /**
         * Player marriages intentionally use the spouse's live character traits rather than
         * snapshotting bonuses when the marriage is created. This means later legitimate
         * character edits/admin repairs stay in sync. NPC marriages keep their generated
         * fixed bonuses.
         */
        public int effectiveStabilityBonus(SettlementData characters) {
            if (kind != MarriageKind.PLAYER || spousePlayerId == null || characters == null) return stabilityBonus;
            SettlementData.CharacterRecord spouse = characters.getCharacter(spousePlayerId);
            return spouse == null || !spouse.isCreated() ? 0
                    : CampaignDefinitions.totalEffect(spouse.getTraits(), CampaignDefinitions.EffectKind.STABILITY);
        }

        public int effectiveWealthPerDay(SettlementData characters) {
            if (kind != MarriageKind.PLAYER || spousePlayerId == null || characters == null) return wealthPerDay;
            SettlementData.CharacterRecord spouse = characters.getCharacter(spousePlayerId);
            return spouse == null || !spouse.isCreated() ? 0
                    : CampaignDefinitions.totalEffect(spouse.getTraits(), CampaignDefinitions.EffectKind.WEALTH);
        }

        public int effectiveMoraleBonus(SettlementData characters) {
            if (kind != MarriageKind.PLAYER || spousePlayerId == null || characters == null) return moraleBonus;
            SettlementData.CharacterRecord spouse = characters.getCharacter(spousePlayerId);
            return spouse == null || !spouse.isCreated() ? 0
                    : CampaignDefinitions.totalEffect(spouse.getTraits(), CampaignDefinitions.EffectKind.MORALE);
        }

        public long marriedDay() { return marriedDay; }
    }

    public static final class FactionProfile {
        private final String factionId;
        private final Map<Integer, String> rankTitles = new HashMap<>();
        private final Map<UUID, Integer> memberRanks = new HashMap<>();

        private FactionProfile(String factionId) {
            this.factionId = factionId == null ? "" : factionId;
            for (int tier = 0; tier <= 4; tier++) rankTitles.put(tier, CampaignDefinitions.defaultRankTitle(tier));
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Faction", factionId);
            ListTag titles = new ListTag();
            rankTitles.forEach((tier, title) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Tier", tier);
                entry.putString("Title", title);
                titles.add(entry);
            });
            tag.put("RankTitles", titles);
            ListTag members = new ListTag();
            memberRanks.forEach((player, tier) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", player);
                entry.putInt("Tier", tier);
                members.add(entry);
            });
            tag.put("MemberRanks", members);
            return tag;
        }

        private static FactionProfile fromNBT(CompoundTag tag) {
            FactionProfile profile = new FactionProfile(tag.getString("Faction"));
            for (Tag raw : tag.getList("RankTitles", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                int tier = clamp(entry.getInt("Tier"), 0, 4);
                profile.rankTitles.put(tier, SettlementData.cleanText(entry.getString("Title"), CampaignDefinitions.defaultRankTitle(tier), 24));
            }
            for (Tag raw : tag.getList("MemberRanks", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                if (entry.hasUUID("Player")) profile.memberRanks.put(entry.getUUID("Player"), clamp(entry.getInt("Tier"), 0, 4));
            }
            return profile;
        }

        public String factionId() { return factionId; }
        public Map<Integer, String> rankTitles() { return Map.copyOf(rankTitles); }
        public Map<UUID, Integer> memberRanks() { return Map.copyOf(memberRanks); }
    }

    public static final class WorldNewsEntry {
        private final UUID id;
        private final NewsCategory category;
        private final NewsImportance importance;
        private final String headline;
        private final String body;
        private final long day;
        private final String factionId;
        private final String settlementId;
        private final NewsVisibility visibility;

        private WorldNewsEntry(UUID id, NewsCategory category, NewsImportance importance, String headline,
                               String body, long day, String factionId, String settlementId, NewsVisibility visibility) {
            this.id = id;
            this.category = category;
            this.importance = importance;
            this.headline = headline;
            this.body = body;
            this.day = day;
            this.factionId = factionId;
            this.settlementId = settlementId;
            this.visibility = visibility;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Category", category.name());
            tag.putString("Importance", importance.name());
            tag.putString("Headline", headline);
            tag.putString("Body", body);
            tag.putLong("Day", day);
            tag.putString("Faction", factionId);
            tag.putString("Settlement", settlementId);
            tag.putString("Visibility", visibility.name());
            return tag;
        }

        private static WorldNewsEntry fromNBT(CompoundTag tag) {
            NewsCategory category;
            NewsImportance importance;
            NewsVisibility visibility;
            try { category = NewsCategory.valueOf(tag.getString("Category")); } catch (IllegalArgumentException ignored) { category = NewsCategory.SETTLEMENTS; }
            try { importance = NewsImportance.valueOf(tag.getString("Importance")); } catch (IllegalArgumentException ignored) { importance = NewsImportance.NORMAL; }
            try { visibility = NewsVisibility.valueOf(tag.getString("Visibility")); } catch (IllegalArgumentException ignored) { visibility = NewsVisibility.PUBLIC; }
            return new WorldNewsEntry(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), category, importance,
                    tag.getString("Headline"), tag.getString("Body"), tag.getLong("Day"),
                    tag.getString("Faction"), SettlementData.normalizeId(tag.getString("Settlement")), visibility);
        }

        public UUID id() { return id; }
        public NewsCategory category() { return category; }
        public NewsImportance importance() { return importance; }
        public String headline() { return headline; }
        public String body() { return body; }
        public long day() { return day; }
        public String factionId() { return factionId; }
        public String settlementId() { return settlementId; }
        public NewsVisibility visibility() { return visibility; }
    }

    private record FactionPair(String first, String second) {
        private static FactionPair of(String a, String b) {
            String first = a == null ? "" : a;
            String second = b == null ? "" : b;
            return first.compareTo(second) <= 0 ? new FactionPair(first, second) : new FactionPair(second, first);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
