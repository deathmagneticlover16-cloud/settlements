package ee.lastpagans.settlements;

import net.minecraft.server.level.ServerLevel;

/** One spendable treasury per political state. Settlements generate revenue into it instead of owning separate wallets. */
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
        migrateLegacy(level, settlement);
        String key = key(settlement);
        return key.isBlank() ? 0 : CampaignSystemsData.get(level).factionWealth(key);
    }

    public static void set(ServerLevel level, SettlementData.SettlementRecord settlement, int amount) {
        if (level == null || settlement == null) return;
        migrateLegacy(level, settlement);
        String key = key(settlement);
        if (!key.isBlank()) CampaignSystemsData.get(level).setFactionWealth(key, Math.max(0, amount));
    }

    public static void change(ServerLevel level, SettlementData.SettlementRecord settlement, int amount) {
        if (level == null || settlement == null || amount == 0) return;
        migrateLegacy(level, settlement);
        String key = key(settlement);
        if (!key.isBlank()) CampaignSystemsData.get(level).changeFactionWealth(key, amount);
    }

    public static boolean spend(ServerLevel level, SettlementData.SettlementRecord settlement, int amount) {
        if (level == null || settlement == null) return false;
        migrateLegacy(level, settlement);
        String key = key(settlement);
        return !key.isBlank() && CampaignSystemsData.get(level).spendFactionWealth(key, Math.max(0, amount));
    }

    /** Transitional cleanup: any old settlement-local Wealth is absorbed into the state treasury once, then zeroed. */
    public static void migrateLegacy(ServerLevel level, SettlementData.SettlementRecord settlement) {
        if (level == null || settlement == null || settlement.getWealth() <= 0) return;
        String key = key(settlement);
        if (key.isBlank()) return;
        int legacy = settlement.getWealth();
        CampaignSystemsData.get(level).changeFactionWealth(key, legacy);
        settlement.setWealth(0);
        settlement.addChronicle(legacy + " legacy settlement Wealth was consolidated into the State Treasury.");
    }

    public static void migrateAll(ServerLevel level) {
        if (level == null) return;
        for (SettlementData.SettlementRecord settlement : SettlementData.get(level).getSettlements()) migrateLegacy(level, settlement);
    }
}
