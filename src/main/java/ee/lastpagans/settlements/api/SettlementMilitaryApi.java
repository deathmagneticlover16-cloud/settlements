package ee.lastpagans.settlements.api;

import ee.lastpagans.settlements.MilitaryManager;
import ee.lastpagans.settlements.SettlementData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/** Stable integration surface for Garrison; Settlements deliberately performs no barracks AI. */
public final class SettlementMilitaryApi {
    private SettlementMilitaryApi() {}

    public static Optional<BarracksView> barracks(ServerLevel level, String settlementId) {
        if (level == null) return Optional.empty();
        SettlementData.SettlementRecord settlement = SettlementData.get(level).getSettlement(settlementId);
        if (settlement == null || !settlement.hasBarracks() || settlement.getBarracksMin() == null || settlement.getBarracksMax() == null)
            return Optional.empty();
        return Optional.of(new BarracksView(settlement.getId(), settlement.getBarracksDimension(), settlement.getBarracksMin(),
                settlement.getBarracksMax(), settlement.getBarracksChest(), settlement.getBarracksCapacity()));
    }

    /** Lets the campaign-map/Garrison owner report a mutually exclusive world/campaign soldier breakdown. */
    public static void reportDeployment(ServerLevel level, UUID player, int worldSoldiers, int campaignSoldiers) {
        MilitaryManager.reportDeployment(level, player, worldSoldiers, campaignSoldiers);
    }
}
