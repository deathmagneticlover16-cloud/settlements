package ee.lastpagans.settlements.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Compact client toast used for major campaign/diplomacy events. */
public record CampaignToastPacket(String title, String body) {
    public static void encode(CampaignToastPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.title == null ? "" : packet.title, 80);
        buffer.writeUtf(packet.body == null ? "" : packet.body, 240);
    }

    public static CampaignToastPacket decode(FriendlyByteBuf buffer) {
        return new CampaignToastPacket(buffer.readUtf(80), buffer.readUtf(240));
    }

    public static void handle(CampaignToastPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal(packet.title), Component.literal(packet.body));
        });
        context.setPacketHandled(true);
    }
}
