package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.client.wildfire.ClientCharacterBodyState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record CharacterSubmissionRejectedPacket(UUID sessionId, String message) {
    public static void encode(CharacterSubmissionRejectedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeUtf(packet.message, 512);
    }

    public static CharacterSubmissionRejectedPacket decode(FriendlyByteBuf buffer) {
        return new CharacterSubmissionRejectedPacket(buffer.readUUID(), buffer.readUtf(512));
    }

    public static void handle(CharacterSubmissionRejectedPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCharacterBodyState.reject(packet.sessionId, packet.message)));
        context.setPacketHandled(true);
    }
}
