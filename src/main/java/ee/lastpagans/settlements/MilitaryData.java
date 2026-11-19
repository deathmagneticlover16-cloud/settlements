package ee.lastpagans.settlements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent, server-authoritative troop reserves and chronological military work queues. */
public final class MilitaryData extends SavedData {
    private static final String DATA_NAME = "settlements_military";
    private final Map<String, EnumMap<CampaignData.UnitType, ReservePool>> reserves = new HashMap<>();
    private final Map<String, List<TrainingOrder>> training = new HashMap<>();
    private final Map<String, List<MusterOrder>> musters = new HashMap<>();
    private final Map<String, Integer> nativeMpCredit = new HashMap<>();
    private final Map<String, Integer> integratedForeignMilli = new HashMap<>();
    private final Map<String, Integer> capturedForeignCredit = new HashMap<>();
    private final Map<UUID, DeploymentCount> deployed = new HashMap<>();
    private long sequence;
    private long lastProcessedGameTime = Long.MIN_VALUE;

    public static MilitaryData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(MilitaryData::load, MilitaryData::new, DATA_NAME);
    }

    public static MilitaryData load(CompoundTag tag) {
        MilitaryData data = new MilitaryData();
        data.sequence = Math.max(0L, tag.getLong("Sequence"));
        data.lastProcessedGameTime = tag.contains("LastGameTime", Tag.TAG_LONG)
                ? tag.getLong("LastGameTime") : Long.MIN_VALUE;
        readCredits(tag.getList("NativeCredits", Tag.TAG_COMPOUND), data.nativeMpCredit);
        readCredits(tag.getList("CapturedForeignCredits", Tag.TAG_COMPOUND), data.capturedForeignCredit);
        readAmounts(tag.getList("IntegratedForeignMilli", Tag.TAG_COMPOUND), data.integratedForeignMilli);
        for (Tag raw : tag.getList("Reserves", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            CampaignData.UnitType type = CampaignData.UnitType.parse(entry.getString("Type"));
            if (!settlement.isBlank() && type != null) data.pool(settlement, type).set(entry.getInt("Native"), entry.getInt("Foreign"));
        }
        for (Tag raw : tag.getList("Training", Tag.TAG_COMPOUND)) {
            TrainingOrder order = TrainingOrder.fromNBT((CompoundTag) raw);
            if (order.valid()) data.training.computeIfAbsent(order.settlementId, ignored -> new ArrayList<>()).add(order);
        }
        data.training.values().forEach(values -> values.sort(Comparator.comparingLong(value -> value.sequence)));
        for (Tag raw : tag.getList("Musters", Tag.TAG_COMPOUND)) {
            MusterOrder order = MusterOrder.fromNBT((CompoundTag) raw);
            if (order.valid()) data.musters.computeIfAbsent(order.settlementId, ignored -> new ArrayList<>()).add(order);
        }
        data.musters.values().forEach(values -> values.sort(Comparator.comparingLong(value -> value.queueSequence)));
        for (Tag raw : tag.getList("Deployed", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Player")) data.deployed.put(entry.getUUID("Player"),
                    new DeploymentCount(entry.getInt("World"), entry.getInt("Campaign")));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putLong("Sequence", sequence);
        if (lastProcessedGameTime != Long.MIN_VALUE) tag.putLong("LastGameTime", lastProcessedGameTime);
        tag.put("NativeCredits", writeCredits(nativeMpCredit));
        tag.put("CapturedForeignCredits", writeCredits(capturedForeignCredit));
        tag.put("IntegratedForeignMilli", writeAmounts(integratedForeignMilli));
        ListTag reserveTag = new ListTag();
        reserves.forEach((settlement, values) -> values.forEach((type, pool) -> {
            if (pool.total() <= 0) return;
            CompoundTag entry = new CompoundTag(); entry.putString("Settlement", settlement); entry.putString("Type", type.name());
            entry.putInt("Native", pool.nativeCount); entry.putInt("Foreign", pool.foreignCount); reserveTag.add(entry);
        }));
        tag.put("Reserves", reserveTag);
        ListTag trainingTag = new ListTag(); training.values().forEach(values -> values.forEach(value -> trainingTag.add(value.toNBT()))); tag.put("Training", trainingTag);
        ListTag musterTag = new ListTag(); musters.values().forEach(values -> values.forEach(value -> musterTag.add(value.toNBT()))); tag.put("Musters", musterTag);
        ListTag deployedTag = new ListTag(); deployed.forEach((player, count) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("Player", player); entry.putInt("World", count.world); entry.putInt("Campaign", count.campaign); deployedTag.add(entry);
        }); tag.put("Deployed", deployedTag);
        return tag;
    }

    public ReservePool pool(String settlement, CampaignData.UnitType type) {
        String id = SettlementData.normalizeId(settlement);
        return reserves.computeIfAbsent(id, ignored -> new EnumMap<>(CampaignData.UnitType.class))
                .computeIfAbsent(type, ignored -> new ReservePool());
    }

    public List<TrainingOrder> training(String settlement) {
        return List.copyOf(training.computeIfAbsent(SettlementData.normalizeId(settlement), ignored -> new ArrayList<>()));
    }

    List<TrainingOrder> mutableTraining(String settlement) {
        return training.computeIfAbsent(SettlementData.normalizeId(settlement), ignored -> new ArrayList<>());
    }

    public List<MusterOrder> musters(String settlement) {
        return List.copyOf(musters.computeIfAbsent(SettlementData.normalizeId(settlement), ignored -> new ArrayList<>()));
    }

    List<MusterOrder> mutableMusters(String settlement) {
        return musters.computeIfAbsent(SettlementData.normalizeId(settlement), ignored -> new ArrayList<>());
    }

    public Collection<MusterOrder> allMusters() { return musters.values().stream().flatMap(Collection::stream).toList(); }
    public long nextSequence() { sequence++; setDirty(); return sequence; }
    public long lastProcessedGameTime() { return lastProcessedGameTime; }
    public void setLastProcessedGameTime(long value) { lastProcessedGameTime = value; setDirty(); }

    public int nativeCredit(String settlement) { return nativeMpCredit.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public int integratedForeignMilli(String settlement) { return integratedForeignMilli.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public int capturedForeignCredit(String settlement) { return capturedForeignCredit.getOrDefault(SettlementData.normalizeId(settlement), 0); }
    public void setNativeCredit(String settlement, int value) { setCredit(nativeMpCredit, settlement, value); }
    public void setIntegratedForeignMilli(String settlement, int value) { setAmount(integratedForeignMilli, settlement, value); }
    public void setCapturedForeignCredit(String settlement, int value) { setCredit(capturedForeignCredit, settlement, value); }

    public DeploymentCount deployed(UUID player) { return deployed.getOrDefault(player, DeploymentCount.ZERO); }
    public void setDeployed(UUID player, int world, int campaign) {
        if (player == null) return;
        DeploymentCount value = new DeploymentCount(world, campaign);
        if (value.total() == 0) deployed.remove(player); else deployed.put(player, value);
        setDirty();
    }
    public void addWorldDeployment(UUID player, int amount) {
        DeploymentCount old = deployed(player); setDeployed(player, old.world + amount, old.campaign);
    }

    public int pendingForPlayer(UUID player) {
        if (player == null) return 0;
        return allMusters().stream().filter(value -> player.equals(value.ownerId)).mapToInt(MusterOrder::remaining).sum();
    }

    public boolean removeTraining(String settlement, UUID id) {
        boolean changed = mutableTraining(settlement).removeIf(value -> value.id.equals(id)); if (changed) setDirty(); return changed;
    }
    public boolean removeMuster(String settlement, UUID id) {
        boolean changed = mutableMusters(settlement).removeIf(value -> value.id.equals(id)); if (changed) setDirty(); return changed;
    }

    private void setCredit(Map<String, Integer> values, String settlement, int value) {
        String id = SettlementData.normalizeId(settlement); int safe = Math.max(0, value) % 1000;
        if (safe == 0) values.remove(id); else values.put(id, safe); setDirty();
    }
    private void setAmount(Map<String, Integer> values, String settlement, int value) {
        String id = SettlementData.normalizeId(settlement); int safe = Math.max(0, value);
        if (safe == 0) values.remove(id); else values.put(id, safe); setDirty();
    }

    private static void readCredits(ListTag list, Map<String, Integer> output) {
        for (Tag raw : list) { CompoundTag entry = (CompoundTag) raw; String id = SettlementData.normalizeId(entry.getString("Settlement"));
            int value = Math.max(0, entry.getInt("Value")) % 1000; if (!id.isBlank() && value > 0) output.put(id, value); }
    }
    private static ListTag writeCredits(Map<String, Integer> values) {
        ListTag result = new ListTag(); values.forEach((settlement, value) -> { CompoundTag entry = new CompoundTag();
            entry.putString("Settlement", settlement); entry.putInt("Value", value); result.add(entry); }); return result;
    }
    private static void readAmounts(ListTag list, Map<String, Integer> output) {
        for (Tag raw : list) { CompoundTag entry = (CompoundTag) raw; String id = SettlementData.normalizeId(entry.getString("Settlement"));
            int value = Math.max(0, entry.getInt("Value")); if (!id.isBlank() && value > 0) output.put(id, value); }
    }
    private static ListTag writeAmounts(Map<String, Integer> values) { return writeCredits(values); }

    public static final class ReservePool {
        private int nativeCount;
        private int foreignCount;
        public int nativeCount() { return nativeCount; }
        public int foreignCount() { return foreignCount; }
        public int total() { return nativeCount + foreignCount; }
        public void addNative(int amount) { nativeCount = Math.max(0, nativeCount + amount); }
        public void addForeign(int amount) { foreignCount = Math.max(0, foreignCount + amount); }
        public void set(int nativeValue, int foreignValue) { nativeCount = Math.max(0, nativeValue); foreignCount = Math.max(0, foreignValue); }
        public int takeNativeFirst(int amount) {
            int safe = Math.max(0, Math.min(amount, total())); int nativeTaken = Math.min(nativeCount, safe);
            nativeCount -= nativeTaken; foreignCount -= safe - nativeTaken; return nativeTaken;
        }
    }

    public static final class TrainingOrder {
        public final UUID id, ownerId;
        public final String settlementId;
        public final CampaignData.UnitType type;
        public final int total, nativeTotal, foreignTotal, silverPerSoldier, mpMilliPerSoldier, durationTicks;
        public final long sequence;
        public int completed, progressTicks;

        public TrainingOrder(UUID id, UUID ownerId, String settlementId, CampaignData.UnitType type, int total,
                             int nativeTotal, int foreignTotal, int silverPerSoldier, int mpMilliPerSoldier,
                             int durationTicks, int completed, int progressTicks, long sequence) {
            this.id = id; this.ownerId = ownerId; this.settlementId = SettlementData.normalizeId(settlementId); this.type = type;
            this.total = Math.max(1, total); this.nativeTotal = Math.max(0, Math.min(this.total, nativeTotal));
            this.foreignTotal = Math.max(0, Math.min(this.total - this.nativeTotal, foreignTotal));
            this.silverPerSoldier = Math.max(0, silverPerSoldier); this.mpMilliPerSoldier = Math.max(1, mpMilliPerSoldier);
            this.durationTicks = Math.max(20, durationTicks); this.completed = Math.max(0, Math.min(this.total, completed));
            this.progressTicks = Math.max(0, Math.min(this.durationTicks - 1, progressTicks)); this.sequence = Math.max(0L, sequence);
        }
        public int remaining() { return total - completed; }
        public int completedNative() { return Math.min(completed, nativeTotal); }
        public int remainingNative() { return nativeTotal - completedNative(); }
        public int remainingForeign() { return remaining() - remainingNative(); }
        public boolean valid() { return id != null && ownerId != null && !settlementId.isBlank() && type != null && remaining() > 0; }
        CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putUUID("Owner", ownerId); tag.putString("Settlement", settlementId);
            tag.putString("Type", type.name()); tag.putInt("Total", total); tag.putInt("Native", nativeTotal); tag.putInt("Foreign", foreignTotal);
            tag.putInt("SilverEach", silverPerSoldier); tag.putInt("MpMilliEach", mpMilliPerSoldier); tag.putInt("Duration", durationTicks);
            tag.putInt("Completed", completed); tag.putInt("Progress", progressTicks); tag.putLong("Sequence", sequence); return tag; }
        static TrainingOrder fromNBT(CompoundTag tag) { return new TrainingOrder(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getString("Settlement"), CampaignData.UnitType.parse(tag.getString("Type")),
                tag.getInt("Total"), tag.getInt("Native"), tag.getInt("Foreign"), tag.getInt("SilverEach"), tag.getInt("MpMilliEach"),
                tag.getInt("Duration"), tag.getInt("Completed"), tag.getInt("Progress"), tag.getLong("Sequence")); }
    }

    public enum MusterPhase { ACCEPTING_CONTRACT, MUSTERING }

    public static final class MusterOrder {
        public final UUID id, groupId, ownerId;
        public final String settlementId, companyId, displayName;
        public final List<CampaignData.UnitType> units;
        public final int nativeTotal, foreignTotal;
        public final boolean mercenary;
        public MusterPhase phase;
        public int spawned, acceptanceTicks;
        public long queueSequence, nextArrivalGameTime;

        public MusterOrder(UUID id, UUID groupId, UUID ownerId, String settlementId, String companyId, String displayName,
                           List<CampaignData.UnitType> units, int nativeTotal, int foreignTotal, boolean mercenary,
                           MusterPhase phase, int spawned, int acceptanceTicks, long queueSequence, long nextArrivalGameTime) {
            this.id = id; this.groupId = groupId; this.ownerId = ownerId; this.settlementId = SettlementData.normalizeId(settlementId);
            this.companyId = SettlementData.normalizeId(companyId); this.displayName = SettlementData.cleanText(displayName, "Troops", 64);
            this.units = units == null ? List.of() : List.copyOf(units); this.nativeTotal = Math.max(0, nativeTotal);
            this.foreignTotal = Math.max(0, foreignTotal); this.mercenary = mercenary; this.phase = phase == null ? MusterPhase.MUSTERING : phase;
            this.spawned = Math.max(0, Math.min(this.units.size(), spawned)); this.acceptanceTicks = Math.max(0, acceptanceTicks);
            this.queueSequence = Math.max(0L, queueSequence); this.nextArrivalGameTime = nextArrivalGameTime;
        }
        public int total() { return units.size(); }
        public int remaining() { return total() - spawned; }
        public CampaignData.UnitType nextType() { return spawned < units.size() ? units.get(spawned) : null; }
        public boolean nextForeign() { return spawned >= nativeTotal; }
        public boolean valid() { return id != null && groupId != null && ownerId != null && !settlementId.isBlank() && !units.isEmpty() && remaining() > 0; }
        CompoundTag toNBT() { CompoundTag tag = new CompoundTag(); tag.putUUID("Id", id); tag.putUUID("Group", groupId); tag.putUUID("Owner", ownerId);
            tag.putString("Settlement", settlementId); tag.putString("Company", companyId); tag.putString("Name", displayName); tag.putInt("Native", nativeTotal);
            tag.putInt("Foreign", foreignTotal); tag.putBoolean("Mercenary", mercenary); tag.putString("Phase", phase.name()); tag.putInt("Spawned", spawned);
            tag.putInt("Acceptance", acceptanceTicks); tag.putLong("QueueSequence", queueSequence); tag.putLong("NextArrival", nextArrivalGameTime);
            ListTag unitTag = new ListTag(); units.forEach(value -> unitTag.add(StringTag.valueOf(value.name()))); tag.put("Units", unitTag); return tag; }
        static MusterOrder fromNBT(CompoundTag tag) { List<CampaignData.UnitType> units = new ArrayList<>();
            for (Tag raw : tag.getList("Units", Tag.TAG_STRING)) { CampaignData.UnitType type = CampaignData.UnitType.parse(raw.getAsString()); if (type != null) units.add(type); }
            MusterPhase phase; try { phase = MusterPhase.valueOf(tag.getString("Phase")); } catch (IllegalArgumentException exception) { phase = MusterPhase.MUSTERING; }
            return new MusterOrder(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), tag.hasUUID("Group") ? tag.getUUID("Group") : UUID.randomUUID(),
                    tag.hasUUID("Owner") ? tag.getUUID("Owner") : null, tag.getString("Settlement"), tag.getString("Company"), tag.getString("Name"), units,
                    tag.getInt("Native"), tag.getInt("Foreign"), tag.getBoolean("Mercenary"), phase, tag.getInt("Spawned"), tag.getInt("Acceptance"),
                    tag.getLong("QueueSequence"), tag.getLong("NextArrival")); }
    }

    public record DeploymentCount(int world, int campaign) {
        public static final DeploymentCount ZERO = new DeploymentCount(0, 0);
        public DeploymentCount { world = Math.max(0, world); campaign = Math.max(0, campaign); }
        public int total() { return world + campaign; }
    }
}
