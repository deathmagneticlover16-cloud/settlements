package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CampaignSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CampaignSnapshotPacket(CampaignSnapshot snapshot, OpenCampaignRequestPacket.ScreenTarget landing) {
    public static void encode(CampaignSnapshotPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); buffer.writeEnum(packet.landing); }
    public static CampaignSnapshotPacket decode(FriendlyByteBuf buffer) { return new CampaignSnapshotPacket(CampaignSnapshot.decode(buffer), buffer.readEnum(OpenCampaignRequestPacket.ScreenTarget.class)); }
    public static void handle(CampaignSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SettlementClientHandler.openCampaign(packet.snapshot, packet.landing)));
        context.setPacketHandled(true);
    }
}
