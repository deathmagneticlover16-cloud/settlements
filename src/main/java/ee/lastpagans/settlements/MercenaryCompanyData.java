package ee.lastpagans.settlements;

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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent company-level contracts and employer trust. */
public final class MercenaryCompanyData extends SavedData {
    private static final String DATA_NAME = "settlements_mercenary_companies";
    private final Map<String, CompanyContract> contracts = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> trust = new HashMap<>();
    private final Set<UUID> retiredUnits = new java.util.HashSet<>();

    public static MercenaryCompanyData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(MercenaryCompanyData::load, MercenaryCompanyData::new, DATA_NAME);
    }

    public static MercenaryCompanyData load(CompoundTag tag) {
        MercenaryCompanyData data = new MercenaryCompanyData();
        for (Tag raw : tag.getList("Contracts", Tag.TAG_COMPOUND)) {
            CompanyContract contract = CompanyContract.fromNBT((CompoundTag) raw);
            if (!contract.companyId().isBlank()) data.contracts.put(contract.companyId(), contract);
        }
        for (Tag raw : tag.getList("Trust", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String company = SettlementData.normalizeId(entry.getString("Company"));
            String employer = entry.getString("Employer");
            if (!company.isBlank() && !employer.isBlank()) data.trust.computeIfAbsent(company, ignored -> new HashMap<>())
                    .put(employer, clamp(entry.getInt("Value")));
        }
        for (Tag raw : tag.getList("RetiredUnits", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Unit")) data.retiredUnits.add(entry.getUUID("Unit"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag contractsTag = new ListTag(); contracts.values().forEach(value -> contractsTag.add(value.toNBT())); tag.put("Contracts", contractsTag);
        ListTag trustTag = new ListTag();
        trust.forEach((company, employers) -> employers.forEach((employer, value) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("Company", company); entry.putString("Employer", employer); entry.putInt("Value", value); trustTag.add(entry);
        }));
        tag.put("Trust", trustTag);
        ListTag retired = new ListTag();
        retiredUnits.forEach(unit -> { CompoundTag entry = new CompoundTag(); entry.putUUID("Unit", unit); retired.add(entry); });
        tag.put("RetiredUnits", retired);
        return tag;
    }

    public Collection<CompanyContract> contracts() { return List.copyOf(contracts.values()); }
    public CompanyContract contract(String companyId) { return contracts.get(SettlementData.normalizeId(companyId)); }
    public void put(CompanyContract contract) { if (contract != null && !contract.companyId().isBlank()) { contracts.put(contract.companyId(), contract); setDirty(); } }
    public CompanyContract remove(String companyId) { CompanyContract removed = contracts.remove(SettlementData.normalizeId(companyId)); if (removed != null) setDirty(); return removed; }

    public int trust(String companyId, String employerKey, int fallback) {
        return trust.getOrDefault(SettlementData.normalizeId(companyId), Map.of()).getOrDefault(employerKey == null ? "" : employerKey, clamp(fallback));
    }
    public void setTrust(String companyId, String employerKey, int value) {
        if (employerKey == null || employerKey.isBlank()) return;
        trust.computeIfAbsent(SettlementData.normalizeId(companyId), ignored -> new HashMap<>()).put(employerKey, clamp(value)); setDirty();
    }
    public int changeTrust(String companyId, String employerKey, int delta, int fallback) {
        int value = clamp(trust(companyId, employerKey, fallback) + delta); setTrust(companyId, employerKey, value); return value;
    }

    public void retireUnits(Collection<UUID> units) { if (units != null && retiredUnits.addAll(units)) setDirty(); }
    public boolean consumeRetiredUnit(UUID unit) { boolean removed = unit != null && retiredUnits.remove(unit); if (removed) setDirty(); return removed; }
    public boolean isActiveUnit(UUID unit) { return unit != null && contracts.values().stream().anyMatch(value -> value.unitIds().contains(unit)); }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }

    public record CompanyContract(String companyId, String settlementId, String factionId, UUID hiringPlayer,
                                  long hiredDay, long nextPaymentDay, int missedPayments, Set<UUID> unitIds) {
        public CompanyContract {
            companyId = SettlementData.normalizeId(companyId); settlementId = SettlementData.normalizeId(settlementId);
            factionId = factionId == null ? "" : factionId; missedPayments = Math.max(0, missedPayments);
            unitIds = unitIds == null ? Set.of() : Set.copyOf(unitIds);
        }
        public String employerKey() { return factionId.isBlank() ? "settlement:" + settlementId : "faction:" + factionId; }
        public String status() { return missedPayments >= 3 ? "Deserting" : missedPayments == 2 ? "Unpaid" : missedPayments == 1 ? "Discontented" : "Paid"; }
        public CompanyContract update(long nextDay, int missed) { return new CompanyContract(companyId, settlementId, factionId, hiringPlayer, hiredDay, nextDay, missed, unitIds); }
        public CompanyContract withFaction(String newFactionId) { return new CompanyContract(companyId, settlementId, newFactionId, hiringPlayer, hiredDay, nextPaymentDay, missedPayments, unitIds); }
        public CompanyContract withoutUnit(UUID unit) { java.util.Set<UUID> copy = new java.util.HashSet<>(unitIds); copy.remove(unit); return new CompanyContract(companyId, settlementId, factionId, hiringPlayer, hiredDay, nextPaymentDay, missedPayments, copy); }
        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag(); tag.putString("Company", companyId); tag.putString("Settlement", settlementId); tag.putString("Faction", factionId);
            if (hiringPlayer != null) tag.putUUID("HiringPlayer", hiringPlayer); tag.putLong("HiredDay", hiredDay); tag.putLong("NextPayment", nextPaymentDay); tag.putInt("Missed", missedPayments);
            ListTag units = new ListTag(); unitIds.forEach(unit -> { CompoundTag entry = new CompoundTag(); entry.putUUID("Unit", unit); units.add(entry); }); tag.put("Units", units); return tag;
        }
        private static CompanyContract fromNBT(CompoundTag tag) {
            java.util.Set<UUID> units = new java.util.HashSet<>();
            for (Tag raw : tag.getList("Units", Tag.TAG_COMPOUND)) { CompoundTag entry = (CompoundTag) raw; if (entry.hasUUID("Unit")) units.add(entry.getUUID("Unit")); }
            return new CompanyContract(tag.getString("Company"), tag.getString("Settlement"), tag.getString("Faction"),
                    tag.hasUUID("HiringPlayer") ? tag.getUUID("HiringPlayer") : null, tag.getLong("HiredDay"), tag.getLong("NextPayment"), tag.getInt("Missed"), units);
        }
    }
}
