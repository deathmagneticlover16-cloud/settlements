package ee.lastpagans.settlements;

import ee.lastpagans.settlements.network.PacketValidation;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** All data needed by the guided diplomacy and conflict-declaration screen. */
public record DiplomacySnapshot(
        String ownFactionId,
        String ownFactionName,
        String viewerRank,
        boolean canManage,
        boolean canDeclareWar,
        List<RelationEntry> relations,
        List<SettlementTarget> settlements,
        List<ProposalEntry> proposals,
        List<WarEntry> wars,
        String message
) {
    public static DiplomacySnapshot from(ServerPlayer player, String message) {
        String own = CampaignManager.diplomaticPolityId(player);
        String ownName = CampaignManager.diplomaticPolityName(player.serverLevel(), own);
        CampaignData campaign = CampaignData.get(player.serverLevel());
        CampaignSystemsData campaignSystems = CampaignSystemsData.get(player.serverLevel());
        SettlementData settlementStore = SettlementData.get(player.serverLevel());
        RecruitsFaction ownFaction = own.isBlank() || FactionEvents.recruitsFactionManager == null ? null
                : FactionEvents.recruitsFactionManager.getFactionByStringID(own);
        boolean leader = ownFaction != null && player.getUUID().equals(ownFaction.getTeamLeaderUUID());
        String rank = BackingFactionManager.isBackingFaction(own) ? "Independent Ruler"
                : own.isBlank() ? "Independent" : campaign.factionRankTitle(own, player.getUUID(), leader);
        boolean canManage = leader || (!own.isBlank() && campaign.factionRankTier(own, player.getUUID(), false) >= 3);

        List<RelationEntry> relations = new ArrayList<>();
        if (FactionEvents.recruitsFactionManager != null) {
            for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
                if (faction == null || faction.getStringID() == null || faction.getStringID().equals(own)) continue;
                // Protected admin polities belong in admin/recovery tools, never in ordinary diplomacy.
                // This also avoids showing operators buttons that the server will correctly reject.
                if (campaignSystems.factionProtected(faction.getStringID())) continue;
                CampaignData.DiplomacyRecord relation = own.isBlank() ? null : campaign.relationIfPresent(own, faction.getStringID());
                SettlementData.CharacterRecord ownCharacter = factionLeaderCharacter(player, ownFaction);
                SettlementData.CharacterRecord otherCharacter = factionLeaderCharacter(player, faction);
                String ownFaith = ownCharacter == null ? "" : ownCharacter.getFaith();
                String otherFaith = otherCharacter == null ? "" : otherCharacter.getFaith();
                int faithModifier = CampaignDefinitions.religionRelationModifier(ownFaith, otherFaith);
                int political = CampaignManager.effectiveRelationship(player.serverLevel(), own, faction.getStringID());
                CampaignData.VassalageRecord vassalage = campaign.vassalageBetween(own, faction.getStringID());
                String vassalId = vassalage == null ? "" : vassalage.vassal();
                int projectedGross = vassalage == null ? 0 : settlementStore.getSettlements().stream()
                        .filter(settlement -> vassalId.equals(settlement.getEffectiveRecruitsTeam()))
                        .mapToInt(settlement -> CampaignSystemsManager.projectedTaxIncome(settlement, campaignSystems)).sum();
                int projectedTribute = vassalage == null ? 0 : (int)((long)projectedGross * vassalage.tributeRate() / 100L);
                relations.add(new RelationEntry(
                        faction.getStringID(), CampaignManager.diplomaticPolityName(player.serverLevel(), faction.getStringID()), faction.getBanner() == null ? new CompoundTag() : faction.getBanner().copy(),
                        political, relation == null ? CampaignData.TreatyState.NEUTRAL.name() : relation.state().name(),
                        faithModifier, campaign.getFactionReliability(faction.getStringID()),
                        SettlementConfig.RELATION_NAP_MINIMUM.get(), SettlementConfig.RELATION_DEFENSIVE_ALLIANCE_MINIMUM.get(),
                        SettlementConfig.RELATION_MILITARY_ALLIANCE_MINIMUM.get(), SettlementConfig.RELATION_VASSALAGE_MINIMUM.get(),
                        vassalage == null ? "" : vassalage.overlord(), vassalId, vassalage == null ? 0 : vassalage.tributeRate(),
                        projectedGross, projectedTribute
                ));
            }
        }
        relations.sort(Comparator.comparing(RelationEntry::factionName, String.CASE_INSENSITIVE_ORDER));

        SettlementData settlementsData = settlementStore;
        List<SettlementTarget> settlements = settlementsData.getSettlements().stream()
                .filter(value -> !campaignSystems.settlementProtected(value.getId())
                        && !campaignSystems.factionProtected(value.getEffectiveRecruitsTeam()))
                .map(value -> new SettlementTarget(value.getId(), value.getName(), value.getEffectiveRecruitsTeam(),
                        CampaignManager.diplomaticPolityName(player.serverLevel(), value.getEffectiveRecruitsTeam()), value.getRank().displayName(), value.isCapital(),
                        CampaignManager.canRepresentSettlement(player, value), value.getClaimLinks().stream()
                        .limit(64).map(claim -> new ClaimTarget(claim.id(), claim.name())).toList()))
                .sorted(Comparator.comparing(SettlementTarget::factionName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SettlementTarget::settlementName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<ProposalEntry> proposals = new ArrayList<>();
        if (!own.isBlank()) proposals.addAll(campaign.proposalsFor(own).stream()
                .map(value -> new ProposalEntry(value.id(), value.type().name(), value.fromFaction(),
                        CampaignManager.diplomaticPolityName(player.serverLevel(), value.fromFaction()), value.linkedWar(), "", true, false))
                .toList());
        for (CampaignSystemsData.PeaceOfferRecord offer : campaignSystems.peaceOffers()) {
            CampaignData.WarRecord war = offer.warId() == null ? null : campaign.getWar(offer.warId());
            if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) continue;
            boolean targetFaction = canManage && !own.isBlank() && own.equals(offer.targetFaction());
            boolean targetSettlement = offer.targetFaction().isBlank() && !offer.targetSettlement().isBlank()
                    && settlementsData.getSettlement(offer.targetSettlement()) != null
                    && CampaignManager.canRepresentSettlement(player, settlementsData.getSettlement(offer.targetSettlement()));
            if (!targetFaction && !targetSettlement) continue;
            String fromName = !offer.proposerFaction().isBlank() ? CampaignManager.diplomaticPolityName(player.serverLevel(), offer.proposerFaction())
                    : (settlementsData.getSettlement(offer.proposerSettlement()) == null ? "Independent settlement" : settlementsData.getSettlement(offer.proposerSettlement()).getName());
            int score = campaignSystems.warScore(war.id());
            boolean proposerAttacker = CampaignManager.peacePartyIsSide(war, offer.proposerFaction(), offer.proposerSettlement(), true);
            boolean proposerDefender = CampaignManager.peacePartyIsSide(war, offer.proposerFaction(), offer.proposerSettlement(), false);
            if (proposerAttacker == proposerDefender) continue;
            int enforcementScore = proposerAttacker ? score : -score;
            int treatyCost = CampaignManager.peaceTermsWarScoreCost(player.serverLevel(), offer.terms());
            boolean targetCapitulated = enforcementScore >= 90 && treatyCost <= enforcementScore;
            String summary = offer.terms().isEmpty() ? "White peace" : offer.terms().stream().map(term -> prettyTerm(term)).reduce((a,b) -> a + "; " + b).orElse("White peace");
            summary += " · Cost " + treatyCost + " WS";
            proposals.add(new ProposalEntry(offer.proposalId(), "PEACE_TERMS", offer.proposerFaction(), fromName, offer.warId(), summary, !targetCapitulated, true));
        }
        java.util.Set<String> managedSettlementIds = settlements.stream().filter(SettlementTarget::manageable)
                .map(SettlementTarget::settlementId).collect(java.util.stream.Collectors.toSet());
        List<CampaignData.WarRecord> visibleWars = campaign.getWars().stream()
                .filter(value -> value.status() == CampaignData.WarStatus.ACTIVE)
                .filter(value -> (!own.isBlank() && value.involves(own))
                        || managedSettlementIds.contains(value.attackerSettlement())
                        || managedSettlementIds.contains(value.defenderSettlement()))
                .toList();
        List<WarEntry> wars = visibleWars.stream().map(value -> {
            SettlementData.SettlementRecord attackerSettlement = value.attackerSettlement().isBlank() ? null : settlementsData.getSettlement(value.attackerSettlement());
            SettlementData.SettlementRecord target = value.targetSettlement().isBlank() ? null : settlementsData.getSettlement(value.targetSettlement());
            boolean sharedParent = value.type() == CampaignData.WarType.SETTLEMENT_WAR
                    && !value.attackerFaction().isBlank() && value.attackerFaction().equals(value.defenderFaction());
            // A faction cannot "join" both sides of an internal settlement conflict. Same-parent
            // wars remain local settlement-v-settlement wars until peace is negotiated by the settlements.
            boolean attackerParent = !sharedParent && !own.isBlank() && own.equals(value.attackerFaction());
            boolean defenderParent = !sharedParent && !own.isBlank() && own.equals(value.defenderFaction());
            return new WarEntry(value.id(), value.type().name(), value.escalationName(), value.goal().name(), value.casusBelliName(),
                    value.attackerFaction(), CampaignManager.diplomaticPolityName(player.serverLevel(), value.attackerFaction()),
                    value.defenderFaction(), CampaignManager.diplomaticPolityName(player.serverLevel(), value.defenderFaction()),
                    value.attackerSettlement(), attackerSettlement == null ? "" : attackerSettlement.getName(),
                    value.targetSettlement(), target == null ? "" : target.getName(), value.startedDay(),
                    value.supportingAttackers().stream().map(CampaignManager::factionName).toList(),
                    value.supportingDefenders().stream().map(CampaignManager::factionName).toList(),
                    value.attackerFactionJoined(), value.defenderFactionJoined(), attackerParent, defenderParent,
                    campaignSystems.ignoredWarIntervention(value.id(), own), campaignSystems.warScore(value.id()));
        }).toList();
        boolean canDeclareWar = leader || settlements.stream().anyMatch(SettlementTarget::manageable);
        return new DiplomacySnapshot(own, ownName, rank, canManage, canDeclareWar, relations, settlements, proposals, wars,
                message == null ? "" : message);
    }

    private static SettlementData.CharacterRecord factionLeaderCharacter(ServerPlayer viewer, RecruitsFaction faction) {
        if (faction == null || faction.getTeamLeaderUUID() == null) return null;
        return SettlementData.get(viewer.serverLevel()).getCharacter(faction.getTeamLeaderUUID());
    }

    private static String prettyTerm(CampaignSystemsData.PeaceTerm term) {
        return switch (term.type()) {
            case "TREASURY" -> term.amount() + " treasury";
            case "MANPOWER" -> term.amount() + " manpower";
            case "NAP" -> "NAP for " + term.durationDays() + " days";
            case "CONVERT_FAITH" -> "convert to " + term.text();
            case "TRANSFER_SETTLEMENT" -> "transfer settlement " + term.targetId();
            case "TRANSFER_CLAIM" -> "transfer claim " + term.text();
            case "RECOGNIZE_INDEPENDENCE" -> "recognize independence";
            default -> term.type().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        };
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(ownFactionId, 64); buffer.writeUtf(ownFactionName, 64); buffer.writeUtf(viewerRank, 32); buffer.writeBoolean(canManage); buffer.writeBoolean(canDeclareWar);
        writeList(buffer, relations, 256, RelationEntry::encode); writeList(buffer, settlements, 1024, SettlementTarget::encode);
        writeList(buffer, proposals, 256, ProposalEntry::encode); writeList(buffer, wars, 256, WarEntry::encode);
        buffer.writeUtf(message, 256);
    }

    public static DiplomacySnapshot decode(FriendlyByteBuf buffer) {
        return new DiplomacySnapshot(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32), buffer.readBoolean(), buffer.readBoolean(),
                readList(buffer, RelationEntry::decode, 256), readList(buffer, SettlementTarget::decode, 1024),
                readList(buffer, ProposalEntry::decode, 256), readList(buffer, WarEntry::decode, 256),
                buffer.readUtf(256));
    }

    private static <T> void writeList(FriendlyByteBuf buffer, List<T> values, int maximum, Encoder<T> encoder) {
        int count = Math.min(maximum, values.size()); buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) encoder.encode(values.get(i), buffer);
    }
    private static <T> List<T> readList(FriendlyByteBuf buffer, Decoder<T> decoder, int maximum) {
        int count = PacketValidation.readCount(buffer, maximum, "diplomacy entry"); List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(decoder.decode(buffer)); return values;
    }
    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        PacketValidation.writeCollection(buffer, values, 128, (buf, value) -> buf.writeUtf(value, 64));
    }
    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int count = PacketValidation.readCount(buffer, 128, "diplomacy string"); List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf(64)); return values;
    }
    private static CompoundTag safe(CompoundTag tag) { return tag == null ? new CompoundTag() : tag; }
    private interface Encoder<T> { void encode(T value, FriendlyByteBuf buffer); }
    private interface Decoder<T> { T decode(FriendlyByteBuf buffer); }

    public record RelationEntry(String factionId, String factionName, CompoundTag banner, int relationship,
                                String state, int faithModifier, int reliability,
                                int napMinimum, int defensiveMinimum, int militaryMinimum, int vassalMinimum,
                                String overlordId, String vassalId, int tributeRate, int projectedGrossTax, int projectedTribute) {
        private static void encode(RelationEntry value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.factionId, 64); buffer.writeUtf(value.factionName, 64); buffer.writeNbt(value.banner);
            buffer.writeInt(value.relationship); buffer.writeUtf(value.state, 32); buffer.writeInt(value.faithModifier); buffer.writeInt(value.reliability);
            buffer.writeInt(value.napMinimum); buffer.writeInt(value.defensiveMinimum); buffer.writeInt(value.militaryMinimum); buffer.writeInt(value.vassalMinimum);
            buffer.writeUtf(value.overlordId, 64); buffer.writeUtf(value.vassalId, 64); buffer.writeVarInt(value.tributeRate);
            buffer.writeVarInt(value.projectedGrossTax); buffer.writeVarInt(value.projectedTribute);
        }
        private static RelationEntry decode(FriendlyByteBuf buffer) {
            return new RelationEntry(buffer.readUtf(64), buffer.readUtf(64), safe(buffer.readNbt()), buffer.readInt(),
                    buffer.readUtf(32), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record SettlementTarget(String settlementId, String settlementName, String factionId, String factionName, String rank, boolean capital, boolean manageable,
                                   List<ClaimTarget> claims) {
        private static void encode(SettlementTarget value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.settlementId, 64); buffer.writeUtf(value.settlementName, 64); buffer.writeUtf(value.factionId, 64);
            buffer.writeUtf(value.factionName, 64); buffer.writeUtf(value.rank, 32); buffer.writeBoolean(value.capital); buffer.writeBoolean(value.manageable);
            PacketValidation.writeCollection(buffer, value.claims, 64, (buf, claim) -> {
                buf.writeUUID(claim.id()); buf.writeUtf(claim.name(), 64);
            });
        }
        private static SettlementTarget decode(FriendlyByteBuf buffer) {
            String id = buffer.readUtf(64), name = buffer.readUtf(64), faction = buffer.readUtf(64), factionName = buffer.readUtf(64), rank = buffer.readUtf(32);
            boolean capital = buffer.readBoolean(); boolean manageable = buffer.readBoolean(); int count = PacketValidation.readCount(buffer, 64, "settlement claim"); List<ClaimTarget> claims = new ArrayList<>(count);
            for (int i = 0; i < count; i++) claims.add(new ClaimTarget(buffer.readUUID(), buffer.readUtf(64)));
            return new SettlementTarget(id, name, faction, factionName, rank, capital, manageable, List.copyOf(claims));
        }
        public String display() { return settlementName + " - " + rank + (capital ? " · Capital" : "") + (factionId.isBlank() ? " · Independent" : " · " + factionName); }
    }
    public record ClaimTarget(UUID id, String name) {
        public String display() { return name == null || name.isBlank() ? id.toString() : name; }
    }

    public record ProposalEntry(UUID id, String type, String fromFactionId, String fromFactionName, UUID linkedWar,
                                String summary, boolean canReject, boolean structuredPeace) {
        private static void encode(ProposalEntry value, FriendlyByteBuf buffer) {
            buffer.writeUUID(value.id); buffer.writeUtf(value.type, 32); buffer.writeUtf(value.fromFactionId, 64); buffer.writeUtf(value.fromFactionName, 80);
            buffer.writeBoolean(value.linkedWar != null); if (value.linkedWar != null) buffer.writeUUID(value.linkedWar);
            buffer.writeUtf(value.summary, 512); buffer.writeBoolean(value.canReject); buffer.writeBoolean(value.structuredPeace);
        }
        private static ProposalEntry decode(FriendlyByteBuf buffer) {
            UUID id = buffer.readUUID(); String type = buffer.readUtf(32); String fromId = buffer.readUtf(64); String fromName = buffer.readUtf(80);
            UUID linked = buffer.readBoolean() ? buffer.readUUID() : null;
            return new ProposalEntry(id, type, fromId, fromName, linked, buffer.readUtf(512), buffer.readBoolean(), buffer.readBoolean());
        }
    }

    public record WarEntry(UUID id, String type, String typeName, String goal, String goalName,
                           String attackerFactionId, String attackerFactionName,
                           String defenderFactionId, String defenderFactionName,
                           String attackerSettlementId, String attackerSettlementName,
                           String targetSettlementId, String targetSettlementName, long startedDay,
                           List<String> supportingAttackers, List<String> supportingDefenders,
                           boolean attackerFactionJoined, boolean defenderFactionJoined,
                           boolean viewerIsAttackerParent, boolean viewerIsDefenderParent, boolean viewerIgnoredIntervention, int warScore) {
        private static void encode(WarEntry value, FriendlyByteBuf buffer) {
            buffer.writeUUID(value.id); buffer.writeUtf(value.type, 32); buffer.writeUtf(value.typeName, 48);
            buffer.writeUtf(value.goal, 48); buffer.writeUtf(value.goalName, 100);
            buffer.writeUtf(value.attackerFactionId, 64); buffer.writeUtf(value.attackerFactionName, 64);
            buffer.writeUtf(value.defenderFactionId, 64); buffer.writeUtf(value.defenderFactionName, 64);
            buffer.writeUtf(value.attackerSettlementId, 64); buffer.writeUtf(value.attackerSettlementName, 64);
            buffer.writeUtf(value.targetSettlementId, 64); buffer.writeUtf(value.targetSettlementName, 64); buffer.writeLong(value.startedDay);
            writeStrings(buffer, value.supportingAttackers); writeStrings(buffer, value.supportingDefenders);
            buffer.writeBoolean(value.attackerFactionJoined); buffer.writeBoolean(value.defenderFactionJoined);
            buffer.writeBoolean(value.viewerIsAttackerParent); buffer.writeBoolean(value.viewerIsDefenderParent); buffer.writeBoolean(value.viewerIgnoredIntervention); buffer.writeInt(value.warScore);
        }
        private static WarEntry decode(FriendlyByteBuf buffer) {
            return new WarEntry(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(48), buffer.readUtf(48), buffer.readUtf(100),
                    buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readLong(),
                    readStrings(buffer), readStrings(buffer), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt());
        }
    }}
