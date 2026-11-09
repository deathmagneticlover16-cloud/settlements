package ee.lastpagans.settlements;

import ee.lastpagans.settlements.network.PacketValidation;
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
        int viewerTier,
        String viewerRank,
        String rosterName,
        String government,
        String teamDye,
        String unitDye,
        int dailyTaxIncome,
        int stateTreasury,
        int realmPopulation,
        int activeWars,
        boolean foundingEnabled,
        boolean foundingRequiresTown,
        int foundingPopulation,
        int foundingStability,
        int foundingSilver,
        List<MemberEntry> members,
        List<SettlementEntry> settlements,
        List<String> allies,
        List<String> enemies,
        List<RankEntry> ranks,
        List<FactionChoice> availableFactions,
        List<RequestEntry> joinRequests,
        List<InvitationEntry> invitations,
        List<PlayerChoice> inviteCandidates,
        String message
) {
    public static FactionSnapshot from(ServerPlayer player, String message) {
        String factionId = CampaignManager.factionId(player);
        if (factionId.isBlank() || FactionEvents.recruitsFactionManager == null) {
            return independentSnapshot(player, message);
        }
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) {
            return new FactionSnapshot(factionId, factionId, new CompoundTag(), "Unknown", false, 0, "Commoner",
                    "None", "Monarchy", "white", "white", 0, 0, 0, 0,
                    configuredFoundingEnabled(), configuredFoundingRequiresTown(), configuredFoundingPopulation(), configuredFoundingStability(), configuredFoundingSilver(),
                    List.of(), List.of(), List.of(), List.of(), defaultRanks(), List.of(), List.of(), List.of(), List.of(),
                    message == null ? "" : message);
        }

        SettlementData settlementData = SettlementData.get(player.serverLevel());
        CampaignData campaign = CampaignData.get(player.serverLevel());
        CampaignSystemsData campaignSystems = CampaignSystemsData.get(player.serverLevel());
        boolean actualLeader = player.getUUID().equals(faction.getTeamLeaderUUID());
        boolean protectedFaction = campaignSystems.factionProtected(factionId);
        boolean administrator = player.createCommandSourceStack().hasPermission(2);
        boolean viewerLeader = actualLeader && (!protectedFaction || administrator);
        int viewerTier = viewerLeader ? 4 : protectedFaction && !administrator ? 0
                : campaign.factionRankTier(factionId, player.getUUID(), false);
        String viewerRank = protectedFaction && !administrator ? "Protected"
                : campaign.factionRankTitle(factionId, player.getUUID(), actualLeader);

        Map<UUID, String> accountNames = new LinkedHashMap<>();
        for (RecruitsPlayerInfo info : faction.getMembers()) {
            if (info != null && info.getUUID() != null) accountNames.put(info.getUUID(), info.getName());
        }
        if (faction.getTeamLeaderUUID() != null) accountNames.putIfAbsent(faction.getTeamLeaderUUID(), faction.getTeamLeaderName());

        List<MemberEntry> members = new ArrayList<>();
        accountNames.forEach((uuid, account) -> {
            SettlementData.CharacterRecord character = settlementData.getCharacter(uuid);
            String characterName = character != null && character.isCreated() ? character.getDisplayName() : account;
            boolean leader = uuid.equals(faction.getTeamLeaderUUID());
            int tier = campaign.factionRankTier(factionId, uuid, leader);
            String rank = campaign.factionRankTitle(factionId, uuid, leader);
            boolean online = player.getServer() != null && player.getServer().getPlayerList().getPlayer(uuid) != null;
            members.add(new MemberEntry(uuid, account == null ? "Unknown" : account, characterName, rank, tier, leader, online));
        });
        members.sort(Comparator.comparingInt(MemberEntry::tier).reversed().thenComparing(MemberEntry::characterName, String.CASE_INSENSITIVE_ORDER));

        List<SettlementData.SettlementRecord> owned = settlementData.getSettlements().stream()
                .filter(settlement -> factionId.equals(settlement.getFactionTeam()))
                .sorted(Comparator.comparingInt((SettlementData.SettlementRecord value) -> value.getRank().ordinal()).reversed()
                        .thenComparing(SettlementData.SettlementRecord::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SettlementEntry> settlements = owned.stream().map(settlement -> new SettlementEntry(
                settlement.getId(), settlement.getName(), settlement.getRank().displayName(),
                settlement.getCivilianPopulation(), settlement.getStability(), StateTreasury.balance(player.serverLevel(), settlement), campaignSystems.lastTaxIncome(settlement.getId()),
                settlement.isCapital()
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

        CampaignData.FactionProfile profile = campaign.factionProfileIfPresent(factionId);
        List<RankEntry> ranks = new ArrayList<>();
        for (int tier = 4; tier >= 0; tier--) {
            String title = profile == null ? CampaignDefinitions.defaultRankTitle(tier)
                    : profile.rankTitles().getOrDefault(tier, CampaignDefinitions.defaultRankTitle(tier));
            ranks.add(new RankEntry(tier, title));
        }

        return new FactionSnapshot(
                factionId,
                faction.getTeamDisplayName(),
                faction.getBanner() == null ? new CompoundTag() : faction.getBanner().copy(),
                faction.getTeamLeaderName(),
                viewerLeader,
                viewerTier,
                viewerRank,
                owned.isEmpty() ? campaign.getDefaultRosterId() : campaign.assignedRosterName(owned.get(0)),
                profile == null ? CampaignData.GovernmentType.MONARCHY.displayName() : profile.government().displayName(),
                profile == null ? "white" : profile.teamDye(),
                profile == null ? "white" : profile.unitDye(),
                owned.stream().mapToInt(settlement -> campaignSystems.lastTaxIncome(settlement.getId())).sum(),
                owned.isEmpty() ? campaignSystems.treasuryBalance(factionId) : StateTreasury.balance(player.serverLevel(), owned.get(0)),
                owned.stream().mapToInt(SettlementData.SettlementRecord::getDisplayedPopulation).sum(),
                campaign.activeWarsFor(factionId).size(),
                configuredFoundingEnabled(), configuredFoundingRequiresTown(), configuredFoundingPopulation(), configuredFoundingStability(), configuredFoundingSilver(),
                members,
                settlements,
                allies,
                enemies,
                ranks,
                List.of(),
                viewerTier >= 3 ? faction.getJoinRequests().stream()
                        .map(name -> new RequestEntry(name, player.getServer() != null && player.getServer().getPlayerList().getPlayerByName(name) != null))
                        .sorted(Comparator.comparing(RequestEntry::accountName, String.CASE_INSENSITIVE_ORDER)).toList() : List.of(),
                List.of(),
                viewerTier >= 3 && player.getServer() != null ? player.getServer().getPlayerList().getPlayers().stream()
                        .filter(candidate -> CampaignManager.factionId(candidate).isBlank() && !candidate.getUUID().equals(player.getUUID()))
                        .map(candidate -> {
                            SettlementData.CharacterRecord character = settlementData.getCharacter(candidate.getUUID());
                            String characterName = character != null && character.isCreated() ? character.getDisplayName() : candidate.getGameProfile().getName();
                            boolean invited = campaignSystems.hasFactionInvitation(factionId, candidate.getUUID());
                            return new PlayerChoice(candidate.getUUID(), candidate.getGameProfile().getName(), characterName, invited);
                        })
                        .sorted(Comparator.comparing(PlayerChoice::characterName, String.CASE_INSENSITIVE_ORDER)).toList() : List.of(),
                message == null ? "" : message
        );
    }

    private static FactionSnapshot independentSnapshot(ServerPlayer player, String message) {
        List<FactionChoice> factions = new ArrayList<>();
        if (FactionEvents.recruitsFactionManager != null) {
            String requesterName = player.getDisplayName().getString();
            CampaignSystemsData campaignSystems = CampaignSystemsData.get(player.serverLevel());
            for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
                if (faction == null || faction.getStringID() == null || faction.getStringID().isBlank()) continue;
                if (BackingFactionManager.isBackingFaction(faction.getStringID())) continue;
                if (campaignSystems.factionProtected(faction.getStringID())) continue;
                boolean pending = faction.getJoinRequests().stream().anyMatch(name -> name.equalsIgnoreCase(requesterName));
                factions.add(new FactionChoice(faction.getStringID(), faction.getTeamDisplayName(), faction.getPlayers(),
                        faction.getMaxPlayers(), pending));
            }
            factions.sort(Comparator.comparing(FactionChoice::name, String.CASE_INSENSITIVE_ORDER));
        }
        List<InvitationEntry> invitations = new ArrayList<>();
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (FactionEvents.recruitsFactionManager != null) {
            for (CampaignSystemsData.FactionInvitationRecord invitation : extra.factionInvitationsFor(player.getUUID())) {
                RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(invitation.factionId());
                if (faction == null || extra.factionProtected(invitation.factionId())) continue;
                invitations.add(new InvitationEntry(invitation.factionId(), faction.getTeamDisplayName()));
            }
            invitations.sort(Comparator.comparing(InvitationEntry::factionName, String.CASE_INSENSITIVE_ORDER));
        }
        SettlementData settlementData = SettlementData.get(player.serverLevel());
        List<SettlementEntry> ownedSettlements = settlementData.getOwnedSettlements(player.getUUID()).stream()
                .filter(value -> value.getFactionTeam().isBlank())
                .sorted(Comparator.comparingInt((SettlementData.SettlementRecord value) -> value.getRank().ordinal()).reversed()
                        .thenComparing(SettlementData.SettlementRecord::getName, String.CASE_INSENSITIVE_ORDER))
                .map(settlement -> new SettlementEntry(settlement.getId(), settlement.getName(), settlement.getRank().displayName(),
                        settlement.getCivilianPopulation(), settlement.getStability(), StateTreasury.balance(player.serverLevel(), settlement),
                        extra.lastTaxIncome(settlement.getId()), settlement.isCapital()))
                .toList();
        return new FactionSnapshot("", "Independent", new CompoundTag(), "None", false, 0, "Independent",
                "None", "Independent", "white", "white", 0, 0,
                ownedSettlements.stream().mapToInt(SettlementEntry::population).sum(), 0,
                configuredFoundingEnabled(), configuredFoundingRequiresTown(), configuredFoundingPopulation(), configuredFoundingStability(), configuredFoundingSilver(),
                List.of(), ownedSettlements, List.of(), List.of(),
                defaultRanks(), factions, List.of(), invitations, List.of(), message == null ? "" : message);
    }

    private static List<RankEntry> defaultRanks() {
        List<RankEntry> values = new ArrayList<>();
        for (int tier = 4; tier >= 0; tier--) values.add(new RankEntry(tier, CampaignDefinitions.defaultRankTitle(tier)));
        return values;
    }

    private static boolean configuredFoundingEnabled() { return SettlementConfig.FACTION_FOUNDING_ENABLED.get(); }
    private static boolean configuredFoundingRequiresTown() { return SettlementConfig.FACTION_FOUNDING_REQUIRE_TOWN.get(); }
    private static int configuredFoundingPopulation() { return SettlementConfig.FACTION_FOUNDING_MIN_POPULATION.get(); }
    private static int configuredFoundingStability() { return SettlementConfig.FACTION_FOUNDING_MIN_STABILITY.get(); }
    private static int configuredFoundingSilver() { return SettlementConfig.FACTION_FOUNDING_TREASURY_COST.get(); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(factionId, 64);
        buffer.writeUtf(factionName, 64);
        buffer.writeNbt(banner);
        buffer.writeUtf(leaderName, 80);
        buffer.writeBoolean(viewerLeader);
        buffer.writeVarInt(viewerTier);
        buffer.writeUtf(viewerRank, 32);
        buffer.writeUtf(rosterName, 64);
        buffer.writeUtf(government, 32);
        buffer.writeUtf(teamDye, 32);
        buffer.writeUtf(unitDye, 32);
        buffer.writeVarInt(dailyTaxIncome);
        buffer.writeVarInt(stateTreasury);
        buffer.writeVarInt(realmPopulation);
        buffer.writeVarInt(activeWars);
        buffer.writeBoolean(foundingEnabled);
        buffer.writeBoolean(foundingRequiresTown);
        buffer.writeVarInt(foundingPopulation);
        buffer.writeVarInt(foundingStability);
        buffer.writeVarInt(foundingSilver);
        writeList(buffer, members, 512, MemberEntry::encode);
        writeList(buffer, settlements, 512, SettlementEntry::encode);
        PacketValidation.writeCollection(buffer, allies, 256, (buf, value) -> buf.writeUtf(value, 64));
        PacketValidation.writeCollection(buffer, enemies, 256, (buf, value) -> buf.writeUtf(value, 64));
        writeList(buffer, ranks, 16, RankEntry::encode);
        writeList(buffer, availableFactions, 512, FactionChoice::encode);
        writeList(buffer, joinRequests, 512, RequestEntry::encode);
        writeList(buffer, invitations, 512, InvitationEntry::encode);
        writeList(buffer, inviteCandidates, 512, PlayerChoice::encode);
        buffer.writeUtf(message, 256);
    }

    public static FactionSnapshot decode(FriendlyByteBuf buffer) {
        String factionId = buffer.readUtf(64);
        String factionName = buffer.readUtf(64);
        CompoundTag banner = buffer.readNbt();
        if (banner == null) banner = new CompoundTag();
        String leaderName = buffer.readUtf(80);
        boolean viewerLeader = buffer.readBoolean();
        int viewerTier = buffer.readVarInt();
        String viewerRank = buffer.readUtf(32);
        String rosterName = buffer.readUtf(64);
        String government = buffer.readUtf(32);
        String teamDye = buffer.readUtf(32);
        String unitDye = buffer.readUtf(32);
        int dailyTaxIncome = buffer.readVarInt();
        int stateTreasury = buffer.readVarInt();
        int population = buffer.readVarInt();
        int activeWars = buffer.readVarInt();
        boolean foundingEnabled = buffer.readBoolean();
        boolean foundingRequiresTown = buffer.readBoolean();
        int foundingPopulation = buffer.readVarInt();
        int foundingStability = buffer.readVarInt();
        int foundingSilver = buffer.readVarInt();
        List<MemberEntry> members = readList(buffer, MemberEntry::decode, 512);
        List<SettlementEntry> settlements = readList(buffer, SettlementEntry::decode, 512);
        int allyCount = PacketValidation.readCount(buffer, 256, "faction ally");
        List<String> allies = new ArrayList<>(allyCount);
        for (int i = 0; i < allyCount; i++) allies.add(buffer.readUtf(64));
        int enemyCount = PacketValidation.readCount(buffer, 256, "faction enemy");
        List<String> enemies = new ArrayList<>(enemyCount);
        for (int i = 0; i < enemyCount; i++) enemies.add(buffer.readUtf(64));
        List<RankEntry> ranks = readList(buffer, RankEntry::decode, 16);
        List<FactionChoice> availableFactions = readList(buffer, FactionChoice::decode, 512);
        List<RequestEntry> joinRequests = readList(buffer, RequestEntry::decode, 512);
        List<InvitationEntry> invitations = readList(buffer, InvitationEntry::decode, 512);
        List<PlayerChoice> inviteCandidates = readList(buffer, PlayerChoice::decode, 512);
        return new FactionSnapshot(factionId, factionName, banner, leaderName, viewerLeader, viewerTier, viewerRank, rosterName,
                government, teamDye, unitDye, dailyTaxIncome, stateTreasury, population, activeWars,
                foundingEnabled, foundingRequiresTown, foundingPopulation, foundingStability, foundingSilver,
                members, settlements, allies, enemies, ranks,
                availableFactions, joinRequests, invitations, inviteCandidates, buffer.readUtf(256));
    }

    private static <T> void writeList(FriendlyByteBuf buffer, List<T> values, int maximum, Encoder<T> encoder) {
        int count = Math.min(maximum, values.size());
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) encoder.encode(values.get(i), buffer);
    }

    private static <T> List<T> readList(FriendlyByteBuf buffer, Decoder<T> decoder, int maximum) {
        int count = PacketValidation.readCount(buffer, maximum, "faction entry");
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
                                  int treasury, int taxIncome, boolean capital) {
        private static void encode(SettlementEntry value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.id, 64);
            buffer.writeUtf(value.name, 64);
            buffer.writeUtf(value.rank, 32);
            buffer.writeVarInt(value.population);
            buffer.writeVarInt(value.stability);
            buffer.writeVarInt(value.treasury);
            buffer.writeVarInt(value.taxIncome);
            buffer.writeBoolean(value.capital);
        }
        private static SettlementEntry decode(FriendlyByteBuf buffer) {
            return new SettlementEntry(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(32),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
    }

    public record FactionChoice(String id, String name, int players, int maxPlayers, boolean pending) {
        private static void encode(FactionChoice value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.id, 64);
            buffer.writeUtf(value.name, 64);
            buffer.writeVarInt(value.players);
            buffer.writeVarInt(value.maxPlayers);
            buffer.writeBoolean(value.pending);
        }
        private static FactionChoice decode(FriendlyByteBuf buffer) {
            return new FactionChoice(buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
    }

    public record RequestEntry(String accountName, boolean online) {
        private static void encode(RequestEntry value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.accountName, 80);
            buffer.writeBoolean(value.online);
        }
        private static RequestEntry decode(FriendlyByteBuf buffer) {
            return new RequestEntry(buffer.readUtf(80), buffer.readBoolean());
        }
    }

    public record InvitationEntry(String factionId, String factionName) {
        private static void encode(InvitationEntry value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.factionId, 64); buffer.writeUtf(value.factionName, 64);
        }
        private static InvitationEntry decode(FriendlyByteBuf buffer) {
            return new InvitationEntry(buffer.readUtf(64), buffer.readUtf(64));
        }
    }

    public record PlayerChoice(UUID playerId, String accountName, String characterName, boolean alreadyInvited) {
        private static void encode(PlayerChoice value, FriendlyByteBuf buffer) {
            buffer.writeUUID(value.playerId); buffer.writeUtf(value.accountName, 40); buffer.writeUtf(value.characterName, 80); buffer.writeBoolean(value.alreadyInvited);
        }
        private static PlayerChoice decode(FriendlyByteBuf buffer) {
            return new PlayerChoice(buffer.readUUID(), buffer.readUtf(40), buffer.readUtf(80), buffer.readBoolean());
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
