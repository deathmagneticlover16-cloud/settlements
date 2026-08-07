package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.FactionSnapshot;
import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FactionSnapshotPacket(FactionSnapshot snapshot) {
    public static void encode(FactionSnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static FactionSnapshotPacket decode(FriendlyByteBuf buffer) { return new FactionSnapshotPacket(FactionSnapshot.decode(buffer)); }
    public static void handle(FactionSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> PrototypeClientHandler.openFaction(packet.snapshot));
        context.setPacketHandled(true);
    }
}
