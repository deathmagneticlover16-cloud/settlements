package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.SettlementSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MilitarySnapshotPacket(SettlementSnapshot snapshot) {
    public static void encode(MilitarySnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static MilitarySnapshotPacket decode(FriendlyByteBuf buffer) { return new MilitarySnapshotPacket(SettlementSnapshot.decode(buffer)); }
    public static void handle(MilitarySnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> SettlementClientHandler.openMilitary(packet.snapshot));
        context.setPacketHandled(true);
    }
}
