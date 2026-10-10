package ee.lastpagans.settlements;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Extended campaign-system state for Settlements.
 */
public final class CampaignSystemsData extends SavedData {
    private static final String DATA_NAME = "settlements_campaign_systems";

    private final Map<String, Integer> factionWealth = new HashMap<>();
    private final Map<String, Integer> marriageAlliances = new HashMap<>();
    private final Map<UUID, Integer> devotion = new HashMap<>();
    private final Map<UUID, Long> lastHolyVisitDay = new HashMap<>();
    private final Map<UUID, Long> lastPrayerDay = new HashMap<>();
    private final Map<UUID, Integer> divorceCount = new HashMap<>();
    private final Map<UUID, Long> lastSettlementFoundingGameTime = new HashMap<>();
    private final Map<String, Integer> prosperity = new HashMap<>();
    /** Settlement tax policy and daily collection history. */
    private final Map<String, Integer> taxPolicies = new HashMap<>();
    private final Map<String, Long> lastTaxDay = new HashMap<>();
    private final Map<String, Integer> lastTaxIncome = new HashMap<>();
    /** Capped settlement memory of repeated real levy service. It grants only modest starting XP. */
    private final Map<String, Integer> levyTradition = new HashMap<>();
    /** Rolling settlement wealth deposits used to calculate Prosperity from actual economic activity. */
    private final Map<String, List<WealthDepositRecord>> wealthDeposits = new HashMap<>();
    /** Temporary manpower awarded by peace terms. It is recruitable, but never raises a settlement's native MP ceiling. */
    private final Map<String, Integer> foreignManpower = new HashMap<>();
    private final Map<String, Long> debugTimeOffsetMillis = new HashMap<>();
    private final Map<String, Long> lastModifierDay = new HashMap<>();
    private final Map<String, Boolean> protectedSettlements = new HashMap<>();
    private final Map<String, Boolean> protectedFactions = new HashMap<>();
    private final Map<String, List<RegionRecord>> housingAreas = new HashMap<>();
    private final Map<String, RegionRecord> musterAreas = new HashMap<>();
    private final Map<String, RegionRecord> prayerSites = new HashMap<>();
    private final List<HolySiteRecord> holySites = new ArrayList<>();
    private final Map<String, List<ModifierRecord>> settlementModifiers = new HashMap<>();
    private final Map<UUID, LevyMobilization> levyMobilizations = new LinkedHashMap<>();
    private final Map<UUID, LevyDeployment> levyDeployments = new LinkedHashMap<>();
    private final Map<UUID, Long> levyCooldownUntil = new HashMap<>();
    private final Map<UUID, PeaceOfferRecord> peaceOffers = new LinkedHashMap<>();
    private final Map<UUID, List<AidRecord>> aidByWar = new HashMap<>();
    private final Map<UUID, Integer> warScore = new HashMap<>();
    private final Map<UUID, List<String>> ignoredWarInterventions = new HashMap<>();
    private final Map<UUID, CandidateHistory> candidateHistory = new HashMap<>();
    private final Map<String, Long> forcedNapUntilDay = new HashMap<>();
    private final List<ReparationRecord> reparations = new ArrayList<>();
    private final Map<UUID, MercenaryContractRecord> mercenaryContracts = new LinkedHashMap<>();
    private final List<FactionInvitationRecord> factionInvitations = new ArrayList<>();
    /** Levy entities that were unloaded when their levy was returned. They are discarded on their next load. */
    private final Set<UUID> retiredLevyUnits = new HashSet<>();

    public static CampaignSystemsData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(CampaignSystemsData::load, CampaignSystemsData::new, DATA_NAME);
    }

    public static CampaignSystemsData load(CompoundTag tag) {
        CampaignSystemsData data = new CampaignSystemsData();
        readStringIntMap(tag, "FactionWealth", data.factionWealth);
        readStringIntMap(tag, "MarriageAlliances", data.marriageAlliances);
        readUuidIntMap(tag, "Devotion", data.devotion);
        readUuidLongMap(tag, "LastHolyVisitDay", data.lastHolyVisitDay);
        readUuidLongMap(tag, "LastPrayerDay", data.lastPrayerDay);
        readUuidIntMap(tag, "DivorceCount", data.divorceCount);
        readUuidLongMap(tag, "LastSettlementFoundingGameTime", data.lastSettlementFoundingGameTime);
        readStringIntMap(tag, "Prosperity", data.prosperity);
        readStringIntMap(tag, "TaxPolicies", data.taxPolicies);
        readStringLongMap(tag, "LastTaxDay", data.lastTaxDay);
        readStringIntMap(tag, "LastTaxIncome", data.lastTaxIncome);
        readStringIntMap(tag, "LevyTradition", data.levyTradition);
        for (Tag raw : tag.getList("WealthDeposits", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            if (settlement.isBlank()) continue;
            List<WealthDepositRecord> samples = new ArrayList<>();
            for (Tag sampleRaw : entry.getList("Samples", Tag.TAG_COMPOUND)) {
                WealthDepositRecord sample = WealthDepositRecord.fromNBT((CompoundTag) sampleRaw);
                if (sample.amount() > 0) samples.add(sample);
            }
            if (!samples.isEmpty()) data.wealthDeposits.put(settlement, samples);
        }
        readStringIntMap(tag, "ForeignManpower", data.foreignManpower);
        readStringLongMap(tag, "DebugOffsets", data.debugTimeOffsetMillis);
        readStringLongMap(tag, "LastModifierDay", data.lastModifierDay);
        readStringBooleanMap(tag, "ProtectedSettlements", data.protectedSettlements);
        readStringBooleanMap(tag, "ProtectedFactions", data.protectedFactions);

        for (Tag raw : tag.getList("HousingAreas", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            if (settlement.isBlank()) continue;
            List<RegionRecord> regions = new ArrayList<>();
            for (Tag regionRaw : entry.getList("Regions", Tag.TAG_COMPOUND)) {
                RegionRecord region = RegionRecord.fromNBT((CompoundTag) regionRaw);
                if (region.valid()) regions.add(new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX));
            }
            if (!regions.isEmpty()) data.housingAreas.put(settlement, regions);
        }
        for (Tag raw : tag.getList("MusterAreas", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            RegionRecord region = RegionRecord.fromNBT(entry.getCompound("Region"));
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            if (!settlement.isBlank() && region.valid()) data.musterAreas.put(settlement,
                    new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX));
        }
        for (Tag raw : tag.getList("PrayerSites", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            RegionRecord region = RegionRecord.fromNBT(entry.getCompound("Region"));
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            if (!settlement.isBlank() && region.valid()) data.prayerSites.put(settlement,
                    new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX));
        }
        for (Tag raw : tag.getList("HolySites", Tag.TAG_COMPOUND)) {
            HolySiteRecord site = HolySiteRecord.fromNBT((CompoundTag) raw);
            if (site.region.valid() && !site.faith.isBlank()) data.holySites.add(site);
        }
        for (Tag raw : tag.getList("Modifiers", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            if (settlement.isBlank()) continue;
            List<ModifierRecord> values = new ArrayList<>();
            for (Tag modifierRaw : entry.getList("Values", Tag.TAG_COMPOUND)) {
                ModifierRecord value = ModifierRecord.fromNBT((CompoundTag) modifierRaw);
                if (!value.id.isBlank()) values.add(value);
            }
            if (!values.isEmpty()) data.settlementModifiers.put(settlement, values);
        }
        for (Tag raw : tag.getList("LevyMobilizations", Tag.TAG_COMPOUND)) {
            LevyMobilization value = LevyMobilization.fromNBT((CompoundTag) raw);
            if (value.id != null) data.levyMobilizations.put(value.id, value);
        }
        for (Tag raw : tag.getList("LevyDeployments", Tag.TAG_COMPOUND)) {
            LevyDeployment value = LevyDeployment.fromNBT((CompoundTag) raw);
            if (value.batchId != null) data.levyDeployments.put(value.batchId, value);
        }
        readUuidLongMap(tag, "LevyCooldowns", data.levyCooldownUntil);
        for (Tag raw : tag.getList("PeaceOffers", Tag.TAG_COMPOUND)) {
            PeaceOfferRecord value = PeaceOfferRecord.fromNBT((CompoundTag) raw);
            if (value.proposalId != null) data.peaceOffers.put(value.proposalId, value);
        }
        for (Tag raw : tag.getList("WarAid", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("War")) continue;
            UUID war = entry.getUUID("War");
            List<AidRecord> values = new ArrayList<>();
            for (Tag aidRaw : entry.getList("Values", Tag.TAG_COMPOUND)) values.add(AidRecord.fromNBT((CompoundTag) aidRaw));
            if (!values.isEmpty()) data.aidByWar.put(war, values);
        }
        readUuidIntMap(tag, "WarScore", data.warScore);
        for (Tag raw : tag.getList("IgnoredWarInterventions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("War")) continue;
            List<String> factions = new ArrayList<>();
            for (Tag factionRaw : entry.getList("Factions", Tag.TAG_STRING)) factions.add(factionRaw.getAsString());
            if (!factions.isEmpty()) data.ignoredWarInterventions.put(entry.getUUID("War"), factions);
        }
        for (Tag raw : tag.getList("CandidateHistory", Tag.TAG_COMPOUND)) {
            CandidateHistory history = CandidateHistory.fromNBT((CompoundTag) raw);
            if (history.playerId != null) data.candidateHistory.put(history.playerId, history);
        }
        readStringLongMap(tag, "ForcedNapUntil", data.forcedNapUntilDay);
        for (Tag raw : tag.getList("Reparations", Tag.TAG_COMPOUND)) {
            ReparationRecord value = ReparationRecord.fromNBT((CompoundTag) raw);
            if (value.id() != null && !value.payerFaction().isBlank() && !value.receiverFaction().isBlank()) data.reparations.add(value);
        }
        for (Tag raw : tag.getList("MercenaryContracts", Tag.TAG_COMPOUND)) {
            MercenaryContractRecord value = MercenaryContractRecord.fromNBT((CompoundTag) raw);
            if (value.unitId() != null && !value.settlementId().isBlank()) data.mercenaryContracts.put(value.unitId(), value);
        }
        for (Tag raw : tag.getList("FactionInvitations", Tag.TAG_COMPOUND)) {
            FactionInvitationRecord value = FactionInvitationRecord.fromNBT((CompoundTag) raw);
            if (value.id() != null && value.targetPlayer() != null && !value.factionId().isBlank()) data.factionInvitations.add(value);
        }
        for (Tag raw : tag.getList("RetiredLevyUnits", Tag.TAG_INT_ARRAY)) {
            try { data.retiredLevyUnits.add(net.minecraft.nbt.NbtUtils.loadUUID(raw)); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        writeStringIntMap(tag, "FactionWealth", factionWealth);
        writeStringIntMap(tag, "MarriageAlliances", marriageAlliances);
        writeUuidIntMap(tag, "Devotion", devotion);
        writeUuidLongMap(tag, "LastHolyVisitDay", lastHolyVisitDay);
        writeUuidLongMap(tag, "LastPrayerDay", lastPrayerDay);
        writeUuidIntMap(tag, "DivorceCount", divorceCount);
        writeUuidLongMap(tag, "LastSettlementFoundingGameTime", lastSettlementFoundingGameTime);
        writeStringIntMap(tag, "Prosperity", prosperity);
        writeStringIntMap(tag, "TaxPolicies", taxPolicies);
        writeStringLongMap(tag, "LastTaxDay", lastTaxDay);
        writeStringIntMap(tag, "LastTaxIncome", lastTaxIncome);
        writeStringIntMap(tag, "LevyTradition", levyTradition);
        ListTag wealthDepositTag = new ListTag();
        wealthDeposits.forEach((settlement, samples) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("Settlement", settlement);
            ListTag list = new ListTag(); samples.forEach(sample -> list.add(sample.toNBT()));
            entry.put("Samples", list); wealthDepositTag.add(entry);
        });
        tag.put("WealthDeposits", wealthDepositTag);
        writeStringIntMap(tag, "ForeignManpower", foreignManpower);
        writeStringLongMap(tag, "DebugOffsets", debugTimeOffsetMillis);
        writeStringLongMap(tag, "LastModifierDay", lastModifierDay);
        writeStringBooleanMap(tag, "ProtectedSettlements", protectedSettlements);
        writeStringBooleanMap(tag, "ProtectedFactions", protectedFactions);

        ListTag housing = new ListTag();
        housingAreas.forEach((settlement, regions) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("Settlement", settlement);
            ListTag list = new ListTag(); regions.forEach(region -> list.add(region.toNBT())); entry.put("Regions", list); housing.add(entry);
        }); tag.put("HousingAreas", housing);
        ListTag muster = new ListTag();
        musterAreas.forEach((settlement, region) -> { CompoundTag entry = new CompoundTag(); entry.putString("Settlement", settlement); entry.put("Region", region.toNBT()); muster.add(entry); });
        tag.put("MusterAreas", muster);
        ListTag prayer = new ListTag();
        prayerSites.forEach((settlement, region) -> { CompoundTag entry = new CompoundTag(); entry.putString("Settlement", settlement); entry.put("Region", region.toNBT()); prayer.add(entry); });
        tag.put("PrayerSites", prayer);
        ListTag holy = new ListTag(); holySites.forEach(site -> holy.add(site.toNBT())); tag.put("HolySites", holy);
        ListTag modifiers = new ListTag();
        settlementModifiers.forEach((settlement, values) -> { CompoundTag entry = new CompoundTag(); entry.putString("Settlement", settlement); ListTag list = new ListTag(); values.forEach(value -> list.add(value.toNBT())); entry.put("Values", list); modifiers.add(entry); });
        tag.put("Modifiers", modifiers);
        ListTag mobilizations = new ListTag(); levyMobilizations.values().forEach(value -> mobilizations.add(value.toNBT())); tag.put("LevyMobilizations", mobilizations);
        ListTag deployments = new ListTag(); levyDeployments.values().forEach(value -> deployments.add(value.toNBT())); tag.put("LevyDeployments", deployments);
        writeUuidLongMap(tag, "LevyCooldowns", levyCooldownUntil);
        ListTag peace = new ListTag(); peaceOffers.values().forEach(value -> peace.add(value.toNBT())); tag.put("PeaceOffers", peace);
        ListTag aid = new ListTag();
        aidByWar.forEach((war, values) -> { CompoundTag entry = new CompoundTag(); entry.putUUID("War", war); ListTag list = new ListTag(); values.forEach(value -> list.add(value.toNBT())); entry.put("Values", list); aid.add(entry); });
        tag.put("WarAid", aid);
        writeUuidIntMap(tag, "WarScore", warScore);
        ListTag ignoredInterventions = new ListTag();
        ignoredWarInterventions.forEach((war, factions) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("War", war);
            ListTag list = new ListTag(); factions.forEach(faction -> list.add(net.minecraft.nbt.StringTag.valueOf(faction)));
            entry.put("Factions", list); ignoredInterventions.add(entry);
        });
        tag.put("IgnoredWarInterventions", ignoredInterventions);
        ListTag histories = new ListTag(); candidateHistory.values().forEach(value -> histories.add(value.toNBT())); tag.put("CandidateHistory", histories);
        writeStringLongMap(tag, "ForcedNapUntil", forcedNapUntilDay);
        ListTag reparationsTag = new ListTag(); reparations.forEach(value -> reparationsTag.add(value.toNBT())); tag.put("Reparations", reparationsTag);
        ListTag mercenaryTag = new ListTag(); mercenaryContracts.values().forEach(value -> mercenaryTag.add(value.toNBT())); tag.put("MercenaryContracts", mercenaryTag);
        ListTag invitationTag = new ListTag(); factionInvitations.forEach(value -> invitationTag.add(value.toNBT())); tag.put("FactionInvitations", invitationTag);
        ListTag retiredLevyTag = new ListTag(); retiredLevyUnits.forEach(value -> retiredLevyTag.add(net.minecraft.nbt.NbtUtils.createUUID(value))); tag.put("RetiredLevyUnits", retiredLevyTag);
        return tag;
    }

    public int factionWealth(String faction) { return factionWealth.getOrDefault(faction == null ? "" : faction, 0); }
    public void setFactionWealth(String faction, int value) { if (faction == null || faction.isBlank()) return; factionWealth.put(faction, Math.max(0, value)); setDirty(); }
    public void changeFactionWealth(String faction, int delta) { setFactionWealth(faction, (int)Math.max(0L, Math.min(Integer.MAX_VALUE, (long)factionWealth(faction) + delta))); }
    public boolean spendFactionWealth(String faction, int amount) { int cost = Math.max(0, amount); if (factionWealth(faction) < cost) return false; setFactionWealth(faction, factionWealth(faction) - cost); return true; }
    public int marriageAllianceCount(String first, String second) { return marriageAlliances.getOrDefault(pairKey(first, second), 0); }
    public boolean marriageAlliance(String first, String second) { return marriageAllianceCount(first, second) > 0; }
    public void addMarriageAlliance(String first, String second) { String key = pairKey(first, second); if (key.isBlank()) return; marriageAlliances.put(key, marriageAlliances.getOrDefault(key, 0) + 1); setDirty(); }
    public void removeMarriageAlliance(String first, String second) { String key = pairKey(first, second); if (key.isBlank()) return; int next = marriageAlliances.getOrDefault(key, 0) - 1; if (next <= 0) marriageAlliances.remove(key); else marriageAlliances.put(key, next); setDirty(); }

    public int devotion(UUID player) { return devotion.getOrDefault(player, 0); }
    public void setDevotion(UUID player, int value) { if (player == null) return; devotion.put(player, clamp(value, 0, 100)); setDirty(); }
    public void changeDevotion(UUID player, int delta) { setDevotion(player, devotion(player) + delta); }
    public long lastHolyVisitDay(UUID player) { return lastHolyVisitDay.getOrDefault(player, Long.MIN_VALUE / 4); }
    public long lastPrayerDay(UUID player) { return lastPrayerDay.getOrDefault(player, Long.MIN_VALUE / 4); }
    public void markHolyVisit(UUID player, long day) { if (player != null) { lastHolyVisitDay.put(player, day); setDirty(); } }
    public void markPrayer(UUID player, long day) { if (player != null) { lastPrayerDay.put(player, day); setDirty(); } }
    public int divorceCount(UUID player) { return divorceCount.getOrDefault(player, 0); }
    public long lastSettlementFoundingGameTime(UUID player) { return player == null ? Long.MIN_VALUE / 4 : lastSettlementFoundingGameTime.getOrDefault(player, Long.MIN_VALUE / 4); }
    public void markSettlementFounded(UUID player, long gameTime) { if (player != null) { lastSettlementFoundingGameTime.put(player, Math.max(0L, gameTime)); setDirty(); } }
    public int incrementDivorceCount(UUID player) { if (player == null) return 0; int value = divorceCount(player) + 1; divorceCount.put(player, value); setDirty(); return value; }

    public int prosperity(String settlement) { return prosperity.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public void setProsperity(String settlement, int value) { String id = SettlementData.normalizeId(settlement); if (id.isBlank()) return; prosperity.put(id, clamp(value, 0, 100)); setDirty(); }

    /** Continuous taxation policy from 50% (low) through 100% (normal) to 150% (high). */
    public int taxRatePercent(String settlement) {
        String id = SettlementData.normalizeId(settlement);
        return clamp(taxPolicies.getOrDefault(id, 100), 50, 150);
    }
    public void setTaxRatePercent(String settlement, int percent) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank()) return;
        int safe = clamp(percent, 50, 150);
        if (safe == 100) taxPolicies.remove(id); else taxPolicies.put(id, safe);
        setDirty();
    }
    public String taxDisplayName(String settlement) {
        int rate = taxRatePercent(settlement);
        if (rate <= 75) return "Low";
        if (rate >= 125) return "High";
        return "Normal";
    }
    public int taxStabilityPerDay(String settlement) {
        int rate = taxRatePercent(settlement);
        if (rate <= 70) return 1;
        if (rate >= 140) return -2;
        if (rate >= 120) return -1;
        return 0;
    }
    public int taxProsperityModifier(String settlement) {
        return clamp((int)Math.round((100 - taxRatePercent(settlement)) / 10.0D), -5, 5);
    }
    /** Legacy helper retained for old call sites while the frontend migrates to the slider. */
    public TaxPolicy taxPolicy(String settlement) {
        int rate = taxRatePercent(settlement);
        return rate <= 75 ? TaxPolicy.LOW : rate >= 125 ? TaxPolicy.HIGH : TaxPolicy.NORMAL;
    }
    public void setTaxPolicy(String settlement, TaxPolicy policy) {
        TaxPolicy safe = policy == null ? TaxPolicy.NORMAL : policy;
        setTaxRatePercent(settlement, safe.ratePercent());
    }
    public boolean beginTaxDay(String settlement, long day) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank() || lastTaxDay.getOrDefault(id, Long.MIN_VALUE) == day) return false;
        lastTaxDay.put(id, day);
        setDirty();
        return true;
    }
    public int lastTaxIncome(String settlement) { return lastTaxIncome.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public void setLastTaxIncome(String settlement, int income) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank()) return;
        lastTaxIncome.put(id, Math.max(0, income));
        setDirty();
    }
    public int levyTradition(String settlement) { return levyTradition.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public void setLevyTradition(String settlement, int value) { String id = SettlementData.normalizeId(settlement); if (id.isBlank()) return; levyTradition.put(id, clamp(value, 0, 50)); setDirty(); }
    public int changeLevyTradition(String settlement, int delta) { setLevyTradition(settlement, levyTradition(settlement) + delta); return levyTradition(settlement); }
    public void recordWealthDeposit(String settlement, int amount, long day) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank() || amount <= 0) return;
        List<WealthDepositRecord> samples = wealthDeposits.computeIfAbsent(id, ignored -> new ArrayList<>());
        for (int i = 0; i < samples.size(); i++) {
            WealthDepositRecord sample = samples.get(i);
            if (sample.day() == day) {
                samples.set(i, new WealthDepositRecord(day, Math.min(1_000_000, sample.amount() + amount)));
                pruneWealthDeposits(id, day, 30);
                setDirty();
                return;
            }
        }
        samples.add(new WealthDepositRecord(day, Math.min(1_000_000, amount)));
        pruneWealthDeposits(id, day, 30);
        setDirty();
    }
    public int averageWealthDeposit(String settlement, long currentDay, int windowDays) {
        String id = SettlementData.normalizeId(settlement);
        int window = Math.max(1, windowDays);
        long firstDay = currentDay - window + 1L;
        long sum = 0L;
        long earliest = Long.MAX_VALUE;
        for (WealthDepositRecord sample : wealthDeposits.getOrDefault(id, List.of())) {
            if (sample.day() >= firstDay && sample.day() <= currentDay) {
                sum += sample.amount();
                earliest = Math.min(earliest, sample.day());
            }
        }
        if (earliest == Long.MAX_VALUE) return 0;
        int observedDays = (int) Math.max(1L, Math.min(window, currentDay - earliest + 1L));
        return (int) Math.min(Integer.MAX_VALUE, sum / observedDays);
    }
    public boolean hasWealthDepositHistory(String settlement) {
        return !wealthDeposits.getOrDefault(SettlementData.normalizeId(settlement), List.of()).isEmpty();
    }
    private void pruneWealthDeposits(String settlement, long currentDay, int keepDays) {
        List<WealthDepositRecord> samples = wealthDeposits.get(settlement);
        if (samples == null) return;
        long firstDay = currentDay - Math.max(1, keepDays) + 1L;
        samples.removeIf(sample -> sample.day() < firstDay || sample.day() > currentDay + 1L);
        if (samples.isEmpty()) wealthDeposits.remove(settlement);
    }

    public int foreignManpower(String settlement) { return foreignManpower.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public void setForeignManpower(String settlement, int value) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank()) return;
        int safe = Math.max(0, value);
        if (safe == 0) foreignManpower.remove(id); else foreignManpower.put(id, safe);
        setDirty();
    }
    public void addForeignManpower(String settlement, int amount) { if (amount > 0) setForeignManpower(settlement, foreignManpower(settlement) + amount); }
    public boolean spendForeignManpower(String settlement, int amount) {
        int cost = Math.max(0, amount);
        if (foreignManpower(settlement) < cost) return false;
        setForeignManpower(settlement, foreignManpower(settlement) - cost);
        return true;
    }

    public long nowMillis(String settlement) { return System.currentTimeMillis() + debugTimeOffsetMillis.getOrDefault(SettlementData.normalizeId(settlement), 0L); }
    public void advanceDebugTime(String settlement, long millis) { String id = SettlementData.normalizeId(settlement); debugTimeOffsetMillis.put(id, debugTimeOffsetMillis.getOrDefault(id, 0L) + Math.max(0L, millis)); setDirty(); }
    public void clearDebugTime(String settlement) { if (debugTimeOffsetMillis.remove(SettlementData.normalizeId(settlement)) != null) setDirty(); }
    public boolean beginModifierDay(String settlement, long day) {
        String id = SettlementData.normalizeId(settlement);
        if (lastModifierDay.getOrDefault(id, Long.MIN_VALUE) == day) return false;
        lastModifierDay.put(id, day);
        setDirty();
        return true;
    }

    public boolean settlementProtected(String settlement) { return protectedSettlements.getOrDefault(SettlementData.normalizeId(settlement), false); }
    public void setSettlementProtected(String settlement, boolean value) { String id = SettlementData.normalizeId(settlement); if (id.isBlank()) return; protectedSettlements.put(id, value); setDirty(); }
    public boolean factionProtected(String faction) { return protectedFactions.getOrDefault(faction == null ? "" : faction, false); }
    public void setFactionProtected(String faction, boolean value) { if (faction == null || faction.isBlank()) return; protectedFactions.put(faction, value); setDirty(); }

    public List<RegionRecord> housingAreas(String settlement) { return List.copyOf(housingAreas.getOrDefault(SettlementData.normalizeId(settlement), List.of())); }
    public void addHousingArea(String settlement, RegionRecord region) { String id = SettlementData.normalizeId(settlement); if (id.isBlank() || !region.valid()) return; housingAreas.computeIfAbsent(id, key -> new ArrayList<>()).add(new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX)); setDirty(); }
    public boolean replaceHousingArea(String settlement, int index, RegionRecord region) {
        String id = SettlementData.normalizeId(settlement);
        List<RegionRecord> values = housingAreas.get(id);
        if (values == null || index < 0 || index >= values.size() || region == null || !region.valid()) return false;
        values.set(index, new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX));
        setDirty();
        return true;
    }
    public boolean removeHousingArea(String settlement, int index) {
        String id = SettlementData.normalizeId(settlement);
        List<RegionRecord> values = housingAreas.get(id);
        if (values == null || index < 0 || index >= values.size()) return false;
        values.remove(index);
        if (values.isEmpty()) housingAreas.remove(id);
        setDirty();
        return true;
    }
    public void clearHousingAreas(String settlement) { if (housingAreas.remove(SettlementData.normalizeId(settlement)) != null) setDirty(); }
    public RegionRecord musterArea(String settlement) { return musterAreas.get(SettlementData.normalizeId(settlement)); }
    public void setMusterArea(String settlement, RegionRecord region) { String id = SettlementData.normalizeId(settlement); if (id.isBlank()) return; if (region == null || !region.valid()) musterAreas.remove(id); else musterAreas.put(id, new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX)); setDirty(); }
    public RegionRecord prayerSite(String settlement) { return prayerSites.get(SettlementData.normalizeId(settlement)); }
    public void setPrayerSite(String settlement, RegionRecord region) { String id = SettlementData.normalizeId(settlement); if (id.isBlank()) return; if (region == null || !region.valid()) prayerSites.remove(id); else prayerSites.put(id, new RegionRecord(region.dimension(), region.min(), region.max(), AreaSelection.Shape.BOX)); setDirty(); }
    public List<HolySiteRecord> holySites() { return List.copyOf(holySites); }
    public void addHolySite(HolySiteRecord site) { if (site != null && site.region.valid() && !site.faith.isBlank()) { holySites.add(site); setDirty(); } }
    public boolean removeHolySite(String id) { boolean removed = holySites.removeIf(site -> site.id.equalsIgnoreCase(id)); if (removed) setDirty(); return removed; }

    public List<ModifierRecord> modifiers(String settlement) { return List.copyOf(settlementModifiers.getOrDefault(SettlementData.normalizeId(settlement), List.of())); }
    public void addModifier(String settlement, ModifierRecord modifier) { String id = SettlementData.normalizeId(settlement); if (id.isBlank() || modifier == null || modifier.id.isBlank()) return; List<ModifierRecord> list = settlementModifiers.computeIfAbsent(id, key -> new ArrayList<>()); list.removeIf(value -> value.id.equalsIgnoreCase(modifier.id)); list.add(modifier); setDirty(); }
    public boolean removeModifier(String settlement, String modifierId) { List<ModifierRecord> list = settlementModifiers.get(SettlementData.normalizeId(settlement)); if (list == null) return false; boolean removed = list.removeIf(value -> value.id.equalsIgnoreCase(modifierId)); if (removed) setDirty(); return removed; }
    public void clearModifiers(String settlement) { if (settlementModifiers.remove(SettlementData.normalizeId(settlement)) != null) setDirty(); }

    /** Clears campaign-system data owned by one settlement without touching the settlement record itself. */
    public void clearSettlementData(String settlement) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank()) return;
        prosperity.remove(id);
        taxPolicies.remove(id);
        lastTaxDay.remove(id);
        lastTaxIncome.remove(id);
        levyTradition.remove(id);
        wealthDeposits.remove(id);
        foreignManpower.remove(id);
        debugTimeOffsetMillis.remove(id);
        lastModifierDay.remove(id);
        protectedSettlements.remove(id);
        housingAreas.remove(id);
        musterAreas.remove(id);
        prayerSites.remove(id);
        settlementModifiers.remove(id);
        levyMobilizations.values().removeIf(value -> id.equals(value.settlementId));
        levyDeployments.values().removeIf(value -> id.equals(value.settlementId));
        mercenaryContracts.values().removeIf(value -> id.equals(value.settlementId()));
        peaceOffers.values().removeIf(value -> id.equals(value.proposerSettlement()) || id.equals(value.targetSettlement()));
        aidByWar.values().forEach(values -> values.removeIf(value -> id.equals(SettlementData.normalizeId(value.targetSettlement()))));
        aidByWar.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        setDirty();
    }

    /** Clears only army/mobilisation tracking owned by one settlement. External entities are not deleted. */
    public void clearSettlementArmyData(String settlement) {
        String id = SettlementData.normalizeId(settlement);
        if (id.isBlank()) return;
        levyMobilizations.values().removeIf(value -> id.equals(value.settlementId));
        levyDeployments.values().removeIf(value -> id.equals(value.settlementId));
        mercenaryContracts.values().removeIf(value -> id.equals(value.settlementId()));
        setDirty();
    }
    public int expireModifiers(String settlement) { String id = SettlementData.normalizeId(settlement); List<ModifierRecord> list = settlementModifiers.get(id); if (list == null) return 0; long now = nowMillis(id); int before = list.size(); list.removeIf(value -> value.expiresAtMillis > 0L && value.expiresAtMillis <= now); int removed = before - list.size(); if (removed > 0) setDirty(); return removed; }

    public Collection<LevyMobilization> levyMobilizations() { return List.copyOf(levyMobilizations.values()); }
    public LevyMobilization levyMobilization(UUID id) { return levyMobilizations.get(id); }
    public void putLevyMobilization(LevyMobilization value) { levyMobilizations.put(value.id, value); setDirty(); }
    public void removeLevyMobilization(UUID id) { if (levyMobilizations.remove(id) != null) setDirty(); }
    public Collection<LevyDeployment> levyDeployments() { return List.copyOf(levyDeployments.values()); }
    public LevyDeployment levyDeployment(UUID batch) { return levyDeployments.get(batch); }
    public void putLevyDeployment(LevyDeployment value) { levyDeployments.put(value.batchId, value); setDirty(); }
    public void removeLevyDeployment(UUID batch) { if (levyDeployments.remove(batch) != null) setDirty(); }
    public long levyCooldownUntil(UUID player) { return levyCooldownUntil.getOrDefault(player, 0L); }
    public void setLevyCooldown(UUID player, long until) { if (player == null) return; levyCooldownUntil.put(player, Math.max(0L, until)); setDirty(); }
    public void clearLevyCooldown(UUID player) { if (levyCooldownUntil.remove(player) != null) setDirty(); }

    public PeaceOfferRecord peaceOffer(UUID proposal) { return peaceOffers.get(proposal); }
    public Collection<PeaceOfferRecord> peaceOffers() { return List.copyOf(peaceOffers.values()); }
    public void putPeaceOffer(PeaceOfferRecord value) { if (value != null && value.proposalId != null) { peaceOffers.put(value.proposalId, value); setDirty(); } }
    public void removePeaceOffer(UUID proposal) { if (peaceOffers.remove(proposal) != null) setDirty(); }
    public List<AidRecord> warAid(UUID war) { return List.copyOf(aidByWar.getOrDefault(war, List.of())); }
    public void addWarAid(UUID war, AidRecord value) { if (war == null || value == null) return; aidByWar.computeIfAbsent(war, key -> new ArrayList<>()).add(value); setDirty(); }
    public void replaceWarAid(UUID war, List<AidRecord> values) { if (war == null) return; if (values == null || values.isEmpty()) aidByWar.remove(war); else aidByWar.put(war, new ArrayList<>(values)); setDirty(); }
    public void removeWarAid(UUID war) { if (aidByWar.remove(war) != null) setDirty(); }
    public boolean removeWarAid(UUID war, UUID aidId) {
        if (war == null || aidId == null) return false;
        List<AidRecord> values = aidByWar.get(war);
        if (values == null) return false;
        boolean removed = values.removeIf(value -> aidId.equals(value.id()));
        if (values.isEmpty()) aidByWar.remove(war);
        if (removed) setDirty();
        return removed;
    }
    public int warScore(UUID war) { return clamp(warScore.getOrDefault(war, 0), -100, 100); }
    public void setWarScore(UUID war, int score) { if (war == null) return; warScore.put(war, clamp(score, -100, 100)); setDirty(); }
    public void changeWarScore(UUID war, int delta) { setWarScore(war, warScore(war) + delta); }
    public boolean ignoredWarIntervention(UUID war, String faction) {
        return war != null && faction != null && ignoredWarInterventions.getOrDefault(war, List.of()).contains(faction);
    }
    public void setIgnoredWarIntervention(UUID war, String faction, boolean ignored) {
        if (war == null || faction == null || faction.isBlank()) return;
        List<String> values = ignoredWarInterventions.computeIfAbsent(war, key -> new ArrayList<>());
        if (ignored) { if (!values.contains(faction)) values.add(faction); } else values.remove(faction);
        if (values.isEmpty()) ignoredWarInterventions.remove(war);
        setDirty();
    }
    public void clearWarInterventionState(UUID war) { if (war != null && ignoredWarInterventions.remove(war) != null) setDirty(); }

    public CandidateHistory candidateHistory(UUID player) { return candidateHistory.computeIfAbsent(player, CandidateHistory::new); }
    public void setCandidateGender(UUID player, String gender) { CandidateHistory h = candidateHistory(player); h.setPreferredGender(gender); setDirty(); }
    public void rejectCandidate(UUID player, String id) { CandidateHistory h = candidateHistory(player); h.reject(id); setDirty(); }
    public void markCandidateSeen(UUID player, String id) { CandidateHistory h = candidateHistory(player); h.seen(id); setDirty(); }

    public long forcedNapUntil(String firstFaction, String secondFaction) { return forcedNapUntilDay.getOrDefault(pairKey(firstFaction, secondFaction), 0L); }
    public void setForcedNap(String firstFaction, String secondFaction, long untilDay) {
        String key = pairKey(firstFaction, secondFaction); if (key.isBlank()) return;
        if (untilDay <= 0) forcedNapUntilDay.remove(key); else forcedNapUntilDay.put(key, untilDay); setDirty();
    }
    public boolean forcedNapActive(String firstFaction, String secondFaction, long day) { return forcedNapUntil(firstFaction, secondFaction) > day; }
    public List<ReparationRecord> reparations() { return List.copyOf(reparations); }
    public void addReparation(ReparationRecord value) { if (value != null) { reparations.add(value); setDirty(); } }
    public void replaceReparations(List<ReparationRecord> values) { reparations.clear(); if (values != null) reparations.addAll(values); setDirty(); }
    public void removeReparation(UUID id) { if (id != null && reparations.removeIf(value -> id.equals(value.id()))) setDirty(); }

    public Collection<MercenaryContractRecord> mercenaryContracts() { return List.copyOf(mercenaryContracts.values()); }
    public MercenaryContractRecord mercenaryContract(UUID unitId) { return unitId == null ? null : mercenaryContracts.get(unitId); }
    public void putMercenaryContract(MercenaryContractRecord value) { if (value != null && value.unitId() != null) { mercenaryContracts.put(value.unitId(), value); setDirty(); } }
    public void removeMercenaryContract(UUID unitId) { if (unitId != null && mercenaryContracts.remove(unitId) != null) setDirty(); }
    public List<MercenaryContractRecord> mercenariesForSettlement(String settlementId) {
        String id = SettlementData.normalizeId(settlementId);
        return mercenaryContracts.values().stream().filter(value -> value.settlementId().equals(id)).toList();
    }
    public List<MercenaryContractRecord> mercenariesForFaction(String factionId) {
        String id = factionId == null ? "" : factionId;
        return mercenaryContracts.values().stream().filter(value -> value.factionId().equals(id)).toList();
    }

    public List<FactionInvitationRecord> factionInvitationsFor(UUID player) {
        if (player == null) return List.of();
        return factionInvitations.stream().filter(value -> player.equals(value.targetPlayer())).toList();
    }
    public boolean hasFactionInvitation(String factionId, UUID player) {
        if (factionId == null || factionId.isBlank() || player == null) return false;
        return factionInvitations.stream().anyMatch(value -> player.equals(value.targetPlayer()) && factionId.equals(value.factionId()));
    }
    public void addFactionInvitation(String factionId, UUID player, UUID inviter, long createdAtMillis) {
        if (factionId == null || factionId.isBlank() || player == null || hasFactionInvitation(factionId, player)) return;
        factionInvitations.add(new FactionInvitationRecord(UUID.randomUUID(), factionId, player, inviter, Math.max(0L, createdAtMillis)));
        setDirty();
    }
    public boolean removeFactionInvitation(String factionId, UUID player) {
        if (factionId == null || factionId.isBlank() || player == null) return false;
        boolean removed = factionInvitations.removeIf(value -> player.equals(value.targetPlayer()) && factionId.equals(value.factionId()));
        if (removed) setDirty();
        return removed;
    }
    public void clearFactionInvitations(UUID player) {
        if (player != null && factionInvitations.removeIf(value -> player.equals(value.targetPlayer()))) setDirty();
    }

    public void markRetiredLevy(UUID unitId) { if (unitId != null && retiredLevyUnits.add(unitId)) setDirty(); }
    public boolean isRetiredLevy(UUID unitId) { return unitId != null && retiredLevyUnits.contains(unitId); }
    public boolean consumeRetiredLevy(UUID unitId) {
        if (unitId == null) return false;
        boolean removed = retiredLevyUnits.remove(unitId);
        if (removed) setDirty();
        return removed;
    }
    public int retiredLevyCount() { return retiredLevyUnits.size(); }

    public void resetPlayerData(UUID player) {
        if (player == null) return;
        devotion.remove(player);
        divorceCount.remove(player);
        lastHolyVisitDay.remove(player);
        lastPrayerDay.remove(player);
        levyCooldownUntil.remove(player);
        levyMobilizations.entrySet().removeIf(entry -> player.equals(entry.getValue().ownerId));
        levyDeployments.entrySet().removeIf(entry -> player.equals(entry.getValue().ownerId));
        candidateHistory.remove(player);
        mercenaryContracts.entrySet().removeIf(entry -> player.equals(entry.getValue().originalOwnerId()));
        factionInvitations.removeIf(value -> player.equals(value.targetPlayer()) || player.equals(value.inviter()));
        setDirty();
    }

    public void resetCharacterData(UUID player) {
        if (player == null) return;
        devotion.remove(player);
        divorceCount.remove(player);
        lastHolyVisitDay.remove(player);
        lastPrayerDay.remove(player);
        levyCooldownUntil.remove(player);
        candidateHistory.remove(player);
        setDirty();
    }

    public void resetReligion(UUID player) { if (player != null && devotion.remove(player) != null) setDirty(); }
    public void resetLevy(UUID player) {
        if (player == null) return;
        levyCooldownUntil.remove(player);
        levyMobilizations.entrySet().removeIf(entry -> player.equals(entry.getValue().ownerId));
        levyDeployments.entrySet().removeIf(entry -> player.equals(entry.getValue().ownerId));
        setDirty();
    }
    public void resetCandidateHistory(UUID player) { if (player != null && candidateHistory.remove(player) != null) setDirty(); }

    public record WealthDepositRecord(long day, int amount) {
        public WealthDepositRecord { amount = Math.max(0, amount); }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putLong("Day", day); tag.putInt("Amount", amount); return tag; }
        private static WealthDepositRecord fromNBT(CompoundTag tag) { return new WealthDepositRecord(tag.getLong("Day"), tag.getInt("Amount")); }
    }

    public enum TaxPolicy {
        LOW("Low", 50, 1, 3),
        NORMAL("Normal", 100, 0, 0),
        HIGH("High", 150, -2, -5);

        private final String displayName;
        private final int ratePercent;
        private final int stabilityPerDay;
        private final int prosperityModifier;

        TaxPolicy(String displayName, int ratePercent, int stabilityPerDay, int prosperityModifier) {
            this.displayName = displayName;
            this.ratePercent = ratePercent;
            this.stabilityPerDay = stabilityPerDay;
            this.prosperityModifier = prosperityModifier;
        }

        public String displayName() { return displayName; }
        public int ratePercent() { return ratePercent; }
        public int stabilityPerDay() { return stabilityPerDay; }
        public int prosperityModifier() { return prosperityModifier; }
        public static TaxPolicy parse(String raw) {
            if (raw == null) return NORMAL;
            try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return NORMAL; }
        }
        public static TaxPolicy fromOrdinal(int ordinal) {
            TaxPolicy[] values = values();
            return ordinal < 0 || ordinal >= values.length ? NORMAL : values[ordinal];
        }
    }

    public record RegionRecord(String dimension, BlockPos min, BlockPos max, AreaSelection.Shape shape) {
        public RegionRecord(String dimension, BlockPos min, BlockPos max) { this(dimension, min, max, AreaSelection.Shape.BOX); }
        public RegionRecord {
            dimension = dimension == null ? "" : dimension;
            shape = shape == null ? AreaSelection.Shape.BOX : shape;
            if (min != null && max != null) {
                BlockPos a = min; BlockPos b = max;
                min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
                max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
            }
        }
        public boolean valid() { return !dimension.isBlank() && min != null && max != null; }
        public long horizontalArea() { return valid() ? AreaSelection.horizontalArea(shape, min, max) : 0L; }
        public long volume() { if (!valid()) return 0; return horizontalArea() * (max.getY() - min.getY() + 1L); }
        public boolean contains(String dim, BlockPos pos) { return valid() && dimension.equals(dim) && AreaSelection.contains(shape, min, max, pos, true); }
        public boolean containsHorizontal(BlockPos pos) { return valid() && AreaSelection.contains(shape, min, max, pos, false); }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putString("Dimension", dimension); tag.putString("Shape", shape.name()); if (min != null) tag.putLong("Min", min.asLong()); if (max != null) tag.putLong("Max", max.asLong()); return tag; }
        private static RegionRecord fromNBT(CompoundTag tag) { return new RegionRecord(tag.getString("Dimension"), tag.contains("Min", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Min")) : null, tag.contains("Max", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Max")) : null, AreaSelection.Shape.parse(tag.getString("Shape"))); }
    }

    public record HolySiteRecord(String id, String name, String faith, RegionRecord region, int devotionGain) {
        public HolySiteRecord { id = SettlementData.normalizeId(id); name = SettlementData.cleanText(name, id, 64); faith = SettlementData.cleanText(faith, "Other", 40); devotionGain = clamp(devotionGain, 1, 100); }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putString("Id", id); tag.putString("Name", name); tag.putString("Faith", faith); tag.put("Region", region.toNBT()); tag.putInt("DevotionGain", devotionGain); return tag; }
        private static HolySiteRecord fromNBT(CompoundTag tag) { return new HolySiteRecord(tag.getString("Id"), tag.getString("Name"), tag.getString("Faith"), RegionRecord.fromNBT(tag.getCompound("Region")), tag.contains("DevotionGain", Tag.TAG_INT) ? tag.getInt("DevotionGain") : 20); }
    }

    public static final class ModifierRecord {
        public final String id, name, iconItem, tone, source, detail;
        public final int stabilityPerDay, prosperity, morale;
        public final long expiresAtMillis;
        public ModifierRecord(String id, String name, String iconItem, String tone, String source, String detail, int stabilityPerDay, int prosperity, int morale, long expiresAtMillis) {
            this.id = SettlementData.normalizeId(id); this.name = SettlementData.cleanText(name, this.id, 64); this.iconItem = iconItem == null ? "minecraft:book" : iconItem; this.tone = tone == null ? "NEUTRAL" : tone.toUpperCase(Locale.ROOT); this.source = SettlementData.cleanText(source, "Campaign", 64); this.detail = SettlementData.cleanText(detail, "", 180); this.stabilityPerDay = stabilityPerDay; this.prosperity = prosperity; this.morale = morale; this.expiresAtMillis = Math.max(0L, expiresAtMillis);
        }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putString("Id", id); tag.putString("Name", name); tag.putString("Icon", iconItem); tag.putString("Tone", tone); tag.putString("Source", source); tag.putString("Detail", detail); tag.putInt("Stability", stabilityPerDay); tag.putInt("Prosperity", prosperity); tag.putInt("Morale", morale); tag.putLong("Expires", expiresAtMillis); return tag; }
        private static ModifierRecord fromNBT(CompoundTag tag) { return new ModifierRecord(tag.getString("Id"), tag.getString("Name"), tag.getString("Icon"), tag.getString("Tone"), tag.getString("Source"), tag.getString("Detail"), tag.getInt("Stability"), tag.getInt("Prosperity"), tag.getInt("Morale"), tag.getLong("Expires")); }
        public boolean permanent() { return expiresAtMillis <= 0L; }
    }

    public static final class LevyMobilization {
        public final UUID id, ownerId;
        public final String settlementId, factionId;
        public final int manpowerSpent, totalUnits;
        public int spawnedUnits;
        public final long startedAtMillis, finishesAtMillis;
        public UUID batchId;
        public LevyMobilization(UUID id, UUID ownerId, String settlementId, String factionId, int manpowerSpent, int totalUnits, int spawnedUnits, long startedAtMillis, long finishesAtMillis, UUID batchId) { this.id = id; this.ownerId = ownerId; this.settlementId = SettlementData.normalizeId(settlementId); this.factionId = factionId == null ? "" : factionId; this.manpowerSpent = Math.max(0, manpowerSpent); this.totalUnits = Math.max(0, totalUnits); this.spawnedUnits = clamp(spawnedUnits, 0, this.totalUnits); this.startedAtMillis = startedAtMillis; this.finishesAtMillis = Math.max(startedAtMillis, finishesAtMillis); this.batchId = batchId; }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); if (ownerId != null) tag.putUUID("Owner", ownerId); tag.putString("Settlement", settlementId); tag.putString("Faction", factionId); tag.putInt("Manpower", manpowerSpent); tag.putInt("Total", totalUnits); tag.putInt("Spawned", spawnedUnits); tag.putLong("Started", startedAtMillis); tag.putLong("Finishes", finishesAtMillis); if (batchId != null) tag.putUUID("Batch", batchId); return tag; }
        private static LevyMobilization fromNBT(CompoundTag tag) { return new LevyMobilization(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getString("Settlement"), tag.getString("Faction"), tag.getInt("Manpower"), tag.getInt("Total"), tag.getInt("Spawned"), tag.getLong("Started"), tag.getLong("Finishes"), tag.hasUUID("Batch") ? tag.getUUID("Batch") : null); }
    }

    public static final class LevyDeployment {
        public final UUID batchId, ownerId;
        public final String settlementId, factionId;
        public final int initialUnits, manpowerSpent;
        public int returnedUnits;
        public final long deployedAtMillis;
        public LevyDeployment(UUID batchId, UUID ownerId, String settlementId, String factionId, int initialUnits, int manpowerSpent, int returnedUnits, long deployedAtMillis) { this.batchId = batchId; this.ownerId = ownerId; this.settlementId = SettlementData.normalizeId(settlementId); this.factionId = factionId == null ? "" : factionId; this.initialUnits = Math.max(0, initialUnits); this.manpowerSpent = Math.max(0, manpowerSpent); this.returnedUnits = Math.max(0, returnedUnits); this.deployedAtMillis = deployedAtMillis; }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Batch", batchId); if (ownerId != null) tag.putUUID("Owner", ownerId); tag.putString("Settlement", settlementId); tag.putString("Faction", factionId); tag.putInt("Initial", initialUnits); tag.putInt("Manpower", manpowerSpent); tag.putInt("Returned", returnedUnits); tag.putLong("Deployed", deployedAtMillis); return tag; }
        private static LevyDeployment fromNBT(CompoundTag tag) { return new LevyDeployment(tag.hasUUID("Batch") ? tag.getUUID("Batch") : UUID.randomUUID(), tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getString("Settlement"), tag.getString("Faction"), tag.getInt("Initial"), tag.getInt("Manpower"), tag.getInt("Returned"), tag.getLong("Deployed")); }
    }

    public record PeaceOfferRecord(UUID proposalId, UUID warId, String proposerFaction, String targetFaction,
                                   String proposerSettlement, String targetSettlement, List<PeaceTerm> terms, long createdAtMillis) {
        public PeaceOfferRecord { proposerFaction = proposerFaction == null ? "" : proposerFaction; targetFaction = targetFaction == null ? "" : targetFaction; proposerSettlement = SettlementData.normalizeId(proposerSettlement); targetSettlement = SettlementData.normalizeId(targetSettlement); terms = terms == null ? List.of() : List.copyOf(terms); }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Proposal", proposalId); if (warId != null) tag.putUUID("War", warId); tag.putString("Proposer", proposerFaction); tag.putString("Target", targetFaction); tag.putString("ProposerSettlement", proposerSettlement); tag.putString("TargetSettlement", targetSettlement); tag.putLong("Created", createdAtMillis); ListTag list = new ListTag(); terms.forEach(term -> list.add(term.toNBT())); tag.put("Terms", list); return tag; }
        private static PeaceOfferRecord fromNBT(CompoundTag tag) { List<PeaceTerm> terms = new ArrayList<>(); for (Tag raw : tag.getList("Terms", Tag.TAG_COMPOUND)) terms.add(PeaceTerm.fromNBT((CompoundTag) raw)); return new PeaceOfferRecord(tag.hasUUID("Proposal") ? tag.getUUID("Proposal") : null, tag.hasUUID("War") ? tag.getUUID("War") : null, tag.getString("Proposer"), tag.getString("Target"), tag.getString("ProposerSettlement"), tag.getString("TargetSettlement"), List.copyOf(terms), tag.getLong("Created")); }
    }

    public record PeaceTerm(String type, String targetId, int amount, int durationDays, String text) {
        public PeaceTerm { type = type == null ? "" : type.toUpperCase(Locale.ROOT); targetId = targetId == null ? "" : targetId; amount = Math.max(0, amount); durationDays = Math.max(0, durationDays); text = SettlementData.cleanText(text, "", 120); }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putString("Type", type); tag.putString("Target", targetId); tag.putInt("Amount", amount); tag.putInt("Duration", durationDays); tag.putString("Text", text); return tag; }
        private static PeaceTerm fromNBT(CompoundTag tag) { return new PeaceTerm(tag.getString("Type"), tag.getString("Target"), tag.getInt("Amount"), tag.getInt("Duration"), tag.getString("Text")); }
    }

    public record AidRecord(UUID id, String fromFaction, String targetSettlement, int manpower, int wealth, int food, boolean recurring, long nextDay) {
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putString("From", fromFaction == null ? "" : fromFaction); tag.putString("Target", SettlementData.normalizeId(targetSettlement)); tag.putInt("Manpower", Math.max(0, manpower)); tag.putInt("Wealth", Math.max(0, wealth)); tag.putInt("Food", Math.max(0, food)); tag.putBoolean("Recurring", recurring); tag.putLong("NextDay", nextDay); return tag; }
        private static AidRecord fromNBT(CompoundTag tag) { return new AidRecord(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), tag.getString("From"), tag.getString("Target"), tag.getInt("Manpower"), tag.getInt("Wealth"), tag.getInt("Food"), tag.getBoolean("Recurring"), tag.getLong("NextDay")); }
    }



    public record FactionInvitationRecord(UUID id, String factionId, UUID targetPlayer, UUID inviter, long createdAtMillis) {
        public FactionInvitationRecord {
            if (id == null) id = UUID.randomUUID();
            factionId = factionId == null ? "" : factionId;
            createdAtMillis = Math.max(0L, createdAtMillis);
        }
        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id); tag.putString("Faction", factionId);
            if (targetPlayer != null) tag.putUUID("Target", targetPlayer);
            if (inviter != null) tag.putUUID("Inviter", inviter);
            tag.putLong("Created", createdAtMillis);
            return tag;
        }
        private static FactionInvitationRecord fromNBT(CompoundTag tag) {
            return new FactionInvitationRecord(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), tag.getString("Faction"),
                    tag.hasUUID("Target") ? tag.getUUID("Target") : null, tag.hasUUID("Inviter") ? tag.getUUID("Inviter") : null, tag.getLong("Created"));
        }
    }

    public record MercenaryContractRecord(UUID unitId, UUID originalOwnerId, String settlementId, String factionId,
                                           int wage, int intervalDays, long nextPaymentDay, int missedPayments, boolean desertPending) {
        public MercenaryContractRecord {
            settlementId = SettlementData.normalizeId(settlementId);
            factionId = factionId == null ? "" : factionId;
            wage = Math.max(1, wage);
            intervalDays = Math.max(1, intervalDays);
            missedPayments = Math.max(0, missedPayments);
        }
        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            if (unitId != null) tag.putUUID("Unit", unitId);
            if (originalOwnerId != null) tag.putUUID("Owner", originalOwnerId);
            tag.putString("Settlement", settlementId); tag.putString("Faction", factionId);
            tag.putInt("Wage", wage); tag.putInt("Interval", intervalDays); tag.putLong("NextPayment", nextPaymentDay);
            tag.putInt("Missed", missedPayments); tag.putBoolean("DesertPending", desertPending);
            return tag;
        }
        private static MercenaryContractRecord fromNBT(CompoundTag tag) {
            return new MercenaryContractRecord(tag.hasUUID("Unit") ? tag.getUUID("Unit") : null,
                    tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getString("Settlement"), tag.getString("Faction"),
                    tag.getInt("Wage"), tag.getInt("Interval"), tag.getLong("NextPayment"), tag.getInt("Missed"), tag.getBoolean("DesertPending"));
        }
        public MercenaryContractRecord update(long nextDay, int missed, boolean pending) {
            return new MercenaryContractRecord(unitId, originalOwnerId, settlementId, factionId, wage, intervalDays, nextDay, missed, pending);
        }
    }

    public record ReparationRecord(UUID id, String payerFaction, String receiverFaction, int wealthPerDay, int remainingDays, long nextDay) {
        public ReparationRecord { if (id == null) id = UUID.randomUUID(); payerFaction = payerFaction == null ? "" : payerFaction; receiverFaction = receiverFaction == null ? "" : receiverFaction; wealthPerDay = Math.max(0, wealthPerDay); remainingDays = Math.max(0, remainingDays); }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putString("Payer", payerFaction); tag.putString("Receiver", receiverFaction); tag.putInt("Wealth", wealthPerDay); tag.putInt("Remaining", remainingDays); tag.putLong("NextDay", nextDay); return tag; }
        private static ReparationRecord fromNBT(CompoundTag tag) { return new ReparationRecord(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), tag.getString("Payer"), tag.getString("Receiver"), tag.getInt("Wealth"), tag.getInt("Remaining"), tag.getLong("NextDay")); }
        public ReparationRecord next(long day, int remaining) { return new ReparationRecord(id, payerFaction, receiverFaction, wealthPerDay, remaining, day); }
    }

    public static final class CandidateHistory {
        private final UUID playerId;
        private String preferredGender = "ANY";
        private final List<String> rejectedIds = new ArrayList<>();
        private final List<String> seenIds = new ArrayList<>();
        private CandidateHistory(UUID playerId) { this.playerId = playerId; }
        public String preferredGender() { return preferredGender; }
        public void setPreferredGender(String value) { preferredGender = value == null ? "ANY" : value.toUpperCase(Locale.ROOT); }
        public List<String> rejectedIds() { return List.copyOf(rejectedIds); }
        public List<String> seenIds() { return List.copyOf(seenIds); }
        public void reject(String id) {
            if (id != null && rejectedIds.stream().noneMatch(value -> value.equalsIgnoreCase(id))) {
                rejectedIds.add(id); while (rejectedIds.size() > 256) rejectedIds.remove(0);
            }
        }
        public void seen(String id) {
            if (id != null && seenIds.stream().noneMatch(value -> value.equalsIgnoreCase(id))) {
                seenIds.add(id); while (seenIds.size() > 256) seenIds.remove(0);
            }
        }
        private CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); if (playerId != null) tag.putUUID("Player", playerId); tag.putString("Gender", preferredGender); ListTag rejected = new ListTag(); rejectedIds.forEach(value -> rejected.add(net.minecraft.nbt.StringTag.valueOf(value))); tag.put("Rejected", rejected); ListTag seen = new ListTag(); seenIds.forEach(value -> seen.add(net.minecraft.nbt.StringTag.valueOf(value))); tag.put("Seen", seen); return tag; }
        private static CandidateHistory fromNBT(CompoundTag tag) { CandidateHistory history = new CandidateHistory(tag.hasUUID("Player") ? tag.getUUID("Player") : null); history.preferredGender = tag.getString("Gender").isBlank() ? "ANY" : tag.getString("Gender"); for (Tag raw : tag.getList("Rejected", Tag.TAG_STRING)) history.rejectedIds.add(raw.getAsString()); for (Tag raw : tag.getList("Seen", Tag.TAG_STRING)) history.seenIds.add(raw.getAsString()); return history; }
    }

    private static String pairKey(String a, String b) { if (a == null || b == null || a.isBlank() || b.isBlank()) return ""; return a.compareToIgnoreCase(b) <= 0 ? a + "|" + b : b + "|" + a; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static void readStringIntMap(CompoundTag tag, String key, Map<String, Integer> output) { for (Tag raw : tag.getList(key, Tag.TAG_COMPOUND)) { CompoundTag e = (CompoundTag) raw; String id = e.getString("Id"); if (!id.isBlank()) output.put(id, e.getInt("Value")); } }
    private static void writeStringIntMap(CompoundTag tag, String key, Map<String, Integer> values) { ListTag list = new ListTag(); values.forEach((id, value) -> { CompoundTag e = new CompoundTag(); e.putString("Id", id); e.putInt("Value", value); list.add(e); }); tag.put(key, list); }
    private static void readStringLongMap(CompoundTag tag, String key, Map<String, Long> output) { for (Tag raw : tag.getList(key, Tag.TAG_COMPOUND)) { CompoundTag e = (CompoundTag) raw; String id = e.getString("Id"); if (!id.isBlank()) output.put(id, e.getLong("Value")); } }
    private static void writeStringLongMap(CompoundTag tag, String key, Map<String, Long> values) { ListTag list = new ListTag(); values.forEach((id, value) -> { CompoundTag e = new CompoundTag(); e.putString("Id", id); e.putLong("Value", value); list.add(e); }); tag.put(key, list); }
    private static void readStringBooleanMap(CompoundTag tag, String key, Map<String, Boolean> output) { for (Tag raw : tag.getList(key, Tag.TAG_COMPOUND)) { CompoundTag e = (CompoundTag) raw; String id = e.getString("Id"); if (!id.isBlank()) output.put(id, e.getBoolean("Value")); } }
    private static void writeStringBooleanMap(CompoundTag tag, String key, Map<String, Boolean> values) { ListTag list = new ListTag(); values.forEach((id, value) -> { CompoundTag e = new CompoundTag(); e.putString("Id", id); e.putBoolean("Value", value); list.add(e); }); tag.put(key, list); }
    private static void readUuidIntMap(CompoundTag tag, String key, Map<UUID, Integer> output) { for (Tag raw : tag.getList(key, Tag.TAG_COMPOUND)) { CompoundTag e = (CompoundTag) raw; if (e.hasUUID("Id")) output.put(e.getUUID("Id"), e.getInt("Value")); } }
    private static void writeUuidIntMap(CompoundTag tag, String key, Map<UUID, Integer> values) { ListTag list = new ListTag(); values.forEach((id, value) -> { CompoundTag e = new CompoundTag(); e.putUUID("Id", id); e.putInt("Value", value); list.add(e); }); tag.put(key, list); }
    private static void readUuidLongMap(CompoundTag tag, String key, Map<UUID, Long> output) { for (Tag raw : tag.getList(key, Tag.TAG_COMPOUND)) { CompoundTag e = (CompoundTag) raw; if (e.hasUUID("Id")) output.put(e.getUUID("Id"), e.getLong("Value")); } }
    private static void writeUuidLongMap(CompoundTag tag, String key, Map<UUID, Long> values) { ListTag list = new ListTag(); values.forEach((id, value) -> { CompoundTag e = new CompoundTag(); e.putUUID("Id", id); e.putLong("Value", value); list.add(e); }); tag.put(key, list); }
}
