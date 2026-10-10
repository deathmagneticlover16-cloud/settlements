package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.SettlementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HousingAreaActionPacket(String settlementId, Action action, int areaIndex) {
    public enum Action { OPEN, DELETE }
    public static void encode(HousingAreaActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.settlementId == null ? "" : packet.settlementId, 64);
        buffer.writeEnum(packet.action);
        buffer.writeInt(packet.areaIndex);
    }
    public static HousingAreaActionPacket decode(FriendlyByteBuf buffer) {
        return new HousingAreaActionPacket(buffer.readUtf(64), buffer.readEnum(Action.class), buffer.readInt());
    }
    public static void handle(HousingAreaActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> SettlementManager.handleHousingAreaAction(player, packet));
        context.setPacketHandled(true);
    }
}
