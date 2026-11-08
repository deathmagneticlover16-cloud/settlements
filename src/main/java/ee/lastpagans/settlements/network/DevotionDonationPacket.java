package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CampaignManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Narrow packet for the only player-authorized former family action: a faith donation. */
public record DevotionDonationPacket() {
    public static void encode(DevotionDonationPacket packet, FriendlyByteBuf buffer) { }
    public static DevotionDonationPacket decode(FriendlyByteBuf buffer) { return new DevotionDonationPacket(); }
    public static void handle(DevotionDonationPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (ClientActionLimiter.allow(player, "devotion-donation", 2)) CampaignManager.handleDevotionDonation(player);
        });
        context.setPacketHandled(true);
    }
}
