package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.character.CharacterBodyCodec;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.client.wildfire.ClientCharacterBodyState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ApplyCharacterBodyPacket(UUID playerId, CharacterBodyData body) {
    public static void encode(ApplyCharacterBodyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        CharacterBodyCodec.write(buffer, packet.body);
    }

    public static ApplyCharacterBodyPacket decode(FriendlyByteBuf buffer) {
        return new ApplyCharacterBodyPacket(buffer.readUUID(), CharacterBodyCodec.read(buffer));
    }

    public static void handle(ApplyCharacterBodyPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCharacterBodyState.accept(packet.playerId, packet.body)));
        context.setPacketHandled(true);
    }
}
