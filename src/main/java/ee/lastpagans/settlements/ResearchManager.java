package ee.lastpagans.settlements;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlements.network.ResearchSnapshotPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One-project-at-a-time research controller. Progress advances only while the server is running. */
public final class ResearchManager {
    private ResearchManager() {}

    public static void open(ServerPlayer player, String message) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ResearchSnapshotPacket(ResearchSnapshot.from(player, message)));
    }

    public static void handleAction(ServerPlayer player, String action, String stageId) {
        Polity polity = polityFor(player);
        if (polity == null) { open(player, "Own a settlement or join a political faction before researching technologies."); return; }
        ResearchData data = ResearchData.get(player.serverLevel());
        String command = action == null ? "" : action.toUpperCase(java.util.Locale.ROOT);
        if ("REFRESH".equals(command)) { open(player, ""); return; }
        if (!canManage(player, polity)) { open(player, "Only the settlement ruler or senior faction leadership may direct research."); return; }
        if ("CANCEL".equals(command)) {
            open(player, data.cancel(polity.key()) ? "Research project cancelled. Progress was lost." : "There is no active research project.");
            return;
        }
        if (!"START".equals(command)) { open(player, "Unknown research action."); return; }
        if (!HistoryStagesCompat.installed()) { open(player, "History Stages is not installed; technology research is unavailable."); return; }
        if (data.active(polity.key()) != null) { open(player, "Finish or cancel the current project before starting another technology."); return; }
        HistoryStagesCompat.Technology technology = HistoryStagesCompat.technologyMap().get(stageId == null ? "" : stageId);
        if (technology == null) { open(player, "That History Stages technology is no longer available."); return; }
        if (data.isCompleted(polity.key(), technology.id())) { open(player, "That technology has already been researched."); return; }
        if (!HistoryStagesCompat.dependenciesMet(technology, data.completed(polity.key()))) {
            open(player, "Research prerequisites are not yet satisfied."); return;
        }
        int tier = researchTier(player.getServer(), polity);
        if (!tierAllows(technology, tier)) {
            open(player, "That technology requires research tier " + technology.minimumTier() + ("EXACT".equals(technology.tierMode()) ? " exactly." : " or higher.")); return;
        }
        if (!data.start(polity.key(), technology)) { open(player, "Unable to start that research project."); return; }
        open(player, "Research begun: " + technology.name() + ".");
    }

    public static Polity polityFor(ServerPlayer player) {
        if (player == null) return null;
        String faction = CampaignManager.factionId(player);
        if (!faction.isBlank()) return new Polity("faction:" + faction, CampaignManager.factionName(faction), "faction", faction);
        SettlementData data = SettlementData.get(player.serverLevel());
        List<SettlementData.SettlementRecord> owned = data.getOwnedSettlements(player.getUUID()).stream()
                .filter(value -> value.getFactionTeam().isBlank()).toList();
        if (owned.isEmpty()) return null;
        SettlementData.SettlementRecord selected = data.getSelectedSettlement(player.getUUID());
        String name = selected != null && player.getUUID().equals(selected.getOwnerId()) && selected.getFactionTeam().isBlank()
                ? selected.getName() : owned.get(0).getName();
        return new Polity("independent:" + player.getUUID(), name + " Independent Polity", "independent", player.getUUID().toString());
    }

    public static boolean canManage(ServerPlayer player, Polity polity) {
        if (player == null || polity == null) return false;
        if ("independent".equals(polity.kind())) return player.getUUID().toString().equals(polity.id());
        if (CampaignManager.isFactionLeader(player, polity.id())) return true;
        return CampaignData.get(player.serverLevel()).factionRankTier(polity.id(), player.getUUID(), false) >= 3;
    }

    public static void processResearch(MinecraftServer server) {
        if (server == null || !HistoryStagesCompat.installed()) return;
        ResearchData data = ResearchData.get(server.overworld());
        for (String polityKey : new ArrayList<>(data.politiesWithActiveResearch())) {
            Polity polity = resolve(server, polityKey);
            if (polity == null) { data.cancel(polityKey); continue; }
            ResearchData.ActiveResearch before = data.active(polityKey);
            if (before == null) continue;
            int speed = speedPercent(server, polity);
            long scaled = Math.max(1L, 20L * 1000L * speed / 100L);
            if (!data.progress(polityKey, scaled)) continue;
            grantCompletedToMembers(server, polity, before.stageId());
            notifyMembers(server, polity, "Research completed: " + before.stageName() + ".");
            CampaignData.get(server.overworld()).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.NORMAL,
                    "Research completed", polity.name() + " completed research into " + before.stageName() + ".",
                    server.overworld().getDayTime() / 24000L, "faction".equals(polity.kind()) ? polity.id() : "",
                    "settlement".equals(polity.kind()) ? polity.id() : "", CampaignData.NewsVisibility.FACTION);
        }
    }

    public static void syncCompletedResearch(ServerPlayer player) {
        if (!HistoryStagesCompat.installed() || player == null) return;
        ResearchData data = ResearchData.get(player.serverLevel());
        String faction = CampaignManager.factionId(player);
        if (!faction.isBlank()) {
            for (String stage : data.completed("faction:" + faction)) HistoryStagesCompat.unlock(player, stage);
            return;
        }
        SettlementData settlements = SettlementData.get(player.serverLevel());
        Set<UUID> independentRulers = new java.util.HashSet<>();
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            if (!settlement.getFactionTeam().isBlank() || settlement.getOwnerId() == null) continue;
            if (settlement.getRole(player.getUUID()) != SettlementData.SettlementRole.OUTSIDER) independentRulers.add(settlement.getOwnerId());
        }
        for (UUID ruler : independentRulers) {
            for (String stage : data.completed("independent:" + ruler)) HistoryStagesCompat.unlock(player, stage);
        }
    }

    public static int researchTier(MinecraftServer server, Polity polity) {
        if (server == null || polity == null) return 1;
        SettlementData data = SettlementData.get(server.overworld());
        List<SettlementData.SettlementRecord> holdings;
        if ("independent".equals(polity.kind())) {
            UUID ruler;
            try { ruler = UUID.fromString(polity.id()); } catch (IllegalArgumentException exception) { return 1; }
            holdings = data.getOwnedSettlements(ruler).stream().filter(value -> value.getFactionTeam().isBlank()).toList();
        } else holdings = data.settlementsForFaction(polity.id());
        int tier = 1;
        for (SettlementData.SettlementRecord holding : holdings) {
            tier = Math.max(tier, settlementTier(holding));
        }
        return tier;
    }

    public static int settlementTier(SettlementData.SettlementRecord holding) {
        return switch (holding.getRank()) {
            case VILLAGE -> 1;
            case TOWN -> 2;
            case CITY -> 3;
            case MAJOR_CITY -> 4;
        };
    }

    public static boolean tierAllows(HistoryStagesCompat.Technology technology, int polityTier) {
        if (technology == null) return false;
        int required = Math.max(1, technology.minimumTier());
        return "EXACT".equalsIgnoreCase(technology.tierMode()) ? polityTier == required : polityTier >= required;
    }

    public static int speedPercent(MinecraftServer server, Polity polity) {
        if (server == null || polity == null) return 100;
        SettlementData settlements = SettlementData.get(server.overworld());
        UUID rulerId = null;
        if ("independent".equals(polity.kind())) {
            try { rulerId = UUID.fromString(polity.id()); } catch (IllegalArgumentException ignored) { rulerId = null; }
        } else if (FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(polity.id());
            if (faction != null) rulerId = faction.getTeamLeaderUUID();
        }
        int speed = 100;
        SettlementData.CharacterRecord ruler = rulerId == null ? null : settlements.getCharacter(rulerId);
        if (ruler != null && ruler.isCreated() && ruler.getTraits().stream().anyMatch("scholarly"::equals)) {
            speed += SettlementConfig.SCHOLARLY_RESEARCH_BONUS_PERCENT.get();
        }
        return Math.max(10, Math.min(500, speed));
    }

    public static long remainingSeconds(ResearchData.ActiveResearch active, int speedPercent) {
        if (active == null) return 0L;
        long required = Math.max(1L, active.baseTicks()) * 1000L;
        long leftScaled = Math.max(0L, required - active.progressScaledTicks());
        double effectiveTicks = leftScaled / (1000.0D * Math.max(1, speedPercent) / 100.0D);
        return (long)Math.ceil(effectiveTicks / 20.0D);
    }

    private static Polity resolve(MinecraftServer server, String key) {
        if (key == null) return null;
        if (key.startsWith("faction:")) {
            String id = key.substring("faction:".length());
            if (id.isBlank() || FactionEvents.recruitsFactionManager == null || FactionEvents.recruitsFactionManager.getFactionByStringID(id) == null) return null;
            return new Polity(key, CampaignManager.factionName(id), "faction", id);
        }
        if (key.startsWith("independent:")) {
            String id = key.substring("independent:".length());
            UUID ruler;
            try { ruler = UUID.fromString(id); } catch (IllegalArgumentException exception) { return null; }
            List<SettlementData.SettlementRecord> holdings = SettlementData.get(server.overworld()).getOwnedSettlements(ruler).stream()
                    .filter(value -> value.getFactionTeam().isBlank()).toList();
            if (holdings.isEmpty()) return null;
            return new Polity(key, holdings.get(0).getName() + " Independent Polity", "independent", id);
        }
        return null;
    }

    private static void grantCompletedToMembers(MinecraftServer server, Polity polity, String stageId) {
        if (!HistoryStagesCompat.installed()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (belongs(player, polity)) HistoryStagesCompat.unlock(player, stageId);
        }
    }

    private static void notifyMembers(MinecraftServer server, Polity polity, String text) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (belongs(player, polity)) player.sendSystemMessage(Component.literal(text));
    }

    private static boolean belongs(ServerPlayer player, Polity polity) {
        if ("faction".equals(polity.kind())) return polity.id().equals(CampaignManager.factionId(player));
        if (!"independent".equals(polity.kind())) return false;
        UUID ruler;
        try { ruler = UUID.fromString(polity.id()); } catch (IllegalArgumentException exception) { return false; }
        return SettlementData.get(player.serverLevel()).getOwnedSettlements(ruler).stream()
                .filter(value -> value.getFactionTeam().isBlank())
                .anyMatch(value -> value.getRole(player.getUUID()) != SettlementData.SettlementRole.OUTSIDER);
    }

    public record Polity(String key, String name, String kind, String id) {}
}
