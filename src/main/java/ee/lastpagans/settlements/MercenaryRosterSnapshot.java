package ee.lastpagans.settlements;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MercenaryRosterSnapshot(String settlementId, String settlementName, String factionName, int availableWealth,
                                      int reliability, List<Entry> entries, String message) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(settlementId, 64); buffer.writeUtf(settlementName, 96); buffer.writeUtf(factionName, 96);
        buffer.writeInt(availableWealth); buffer.writeInt(reliability); buffer.writeVarInt(entries.size());
        for (Entry entry : entries) entry.encode(buffer);
        buffer.writeUtf(message == null ? "" : message, 512);
    }

    public static MercenaryRosterSnapshot decode(FriendlyByteBuf buffer) {
        String settlement = buffer.readUtf(64), settlementName = buffer.readUtf(96), faction = buffer.readUtf(96);
        int wealth = buffer.readInt(), reliability = buffer.readInt();
        int count = Math.min(128, Math.max(0, buffer.readVarInt()));
        List<Entry> entries = new ArrayList<>(count); for (int i = 0; i < count; i++) entries.add(Entry.decode(buffer));
        return new MercenaryRosterSnapshot(settlement, settlementName, faction, wealth, reliability, List.copyOf(entries), buffer.readUtf(512));
    }

    public record Entry(String id, String name, String iconItem, String composition, int size, int hireCost, int effectiveHireCost,
                        int upkeep, int intervalDays, int loyalty, String status, String employer, boolean canHire,
                        boolean yours, String actionLabel, String reason) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64); buffer.writeUtf(name, 96); buffer.writeUtf(iconItem, 128); buffer.writeUtf(composition, 256);
            buffer.writeInt(size); buffer.writeInt(hireCost); buffer.writeInt(effectiveHireCost); buffer.writeInt(upkeep); buffer.writeInt(intervalDays);
            buffer.writeInt(loyalty); buffer.writeUtf(status, 32); buffer.writeUtf(employer, 128); buffer.writeBoolean(canHire); buffer.writeBoolean(yours);
            buffer.writeUtf(actionLabel, 48); buffer.writeUtf(reason, 256);
        }
        private static Entry decode(FriendlyByteBuf buffer) {
            return new Entry(buffer.readUtf(64), buffer.readUtf(96), buffer.readUtf(128), buffer.readUtf(256),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readUtf(32), buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(48), buffer.readUtf(256));
        }
    }
}
