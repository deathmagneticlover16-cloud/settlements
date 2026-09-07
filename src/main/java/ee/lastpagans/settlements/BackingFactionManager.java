package ee.lastpagans.settlements;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hidden Recruits factions let an independent Settlements polity own claims and military entities
 * without pretending to be a full political faction in Settlements diplomacy.
 */
public final class BackingFactionManager {
    public static final String PREFIX = "stl_i_";
    private BackingFactionManager() {}

    public static boolean isBackingFaction(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    public static String rawTeam(ServerPlayer player) {
        return player == null || player.getTeam() == null ? "" : player.getTeam().getName();
    }

    /** Ensures the independent ruler has a lightweight Recruits faction and returns its ID. */
    public static String ensureForIndependent(ServerPlayer player) {
        if (player == null || FactionEvents.recruitsFactionManager == null) return "";
        String raw = rawTeam(player);
        if (!raw.isBlank() && !isBackingFaction(raw)) return "";
        if (isBackingFaction(raw) && FactionEvents.recruitsFactionManager.getFactionByStringID(raw) != null) {
            attachOwnedIndependentSettlements(player, raw);
            return raw;
        }

        String compact = player.getUUID().toString().replace("-", "");
        String base = PREFIX + compact.substring(0, Math.min(16, compact.length()));
        String id = base;
        int suffix = 2;
        while (FactionEvents.recruitsFactionManager.getFactionByStringID(id) != null
                || player.getServer().getScoreboard().getPlayerTeam(id) != null) {
            id = base.substring(0, Math.min(base.length(), 28)) + "_" + suffix++;
        }
        try {
            FactionEvents.createTeam(false, player, player.serverLevel(), id,
                    "Independent Holdings", player.getGameProfile().getName(), Items.WHITE_BANNER.getDefaultInstance(),
                    ChatFormatting.WHITE, (byte)15);
        } catch (RuntimeException exception) {
            return "";
        }
        if (FactionEvents.recruitsFactionManager.getFactionByStringID(id) == null) return "";
        attachOwnedIndependentSettlements(player, id);
        return id;
    }

    private static void attachOwnedIndependentSettlements(ServerPlayer player, String backingId) {
        SettlementData data = SettlementData.get(player.serverLevel());
        for (SettlementData.SettlementRecord settlement : data.getOwnedSettlements(player.getUUID())) {
            if (settlement.getFactionTeam().isBlank()) settlement.setBackingFactionTeam(backingId);
        }
    }

    public static void assignIndependentSettlement(ServerPlayer owner, SettlementData.SettlementRecord settlement) {
        if (owner == null || settlement == null || !settlement.getFactionTeam().isBlank()) return;
        String backing = ensureForIndependent(owner);
        if (backing.isBlank()) return;
        String previous = settlement.getBackingFactionTeam();
        settlement.setBackingFactionTeam(backing);
        migrateClaimOwnership(owner.getServer(), settlement, backing);
        migrateLoadedEntities(owner.getServer(), settlement, previous, backing);
        retireIfUnused(owner.getServer(), previous);
    }


    /**
     * Temporarily stops Recruits from treating an independent ruler as the leader of a real faction
     * while its native addPlayerToTeam routine moves them into a political faction. The technical
     * faction and claims remain intact until the join is confirmed, so a failed join is lossless.
     */
    public static PoliticalTransition beginPoliticalJoin(ServerPlayer player) {
        String raw = rawTeam(player);
        if (!isBackingFaction(raw) || FactionEvents.recruitsFactionManager == null) return new PoliticalTransition(raw, null, "");
        RecruitsFaction backing = FactionEvents.recruitsFactionManager.getFactionByStringID(raw);
        if (backing == null) return new PoliticalTransition(raw, null, "");
        UUID oldLeader = backing.getTeamLeaderUUID();
        String oldLeaderName = backing.getTeamLeaderName();
        // Recruits checks every faction leader UUID before accepting a member. A non-player sentinel
        // keeps the technical faction valid without allowing the check to mistake this ruler for a
        // political faction leader.
        backing.setTeamLeaderID(new UUID(0L, 1L));
        backing.setTeamLeaderName("Settlements internal");
        FactionEvents.recruitsFactionManager.save(player.serverLevel());
        return new PoliticalTransition(raw, oldLeader, oldLeaderName);
    }

    public static void finishPoliticalJoin(ServerPlayer player, PoliticalTransition transition, String factionId, boolean success) {
        if (player == null || transition == null || !isBackingFaction(transition.backingId()) || FactionEvents.recruitsFactionManager == null) return;
        RecruitsFaction backing = FactionEvents.recruitsFactionManager.getFactionByStringID(transition.backingId());
        if (!success) {
            if (backing != null) {
                backing.setTeamLeaderID(transition.originalLeader());
                backing.setTeamLeaderName(transition.originalLeaderName());
                FactionEvents.recruitsFactionManager.save(player.serverLevel());
            }
            return;
        }
        migrateOwnedSettlementsToPoliticalFaction(player, factionId);
        retireIfUnused(player.getServer(), transition.backingId());
    }

    public record PoliticalTransition(String backingId, UUID originalLeader, String originalLeaderName) {}

    /** Called after a real Recruits faction has been successfully created/joined. */
    public static void migrateOwnedSettlementsToPoliticalFaction(ServerPlayer player, String factionId) {
        if (player == null || factionId == null || factionId.isBlank() || isBackingFaction(factionId)) return;
        SettlementData data = SettlementData.get(player.serverLevel());
        ResearchData.get(player.serverLevel()).mergePolity("independent:" + player.getUUID(), "faction:" + factionId);
        Set<String> oldBacking = new HashSet<>();
        for (SettlementData.SettlementRecord settlement : data.getOwnedSettlements(player.getUUID())) {
            if (!settlement.getFactionTeam().isBlank()) continue;
            StateTreasury.migrateLegacy(player.serverLevel(), settlement);
            String previous = settlement.getBackingFactionTeam();
            if (!previous.isBlank()) oldBacking.add(previous);
            settlement.setFactionTeam(factionId);
            settlement.setBackingFactionTeam("");
            migrateClaimOwnership(player.getServer(), settlement, factionId);
            migrateLoadedEntities(player.getServer(), settlement, previous, factionId);
            settlement.addChronicle("Joined political faction " + CampaignManager.factionName(factionId) + ".");
        }
        CampaignSystemsData systems = CampaignSystemsData.get(player.serverLevel());
        for (String backingId : oldBacking) {
            int balance = systems.factionWealth(backingId);
            if (balance > 0) {
                systems.changeFactionWealth(factionId, balance);
                systems.setFactionWealth(backingId, 0);
            }
        }
        oldBacking.forEach(value -> retireIfUnused(player.getServer(), value));
    }

    /** Reassigns the linked Recruits claims without changing Settlements political ownership. */
    public static void migrateClaimOwnership(MinecraftServer server, SettlementData.SettlementRecord settlement, String recruitsFactionId) {
        if (server == null || settlement == null || recruitsFactionId == null || recruitsFactionId.isBlank()
                || ClaimEvents.recruitsClaimManager == null || FactionEvents.recruitsFactionManager == null) return;
        RecruitsFaction target = FactionEvents.recruitsFactionManager.getFactionByStringID(recruitsFactionId);
        if (target == null) return;
        for (SettlementData.ClaimLink link : settlement.getClaimLinks()) {
            if (link.id() == null) continue;
            RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(link.id());
            if (claim == null) continue;
            claim.setOwnerFaction(target);
            ServerLevel level = resolveLevel(server, link.dimension());
            if (level != null) ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        }
        ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(server.overworld());
    }

    private static void migrateLoadedEntities(MinecraftServer server, SettlementData.SettlementRecord settlement, String oldTeam, String newTeam) {
        if (server == null || settlement == null || newTeam == null || newTeam.isBlank()) return;
        PlayerTeam target = server.getScoreboard().getPlayerTeam(newTeam);
        if (target == null) return;
        Set<UUID> ids = new HashSet<>(settlement.getAllMilitaryUnits());
        ids.addAll(settlement.getVillagerIds());
        for (UUID id : ids) {
            Entity entity = findLoaded(server, id);
            if (entity == null) continue;
            PlayerTeam current = entity.getTeam() instanceof PlayerTeam team ? team : null;
            if (current != null && (oldTeam == null || oldTeam.isBlank() || current.getName().equals(oldTeam))) {
                server.getScoreboard().removePlayerFromTeam(entity.getScoreboardName(), current);
            }
            server.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), target);
        }
    }

    private static Entity findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    public static void retireIfUnused(MinecraftServer server, String backingId) {
        if (server == null || !isBackingFaction(backingId) || FactionEvents.recruitsFactionManager == null) return;
        boolean used = SettlementData.get(server.overworld()).getSettlements().stream()
                .anyMatch(value -> backingId.equals(value.getBackingFactionTeam()));
        if (used) return;
        try { FactionEvents.removeTeam(server.overworld(), backingId); }
        catch (RuntimeException ignored) {
            FactionEvents.recruitsFactionManager.removeTeam(backingId);
            PlayerTeam team = server.getScoreboard().getPlayerTeam(backingId);
            if (team != null) server.getScoreboard().removePlayerTeam(team);
        }
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension == null ? "" : dimension);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }
}
