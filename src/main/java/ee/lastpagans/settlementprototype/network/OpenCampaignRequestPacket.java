package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.CampaignManager;
import ee.lastpagans.settlementprototype.PrototypeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenCampaignRequestPacket(ScreenTarget target) {
    public enum ScreenTarget { MAIN, CHARACTER, SETTLEMENT, FACTION, MILITARY, DIPLOMACY, FAMILY, WORLD_NEWS }

    public static void encode(OpenCampaignRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeEnum(packet.target); }
    public static OpenCampaignRequestPacket decode(FriendlyByteBuf buffer) { return new OpenCampaignRequestPacket(buffer.readEnum(ScreenTarget.class)); }
    public static void handle(OpenCampaignRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            switch (packet.target) {
                case MAIN -> CampaignManager.openMain(player);
                case CHARACTER -> PrototypeManager.openCharacterSheet(player, true);
                case SETTLEMENT -> PrototypeManager.open(player);
                case FACTION -> CampaignManager.openFaction(player, "");
                case MILITARY -> CampaignManager.openMilitary(player, "");
                case DIPLOMACY -> CampaignManager.openDiplomacy(player, "");
                case FAMILY -> CampaignManager.openFamily(player, "");
                case WORLD_NEWS -> CampaignManager.openWorldNews(player);
            }
        });
        context.setPacketHandled(true);
    }
}
