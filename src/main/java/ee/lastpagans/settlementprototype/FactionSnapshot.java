package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FactionSnapshot(
        String factionId,
        String factionName,
        CompoundTag banner,
        String leaderName,
        boolean viewerLeader,
        String viewerRank,
        String rosterName,
        int realmWealth,
        int factionTreasury,
        int realmPopulation,
        int activeWars,
        List<MemberEntry> members,
        List<SettlementEntry> settlements,
        List<String> allies,
        List<String> enemies,
        List<RankEntry> ranks,
        String message
) {
    public static FactionSnapshot from(ServerPlayer player, String message) {
        String factionId = player.getTeam() == null ? "" : player.getTeam().getName();
        if (factionId.isBlank() || FactionEvents.recruitsFactionManager == null) {
            return new FactionSnapshot("", "Independent", new CompoundTag(), "None", false, "Independent",
                    "None", 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), defaultRanks(),
                    message == null ? "" : message);
        }
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) {
            return new FactionSnapshot(factionId, factionId, new CompoundTag(), "Unknown", false, "Commoner",
                    "None", 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), defaultRanks(),
                    message == null ? "" : message);
        }

        PrototypeData settlementData = PrototypeData.get(player.serverLevel());
        CampaignData campaign = CampaignData.get(player.serverLevel());
        boolean viewerLeader = player.createCommandSourceStack().hasPermission(2) || player.getUUID().equals(faction.getTeamLeaderUUID());
        String viewerRank = campaign.factionRankTitle(factionId, player.getUUID(), player.getUUID().equals(faction.getTeamLeaderUUID()));

        Map<UUID, String> accountNames = new LinkedHashMap<>();
        for (RecruitsPlayerInfo info : faction.getMembers()) {
            if (info != null && info.getUUID() != null) accountNames.put(info.getUUID(), info.getName());
        }
        if (faction.getTeamLeaderUUID() != null) accountNames.putIfAbsent(faction.getTeamLeaderUUID(), faction.getTeamLeaderName());

        List<MemberEntry> members = new ArrayList<>();
        accountNames.forEach((uuid, account) -> {
            PrototypeData.CharacterRecord character = settlementData.getCharacter(uuid);
            String characterName = character != null && character.isCreated() ? character.getDisplayName() : account;
            boolean leader = uuid.equals(faction.getTeamLeaderUUID());
            int tier = campaign.factionRankTier(factionId, uuid, leader);
            String rank = campaign.factionRankTitle(factionId, uuid, leader);
            boolean online = player.getServer() != null && player.getServer().getPlayerList().getPlayer(uuid) != null;
            members.add(new MemberEntry(uuid, account == null ? "Unknown" : account, characterName, rank, tier, leader, online));
        });
        members.sort(Comparator.comparingInt(MemberEntry::tier).reversed().thenComparing(MemberEntry::characterName, String.CASE_INSENSITIVE_ORDER));

        List<PrototypeData.SettlementRecord> owned = settlementData.getSettlements().stream()
                .filter(settlement -> factionId.equals(settlement.getFactionTeam()))
                .sorted(Comparator.comparingInt((PrototypeData.SettlementRecord value) -> value.getRank().ordinal()).reversed()
                        .thenComparing(PrototypeData.SettlementRecord::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SettlementEntry> settlements = owned.stream().map(settlement -> new SettlementEntry(
                settlement.getId(), settlement.getName(), settlement.getRank().displayName(),
                settlement.getDisplayedPopulation(), settlement.getStability(), settlement.getWealth(),
                settlement.getRank() == PrototypeData.SettlementRank.CAPITAL
        )).toList();

        List<String> allies = new ArrayList<>();
        List<String> enemies = new ArrayList<>();
        for (CampaignData.DiplomacyRecord relation : campaign.getDiplomacy()) {
            String other = relation.firstFaction().equals(factionId) ? relation.secondFaction()
                    : relation.secondFaction().equals(factionId) ? relation.firstFaction() : "";
            if (other.isBlank()) continue;
            if (relation.state().isAlliance()) allies.add(CampaignManager.factionName(other));
            if (relation.state() == CampaignData.TreatyState.WAR) enemies.add(CampaignManager.factionName(other));
        }
        allies.sort(String.CASE_INSENSITIVE_ORDER);
        enemies.sort(String.CASE_INSENSITIVE_ORDER);

        CampaignData.FactionProfile profile = campaign.factionProfile(factionId);
        List<RankEntry> ranks = profile.rankTitles().entrySet().stream()
                .sorted(Map.Entry.<Integer, String>comparingByKey().reversed())
                .map(entry -> new RankEntry(entry.getKey(), entry.getValue()))
                .toList();

        return new FactionSnapshot(
                factionId,
                faction.getTeamDisplayName(),
                faction.getBanner() == null ? new CompoundTag() : faction.getBanner().copy(),
                faction.getTeamLeaderName(),
                viewerLeader,
                viewerRank,
                owned.isEmpty() ? campaign.getDefaultRosterId() : campaign.assignedRosterName(owned.get(0)),
                owned.stream().mapToInt(PrototypeData.SettlementRecord::getWealth).sum(),
                Campaign23Data.get(player.serverLevel()).factionWealth(factionId),
                owned.stream().mapToInt(PrototypeData.SettlementRecord::getDisplayedPopulation).sum(),
                campaign.activeWarsFor(factionId).size(),
                members,
                settlements,
                allies,
                enemies,
                ranks,
                message == null ? "" : message
        );
    }

    private static List<RankEntry> defaultRanks() {
        List<RankEntry> values = new ArrayList<>();
        for (int tier = 4; tier >= 0; tier--) values.add(new RankEntry(tier, CampaignDefinitions.defaultRankTitle(tier)));
        return values;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(factionId, 64);
        buffer.writeUtf(factionName, 64);
        buffer.writeNbt(banner);
        buffer.writeUtf(leaderName, 80);
        buffer.writeBoolean(viewerLeader);
        buffer.writeUtf(viewerRank, 32);
        buffer.writeUtf(rosterName, 64);
        buffer.writeVarInt(realmWealth);
        buffer.writeVarInt(factionTreasury);
        buffer.writeVarInt(realmPopulation);
        buffer.writeVarInt(activeWars);
        writeList(buffer, members, MemberEntry::encode);
        writeList(buffer, settlements, SettlementEntry::encode);
        buffer.writeVarInt(allies.size()); allies.forEach(value -> buffer.writeUtf(value, 64));
        buffer.writeVarInt(enemies.size()); enemies.forEach(value -> buffer.writeUtf(value, 64));
        writeList(buffer, ranks, RankEntry::encode);
        buffer.writeUtf(message, 256);
    }

    public static FactionSnapshot decode(FriendlyByteBuf buffer) {
        String factionId = buffer.readUtf(64);
        String factionName = buffer.readUtf(64);
        CompoundTag banner = buffer.readNbt();
        if (banner == null) banner = new CompoundTag();
        String leaderName = buffer.readUtf(80);
        boolean viewerLeader = buffer.readBoolean();
        String viewerRank = buffer.readUtf(32);
        String rosterName = buffer.readUtf(64);
        int wealth = buffer.readVarInt();
        int treasury = buffer.readVarInt();
        int population = buffer.readVarInt();
        int activeWars = buffer.readVarInt();
        List<MemberEntry> members = readList(buffer, MemberEntry::decode, 512);
        List<SettlementEntry> settlements = readList(buffer, SettlementEntry::decode, 512);
        int allyCount = Math.min(256, buffer.readVarInt());
        List<String> allies = new ArrayList<>(allyCount);
        for (int i = 0; i < allyCount; i++) allies.add(buffer.readUtf(64));
        int enemyCount = Math.min(256, buffer.readVarInt());
        List<String> enemies = new ArrayList<>(enemyCount);
        for (int i = 0; i < enemyCount; i++) enemies.add(buffer.readUtf(64));
        List<RankEntry> ranks = readList(buffer, RankEntry::decode, 16);
        return new FactionSnapshot(factionId, factionName, banner, leaderName, viewerLeader, viewerRank, rosterName,
                wealth, treasury, population, activeWars, members, settlements, allies, enemies, ranks, buffer.readUtf(256));
    }

    private static <T> void writeList(FriendlyByteBuf buffer, List<T> values, Encoder<T> encoder) {
        buffer.writeVarInt(values.size());
        values.forEach(value -> encoder.encode(value, buffer));
    }

    private static <T> List<T> readList(FriendlyByteBuf buffer, Decoder<T> decoder, int maximum) {
        int count = Math.min(maximum, buffer.readVarInt());
        List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(decoder.decode(buffer));
        return values;
    }

    private interface Encoder<T> { void encode(T value, FriendlyByteBuf buffer); }
    private interface Decoder<T> { T decode(FriendlyByteBuf buffer); }

    public record MemberEntry(UUID playerId, String accountName, String characterName, String rankTitle,
                              int tier, boolean leader, boolean online) {
        private static void encode(MemberEntry value, FriendlyByteBuf buffer) {
            buffer.writeUUID(value.playerId);
            buffer.writeUtf(value.accountName, 40);
            buffer.writeUtf(value.characterName, 80);
            buffer.writeUtf(value.rankTitle, 32);
            buffer.writeVarInt(value.tier);
            buffer.writeBoolean(value.leader);
            buffer.writeBoolean(value.online);
        }
        private static MemberEntry decode(FriendlyByteBuf buffer) {
            return new MemberEntry(buffer.readUUID(), buffer.readUtf(40), buffer.readUtf(80), buffer.readUtf(32),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
        }
    }

    public record SettlementEntry(String id, String name, String rank, int population, int stability,
                                  int wealth, boolean capital) {
        private static void encode(SettlementEntry value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.id, 64);
            buffer.writeUtf(value.name, 64);
            buffer.writeUtf(value.rank, 32);
            buffer.writeVarInt(value.population);
            buffer.writeVarInt(value.stability);
            buffer.writeVarInt(value.wealth);
            buffer.writeBoolean(value.capital);
        }
        private static SettlementEntry decode(FriendlyByteBuf buffer) {
            return new SettlementEntry(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
    }

    public record RankEntry(int tier, String title) {
        private static void encode(RankEntry value, FriendlyByteBuf buffer) {
            buffer.writeVarInt(value.tier);
            buffer.writeUtf(value.title, 32);
        }
        private static RankEntry decode(FriendlyByteBuf buffer) {
            return new RankEntry(buffer.readVarInt(), buffer.readUtf(32));
        }
    }
}
