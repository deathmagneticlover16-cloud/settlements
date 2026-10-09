package ee.lastpagans.settlements;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent tactical settlement objectives. Political ownership remains in Settlements diplomacy. */
public final class CaptureZoneData extends SavedData {
    private static final String DATA_NAME = "settlements_capture_zones";
    private final Map<UUID, CaptureZoneRecord> zones = new LinkedHashMap<>();
    private final Map<String, String> occupyingTeamBySettlement = new LinkedHashMap<>();

    public static CaptureZoneData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(CaptureZoneData::load, CaptureZoneData::new, DATA_NAME);
    }

    public static CaptureZoneData load(CompoundTag tag) {
        CaptureZoneData data = new CaptureZoneData();
        for (Tag raw : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            CaptureZoneRecord record = CaptureZoneRecord.fromNBT((CompoundTag) raw);
            if (record != null) data.zones.put(record.id(), record);
        }
        for (Tag raw : tag.getList("Occupations", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            String settlement = SettlementData.normalizeId(entry.getString("Settlement"));
            String team = entry.getString("Team");
            if (!settlement.isBlank() && !team.isBlank()) data.occupyingTeamBySettlement.put(settlement, team);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag zoneList = new ListTag();
        zones.values().forEach(zone -> zoneList.add(zone.toNBT()));
        tag.put("Zones", zoneList);
        ListTag occupations = new ListTag();
        occupyingTeamBySettlement.forEach((settlement, team) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Settlement", settlement);
            entry.putString("Team", team);
            occupations.add(entry);
        });
        tag.put("Occupations", occupations);
        return tag;
    }

    public List<CaptureZoneRecord> allZones() { return List.copyOf(zones.values()); }
    public CaptureZoneRecord get(UUID id) { return id == null ? null : zones.get(id); }
    public List<CaptureZoneRecord> zonesFor(String settlementId) {
        String id = SettlementData.normalizeId(settlementId);
        return zones.values().stream().filter(zone -> zone.settlementId().equals(id))
                .sorted(Comparator.comparing((CaptureZoneRecord z) -> z.type() == ZoneType.MAIN ? 0 : 1)
                        .thenComparing(z -> z.id().toString())).toList();
    }
    public void put(CaptureZoneRecord zone) { if (zone != null) { zones.put(zone.id(), zone); setDirty(); } }
    public boolean remove(UUID id) { boolean changed = id != null && zones.remove(id) != null; if (changed) setDirty(); return changed; }
    public void removeSettlement(String settlementId) {
        String id = SettlementData.normalizeId(settlementId);
        boolean changed = zones.values().removeIf(zone -> zone.settlementId().equals(id));
        changed |= occupyingTeamBySettlement.remove(id) != null;
        if (changed) setDirty();
    }
    public String occupyingTeam(String settlementId) { return occupyingTeamBySettlement.getOrDefault(SettlementData.normalizeId(settlementId), ""); }
    public void setOccupyingTeam(String settlementId, String team) {
        String id = SettlementData.normalizeId(settlementId);
        if (id.isBlank()) return;
        if (team == null || team.isBlank()) occupyingTeamBySettlement.remove(id); else occupyingTeamBySettlement.put(id, team);
        setDirty();
    }

    public enum ZoneType { MAIN, SECONDARY }
    public enum ZoneState { SECURE, UNDER_ATTACK, CONTESTED, CAPTURED, LOCKED }

    public static final class CaptureZoneRecord {
        private final UUID id;
        private final String settlementId;
        private final ZoneType type;
        private final String dimension;
        private final BlockPos pos1;
        private final BlockPos pos2;
        private final AreaSelection.Shape shape;
        private double progress;
        private ZoneState state;
        private String attackerTeam;
        private boolean captured;

        public CaptureZoneRecord(UUID id, String settlementId, ZoneType type, String dimension, BlockPos pos1, BlockPos pos2) {
            this(id, settlementId, type, dimension, pos1, pos2, AreaSelection.Shape.BOX);
        }

        public CaptureZoneRecord(UUID id, String settlementId, ZoneType type, String dimension, BlockPos pos1, BlockPos pos2, AreaSelection.Shape shape) {
            this(id, settlementId, type, dimension, pos1, pos2, shape, 0.0D, ZoneState.SECURE, "");
        }

        private CaptureZoneRecord(UUID id, String settlementId, ZoneType type, String dimension, BlockPos first, BlockPos second,
                                  AreaSelection.Shape shape, double progress, ZoneState state, String attackerTeam) {
            this.id = id == null ? UUID.randomUUID() : id;
            this.settlementId = SettlementData.normalizeId(settlementId);
            this.type = type == null ? ZoneType.SECONDARY : type;
            this.dimension = dimension == null ? "" : dimension;
            int minX = Math.min(first.getX(), second.getX()), maxX = Math.max(first.getX(), second.getX());
            int minY = Math.min(first.getY(), second.getY()), maxY = Math.max(first.getY(), second.getY());
            int minZ = Math.min(first.getZ(), second.getZ()), maxZ = Math.max(first.getZ(), second.getZ());
            this.pos1 = new BlockPos(minX, minY, minZ);
            this.pos2 = new BlockPos(maxX, maxY, maxZ);
            this.shape = shape == null ? AreaSelection.Shape.BOX : shape;
            this.progress = clamp(progress);
            this.state = state == null ? ZoneState.SECURE : state;
            this.attackerTeam = attackerTeam == null ? "" : attackerTeam;
            this.captured = state == ZoneState.CAPTURED;
        }

        public UUID id() { return id; }
        public String settlementId() { return settlementId; }
        public ZoneType type() { return type; }
        public String dimension() { return dimension; }
        public BlockPos pos1() { return pos1; }
        public BlockPos pos2() { return pos2; }
        public AreaSelection.Shape shape() { return shape; }
        public double progress() { return progress; }
        public ZoneState state() { return state; }
        public String attackerTeam() { return attackerTeam; }
        public boolean captured() { return captured; }
        public boolean setCaptured(boolean next) { if (captured == next) return false; captured = next; return true; }
        public int width() { return pos2.getX() - pos1.getX() + 1; }
        public int depth() { return pos2.getZ() - pos1.getZ() + 1; }
        public BlockPos center() { return new BlockPos((pos1.getX()+pos2.getX())/2, (pos1.getY()+pos2.getY())/2, (pos1.getZ()+pos2.getZ())/2); }
        public boolean contains(BlockPos pos) { return AreaSelection.contains(shape, pos1, pos2, pos, false); }
        public boolean intersects(CaptureZoneRecord other) {
            if (other == null || !dimension.equals(other.dimension)) return false;
            return pos1.getX() <= other.pos2.getX() && pos2.getX() >= other.pos1.getX()
                    && pos1.getZ() <= other.pos2.getZ() && pos2.getZ() >= other.pos1.getZ();
        }
        public double horizontalDistance(CaptureZoneRecord other) {
            if (other == null || !dimension.equals(other.dimension)) return Double.POSITIVE_INFINITY;
            BlockPos a = center(), b = other.center();
            double dx = a.getX()-b.getX(), dz=a.getZ()-b.getZ(); return Math.sqrt(dx*dx+dz*dz);
        }
        public boolean setProgress(double next) { double value=clamp(next); if (Math.abs(value-progress)<0.000001D) return false; progress=value; return true; }
        public boolean setState(ZoneState next) { if (next==null || state==next) return false; state=next; return true; }
        public boolean setAttackerTeam(String next) { String value=next==null?"":next; if (value.equals(attackerTeam)) return false; attackerTeam=value; return true; }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id); tag.putString("Settlement", settlementId); tag.putString("Type", type.name()); tag.putString("Dimension", dimension);
            tag.putLong("Pos1", pos1.asLong()); tag.putLong("Pos2", pos2.asLong()); tag.putString("Shape", shape.name()); tag.putDouble("Progress", progress); tag.putString("State", state.name()); tag.putString("Attacker", attackerTeam);
            tag.putBoolean("Captured", captured);
            return tag;
        }
        private static CaptureZoneRecord fromNBT(CompoundTag tag) {
            try {
                UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
                ZoneType type = ZoneType.valueOf(tag.getString("Type"));
                ZoneState state; try { state = ZoneState.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) { state=ZoneState.SECURE; }
                String settlement = SettlementData.normalizeId(tag.getString("Settlement"));
                if (settlement.isBlank()) return null;
                CaptureZoneRecord record = new CaptureZoneRecord(id, settlement, type, tag.getString("Dimension"), BlockPos.of(tag.getLong("Pos1")), BlockPos.of(tag.getLong("Pos2")), AreaSelection.Shape.parse(tag.getString("Shape")), tag.getDouble("Progress"), state, tag.getString("Attacker"));
                if (tag.contains("Captured")) record.captured = tag.getBoolean("Captured");
                return record;
            } catch (RuntimeException ignored) { return null; }
        }
        private static double clamp(double value) { return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D; }
    }
}
