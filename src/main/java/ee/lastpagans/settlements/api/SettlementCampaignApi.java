package ee.lastpagans.settlements.api;

import ee.lastpagans.settlements.CampaignSystemsData;
import ee.lastpagans.settlements.CaptureZoneData;
import ee.lastpagans.settlements.SettlementData;
import ee.lastpagans.settlements.StateTreasury;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

/** Stable read-only strategic settlement surface for a future Total-War-style campaign map. */
public final class SettlementCampaignApi {
    private SettlementCampaignApi() {}

    public static List<SettlementView> settlements(ServerLevel level) {
        CampaignSystemsData systems = CampaignSystemsData.get(level);
        CaptureZoneData capture = CaptureZoneData.get(level);
        return SettlementData.get(level).getSettlements().stream().map(settlement -> new SettlementView(
                settlement.getId(), settlement.getName(), settlement.getRank().displayName(),
                settlement.getTownSquareDimension(), settlement.getTownSquare(), settlement.getOwnerId(), settlement.isCapital(),
                settlement.getFactionTeam(), settlement.getBackingFactionTeam(), settlement.getDisplayedPopulation(),
                settlement.getStability(), systems.prosperity(settlement.getId()), StateTreasury.balance(level, settlement),
                settlement.getClaimCount(), capture.zonesFor(settlement.getId()).size(), capture.occupyingTeam(settlement.getId())
        )).toList();
    }

    public static SettlementView settlement(ServerLevel level, String settlementId) {
        return settlements(level).stream().filter(value -> value.id().equals(SettlementData.normalizeId(settlementId))).findFirst().orElse(null);
    }

    public record SettlementView(
            String id, String name, String tier, String dimension, BlockPos center, UUID owner, boolean capital,
            String factionId, String backingFactionId, int population, int stability, int prosperity,
            int stateTreasury, int linkedClaims, int captureZones, String occupyingTeam
    ) {}
}
