package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.FactionSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FactionSnapshotPacket(FactionSnapshot snapshot) {
    public static void encode(FactionSnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static FactionSnapshotPacket decode(FriendlyByteBuf buffer) { return new FactionSnapshotPacket(FactionSnapshot.decode(buffer)); }
    public static void handle(FactionSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> SettlementClientHandler.openFaction(packet.snapshot));
        context.setPacketHandled(true);
    }
}
