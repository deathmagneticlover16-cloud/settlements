package ee.lastpagans.settlements;

import net.minecraft.server.level.ServerLevel;

/** The single spendable money pot for a political state. */
public final class StateTreasury {
    private StateTreasury() {}

    public static String key(SettlementData.SettlementRecord settlement) {
        if (settlement == null) return "";
        String faction = settlement.getFactionTeam();
        if (faction != null && !faction.isBlank()) return faction;
        String backing = settlement.getBackingFactionTeam();
        return backing == null ? "" : backing;
    }

    public static int balance(ServerLevel level, SettlementData.SettlementRecord settlement) {
        if (level == null || settlement == null) return 0;
        String key = key(settlement);
        return key.isBlank() ? 0 : CampaignSystemsData.get(level).treasuryBalance(key);
    }

    public static void set(ServerLevel level, SettlementData.SettlementRecord settlement, int amount) {
        if (level == null || settlement == null) return;
        String key = key(settlement);
        if (!key.isBlank()) CampaignSystemsData.get(level).setTreasuryBalance(key, Math.max(0, amount));
    }

    public static void change(ServerLevel level, SettlementData.SettlementRecord settlement, int amount) {
        if (level == null || settlement == null || amount == 0) return;
        String key = key(settlement);
        if (!key.isBlank()) CampaignSystemsData.get(level).changeTreasury(key, amount);
    }

    public static boolean spend(ServerLevel level, SettlementData.SettlementRecord settlement, int amount) {
        if (level == null || settlement == null) return false;
        String key = key(settlement);
        return !key.isBlank() && CampaignSystemsData.get(level).spendTreasury(key, Math.max(0, amount));
    }
}
