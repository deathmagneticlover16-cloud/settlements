package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.client.wildfire.ClientCharacterBodyState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ClearCharacterBodyPacket(UUID playerId) {
    public static void encode(ClearCharacterBodyPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.playerId); }
    public static ClearCharacterBodyPacket decode(FriendlyByteBuf buffer) { return new ClearCharacterBodyPacket(buffer.readUUID()); }
    public static void handle(ClearCharacterBodyPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCharacterBodyState.clear(packet.playerId)));
        context.setPacketHandled(true);
    }
}
