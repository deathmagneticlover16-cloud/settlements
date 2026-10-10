package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.AreaSelection;
import ee.lastpagans.settlements.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AreaSelectionPacket(String settlementId, AreaSelection.Type type, AreaSelection.Shape shape,
                                  BlockPos first, BlockPos second, int areaIndex) {
    public AreaSelectionPacket(String settlementId, AreaSelection.Type type, AreaSelection.Shape shape,
                               BlockPos first, BlockPos second) { this(settlementId, type, shape, first, second, -1); }
    public static void encode(AreaSelectionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.settlementId == null ? "" : packet.settlementId, 64);
        buffer.writeEnum(packet.type);
        buffer.writeEnum(packet.shape);
        buffer.writeBlockPos(packet.first);
        buffer.writeBlockPos(packet.second);
        buffer.writeInt(packet.areaIndex);
    }

    public static AreaSelectionPacket decode(FriendlyByteBuf buffer) {
        return new AreaSelectionPacket(buffer.readUtf(64), buffer.readEnum(AreaSelection.Type.class),
                buffer.readEnum(AreaSelection.Shape.class), buffer.readBlockPos(), buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(AreaSelectionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> SettlementManager.handleAreaSelection(sender, packet));
        context.setPacketHandled(true);
    }
}
