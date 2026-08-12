package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.ResearchSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ResearchSnapshotPacket(ResearchSnapshot snapshot) {
    public static void encode(ResearchSnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static ResearchSnapshotPacket decode(FriendlyByteBuf buffer) { return new ResearchSnapshotPacket(ResearchSnapshot.decode(buffer)); }
    public static void handle(ResearchSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SettlementClientHandler.openResearch(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
