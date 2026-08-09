package ee.lastpagans.settlements;

import ee.lastpagans.settlements.network.CampaignToastPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CampaignToastSender {
    private CampaignToastSender() {}

    public static void send(ServerPlayer player, String title, String body) {
        if (player == null) return;
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CampaignToastPacket(title, body));
    }

    public static void faction(MinecraftServer server, String factionId, String title, String body) {
        if (server == null || factionId == null || factionId.isBlank()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && factionId.equals(player.getTeam().getName())) send(player, title, body);
        }
    }

    public static void factionFounded(ServerPlayer founder, String factionName) {
        send(founder, "FACTION FOUNDED", factionName + " has entered the world campaign.");
    }

    public static void warDeclared(MinecraftServer server, String attackerFaction, String defenderFaction,
                                   String warType, String casusBelli) {
        String attackerName = CampaignManager.factionName(attackerFaction);
        String defenderName = CampaignManager.factionName(defenderFaction);
        faction(server, defenderFaction, "WAR DECLARED",
                attackerName + " has declared " + warType + " on you!\nCasus Belli: " + casusBelli);
        faction(server, attackerFaction, "WAR DECLARED",
                "Your faction has declared " + warType + " on " + defenderName + ".\nCasus Belli: " + casusBelli);
    }

    public static void settlementWar(MinecraftServer server, SettlementData.SettlementRecord attacker,
                                     SettlementData.SettlementRecord defender, String casusBelli) {
        String body = attacker.getName() + " has declared a Settlement War on " + defender.getName()
                + ".\nCasus Belli: " + casusBelli;
        Set<UUID> notified = new HashSet<>();
        notifyFaction(server, defender.getFactionTeam(), "SETTLEMENT WAR", body, notified);
        notifyFaction(server, attacker.getFactionTeam(), "SETTLEMENT WAR", body, notified);
        notifySettlementManagers(server, attacker, "SETTLEMENT WAR", body, notified);
        notifySettlementManagers(server, defender, "SETTLEMENT WAR", body, notified);
    }

    public static void settlementManagers(MinecraftServer server, String settlementId, String title, String body) {
        if (server == null || settlementId == null || settlementId.isBlank()) return;
        SettlementData.SettlementRecord settlement = SettlementData.get(server.overworld()).getSettlement(settlementId);
        if (settlement == null) return;
        notifySettlementManagers(server, settlement, title, body, new HashSet<>());
    }

    private static void notifyFaction(MinecraftServer server, String factionId, String title, String body, Set<UUID> notified) {
        if (server == null || factionId == null || factionId.isBlank()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && factionId.equals(player.getTeam().getName()) && notified.add(player.getUUID())) {
                send(player, title, body);
            }
        }
    }

    private static void notifySettlementManagers(MinecraftServer server, SettlementData.SettlementRecord settlement,
                                                 String title, String body, Set<UUID> notified) {
        if (server == null || settlement == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CampaignManager.canRepresentSettlement(player, settlement) && notified.add(player.getUUID())) send(player, title, body);
        }
    }

    public static void relationChanged(MinecraftServer server, String first, String second, String state) {
        faction(server, first, "DIPLOMACY UPDATED", "Relations with " + CampaignManager.factionName(second) + " are now " + state + ".");
        faction(server, second, "DIPLOMACY UPDATED", "Relations with " + CampaignManager.factionName(first) + " are now " + state + ".");
    }
}
