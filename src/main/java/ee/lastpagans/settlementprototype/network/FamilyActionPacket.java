package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.CampaignManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record FamilyActionPacket(String action, String value, UUID targetPlayer) {
    public static void encode(FamilyActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action, 32); buffer.writeUtf(packet.value == null ? "" : packet.value, 64);
        buffer.writeBoolean(packet.targetPlayer != null); if (packet.targetPlayer != null) buffer.writeUUID(packet.targetPlayer);
    }
    public static FamilyActionPacket decode(FriendlyByteBuf buffer) {
        String action = buffer.readUtf(32); String value = buffer.readUtf(64); UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        return new FamilyActionPacket(action, value, target);
    }
    public static void handle(FamilyActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> CampaignManager.handleFamilyAction(player, packet.action, packet.value, packet.targetPlayer));
        context.setPacketHandled(true);
    }
}
