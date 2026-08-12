package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.MercenaryRosterManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MercenaryRosterActionPacket(String companyId, String action) {
    public static void encode(MercenaryRosterActionPacket packet, FriendlyByteBuf buffer) { buffer.writeUtf(packet.companyId(), 64); buffer.writeUtf(packet.action(), 32); }
    public static MercenaryRosterActionPacket decode(FriendlyByteBuf buffer) { return new MercenaryRosterActionPacket(buffer.readUtf(64), buffer.readUtf(32)); }
    public static void handle(MercenaryRosterActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> MercenaryRosterManager.handleAction(player, packet.companyId(), packet.action()));
        context.setPacketHandled(true);
    }
}
