package ee.lastpagans.settlementprototype;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record WorldNewsSnapshot(long currentDay, List<Entry> entries) {
    public static WorldNewsSnapshot from(ServerPlayer player) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        PrototypeData settlements = PrototypeData.get(player.serverLevel());
        String factionId = player.getTeam() == null ? "" : player.getTeam().getName();
        boolean admin = player.createCommandSourceStack().hasPermission(2);
        List<Entry> entries = campaign.newsFor(factionId, admin).stream().map(news -> {
            PrototypeData.SettlementRecord settlement = settlements.getSettlement(news.settlementId());
            return new Entry(
                    news.id(), news.category().name(), news.importance().name(), news.headline(), news.body(), news.day(),
                    CampaignManager.factionName(news.factionId()),
                    settlement == null ? "" : settlement.getName(),
                    news.visibility().name()
            );
        }).toList();
        return new WorldNewsSnapshot(player.serverLevel().getDayTime() / 24000L, entries);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(currentDay);
        buffer.writeVarInt(entries.size());
        entries.forEach(entry -> entry.encode(buffer));
    }

    public static WorldNewsSnapshot decode(FriendlyByteBuf buffer) {
        long currentDay = buffer.readLong();
        int count = Math.min(1000, buffer.readVarInt());
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(Entry.decode(buffer));
        return new WorldNewsSnapshot(currentDay, entries);
    }

    public record Entry(UUID id, String category, String importance, String headline, String body,
                        long day, String factionName, String settlementName, String visibility) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(category, 32);
            buffer.writeUtf(importance, 16);
            buffer.writeUtf(headline, 80);
            buffer.writeUtf(body, 240);
            buffer.writeLong(day);
            buffer.writeUtf(factionName, 64);
            buffer.writeUtf(settlementName, 64);
            buffer.writeUtf(visibility, 16);
        }
        private static Entry decode(FriendlyByteBuf buffer) {
            return new Entry(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(16), buffer.readUtf(80),
                    buffer.readUtf(240), buffer.readLong(), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(16));
        }
    }
}
