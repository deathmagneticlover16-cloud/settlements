package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.PrototypeSnapshot;
import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MilitarySnapshotPacket(PrototypeSnapshot snapshot) {
    public static void encode(MilitarySnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static MilitarySnapshotPacket decode(FriendlyByteBuf buffer) { return new MilitarySnapshotPacket(PrototypeSnapshot.decode(buffer)); }
    public static void handle(MilitarySnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> PrototypeClientHandler.openMilitary(packet.snapshot));
        context.setPacketHandled(true);
    }
}
