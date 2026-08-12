package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.MercenaryRosterSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MercenaryRosterSnapshotPacket(MercenaryRosterSnapshot snapshot) {
    public static void encode(MercenaryRosterSnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot().encode(buffer); }
    public static MercenaryRosterSnapshotPacket decode(FriendlyByteBuf buffer) { return new MercenaryRosterSnapshotPacket(MercenaryRosterSnapshot.decode(buffer)); }
    public static void handle(MercenaryRosterSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> SettlementClientHandler.openMercenaryRosters(packet.snapshot())); context.setPacketHandled(true);
    }
}
