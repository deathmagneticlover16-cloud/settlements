package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CampaignManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FactionActionPacket(String action, UUID targetPlayer, int tier, String value) {
    public static void encode(FactionActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action, 48);
        buffer.writeBoolean(packet.targetPlayer != null);
        if (packet.targetPlayer != null) buffer.writeUUID(packet.targetPlayer);
        buffer.writeVarInt(packet.tier);
        buffer.writeUtf(packet.value == null ? "" : packet.value, 256);
    }
    public static FactionActionPacket decode(FriendlyByteBuf buffer) {
        String action = buffer.readUtf(48);
        UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        int tier = buffer.readVarInt();
        String value = buffer.readUtf(256);
        return new FactionActionPacket(action, target, tier, value);
    }
    public static void handle(FactionActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> CampaignManager.handleFactionAction(player, packet.action, packet.targetPlayer, packet.tier, packet.value));
        context.setPacketHandled(true);
    }
}
