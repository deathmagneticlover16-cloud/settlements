package ee.lastpagans.settlementprototype;

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
        String own = CampaignManager.factionId(player);
        String ownName = CampaignManager.factionName(own);
        CampaignData campaign = CampaignData.get(player.serverLevel());
        RecruitsFaction ownFaction = own.isBlank() || FactionEvents.recruitsFactionManager == null ? null
                : FactionEvents.recruitsFactionManager.getFactionByStringID(own);
        boolean leader = ownFaction != null && player.getUUID().equals(ownFaction.getTeamLeaderUUID());
        String rank = own.isBlank() ? "Independent" : campaign.factionRankTitle(own, player.getUUID(), leader);
        boolean canManage = player.createCommandSourceStack().hasPermission(2) || leader
                || (!own.isBlank() && campaign.factionRankTier(own, player.getUUID(), false) >= 3);

        List<RelationEntry> relations = new ArrayList<>();
        if (FactionEvents.recruitsFactionManager != null) {
            for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
                if (faction == null || faction.getStringID() == null || faction.getStringID().equals(own)) continue;
                CampaignData.DiplomacyRecord relation = campaign.relation(own, faction.getStringID());
                PrototypeData.CharacterRecord ownCharacter = PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID());
                String ownFaith = ownCharacter == null ? "" : ownCharacter.getFaith();
                String otherFaith = factionLeaderFaith(player, faction);
                int faithModifier = CampaignDefinitions.religionRelationModifier(ownFaith, otherFaith);
                relations.add(new RelationEntry(
                        faction.getStringID(), faction.getTeamDisplayName(), faction.getBanner() == null ? new CompoundTag() : faction.getBanner().copy(),
                        relation.relationship(), relation.state().name(), faithModifier, campaign.getFactionReliability(faction.getStringID())
                ));
            }
        }
        relations.sort(Comparator.comparing(RelationEntry::factionName, String.CASE_INSENSITIVE_ORDER));

        PrototypeData settlementsData = PrototypeData.get(player.serverLevel());
        List<SettlementTarget> settlements = settlementsData.getSettlements().stream()
                .map(value -> new SettlementTarget(value.getId(), value.getName(), value.getFactionTeam(),
                        CampaignManager.factionName(value.getFactionTeam()), value.getRank().displayName(),
                        PrototypeManager.canManage(player, value)))
                .sorted(Comparator.comparing(SettlementTarget::factionName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SettlementTarget::settlementName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<ProposalEntry> proposals = own.isBlank() ? List.of() : campaign.proposalsFor(own).stream()
                .map(value -> new ProposalEntry(value.id(), value.type().name(), value.fromFaction(), CampaignManager.factionName(value.fromFaction()), value.linkedWar()))
                .toList();
        java.util.Set<String> managedSettlementIds = settlements.stream().filter(SettlementTarget::manageable)
                .map(SettlementTarget::settlementId).collect(java.util.stream.Collectors.toSet());
        List<CampaignData.WarRecord> visibleWars = campaign.getWars().stream()
                .filter(value -> value.status() == CampaignData.WarStatus.ACTIVE)
                .filter(value -> (!own.isBlank() && value.involves(own))
                        || managedSettlementIds.contains(value.attackerSettlement())
                        || managedSettlementIds.contains(value.defenderSettlement()))
                .toList();
        List<WarEntry> wars = visibleWars.stream().map(value -> {
            PrototypeData.SettlementRecord attackerSettlement = value.attackerSettlement().isBlank() ? null : settlementsData.getSettlement(value.attackerSettlement());
            PrototypeData.SettlementRecord target = value.targetSettlement().isBlank() ? null : settlementsData.getSettlement(value.targetSettlement());
            boolean attackerParent = !own.isBlank() && own.equals(value.attackerFaction());
            boolean defenderParent = !own.isBlank() && own.equals(value.defenderFaction());
            return new WarEntry(value.id(), value.type().name(), value.escalationName(), value.goal().name(), value.casusBelliName(),
                    value.attackerFaction(), CampaignManager.factionName(value.attackerFaction()),
                    value.defenderFaction(), CampaignManager.factionName(value.defenderFaction()),
                    value.attackerSettlement(), attackerSettlement == null ? "" : attackerSettlement.getName(),
                    value.targetSettlement(), target == null ? "" : target.getName(), value.startedDay(),
                    value.supportingAttackers().stream().map(CampaignManager::factionName).toList(),
                    value.supportingDefenders().stream().map(CampaignManager::factionName).toList(),
                    value.attackerFactionJoined(), value.defenderFactionJoined(), attackerParent, defenderParent,
                    Campaign23Data.get(player.serverLevel()).warScore(value.id()));
        }).toList();

        boolean canDeclareWar = player.createCommandSourceStack().hasPermission(2) || leader
                || settlements.stream().anyMatch(SettlementTarget::manageable);
        return new DiplomacySnapshot(own, ownName, rank, canManage, canDeclareWar, relations, settlements, proposals, wars,
                message == null ? "" : message);
    }

    private static String factionLeaderFaith(ServerPlayer viewer, RecruitsFaction faction) {
        if (faction == null || faction.getTeamLeaderUUID() == null) return "";
        PrototypeData.CharacterRecord character = PrototypeData.get(viewer.serverLevel()).getCharacter(faction.getTeamLeaderUUID());
        return character == null ? "" : character.getFaith();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(ownFactionId, 64); buffer.writeUtf(ownFactionName, 64); buffer.writeUtf(viewerRank, 32); buffer.writeBoolean(canManage); buffer.writeBoolean(canDeclareWar);
        writeList(buffer, relations, RelationEntry::encode); writeList(buffer, settlements, SettlementTarget::encode);
        writeList(buffer, proposals, ProposalEntry::encode); writeList(buffer, wars, WarEntry::encode);
        buffer.writeUtf(message, 256);
    }

    public static DiplomacySnapshot decode(FriendlyByteBuf buffer) {
        return new DiplomacySnapshot(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32), buffer.readBoolean(), buffer.readBoolean(),
                readList(buffer, RelationEntry::decode, 256), readList(buffer, SettlementTarget::decode, 1024),
                readList(buffer, ProposalEntry::decode, 256), readList(buffer, WarEntry::decode, 256), buffer.readUtf(256));
    }

    private static <T> void writeList(FriendlyByteBuf buffer, List<T> values, Encoder<T> encoder) {
        buffer.writeVarInt(values.size()); values.forEach(value -> encoder.encode(value, buffer));
    }
    private static <T> List<T> readList(FriendlyByteBuf buffer, Decoder<T> decoder, int maximum) {
        int count = Math.min(maximum, buffer.readVarInt()); List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(decoder.decode(buffer)); return values;
    }
    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size()); values.forEach(value -> buffer.writeUtf(value, 64));
    }
    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int count = Math.min(128, buffer.readVarInt()); List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readUtf(64)); return values;
    }
    private static CompoundTag safe(CompoundTag tag) { return tag == null ? new CompoundTag() : tag; }
    private interface Encoder<T> { void encode(T value, FriendlyByteBuf buffer); }
    private interface Decoder<T> { T decode(FriendlyByteBuf buffer); }

    public record RelationEntry(String factionId, String factionName, CompoundTag banner, int relationship,
                                String state, int faithModifier, int reliability) {
        private static void encode(RelationEntry value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.factionId, 64); buffer.writeUtf(value.factionName, 64); buffer.writeNbt(value.banner);
            buffer.writeInt(value.relationship); buffer.writeUtf(value.state, 32); buffer.writeInt(value.faithModifier); buffer.writeInt(value.reliability);
        }
        private static RelationEntry decode(FriendlyByteBuf buffer) {
            return new RelationEntry(buffer.readUtf(64), buffer.readUtf(64), safe(buffer.readNbt()), buffer.readInt(),
                    buffer.readUtf(32), buffer.readInt(), buffer.readInt());
        }
    }

    public record SettlementTarget(String settlementId, String settlementName, String factionId, String factionName, String rank, boolean manageable) {
        private static void encode(SettlementTarget value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.settlementId, 64); buffer.writeUtf(value.settlementName, 64); buffer.writeUtf(value.factionId, 64);
            buffer.writeUtf(value.factionName, 64); buffer.writeUtf(value.rank, 32); buffer.writeBoolean(value.manageable);
        }
        private static SettlementTarget decode(FriendlyByteBuf buffer) {
            return new SettlementTarget(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32), buffer.readBoolean());
        }
        public String display() { return settlementName + " — " + rank + (factionId.isBlank() ? " · Independent" : " · " + factionName); }
    }

    public record ProposalEntry(UUID id, String type, String fromFactionId, String fromFactionName, UUID linkedWar) {
        private static void encode(ProposalEntry value, FriendlyByteBuf buffer) {
            buffer.writeUUID(value.id); buffer.writeUtf(value.type, 32); buffer.writeUtf(value.fromFactionId, 64); buffer.writeUtf(value.fromFactionName, 64);
            buffer.writeBoolean(value.linkedWar != null); if (value.linkedWar != null) buffer.writeUUID(value.linkedWar);
        }
        private static ProposalEntry decode(FriendlyByteBuf buffer) {
            UUID id = buffer.readUUID(); String type = buffer.readUtf(32); String fromId = buffer.readUtf(64); String fromName = buffer.readUtf(64);
            return new ProposalEntry(id, type, fromId, fromName, buffer.readBoolean() ? buffer.readUUID() : null);
        }
    }

    public record WarEntry(UUID id, String type, String typeName, String goal, String goalName,
                           String attackerFactionId, String attackerFactionName,
                           String defenderFactionId, String defenderFactionName,
                           String attackerSettlementId, String attackerSettlementName,
                           String targetSettlementId, String targetSettlementName, long startedDay,
                           List<String> supportingAttackers, List<String> supportingDefenders,
                           boolean attackerFactionJoined, boolean defenderFactionJoined,
                           boolean viewerIsAttackerParent, boolean viewerIsDefenderParent, int warScore) {
        private static void encode(WarEntry value, FriendlyByteBuf buffer) {
            buffer.writeUUID(value.id); buffer.writeUtf(value.type, 32); buffer.writeUtf(value.typeName, 48);
            buffer.writeUtf(value.goal, 48); buffer.writeUtf(value.goalName, 100);
            buffer.writeUtf(value.attackerFactionId, 64); buffer.writeUtf(value.attackerFactionName, 64);
            buffer.writeUtf(value.defenderFactionId, 64); buffer.writeUtf(value.defenderFactionName, 64);
            buffer.writeUtf(value.attackerSettlementId, 64); buffer.writeUtf(value.attackerSettlementName, 64);
            buffer.writeUtf(value.targetSettlementId, 64); buffer.writeUtf(value.targetSettlementName, 64); buffer.writeLong(value.startedDay);
            writeStrings(buffer, value.supportingAttackers); writeStrings(buffer, value.supportingDefenders);
            buffer.writeBoolean(value.attackerFactionJoined); buffer.writeBoolean(value.defenderFactionJoined);
            buffer.writeBoolean(value.viewerIsAttackerParent); buffer.writeBoolean(value.viewerIsDefenderParent); buffer.writeInt(value.warScore);
        }
        private static WarEntry decode(FriendlyByteBuf buffer) {
            return new WarEntry(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(48), buffer.readUtf(48), buffer.readUtf(100),
                    buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64), buffer.readLong(),
                    readStrings(buffer), readStrings(buffer), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readInt());
        }
    }}
