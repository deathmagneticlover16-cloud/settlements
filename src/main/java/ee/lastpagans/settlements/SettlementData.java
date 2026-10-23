package ee.lastpagans.settlements;

import ee.lastpagans.settlements.character.CharacterBodyData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SettlementData extends SavedData {
    private static final String DATA_NAME = "settlements_core";

    private final Map<String, SettlementRecord> settlements = new LinkedHashMap<>();
    private final Map<UUID, String> selectedSettlementByPlayer = new HashMap<>();
    private final Map<UUID, CharacterRecord> characters = new HashMap<>();
    private final Set<UUID> redeemedSettlerContracts = new HashSet<>();

    public static SettlementData get(ServerLevel level) {
        ServerLevel storage = level.getServer().overworld();
        return storage.getDataStorage().computeIfAbsent(SettlementData::load, SettlementData::new, DATA_NAME);
    }

    public static SettlementData load(CompoundTag tag) {
        SettlementData data = new SettlementData();

        if (tag.contains("Settlements", Tag.TAG_LIST)) {
            for (Tag raw : tag.getList("Settlements", Tag.TAG_COMPOUND)) {
                SettlementRecord settlement = SettlementRecord.fromNBT((CompoundTag) raw, data);
                if (!settlement.id.isBlank()) {
                    data.settlements.put(settlement.id, settlement);
                }
            }
        }

        if (tag.contains("Selections", Tag.TAG_LIST)) {
            for (Tag raw : tag.getList("Selections", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                if (entry.hasUUID("Player")) {
                    String id = normalizeId(entry.getString("Settlement"));
                    if (data.settlements.containsKey(id)) {
                        data.selectedSettlementByPlayer.put(entry.getUUID("Player"), id);
                    }
                }
            }
        }

        if (tag.contains("Characters", Tag.TAG_LIST)) {
            for (Tag raw : tag.getList("Characters", Tag.TAG_COMPOUND)) {
                CharacterRecord character = CharacterRecord.fromNBT((CompoundTag) raw);
                if (character.playerId != null) {
                    data.characters.put(character.playerId, character);
                }
            }
        }
        readUuidSet(tag, "RedeemedSettlerContracts", data.redeemedSettlerContracts);
        data.enforceUniqueVillagerRegistries();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag settlementList = new ListTag();
        settlements.values().forEach(settlement -> settlementList.add(settlement.toNBT()));
        tag.put("Settlements", settlementList);

        ListTag selections = new ListTag();
        selectedSettlementByPlayer.forEach((player, settlement) -> {
            if (settlements.containsKey(settlement)) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", player);
                entry.putString("Settlement", settlement);
                selections.add(entry);
            }
        });
        tag.put("Selections", selections);

        ListTag characterList = new ListTag();
        characters.values().forEach(character -> characterList.add(character.toNBT()));
        tag.put("Characters", characterList);
        writeUuidSet(tag, "RedeemedSettlerContracts", redeemedSettlerContracts);
        return tag;
    }

    public SettlementRecord createSettlement(String rawId, String rawName, UUID founder) {
        String id = normalizeId(rawId);
        if (id.isBlank() || settlements.containsKey(id)) {
            return null;
        }
        SettlementRecord settlement = new SettlementRecord(this, id, cleanText(rawName, id, 64));
        settlement.ownerId = founder;
        settlement.members.put(founder, SettlementRole.OWNER);
        settlement.addChronicle("Settlement founded.");
        settlements.put(id, settlement);
        setDirty();
        return settlement;
    }

    public SettlementRecord getSettlement(String rawId) {
        return settlements.get(normalizeId(rawId));
    }

    public Collection<SettlementRecord> getSettlements() {
        return List.copyOf(settlements.values());
    }

    public List<SettlementRecord> getOwnedSettlements(UUID playerId) {
        return settlements.values().stream()
                .filter(settlement -> playerId.equals(settlement.ownerId))
                .sorted(Comparator.comparing(SettlementRecord::getName))
                .toList();
    }

    public boolean deleteSettlement(String rawId) {
        String id = normalizeId(rawId);
        if (settlements.remove(id) == null) {
            return false;
        }
        selectedSettlementByPlayer.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        characters.values().forEach(character -> {
            if (id.equals(character.primarySeat)) {
                character.primarySeat = "";
            }
        });
        setDirty();
        return true;
    }

    public boolean selectSettlement(UUID player, String rawId) {
        String id = normalizeId(rawId);
        if (!settlements.containsKey(id)) {
            return false;
        }
        selectedSettlementByPlayer.put(player, id);
        setDirty();
        return true;
    }

    public SettlementRecord getSelectedSettlement(UUID player) {
        String id = selectedSettlementByPlayer.get(player);
        if (id != null && settlements.containsKey(id)) {
            return settlements.get(id);
        }
        List<SettlementRecord> accessible = settlements.values().stream()
                .filter(settlement -> settlement.getRole(player) != SettlementRole.OUTSIDER)
                .toList();
        if (accessible.size() == 1) {
            SettlementRecord only = accessible.get(0);
            selectedSettlementByPlayer.put(player, only.id);
            setDirty();
            return only;
        }
        return null;
    }

    public SettlementRecord findSettlementTrackingUnit(UUID unitId) {
        for (SettlementRecord settlement : settlements.values()) {
            if (settlement.isTrackedUnit(unitId)) {
                return settlement;
            }
        }
        return null;
    }

    public SettlementRecord findSettlementTrackingVillager(UUID villagerId) {
        for (SettlementRecord settlement : settlements.values()) {
            if (settlement.villagerResidents.contains(villagerId)) {
                return settlement;
            }
        }
        return null;
    }

    /**
     * Enforces the invariant that one villager UUID may belong to only one settlement.
     * Returns the number of duplicate registry entries removed.
     */
    public int removeVillagerFromOtherSettlements(UUID villagerId, String keepSettlementId) {
        int removed = 0;
        for (SettlementRecord settlement : settlements.values()) {
            if (settlement.id.equals(keepSettlementId)) continue;
            if (settlement.removeVillager(villagerId)) removed++;
        }
        if (removed > 0) setDirty();
        return removed;
    }

    /** Keeps each villager assigned to at most one settlement registry. */
    public int enforceUniqueVillagerRegistries() {
        Map<UUID, String> ownerByVillager = new HashMap<>();
        int removed = 0;
        for (SettlementRecord settlement : settlements.values()) {
            Iterator<UUID> iterator = settlement.villagerResidents.iterator();
            while (iterator.hasNext()) {
                UUID villagerId = iterator.next();
                String existingOwner = ownerByVillager.putIfAbsent(villagerId, settlement.id);
                if (existingOwner != null && !existingOwner.equals(settlement.id)) {
                    iterator.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) setDirty();
        return removed;
    }

    public SettlementRecord findSettlementByClaim(String dimension, UUID claimId) {
        if (claimId == null) {
            return null;
        }
        for (SettlementRecord settlement : settlements.values()) {
            if (settlement.containsClaim(dimension, claimId)) return settlement;
        }
        return null;
    }


    public boolean isSettlerContractRedeemed(UUID contractId) {
        return contractId != null && redeemedSettlerContracts.contains(contractId);
    }

    public boolean redeemSettlerContract(UUID contractId) {
        if (contractId == null) return false;
        boolean added = redeemedSettlerContracts.add(contractId);
        if (added) setDirty();
        return added;
    }

    public void unredeemSettlerContract(UUID contractId) {
        if (contractId != null && redeemedSettlerContracts.remove(contractId)) setDirty();
    }

    public CharacterRecord getCharacter(UUID playerId) {
        return characters.get(playerId);
    }

    public CharacterRecord getOrCreateCharacter(UUID playerId) {
        return characters.computeIfAbsent(playerId, CharacterRecord::new);
    }

    public Collection<CharacterRecord> getCharacters() {
        return List.copyOf(characters.values());
    }

    public void resetCharacter(UUID playerId) {
        characters.remove(playerId);
        selectedSettlementByPlayer.remove(playerId);
        setDirty();
    }

    /** Removes all Settlements-owned references to a player. Recruits faction/team data is deliberately untouched. */
    public int resetPlayerData(UUID playerId) {
        if (playerId == null) return 0;
        int changed = 0;
        if (characters.remove(playerId) != null) changed++;
        if (selectedSettlementByPlayer.remove(playerId) != null) changed++;
        for (SettlementRecord settlement : settlements.values()) {
            SettlementRole role = settlement.getRole(playerId);
            if (role != SettlementRole.OUTSIDER) {
                if (role == SettlementRole.OWNER) {
                    settlement.members.remove(playerId);
                    settlement.ownerId = null;
                } else {
                    settlement.members.remove(playerId);
                }
                changed++;
            }
        }
        if (changed > 0) setDirty();
        return changed;
    }

    public void clearSelection(UUID playerId) {
        if (selectedSettlementByPlayer.remove(playerId) != null) setDirty();
    }

    public void resetTraits(UUID playerId) {
        CharacterRecord character = characters.get(playerId);
        if (character != null && !character.traits.isEmpty()) {
            character.traits.clear();
            setDirty();
        }
    }

    public List<SettlementRecord> settlementsForFaction(String factionId) {
        String id = factionId == null ? "" : factionId;
        return settlements.values().stream().filter(value -> id.equals(value.factionTeam))
                .sorted(Comparator.comparing(SettlementRecord::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public int runAllCheckers() {
        enforceUniqueVillagerRegistries();
        int recovered = 0;
        for (SettlementRecord settlement : settlements.values()) {
            recovered += settlement.runManpowerChecker();
        }
        return recovered;
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
        while (normalized.startsWith("_")) normalized = normalized.substring(1);
        while (normalized.endsWith("_")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    public static String cleanText(String raw, String fallback, int maximumLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) value = fallback == null ? "" : fallback;
        value = value.replaceAll("[\\p{Cntrl}§]", "");
        return value.length() > maximumLength ? value.substring(0, maximumLength) : value;
    }

    private static boolean safeEquals(String first, String second) {
        return (first == null ? "" : first).equals(second == null ? "" : second);
    }

    public enum SettlementRole {
        OWNER,
        CO_OWNER,
        CITIZEN,
        OUTSIDER;

        public boolean canManage() {
            return this == OWNER || this == CO_OWNER;
        }
    }

    public enum SettlementRank {
        VILLAGE("Village", "Minor Settlement"),
        TOWN("Town", "Developed Settlement"),
        CITY("City", "Urban Settlement"),
        MAJOR_CITY("Major City", "Major Urban Centre");

        private final String displayName;
        private final String subtitle;

        SettlementRank(String displayName, String subtitle) {
            this.displayName = displayName;
            this.subtitle = subtitle;
        }

        public String displayName() { return displayName; }
        public String subtitle() { return subtitle; }
    }

    public static final class SettlementRecord {
        private transient SettlementData owner;
        private final String id;
        private String name;
        private UUID ownerId;
        private final Map<UUID, SettlementRole> members = new HashMap<>();
        private SettlementRank rank = SettlementRank.VILLAGE;
        /** Political capital designation is intentionally separate from physical settlement tier. */
        private boolean capital;

        private int stability = 100;
        private int food;
        private int availableManpower;
        private int maximumManpower;
        private int housingCapacity;
        private int pendingContracts;
        private long lastFoodDay = Long.MIN_VALUE;
        private long lastPlagueCheckDay = Long.MIN_VALUE;
        private long lastContractGameTime = Long.MIN_VALUE;
        private String notice = "";

        private String townSquareDimension = "";
        private BlockPos townSquare;
        private String musterGroundDimension = "";
        private BlockPos musterGround;
        private String barracksDimension = "";
        private BlockPos barracks;
        private BlockPos barracksChest;
        private String upkeepChestDimension = "";
        private BlockPos upkeepChest;
        private int barracksRadius = 12;
        private int barracksCapacity = 64;
        private AreaSelection.Shape barracksShape = AreaSelection.Shape.BOX;
        /** Political faction. Blank means this settlement is independent in Settlements diplomacy. */
        private String factionTeam = "";
        /** Hidden Recruits faction used only to make independent claims/units function correctly. */
        private String backingFactionTeam = "";
        private String claimDimension = "";
        private UUID claimId;
        private String claimName = "";
        private final List<ClaimLink> additionalClaims = new ArrayList<>();
        private long firstSettlersDueGameTime;
        private int firstSettlersRemaining;

        private final Set<UUID> villagerResidents = new HashSet<>();
        private final Set<UUID> professionalUnits = new HashSet<>();
        /** Named-company mercenaries are military residents but do not consume settlement manpower. */
        private final Set<UUID> mercenaryCompanyUnits = new HashSet<>();
        private final Map<UUID, UUID> levyUnitToBatch = new HashMap<>();
        private final Map<UUID, LevyBatch> levyBatches = new HashMap<>();
        private final List<String> chronicle = new ArrayList<>();
        private final List<String> contributions = new ArrayList<>();

        private SettlementRecord(SettlementData owner, String id, String name) {
            this.owner = owner;
            this.id = id;
            this.name = name;
        }

        private static SettlementRecord fromNBT(CompoundTag tag, SettlementData owner) {
            String id = normalizeId(tag.getString("Id"));
            SettlementRecord settlement = new SettlementRecord(owner, id, cleanText(tag.getString("Name"), id, 64));
            settlement.load(tag);
            return settlement;
        }

        private void load(CompoundTag tag) {
            if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
            try { rank = SettlementRank.valueOf(tag.getString("Rank")); } catch (IllegalArgumentException ignored) { rank = SettlementRank.VILLAGE; }
            capital = tag.getBoolean("Capital");
            stability = clamp(tag.getInt("Stability"), 0, 100);
            food = Math.max(0, tag.getInt("Food"));
            availableManpower = Math.max(0, tag.getInt("AvailableManpower"));
            maximumManpower = Math.max(0, tag.getInt("MaximumManpower"));
            housingCapacity = Math.max(0, tag.getInt("HousingCapacity"));
            pendingContracts = Math.max(0, tag.getInt("PendingContracts"));
            lastFoodDay = tag.getLong("LastFoodDay");
            lastPlagueCheckDay = tag.contains("LastPlagueCheckDay") ? tag.getLong("LastPlagueCheckDay") : Long.MIN_VALUE;
            lastContractGameTime = tag.contains("LastContractGameTime", Tag.TAG_LONG) ? tag.getLong("LastContractGameTime") : Long.MIN_VALUE;
            notice = cleanText(tag.getString("Notice"), "", 160);
            factionTeam = tag.getString("FactionTeam");
            backingFactionTeam = tag.getString("BackingFactionTeam");
            claimDimension = tag.getString("ClaimDimension");
            claimName = tag.getString("ClaimName");
            if (tag.hasUUID("ClaimId")) claimId = tag.getUUID("ClaimId");
            firstSettlersDueGameTime = Math.max(0L, tag.getLong("FirstSettlersDueGameTime"));
            firstSettlersRemaining = Math.max(0, tag.getInt("FirstSettlersRemaining"));
            for (Tag raw : tag.getList("AdditionalClaims", Tag.TAG_COMPOUND)) {
                ClaimLink link = ClaimLink.fromNBT((CompoundTag) raw);
                if (link.id() != null && !link.dimension().isBlank() && !containsClaim(link.dimension(), link.id())) {
                    additionalClaims.add(link);
                }
            }

            if (tag.contains("TownSquare", Tag.TAG_COMPOUND)) {
                CompoundTag square = tag.getCompound("TownSquare");
                townSquareDimension = square.getString("Dimension");
                townSquare = BlockPos.of(square.getLong("Pos"));
            }
            if (tag.contains("MusterGround", Tag.TAG_COMPOUND)) {
                CompoundTag point = tag.getCompound("MusterGround");
                musterGroundDimension = point.getString("Dimension");
                musterGround = BlockPos.of(point.getLong("Pos"));
            }
            if (tag.contains("UpkeepChest", Tag.TAG_COMPOUND)) {
                CompoundTag point = tag.getCompound("UpkeepChest");
                upkeepChestDimension = point.getString("Dimension");
                upkeepChest = BlockPos.of(point.getLong("Pos"));
            }
            if (tag.contains("Barracks", Tag.TAG_COMPOUND)) {
                CompoundTag point = tag.getCompound("Barracks");
                barracksDimension = point.getString("Dimension");
                barracks = BlockPos.of(point.getLong("Pos"));
                if (point.contains("Chest", Tag.TAG_LONG)) barracksChest = BlockPos.of(point.getLong("Chest"));
                barracksRadius = point.contains("Radius", Tag.TAG_INT) ? Math.max(2, point.getInt("Radius")) : 12;
                barracksCapacity = point.contains("Capacity", Tag.TAG_INT) ? Math.max(1, point.getInt("Capacity")) : 64;
                barracksShape = AreaSelection.Shape.BOX;
            }

            readUuidSet(tag, "Villagers", villagerResidents);
            readUuidSet(tag, "Professionals", professionalUnits);
            readUuidSet(tag, "MercenaryCompanyUnits", mercenaryCompanyUnits);
            // Named mercenary companies are distinct from professional manpower.
            professionalUnits.removeAll(mercenaryCompanyUnits);

            for (Tag raw : tag.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) raw;
                if (!member.hasUUID("Player")) continue;
                try {
                    members.put(member.getUUID("Player"), SettlementRole.valueOf(member.getString("Role")));
                } catch (IllegalArgumentException ignored) {
                    members.put(member.getUUID("Player"), SettlementRole.CITIZEN);
                }
            }
            if (ownerId != null) members.put(ownerId, SettlementRole.OWNER);

            for (Tag raw : tag.getList("LevyBatches", Tag.TAG_COMPOUND)) {
                LevyBatch batch = LevyBatch.fromNBT((CompoundTag) raw);
                levyBatches.put(batch.id, batch);
                batch.units.forEach(uuid -> levyUnitToBatch.put(uuid, batch.id));
            }
            readStringList(tag, "Chronicle", chronicle);
            readStringList(tag, "Contributions", contributions);
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Name", name);
            if (ownerId != null) tag.putUUID("Owner", ownerId);
            tag.putString("Rank", rank.name());
            tag.putBoolean("Capital", capital);
            tag.putInt("Stability", stability);
            tag.putInt("Food", food);
            tag.putInt("AvailableManpower", availableManpower);
            tag.putInt("MaximumManpower", maximumManpower);
            tag.putInt("HousingCapacity", housingCapacity);
            tag.putInt("PendingContracts", pendingContracts);
            tag.putLong("LastFoodDay", lastFoodDay);
            tag.putLong("LastPlagueCheckDay", lastPlagueCheckDay);
            if (lastContractGameTime != Long.MIN_VALUE) tag.putLong("LastContractGameTime", lastContractGameTime);
            tag.putString("Notice", notice);
            tag.putString("FactionTeam", factionTeam);
            tag.putString("BackingFactionTeam", backingFactionTeam);
            tag.putString("ClaimDimension", claimDimension);
            tag.putString("ClaimName", claimName);
            if (claimId != null) tag.putUUID("ClaimId", claimId);
            if (firstSettlersRemaining > 0) {
                tag.putLong("FirstSettlersDueGameTime", firstSettlersDueGameTime);
                tag.putInt("FirstSettlersRemaining", firstSettlersRemaining);
            }
            ListTag linkedClaims = new ListTag();
            additionalClaims.forEach(link -> linkedClaims.add(link.toNBT()));
            tag.put("AdditionalClaims", linkedClaims);

            if (townSquare != null) {
                CompoundTag square = new CompoundTag();
                square.putString("Dimension", townSquareDimension);
                square.putLong("Pos", townSquare.asLong());
                tag.put("TownSquare", square);
            }
            if (musterGround != null) {
                CompoundTag point = new CompoundTag();
                point.putString("Dimension", musterGroundDimension);
                point.putLong("Pos", musterGround.asLong());
                tag.put("MusterGround", point);
            }
            if (upkeepChest != null && !upkeepChestDimension.isBlank()) {
                CompoundTag point = new CompoundTag();
                point.putString("Dimension", upkeepChestDimension);
                point.putLong("Pos", upkeepChest.asLong());
                tag.put("UpkeepChest", point);
            }
            if (barracks != null) {
                CompoundTag point = new CompoundTag();
                point.putString("Dimension", barracksDimension);
                point.putLong("Pos", barracks.asLong());
                if (barracksChest != null) point.putLong("Chest", barracksChest.asLong());
                point.putInt("Radius", barracksRadius);
                point.putInt("Capacity", barracksCapacity);
                point.putString("Shape", barracksShape.name());
                tag.put("Barracks", point);
            }

            writeUuidSet(tag, "Villagers", villagerResidents);
            writeUuidSet(tag, "Professionals", professionalUnits);
            writeUuidSet(tag, "MercenaryCompanyUnits", mercenaryCompanyUnits);

            ListTag memberList = new ListTag();
            members.forEach((player, role) -> {
                CompoundTag member = new CompoundTag();
                member.putUUID("Player", player);
                member.putString("Role", role.name());
                memberList.add(member);
            });
            tag.put("Members", memberList);

            ListTag batchList = new ListTag();
            levyBatches.values().forEach(batch -> batchList.add(batch.toNBT()));
            tag.put("LevyBatches", batchList);
            writeStringList(tag, "Chronicle", chronicle);
            writeStringList(tag, "Contributions", contributions);
            return tag;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public UUID getOwnerId() { return ownerId; }
        public SettlementRank getRank() { return rank; }
        public boolean isCapital() { return capital; }
        public int getStability() { return stability; }
        public int getFood() { return food; }
        public int getAvailableManpower() { return availableManpower; }
        public int getMaximumManpower() { return maximumManpower; }
        public int getUsedManpower() { return professionalUnits.size(); }
        public int getHousingCapacity() { return housingCapacity; }
        public int getPendingContracts() { return pendingContracts; }
        public String getNotice() { return notice; }
        public String getTownSquareDimension() { return townSquareDimension; }
        public BlockPos getTownSquare() { return townSquare; }
        public String getMusterGroundDimension() { return musterGroundDimension; }
        public BlockPos getMusterGround() { return musterGround; }
        public String getBarracksDimension() { return barracksDimension; }
        public BlockPos getBarracks() { return barracks; }
        public BlockPos getBarracksChest() { return barracksChest; }
        public String getUpkeepChestDimension() { return upkeepChestDimension; }
        public BlockPos getUpkeepChest() { return upkeepChest; }
        public int getBarracksRadius() { return barracksRadius; }
        public int getBarracksCapacity() { return barracksCapacity; }
        public AreaSelection.Shape getBarracksShape() { return barracksShape; }
        public String getFactionTeam() { return factionTeam; }
        public String getBackingFactionTeam() { return backingFactionTeam; }
        /** Recruits-facing team. Political faction wins; independents use their hidden backing faction. */
        public String getEffectiveRecruitsTeam() { return factionTeam.isBlank() ? backingFactionTeam : factionTeam; }
        public String getClaimName() { return claimName; }
        public UUID getClaimId() { return claimId; }
        public String getClaimDimension() { return claimDimension; }
        public int getClaimCount() { return (claimId == null ? 0 : 1) + additionalClaims.size(); }
        public List<ClaimLink> getClaimLinks() {
            List<ClaimLink> values = new ArrayList<>();
            if (claimId != null && !claimDimension.isBlank()) values.add(new ClaimLink(claimDimension, claimId, claimName));
            values.addAll(additionalClaims);
            return List.copyOf(values);
        }
        public String getClaimSummary() {
            List<String> names = getClaimLinks().stream().map(link -> link.name().isBlank() ? link.id().toString() : link.name()).toList();
            if (names.isEmpty()) return "";
            if (names.size() <= 2) return String.join(", ", names);
            return names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2) + " more";
        }
        public boolean hasPendingFirstSettlers() { return firstSettlersRemaining > 0; }
        public long getFirstSettlersDueGameTime() { return firstSettlersDueGameTime; }
        public int getFirstSettlersRemaining() { return firstSettlersRemaining; }
        public void scheduleFirstSettlers(long dueGameTime, int count) {
            if (count <= 0 || firstSettlersRemaining > 0) return;
            firstSettlersDueGameTime = Math.max(0L, dueGameTime);
            firstSettlersRemaining = Math.max(0, count);
            dirty();
        }
        public void recordFirstSettlerArrival() {
            if (firstSettlersRemaining <= 0) return;
            firstSettlersRemaining--;
            if (firstSettlersRemaining == 0) firstSettlersDueGameTime = 0L;
            dirty();
        }

        public int getVillagerCount() { return villagerResidents.size(); }
        public Set<UUID> getVillagerIds() { return Set.copyOf(villagerResidents); }
        public int getProfessionalCount() { return professionalUnits.size(); }
        public int getMercenaryCompanyCount() { return mercenaryCompanyUnits.size(); }
        public int getLivingLevymen() { return levyUnitToBatch.size(); }
        public int getLevyReservedManpower() { return levyBatches.values().stream().mapToInt(batch -> batch.manpowerSpent).sum(); }
        public List<String> getChronicle() { return List.copyOf(chronicle); }
        public List<String> getContributions() { return List.copyOf(contributions); }
        public Map<UUID, SettlementRole> getMembers() { return Map.copyOf(members); }
        public java.util.Set<UUID> getProfessionalUnits() { return java.util.Set.copyOf(professionalUnits); }
        public java.util.Set<UUID> getMercenaryCompanyUnits() { return java.util.Set.copyOf(mercenaryCompanyUnits); }
        public java.util.Set<UUID> getLevyUnits() { return java.util.Set.copyOf(levyUnitToBatch.keySet()); }
        public java.util.Set<UUID> getAllMilitaryUnits() {
            java.util.Set<UUID> values = new java.util.HashSet<>(professionalUnits);
            values.addAll(mercenaryCompanyUnits);
            values.addAll(levyUnitToBatch.keySet());
            return java.util.Set.copyOf(values);
        }

        public double getPopulationMultiplier() {
            if (rank == SettlementRank.VILLAGE) return 2.0D;
            double progress = clampDouble((getVillagerCount() - 50) / 25.0D, 0.0D, 1.0D);
            return 2.0D + progress;
        }

        public int getCivilianPopulation() {
            return (int) Math.round(getVillagerCount() * getPopulationMultiplier());
        }

        public int getMilitaryPopulation() {
            return getProfessionalCount() + getMercenaryCompanyCount() + getLivingLevymen();
        }

        public int getDisplayedPopulation() {
            return getCivilianPopulation() + getMilitaryPopulation();
        }

        public int getSupportedManpower() {
            double stabilityFactor = stability / 100.0D;
            return Math.max(0, (int) Math.floor(getCivilianPopulation() * SettlementManager.MANPOWER_PER_POPULATION * stabilityFactor));
        }

        public int getVacantManpower() {
            return Math.max(0, maximumManpower - availableManpower - getUsedManpower() - getLevyReservedManpower());
        }

        public int getFreeHousing() {
            return Math.max(0, housingCapacity - getVillagerCount());
        }

        public boolean hasTownSquare() { return townSquare != null && !townSquareDimension.isBlank(); }
        public boolean hasMusterGround() { return musterGround != null && !musterGroundDimension.isBlank(); }
        public boolean hasBarracks() { return barracks != null && !barracksDimension.isBlank(); }
        public boolean hasClaim() { return getClaimCount() > 0; }
        public boolean containsClaim(String dimension, UUID id) {
            if (id == null) return false;
            if (id.equals(claimId) && safeEquals(dimension, claimDimension)) return true;
            return additionalClaims.stream().anyMatch(link -> id.equals(link.id()) && safeEquals(dimension, link.dimension()));
        }

        public SettlementRole getRole(UUID player) {
            if (player == null) return SettlementRole.OUTSIDER;
            if (player.equals(ownerId)) return SettlementRole.OWNER;
            return members.getOrDefault(player, SettlementRole.OUTSIDER);
        }

        public void rename(String value) {
            name = cleanText(value, name, 64);
            addChronicle("Settlement renamed to " + name + ".");
            dirty();
        }

        public void setOwner(UUID newOwner) {
            if (newOwner == null || newOwner.equals(ownerId)) return;
            UUID previousOwner = ownerId;
            if (previousOwner != null) {
                members.put(previousOwner, SettlementRole.CO_OWNER);
                CharacterRecord previousCharacter = owner.characters.get(previousOwner);
                if (previousCharacter != null && id.equals(previousCharacter.primarySeat)) previousCharacter.primarySeat = "";
            }
            ownerId = newOwner;
            members.put(newOwner, SettlementRole.OWNER);
            addChronicle("Ownership transferred.");
            dirty();
        }

        public void setRole(UUID player, SettlementRole role) {
            if (player.equals(ownerId)) return;
            if (role == SettlementRole.OUTSIDER) members.remove(player); else members.put(player, role);
            dirty();
        }

        public void setRank(SettlementRank value) {
            rank = value == null ? SettlementRank.VILLAGE : value;
            addChronicle("Settlement rank changed to " + rank.displayName() + ".");
            dirty();
        }

        public void setCapital(boolean value) {
            if (capital == value) return;
            capital = value;
            addChronicle(value ? "Designated as the political capital." : "No longer designated as the political capital.");
            dirty();
        }

        public void setStability(int value) { stability = clamp(value, 0, 100); dirty(); }
        public void changeStability(int delta) { setStability(stability + delta); }
        public void setFood(int value) { food = Math.max(0, value); dirty(); }
        public void changeFood(int delta) { setFood(food + delta); }
        public void setMaximumManpower(int value) {
            int committed = getUsedManpower() + getLevyReservedManpower();
            maximumManpower = Math.max(committed, Math.max(0, value));
            availableManpower = Math.min(availableManpower, Math.max(0, maximumManpower - committed));
            dirty();
        }
        public void changeMaximumManpower(int delta) { setMaximumManpower(maximumManpower + delta); }
        public void setAvailableManpower(int value) {
            int capacity = Math.max(0, maximumManpower - getUsedManpower() - getLevyReservedManpower());
            availableManpower = Math.max(0, Math.min(value, capacity));
            dirty();
        }
        public void changeAvailableManpower(int delta) { setAvailableManpower(availableManpower + delta); }
        public void setPendingContracts(int value) { pendingContracts = Math.max(0, value); dirty(); }
        public void changePendingContracts(int delta) { setPendingContracts(pendingContracts + delta); }
        public void setHousingCapacity(int value) { housingCapacity = Math.max(0, value); dirty(); }
        public void changeHousingCapacity(int delta) { setHousingCapacity(housingCapacity + delta); }
        public void setNotice(String value) { notice = cleanText(value, "", 160); dirty(); }
        public void setFactionTeam(String value) { factionTeam = value == null ? "" : value; dirty(); }
        public void setBackingFactionTeam(String value) { backingFactionTeam = value == null ? "" : value; dirty(); }

        public void setTownSquare(String dimension, BlockPos position) {
            townSquareDimension = dimension == null ? "" : dimension;
            townSquare = position == null ? null : position.immutable();
            dirty();
        }

        public void setMusterGround(String dimension, BlockPos position) {
            musterGroundDimension = dimension == null ? "" : dimension;
            musterGround = position == null ? null : position.immutable();
            addChronicle(position == null ? "Muster Ground cleared." : "Muster Ground established.");
            dirty();
        }

        public void setBarracks(String dimension, BlockPos position) {
            barracksDimension = dimension == null ? "" : dimension;
            barracks = position == null ? null : position.immutable();
            if (position == null) barracksChest = null;
            addChronicle(position == null ? "Barracks cleared." : "Barracks established.");
            dirty();
        }

        public void setBarracksChest(BlockPos position) {
            barracksChest = position == null ? null : position.immutable();
            dirty();
        }

        public void setUpkeepChest(String dimension, BlockPos position) {
            upkeepChestDimension = position == null || dimension == null ? "" : dimension;
            upkeepChest = position == null ? null : position.immutable();
            addChronicle(position == null ? "City Upkeep Chest cleared." : "City Upkeep Chest linked for automatic Food supply.");
            dirty();
        }

        public void setBarracksSettings(int radius, int capacity) {
            barracksRadius = Math.max(2, Math.min(64, radius));
            barracksCapacity = Math.max(1, Math.min(512, capacity));
            dirty();
        }

        public void setBarracksArea(String dimension, BlockPos first, BlockPos second, AreaSelection.Shape shape) {
            BlockPos min = AreaSelection.min(first, second);
            BlockPos max = AreaSelection.max(first, second);
            BlockPos center = new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
            int radius = Math.max(2, Math.min(64, Math.max(max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1) / 2));
            setBarracks(dimension, center);
            barracksRadius = radius;
            barracksShape = AreaSelection.Shape.BOX;
            dirty();
        }

        public boolean linkClaim(String dimension, UUID id, String name) {
            String safeDimension = dimension == null ? "" : dimension;
            String safeName = name == null ? "" : name;
            if (id == null || safeDimension.isBlank()) return false;
            if (id.equals(claimId) && safeEquals(safeDimension, claimDimension)) {
                claimName = safeName;
                dirty();
                return false;
            }
            for (int i = 0; i < additionalClaims.size(); i++) {
                ClaimLink link = additionalClaims.get(i);
                if (id.equals(link.id()) && safeEquals(safeDimension, link.dimension())) {
                    additionalClaims.set(i, new ClaimLink(safeDimension, id, safeName));
                    dirty();
                    return false;
                }
            }
            if (claimId == null || claimDimension.isBlank()) {
                claimDimension = safeDimension;
                claimId = id;
                claimName = safeName;
            } else {
                additionalClaims.add(new ClaimLink(safeDimension, id, safeName));
            }
            addChronicle("Linked claim " + (safeName.isBlank() ? id : safeName) + ".");
            dirty();
            return true;
        }

        public boolean unlinkClaim(String dimension, UUID id) {
            if (id == null) return false;
            if (id.equals(claimId) && safeEquals(dimension, claimDimension)) {
                if (additionalClaims.isEmpty()) {
                    claimDimension = "";
                    claimId = null;
                    claimName = "";
                } else {
                    ClaimLink promoted = additionalClaims.remove(0);
                    claimDimension = promoted.dimension();
                    claimId = promoted.id();
                    claimName = promoted.name();
                }
                dirty();
                return true;
            }
            boolean removed = additionalClaims.removeIf(link -> id.equals(link.id()) && safeEquals(dimension, link.dimension()));
            if (removed) dirty();
            return removed;
        }

        public void clearClaimLink() {
            claimDimension = "";
            claimId = null;
            claimName = "";
            additionalClaims.clear();
            dirty();
        }

        public void updateClaimName(String value) { claimName = value == null ? "" : value; dirty(); }

        /** Updates the display name of one linked Recruits claim without disturbing the rest of a multi-claim city. */
        public boolean updateClaimName(String dimension, UUID id, String value) {
            if (id == null) return false;
            String safeName = value == null ? "" : value;
            if (id.equals(claimId) && safeEquals(dimension, claimDimension)) {
                claimName = safeName;
                dirty();
                return true;
            }
            for (int i = 0; i < additionalClaims.size(); i++) {
                ClaimLink link = additionalClaims.get(i);
                if (id.equals(link.id()) && safeEquals(dimension, link.dimension())) {
                    additionalClaims.set(i, new ClaimLink(link.dimension(), link.id(), safeName));
                    dirty();
                    return true;
                }
            }
            return false;
        }

        public void addFood(int amount, String contributor) {
            if (amount <= 0) return;
            food += amount;
            addContribution(contributor + " deposited " + amount + " food.");
            dirty();
        }

        public void addFoodStockpile(int amount) {
            if (amount <= 0) return;
            food += amount;
            dirty();
        }

        public boolean spendFood(int amount) {
            if (amount < 0 || food < amount) return false;
            food -= amount;
            dirty();
            return true;
        }

        public boolean spendAvailableManpower(int amount) {
            if (amount < 0 || availableManpower < amount) return false;
            availableManpower -= amount;
            dirty();
            return true;
        }

        public boolean registerVillager(UUID villager) {
            boolean added = villagerResidents.add(villager);
            if (added) dirty();
            return added;
        }
        public boolean hasVillager(UUID villager) { return villagerResidents.contains(villager); }
        public boolean removeVillager(UUID villager) { boolean removed = villagerResidents.remove(villager); if (removed) dirty(); return removed; }
        public void addProfessional(UUID unit) {
            if (unit == null) return;
            boolean changed = mercenaryCompanyUnits.remove(unit);
            changed |= professionalUnits.add(unit);
            if (changed) dirty();
        }
        public boolean removeProfessional(UUID unit) { boolean removed = professionalUnits.remove(unit); if (removed) dirty(); return removed; }
        public void addMercenaryCompanyUnit(UUID unit) {
            if (unit == null) return;
            boolean changed = professionalUnits.remove(unit);
            changed |= mercenaryCompanyUnits.add(unit);
            if (changed) dirty();
        }
        public boolean removeMercenaryCompanyUnit(UUID unit) { boolean removed = unit != null && mercenaryCompanyUnits.remove(unit); if (removed) dirty(); return removed; }

        public UUID addLevyBatch(int manpowerSpent, Collection<UUID> units) {
            UUID id = UUID.randomUUID();
            LevyBatch batch = new LevyBatch(id, manpowerSpent, new HashSet<>(units));
            levyBatches.put(id, batch);
            units.forEach(unit -> levyUnitToBatch.put(unit, id));
            addChronicle("Raised a levy of " + units.size() + " men.");
            dirty();
            return id;
        }

        public void addLevymanToBatch(UUID batchId, UUID unit) {
            if (batchId == null || unit == null) return;
            LevyBatch batch = levyBatches.get(batchId);
            if (batch == null) return;
            batch.units.add(unit);
            levyUnitToBatch.put(unit, batchId);
            dirty();
        }

        public List<UUID> getLevyBatches() { return List.copyOf(levyBatches.keySet()); }
        public Set<UUID> getLevyUnitsForBatch(UUID batchId) {
            LevyBatch batch = levyBatches.get(batchId);
            return batch == null ? Set.of() : Set.copyOf(batch.units);
        }

        public int clearLevyBatch(UUID batchId) {
            LevyBatch batch = levyBatches.remove(batchId);
            if (batch == null) return 0;
            int units = batch.units.size();
            batch.units.forEach(levyUnitToBatch::remove);
            dirty();
            return units;
        }

        public LevyDeathResult removeLevyman(UUID unit) {
            UUID batchId = levyUnitToBatch.remove(unit);
            if (batchId == null) return LevyDeathResult.NONE;
            LevyBatch batch = levyBatches.get(batchId);
            if (batch == null) { dirty(); return LevyDeathResult.NONE; }
            batch.units.remove(unit);
            boolean ended = batch.units.isEmpty();
            int released = 0;
            if (ended) {
                released = batch.manpowerSpent;
                levyBatches.remove(batchId);
                addChronicle("A levy was destroyed; " + released + " MP became vacant.");
            }
            dirty();
            return new LevyDeathResult(true, ended, released);
        }

        public boolean isTrackedUnit(UUID unit) {
            return professionalUnits.contains(unit) || mercenaryCompanyUnits.contains(unit) || levyUnitToBatch.containsKey(unit);
        }

        /** Clears Settlements-owned military tracking. This intentionally does not delete external Recruits entities. */
        public void clearMilitaryTracking() {
            professionalUnits.clear();
            mercenaryCompanyUnits.clear();
            levyUnitToBatch.clear();
            levyBatches.clear();
            availableManpower = Math.min(availableManpower, maximumManpower);
            addChronicle("Administrative military tracking reset.");
            dirty();
        }

        public int runManpowerChecker() {
            int supported = getSupportedManpower();
            int committed = getUsedManpower() + getLevyReservedManpower();
            boolean changed = false;

            if (supported > maximumManpower) {
                maximumManpower++;
                changed = true;
            } else if (supported < maximumManpower && maximumManpower > committed) {
                maximumManpower--;
                int clamped = Math.min(availableManpower, Math.max(0, maximumManpower - committed));
                if (clamped != availableManpower) {
                    availableManpower = clamped;
                }
                changed = true;
            }

            int capacity = Math.max(0, maximumManpower - committed);
            int recovered = 0;
            if (availableManpower < capacity && availableManpower + committed < supported) {
                availableManpower++;
                recovered = 1;
                changed = true;
            }
            if (availableManpower > capacity) {
                availableManpower = capacity;
                changed = true;
            }
            if (changed) dirty();
            return recovered;
        }

        public boolean beginPlagueCheck(long day) {
            if (lastPlagueCheckDay == day) return false;
            lastPlagueCheckDay = day;
            dirty();
            return true;
        }



        public void processDaily(ServerLevel level, long day, CampaignData campaign) {
            if (lastFoodDay == day) return;
            lastFoodDay = day;

            int mouths = Math.max(0, getVillagerCount() + getProfessionalCount() + getMercenaryCompanyCount());
            if (mouths == 0) {
                changeStability(-1);
            } else if (food >= mouths) {
                food -= mouths;
                changeStability(2);
            } else if (food > 0) {
                food = 0;
                changeStability(-2);
            } else {
                changeStability(-5);
            }

            if (getVillagerCount() > housingCapacity) changeStability(-3);

            CharacterRecord ruler = ownerId == null || owner == null ? null : owner.getCharacter(ownerId);
            if (ruler != null && ruler.isCreated()) {
                int stabilityEffect = CampaignDefinitions.totalEffect(ruler.getTraits(), CampaignDefinitions.EffectKind.STABILITY);
                int treasuryEffect = CampaignDefinitions.totalEffect(ruler.getTraits(), CampaignDefinitions.EffectKind.TREASURY);
                if (stabilityEffect != 0) changeStability(stabilityEffect);
                if (treasuryEffect != 0 && level != null) StateTreasury.change(level, this, treasuryEffect);
            }

            if (campaign != null && ownerId != null && (capital || (factionTeam.isBlank() && rank.ordinal() >= SettlementRank.CITY.ordinal()))) {
                CampaignData.MarriageRecord marriage = campaign.getMarriage(ownerId);
                if (marriage != null) {
                    int spouseStability = marriage.effectiveStabilityBonus(owner);
                    int spouseTreasury = marriage.effectiveTreasuryPerDay(owner);
                    if (spouseStability != 0) changeStability(spouseStability);
                    if (spouseTreasury != 0 && level != null) StateTreasury.change(level, this, spouseTreasury);
                }
                int spouseIndex = 1;
                for (CampaignData.MarriageRecord additional : campaign.getAdditionalNpcMarriages(ownerId)) {
                    int stability = CampaignData.scaledSpouseEffect(additional.stabilityBonus(), spouseIndex);
                    int spouseTreasury = CampaignData.scaledSpouseEffect(additional.treasuryPerDay(), spouseIndex);
                    if (stability != 0) changeStability(stability);
                    if (spouseTreasury != 0 && level != null) StateTreasury.change(level, this, spouseTreasury);
                    spouseIndex++;
                }
            }

            dirty();
        }

        /**
         * Population growth is intentionally independent from Minecraft day length. Eligible settlements
         * generate contracts on an active-server timer so seasonal/day-length mods cannot turn one settler
         * into an hour-long wait. The very first eligible contract is immediate on the next settlement tick.
         */
        public void processSettlerContracts(long gameTime, long day, CampaignData campaign) {
            int maxPending = SettlementConfig.SETTLER_CONTRACT_MAX_PENDING.get();
            int minimumStability = SettlementConfig.SETTLER_CONTRACT_MIN_STABILITY.get();
            int reserve = SettlementConfig.SETTLER_CONTRACT_FOOD_RESERVE.get();
            int cost = SettlementConfig.SETTLER_CONTRACT_FOOD_COST.get();
            int requiredFood = Math.max(reserve, cost);
            if (pendingContracts >= maxPending || getFreeHousing() <= 0 || stability < minimumStability || food < requiredFood) return;

            long intervalTicks = settlerContractIntervalTicks();
            if (lastContractGameTime != Long.MIN_VALUE && gameTime - lastContractGameTime < intervalTicks) return;

            food = Math.max(0, food - cost);
            pendingContracts++;
            lastContractGameTime = Math.max(0L, gameTime);
            addChronicle("A Settler Contract became available.");
            if (campaign != null) {
                campaign.addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.MINOR,
                        "New settlers sought", name + " has prepared a Settler Contract.", day,
                        factionTeam, id, CampaignData.NewsVisibility.FACTION);
            }
            dirty();
        }

        public String getSettlerGrowthStatus(long gameTime) {
            int maxPending = SettlementConfig.SETTLER_CONTRACT_MAX_PENDING.get();
            int minimumStability = SettlementConfig.SETTLER_CONTRACT_MIN_STABILITY.get();
            int reserve = SettlementConfig.SETTLER_CONTRACT_FOOD_RESERVE.get();
            int cost = SettlementConfig.SETTLER_CONTRACT_FOOD_COST.get();
            int requiredFood = Math.max(reserve, cost);
            if (pendingContracts >= maxPending) return "Paused: contract queue full (" + pendingContracts + "/" + maxPending + ").";
            if (getFreeHousing() <= 0) return "Blocked: no free housing.";
            if (stability < minimumStability) return "Blocked: Stability " + stability + "/" + minimumStability + ".";
            if (food < requiredFood) return "Blocked: Food " + food + "/" + requiredFood + ".";
            int attraction = settlerAttractionPercent();
            if (lastContractGameTime == Long.MIN_VALUE) return "Eligible now · attraction " + attraction + "% · costs " + cost + " Food.";
            long intervalTicks = settlerContractIntervalTicks();
            long remainingTicks = Math.max(0L, intervalTicks - Math.max(0L, gameTime - lastContractGameTime));
            if (remainingTicks <= 0L) return "Eligible now · attraction " + attraction + "% · costs " + cost + " Food.";
            long seconds = (remainingTicks + 19L) / 20L;
            long minutes = seconds / 60L;
            long remainder = seconds % 60L;
            return "Next contract in " + (minutes > 0 ? minutes + "m " : "") + remainder + "s · attraction " + attraction + "% · costs " + cost + " Food.";
        }

        private int settlerAttractionPercent() {
            int minimumStability = SettlementConfig.SETTLER_CONTRACT_MIN_STABILITY.get();
            int requiredFood = Math.max(SettlementConfig.SETTLER_CONTRACT_FOOD_RESERVE.get(), SettlementConfig.SETTLER_CONTRACT_FOOD_COST.get());
            double stabilityQuality = 0.15D + 0.85D * clamp01((stability - minimumStability) / (double)Math.max(1, 100 - minimumStability));
            double housingQuality = 0.15D + 0.85D * clamp01(getFreeHousing() / (double)Math.max(1, housingCapacity));
            int mouths = Math.max(1, getVillagerCount() + getProfessionalCount() + getMercenaryCompanyCount());
            double foodQuality = 0.15D + 0.85D * clamp01(food / (double)Math.max(requiredFood, requiredFood + mouths * 4));
            return (int)Math.round(100.0D * stabilityQuality * housingQuality * foodQuality);
        }

        private long settlerContractIntervalTicks() {
            int fastest = Math.max(1, SettlementConfig.SETTLER_CONTRACT_FASTEST_MINUTES.get());
            int slowest = Math.max(fastest, SettlementConfig.SETTLER_CONTRACT_SLOWEST_MINUTES.get());
            double attraction = clamp01(settlerAttractionPercent() / 100.0D);
            double minutes = slowest - (slowest - fastest) * attraction;
            return Math.max(20L, Math.round(minutes * 60.0D * 20.0D));
        }

        private static double clamp01(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }

        public boolean claimContract() {
            if (pendingContracts <= 0) return false;
            pendingContracts--;
            dirty();
            return true;
        }

        public void addChronicle(String entry) {
            appendLimited(chronicle, entry, 32);
            dirty();
        }

        public void addContribution(String entry) {
            appendLimited(contributions, entry, 24);
            dirty();
        }

        private void dirty() { if (owner != null) owner.setDirty(); }
    }

    public static final class LevyBatch {
        private final UUID id;
        private final int manpowerSpent;
        private final Set<UUID> units;

        private LevyBatch(UUID id, int manpowerSpent, Set<UUID> units) {
            this.id = id;
            this.manpowerSpent = manpowerSpent;
            this.units = units;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putInt("Manpower", manpowerSpent);
            writeUuidSet(tag, "Units", units);
            return tag;
        }

        private static LevyBatch fromNBT(CompoundTag tag) {
            UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
            Set<UUID> units = new HashSet<>();
            readUuidSet(tag, "Units", units);
            return new LevyBatch(id, Math.max(0, tag.getInt("Manpower")), units);
        }
    }

    public record ClaimLink(String dimension, UUID id, String name) {
        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension == null ? "" : dimension);
            if (id != null) tag.putUUID("Id", id);
            tag.putString("Name", name == null ? "" : name);
            return tag;
        }

        private static ClaimLink fromNBT(CompoundTag tag) {
            return new ClaimLink(tag.getString("Dimension"), tag.hasUUID("Id") ? tag.getUUID("Id") : null, tag.getString("Name"));
        }
    }

    public record LevyDeathResult(boolean wasLevyman, boolean batchEnded, int releasedManpower) {
        public static final LevyDeathResult NONE = new LevyDeathResult(false, false, 0);
    }

    public static final class CharacterRecord {
        private final UUID playerId;
        private boolean created;
        private String firstName = "";
        private String middleName = "";
        private String lastName = "";
        private String faith = "Finnic Paganism";
        private String address = "Neutral";
        private String title = "Lord";
        private String house = "";
        private String primarySeat = "";
        private CharacterBodyData body;
        private final List<String> traits = new ArrayList<>();

        private CharacterRecord(UUID playerId) { this.playerId = playerId; }

        private static CharacterRecord fromNBT(CompoundTag tag) {
            CharacterRecord record = new CharacterRecord(tag.hasUUID("Player") ? tag.getUUID("Player") : null);
            record.created = tag.getBoolean("Created");
            record.firstName = cleanText(tag.getString("FirstName"), "", 24);
            record.middleName = cleanText(tag.getString("MiddleName"), "", 24);
            record.lastName = cleanText(tag.getString("LastName"), "", 24);
            record.faith = validFaith(cleanText(tag.getString("Faith"), "Finnic Paganism", 32));
            record.address = cleanText(tag.getString("Address"), "Neutral", 16);
            record.title = cleanText(tag.getString("Title"), "Lord", 32);
            record.house = cleanText(tag.getString("House"), "", 32);
            record.primarySeat = normalizeId(tag.getString("PrimarySeat"));
            if (tag.contains("Body", Tag.TAG_COMPOUND)) record.body = CharacterBodyData.fromNBT(tag.getCompound("Body"));
            readStringList(tag, "Traits", record.traits);
            return record;
        }

        private CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            if (playerId != null) tag.putUUID("Player", playerId);
            tag.putBoolean("Created", created);
            tag.putString("FirstName", firstName);
            tag.putString("MiddleName", middleName);
            tag.putString("LastName", lastName);
            tag.putString("Faith", faith);
            tag.putString("Address", address);
            tag.putString("Title", title);
            tag.putString("House", house);
            tag.putString("PrimarySeat", primarySeat);
            if (body != null) tag.put("Body", body.sanitized().toNBT());
            writeStringList(tag, "Traits", traits);
            return tag;
        }

        public UUID getPlayerId() { return playerId; }
        public boolean isCreated() { return created; }
        public String getFirstName() { return firstName; }
        public String getMiddleName() { return middleName; }
        public String getLastName() { return lastName; }
        public String getFaith() { return faith; }
        public String getAddress() { return address; }
        public String getTitle() { return title; }
        public String getHouse() { return house; }
        public String getPrimarySeat() { return primarySeat; }
        public boolean hasBodyData() { return body != null; }
        public CharacterBodyData getBody() { return body; }
        public List<String> getTraits() { return List.copyOf(traits); }

        public String getDisplayName() {
            return String.join(" ", List.of(firstName, middleName, lastName).stream().filter(value -> !value.isBlank()).toList()).trim();
        }

        public void create(String first, String middle, String last, String faith, String address, String title) {
            this.firstName = cleanText(first, "", 24);
            this.middleName = cleanText(middle, "", 24);
            this.lastName = cleanText(last, "", 24);
            this.faith = validFaith(cleanText(faith, "Finnic Paganism", 32));
            this.address = cleanText(address, "Neutral", 16);
            this.title = cleanText(title, "Lord", 32);
            this.created = !this.firstName.isBlank() && !this.lastName.isBlank();
        }

        public void rename(String first, String middle, String last) {
            this.firstName = cleanText(first, "", 24);
            this.middleName = cleanText(middle, "", 24);
            this.lastName = cleanText(last, "", 24);
            this.created = !this.firstName.isBlank() && !this.lastName.isBlank();
        }

        public void setHouse(String house) { this.house = cleanText(house, "", 32); }

        public void setPrimarySeat(String value) { primarySeat = normalizeId(value); }
        public void setFaith(String value) { this.faith = validFaith(cleanText(value, "Finnic Paganism", 32)); }
        public void setBody(CharacterBodyData value) { body = value == null ? null : value.sanitized(); }
        public void setTraits(List<String> values) { traits.clear(); if (values != null) values.stream().map(CampaignDefinitions::normalize).filter(id -> CampaignDefinitions.trait(id) != null).distinct().limit(3).forEach(traits::add); }

        private static String validFaith(String value) {
            if (value == null || value.isBlank()) return "Finnic Paganism";
            return CampaignDefinitions.isValidReligion(value) ? value : "Other";
        }
    }

    private static void appendLimited(List<String> list, String raw, int maximum) {
        String value = cleanText(raw, "", 180);
        if (value.isBlank()) return;
        list.add(0, value);
        while (list.size() > maximum) list.remove(list.size() - 1);
    }

    private static void writeUuidSet(CompoundTag tag, String key, Set<UUID> values) {
        ListTag list = new ListTag();
        values.forEach(uuid -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uuid);
            list.add(entry);
        });
        tag.put(key, list);
    }

    private static void readUuidSet(CompoundTag tag, String key, Set<UUID> output) {
        for (Tag raw : tag.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.hasUUID("Id")) output.add(entry.getUUID("Id"));
        }
    }

    private static void writeStringList(CompoundTag tag, String key, List<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        tag.put(key, list);
    }

    private static void readStringList(CompoundTag tag, String key, List<String> output) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (Tag value : list) output.add(value.getAsString());
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clampDouble(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
