package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CampaignManager;
import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.ResearchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenCampaignRequestPacket(ScreenTarget target) {
    public enum ScreenTarget { MAIN, CHARACTER, SETTLEMENT, FACTION, MILITARY, DIPLOMACY, FAMILY, WORLD_NEWS, RESEARCH }

    public static void encode(OpenCampaignRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeEnum(packet.target); }
    public static OpenCampaignRequestPacket decode(FriendlyByteBuf buffer) { return new OpenCampaignRequestPacket(buffer.readEnum(ScreenTarget.class)); }
    public static void handle(OpenCampaignRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            switch (packet.target) {
                case MAIN -> CampaignManager.openMain(player);
                case CHARACTER -> SettlementManager.openCharacterSheet(player, true);
                case SETTLEMENT -> SettlementManager.open(player);
                case FACTION -> CampaignManager.openFaction(player, "");
                case MILITARY -> CampaignManager.openMilitary(player, "");
                case DIPLOMACY -> CampaignManager.openDiplomacy(player, "");
                case FAMILY -> CampaignManager.openFamily(player, "");
                case WORLD_NEWS -> CampaignManager.openWorldNews(player);
                case RESEARCH -> ResearchManager.open(player, "");
            }
        });
        context.setPacketHandled(true);
    }
}
