package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.MercenaryRosterSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MercenaryRosterSnapshotPacket(boolean open, MercenaryRosterSnapshot snapshot) {
    public static void encode(MercenaryRosterSnapshotPacket packet, FriendlyByteBuf buffer) { buffer.writeBoolean(packet.open); packet.snapshot().encode(buffer); }
    public static MercenaryRosterSnapshotPacket decode(FriendlyByteBuf buffer) { return new MercenaryRosterSnapshotPacket(buffer.readBoolean(), MercenaryRosterSnapshot.decode(buffer)); }
    public static void handle(MercenaryRosterSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> SettlementClientHandler.acceptMercenaryRosters(packet.open, packet.snapshot())); context.setPacketHandled(true);
    }
}
