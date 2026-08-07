package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class OpenCharacterCreationPacket {
    public static void encode(OpenCharacterCreationPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenCharacterCreationPacket decode(FriendlyByteBuf buffer) {
        return new OpenCharacterCreationPacket();
    }

    public static void handle(OpenCharacterCreationPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> PrototypeClientHandler::openCharacterCreation
        ));
        context.setPacketHandled(true);
    }
}
