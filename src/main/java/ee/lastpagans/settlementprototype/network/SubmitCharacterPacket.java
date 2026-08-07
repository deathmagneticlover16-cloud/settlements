package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.PrototypeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SubmitCharacterPacket(
        String firstName,
        String middleName,
        String lastName,
        String faith,
        String address,
        String title,
        String dynasty
) {
    public static void encode(SubmitCharacterPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.firstName, 24);
        buffer.writeUtf(packet.middleName, 24);
        buffer.writeUtf(packet.lastName, 24);
        buffer.writeUtf(packet.faith, 32);
        buffer.writeUtf(packet.address, 16);
        buffer.writeUtf(packet.title, 32);
        buffer.writeUtf(packet.dynasty, 32);
    }

    public static SubmitCharacterPacket decode(FriendlyByteBuf buffer) {
        return new SubmitCharacterPacket(
                buffer.readUtf(24),
                buffer.readUtf(24),
                buffer.readUtf(24),
                buffer.readUtf(32),
                buffer.readUtf(16),
                buffer.readUtf(32),
                buffer.readUtf(32)
        );
    }

    public static void handle(SubmitCharacterPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> PrototypeManager.submitCharacter(
                    sender,
                    packet.firstName,
                    packet.middleName,
                    packet.lastName,
                    packet.faith,
                    packet.address,
                    packet.title,
                    packet.dynasty
            ));
        }
        context.setPacketHandled(true);
    }
}
