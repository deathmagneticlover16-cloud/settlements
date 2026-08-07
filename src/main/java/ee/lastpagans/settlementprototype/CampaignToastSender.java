package ee.lastpagans.settlementprototype;

import ee.lastpagans.settlementprototype.network.CampaignToastPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class CampaignToastSender {
    private CampaignToastSender() {}

    public static void send(ServerPlayer player, String title, String body) {
        if (player == null) return;
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
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

    public static void settlementWar(MinecraftServer server, PrototypeData.SettlementRecord attacker,
                                     PrototypeData.SettlementRecord defender, String casusBelli) {
        String body = attacker.getName() + " has declared a Settlement War on " + defender.getName()
                + ".\nCasus Belli: " + casusBelli;
        faction(server, defender.getFactionTeam(), "SETTLEMENT WAR", body);
        faction(server, attacker.getFactionTeam(), "SETTLEMENT WAR", body);
    }

    public static void relationChanged(MinecraftServer server, String first, String second, String state) {
        faction(server, first, "DIPLOMACY UPDATED", "Relations with " + CampaignManager.factionName(second) + " are now " + state + ".");
        faction(server, second, "DIPLOMACY UPDATED", "Relations with " + CampaignManager.factionName(first) + " are now " + state + ".");
    }
}
