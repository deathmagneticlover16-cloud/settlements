package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.PrototypeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateCharacterIdentityPacket(String dynasty, String house, boolean returnToMain) {
    public static void encode(UpdateCharacterIdentityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.dynasty, 32);
        buffer.writeUtf(packet.house, 32);
        buffer.writeBoolean(packet.returnToMain);
    }

    public static UpdateCharacterIdentityPacket decode(FriendlyByteBuf buffer) {
        return new UpdateCharacterIdentityPacket(buffer.readUtf(32), buffer.readUtf(32), buffer.readBoolean());
    }

    public static void handle(UpdateCharacterIdentityPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> PrototypeManager.updateCharacterIdentity(sender, packet.dynasty, packet.house, packet.returnToMain));
        }
        context.setPacketHandled(true);
    }
}
