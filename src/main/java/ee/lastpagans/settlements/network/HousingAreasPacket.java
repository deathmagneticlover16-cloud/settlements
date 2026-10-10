package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.HousingAreasSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HousingAreasPacket(boolean open, HousingAreasSnapshot snapshot) {
    public static void encode(HousingAreasPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.open);
        packet.snapshot.encode(buffer);
    }
    public static HousingAreasPacket decode(FriendlyByteBuf buffer) {
        return new HousingAreasPacket(buffer.readBoolean(), HousingAreasSnapshot.decode(buffer));
    }
    public static void handle(HousingAreasPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SettlementClientHandler.acceptHousingAreas(packet)));
        context.setPacketHandled(true);
    }
}
