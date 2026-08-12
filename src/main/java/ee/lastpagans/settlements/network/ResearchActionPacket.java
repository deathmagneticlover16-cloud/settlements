package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.ResearchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ResearchActionPacket(String action, String stageId) {
    public static void encode(ResearchActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action == null ? "" : packet.action, 32);
        buffer.writeUtf(packet.stageId == null ? "" : packet.stageId, 128);
    }
    public static ResearchActionPacket decode(FriendlyByteBuf buffer) { return new ResearchActionPacket(buffer.readUtf(32), buffer.readUtf(128)); }
    public static void handle(ResearchActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> ResearchManager.handleAction(player, packet.action, packet.stageId));
        context.setPacketHandled(true);
    }
}
