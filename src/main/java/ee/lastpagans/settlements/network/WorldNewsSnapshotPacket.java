package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.WorldNewsSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record WorldNewsSnapshotPacket(WorldNewsSnapshot snapshot) {
    public static void encode(WorldNewsSnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static WorldNewsSnapshotPacket decode(FriendlyByteBuf buffer) { return new WorldNewsSnapshotPacket(WorldNewsSnapshot.decode(buffer)); }
    public static void handle(WorldNewsSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> SettlementClientHandler.openWorldNews(packet.snapshot));
        context.setPacketHandled(true);
    }
}
