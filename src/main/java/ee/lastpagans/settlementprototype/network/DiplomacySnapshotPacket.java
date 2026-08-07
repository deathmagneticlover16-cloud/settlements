package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.DiplomacySnapshot;
import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record DiplomacySnapshotPacket(DiplomacySnapshot snapshot) {
    public static void encode(DiplomacySnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static DiplomacySnapshotPacket decode(FriendlyByteBuf buffer) { return new DiplomacySnapshotPacket(DiplomacySnapshot.decode(buffer)); }
    public static void handle(DiplomacySnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrototypeClientHandler.openDiplomacy(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
