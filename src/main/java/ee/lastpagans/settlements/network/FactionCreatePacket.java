package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CampaignManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FactionCreatePacket(String name, String teamDye, String unitDye, String government,
                                  String capitalSettlementId, ItemStack banner) {
    public static void encode(FactionCreatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.name == null ? "" : packet.name, 64);
        buffer.writeUtf(packet.teamDye == null ? "white" : packet.teamDye, 32);
        buffer.writeUtf(packet.unitDye == null ? "white" : packet.unitDye, 32);
        buffer.writeUtf(packet.government == null ? "MONARCHY" : packet.government, 32);
        buffer.writeUtf(packet.capitalSettlementId == null ? "" : packet.capitalSettlementId, 64);
        buffer.writeItem(packet.banner == null ? ItemStack.EMPTY : packet.banner);
    }

    public static FactionCreatePacket decode(FriendlyByteBuf buffer) {
        return new FactionCreatePacket(buffer.readUtf(64), buffer.readUtf(32), buffer.readUtf(32),
                buffer.readUtf(32), buffer.readUtf(64), buffer.readItem());
    }

    public static void handle(FactionCreatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> CampaignManager.createFactionFromGui(player, packet));
        context.setPacketHandled(true);
    }
}
