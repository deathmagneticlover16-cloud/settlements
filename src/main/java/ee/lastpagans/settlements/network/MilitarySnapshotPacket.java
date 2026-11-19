package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.MilitarySnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MilitarySnapshotPacket(boolean open, MilitarySnapshot snapshot) {
    public static void encode(MilitarySnapshotPacket packet, FriendlyByteBuf buffer) { buffer.writeBoolean(packet.open); packet.snapshot.encode(buffer); }
    public static MilitarySnapshotPacket decode(FriendlyByteBuf buffer) { return new MilitarySnapshotPacket(buffer.readBoolean(), MilitarySnapshot.decode(buffer)); }
    public static void handle(MilitarySnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> SettlementClientHandler.acceptMilitary(packet.open, packet.snapshot));
        context.setPacketHandled(true);
    }
}
