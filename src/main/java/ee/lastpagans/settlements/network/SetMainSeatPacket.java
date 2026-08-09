package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.SettlementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetMainSeatPacket(String settlementId, boolean returnToMain) {
    public static void encode(SetMainSeatPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.settlementId == null ? "" : packet.settlementId, 64);
        buffer.writeBoolean(packet.returnToMain);
    }

    public static SetMainSeatPacket decode(FriendlyByteBuf buffer) {
        return new SetMainSeatPacket(buffer.readUtf(64), buffer.readBoolean());
    }

    public static void handle(SetMainSeatPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> SettlementManager.setMainSeatFromGui(player, packet.settlementId, packet.returnToMain));
        }
        context.setPacketHandled(true);
    }
}
