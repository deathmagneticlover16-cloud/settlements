package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.PrototypeSnapshot;
import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SnapshotPacket(boolean open, PrototypeSnapshot snapshot) {
    public static void encode(SnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.open);
        packet.snapshot.encode(buffer);
    }

    public static SnapshotPacket decode(FriendlyByteBuf buffer) {
        return new SnapshotPacket(buffer.readBoolean(), PrototypeSnapshot.decode(buffer));
    }

    public static void handle(SnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> PrototypeClientHandler.accept(packet)
        ));
        context.setPacketHandled(true);
    }
}
