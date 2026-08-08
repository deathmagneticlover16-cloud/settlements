package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.PrototypeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SubmitCharacterPacket(
        String firstName,
        String middleName,
        String lastName,
        String faith,
        String address,
        String title,
        List<String> traits,
        int rerollRound
) {
    public SubmitCharacterPacket {
        traits = traits == null ? List.of() : List.copyOf(traits);
    }

    public static void encode(SubmitCharacterPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.firstName, 24);
        buffer.writeUtf(packet.middleName, 24);
        buffer.writeUtf(packet.lastName, 24);
        buffer.writeUtf(packet.faith, 32);
        buffer.writeUtf(packet.address, 16);
        buffer.writeUtf(packet.title, 32);
        buffer.writeVarInt(Math.min(3, packet.traits.size()));
        for (int i = 0; i < Math.min(3, packet.traits.size()); i++) buffer.writeUtf(packet.traits.get(i), 32);
        buffer.writeVarInt(packet.rerollRound + 1);
    }

    public static SubmitCharacterPacket decode(FriendlyByteBuf buffer) {
        String first = buffer.readUtf(24);
        String middle = buffer.readUtf(24);
        String last = buffer.readUtf(24);
        String faith = buffer.readUtf(32);
        String address = buffer.readUtf(16);
        String title = buffer.readUtf(32);
        int count = Math.min(3, Math.max(0, buffer.readVarInt()));
        List<String> traits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) traits.add(buffer.readUtf(32));
        return new SubmitCharacterPacket(first, middle, last, faith, address, title, traits, buffer.readVarInt() - 1);
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
                    packet.traits,
                    packet.rerollRound
            ));
        }
        context.setPacketHandled(true);
    }
}
