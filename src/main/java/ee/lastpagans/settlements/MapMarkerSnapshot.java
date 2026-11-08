package ee.lastpagans.settlements;

import ee.lastpagans.settlements.network.PacketValidation;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public record MapMarkerSnapshot(List<Marker> markers) {
    public MapMarkerSnapshot {
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public static MapMarkerSnapshot from(ServerLevel level) {
        SettlementData settlements = SettlementData.get(level);
        CampaignData campaign = CampaignData.get(level);
        List<Marker> values = new ArrayList<>();
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            if (values.size() >= 2048) break;
            if (!settlement.hasTownSquare()) continue;
            String factionName = settlement.getFactionTeam().isBlank() ? "Independent" : settlement.getFactionTeam();
            if (!settlement.getFactionTeam().isBlank() && FactionEvents.recruitsFactionManager != null) {
                RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(settlement.getFactionTeam());
                if (faction != null) factionName = faction.getTeamDisplayName();
            }
            CampaignData.SettlementVisualRecord visual = campaign.visual(settlement.getId());
            CampaignData.FactionProfile profile = campaign.factionProfileIfPresent(settlement.getFactionTeam());
            values.add(new Marker(
                    settlement.getId(),
                    settlement.getName(),
                    settlement.getTownSquareDimension(),
                    settlement.getTownSquare().getX(),
                    settlement.getTownSquare().getZ(),
                    settlement.getRank().name(),
                    settlement.isCapital(),
                    settlement.getFactionTeam(),
                    factionName,
                    settlement.getDisplayedPopulation(),
                    settlement.getStability(),
                    visual == null ? CampaignData.BannerMode.FACTION.name() : visual.getBannerMode().name(),
                    FactionColorUtil.rgb(FactionColorUtil.dye(profile == null ? "white" : profile.teamDye()))
            ));
        }
        return new MapMarkerSnapshot(values);
    }

    public void encode(FriendlyByteBuf buffer) {
        int count = Math.min(2048, markers.size());
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) markers.get(i).encode(buffer);
    }

    public static MapMarkerSnapshot decode(FriendlyByteBuf buffer) {
        int count = PacketValidation.readCount(buffer, 2048, "map marker");
        List<Marker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) markers.add(Marker.decode(buffer));
        return new MapMarkerSnapshot(markers);
    }

    public record Marker(
            String id,
            String name,
            String dimension,
            int x,
            int z,
            String rank,
            boolean capital,
            String factionId,
            String factionName,
            int population,
            int stability,
            String bannerMode,
            int factionColor
    ) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeUtf(name, 64);
            buffer.writeUtf(dimension, 96);
            buffer.writeInt(x);
            buffer.writeInt(z);
            buffer.writeUtf(rank, 16);
            buffer.writeBoolean(capital);
            buffer.writeUtf(factionId, 64);
            buffer.writeUtf(factionName, 64);
            buffer.writeVarInt(population);
            buffer.writeVarInt(stability);
            buffer.writeUtf(bannerMode, 16);
            buffer.writeInt(factionColor);
        }

        private static Marker decode(FriendlyByteBuf buffer) {
            return new Marker(
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readUtf(96),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readUtf(16),
                    buffer.readBoolean(),
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(16),
                    buffer.readInt()
            );
        }
    }
}
