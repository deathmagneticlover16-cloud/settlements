package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.FamilySnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
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
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SettlementClientHandler.openFamily(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
