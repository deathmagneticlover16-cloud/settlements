package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.MapMarkerSnapshot;
import ee.lastpagans.settlementprototype.client.PrototypeClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MapMarkersPacket(MapMarkerSnapshot snapshot) {
    public static void encode(MapMarkersPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); }
    public static MapMarkersPacket decode(FriendlyByteBuf buffer) { return new MapMarkersPacket(MapMarkerSnapshot.decode(buffer)); }
    public static void handle(MapMarkersPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrototypeClientHandler.cacheMapMarkers(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
