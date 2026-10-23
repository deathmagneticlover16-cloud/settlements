package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.SettlementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateCharacterIdentityPacket(String firstName, String middleName, String lastName, String house, boolean returnToMain) {
    public static void encode(UpdateCharacterIdentityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.firstName, 24);
        buffer.writeUtf(packet.middleName, 24);
        buffer.writeUtf(packet.lastName, 24);
        buffer.writeUtf(packet.house, 32);
        buffer.writeBoolean(packet.returnToMain);
    }

    public static UpdateCharacterIdentityPacket decode(FriendlyByteBuf buffer) {
        return new UpdateCharacterIdentityPacket(
                buffer.readUtf(24),
                buffer.readUtf(24),
                buffer.readUtf(24),
                buffer.readUtf(32),
                buffer.readBoolean()
        );
    }

    public static void handle(UpdateCharacterIdentityPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> SettlementManager.updateCharacterIdentity(
                    sender, packet.firstName, packet.middleName, packet.lastName, packet.house, packet.returnToMain));
        }
        context.setPacketHandled(true);
    }
}
