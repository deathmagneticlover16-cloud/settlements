package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CaptureZoneData;
import ee.lastpagans.settlements.api.CaptureZoneView;
import ee.lastpagans.settlements.client.api.ClientCaptureZoneApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record CaptureZoneSnapshotPacket(List<CaptureZoneView> zones) {
    public static void encode(CaptureZoneSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.zones.size());
        for (CaptureZoneView zone : packet.zones) {
            buffer.writeUUID(zone.id()); buffer.writeUtf(zone.settlementId(), 64); buffer.writeEnum(zone.type()); buffer.writeUtf(zone.dimension(), 128);
            buffer.writeBlockPos(zone.pos1()); buffer.writeBlockPos(zone.pos2()); buffer.writeDouble(zone.progress()); buffer.writeEnum(zone.state()); buffer.writeUtf(zone.attackerTeam(), 64);
        }
    }
    public static CaptureZoneSnapshotPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(256, buffer.readVarInt());
        List<CaptureZoneView> zones = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            UUID id=buffer.readUUID(); String settlement=buffer.readUtf(64); CaptureZoneData.ZoneType type=buffer.readEnum(CaptureZoneData.ZoneType.class); String dimension=buffer.readUtf(128);
            BlockPos p1=buffer.readBlockPos(), p2=buffer.readBlockPos(); double progress=buffer.readDouble(); CaptureZoneData.ZoneState state=buffer.readEnum(CaptureZoneData.ZoneState.class); String attacker=buffer.readUtf(64);
            zones.add(new CaptureZoneView(id, settlement, type, dimension, p1, p2, progress, state, attacker, state==CaptureZoneData.ZoneState.LOCKED));
        }
        return new CaptureZoneSnapshotPacket(List.copyOf(zones));
    }
    public static void handle(CaptureZoneSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context=supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCaptureZoneApi.replace(packet.zones)));
        context.setPacketHandled(true);
    }
}
