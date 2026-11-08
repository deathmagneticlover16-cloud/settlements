package ee.lastpagans.settlements;

import ee.lastpagans.settlements.network.PacketValidation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record HousingAreasSnapshot(String settlementId, String settlementName, boolean canManage,
                                   List<Entry> areas, String message) {
    public static HousingAreasSnapshot from(SettlementData.SettlementRecord settlement, CampaignSystemsData systems,
                                            boolean canManage, String message) {
        List<Entry> entries = new ArrayList<>();
        List<CampaignSystemsData.RegionRecord> regions = systems.housingAreas(settlement.getId());
        for (int i = 0; i < regions.size(); i++) {
            CampaignSystemsData.RegionRecord region = regions.get(i);
            entries.add(new Entry(i, region.dimension(), region.min(), region.max()));
        }
        return new HousingAreasSnapshot(settlement.getId(), settlement.getName(), canManage, List.copyOf(entries),
                message == null ? "" : message);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(settlementId, 64);
        buffer.writeUtf(settlementName, 64);
        buffer.writeBoolean(canManage);
        PacketValidation.writeCollection(buffer, areas, CampaignSystemsData.MAX_HOUSING_AREAS, (buf, area) -> area.encode(buf));
        buffer.writeUtf(message, 256);
    }

    public static HousingAreasSnapshot decode(FriendlyByteBuf buffer) {
        String settlementId = buffer.readUtf(64);
        String settlementName = buffer.readUtf(64);
        boolean canManage = buffer.readBoolean();
        int count = PacketValidation.readCount(buffer, CampaignSystemsData.MAX_HOUSING_AREAS, "housing area");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(Entry.decode(buffer));
        return new HousingAreasSnapshot(settlementId, settlementName, canManage, List.copyOf(entries), buffer.readUtf(256));
    }

    public record Entry(int index, String dimension, BlockPos min, BlockPos max) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(index);
            buffer.writeUtf(dimension, 128);
            buffer.writeBlockPos(min);
            buffer.writeBlockPos(max);
        }
        private static Entry decode(FriendlyByteBuf buffer) {
            return new Entry(buffer.readVarInt(), buffer.readUtf(128), buffer.readBlockPos(), buffer.readBlockPos());
        }
        public int width() { return max.getX() - min.getX() + 1; }
        public int height() { return max.getY() - min.getY() + 1; }
        public int depth() { return max.getZ() - min.getZ() + 1; }
    }
}
