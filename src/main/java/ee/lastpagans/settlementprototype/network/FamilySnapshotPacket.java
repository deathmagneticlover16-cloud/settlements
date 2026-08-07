package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.FamilySnapshot;
import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record FamilySnapshotPacket(FamilySnapshot snapshot) {
    public static void encode(FamilySnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static FamilySnapshotPacket decode(FriendlyByteBuf buffer) { return new FamilySnapshotPacket(FamilySnapshot.decode(buffer)); }
    public static void handle(FamilySnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrototypeClientHandler.openFamily(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
