package ee.lastpagans.settlements;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import ee.lastpagans.settlements.item.ModItems;
import ee.lastpagans.settlements.item.SettlerContractItem;
import ee.lastpagans.settlements.network.CharacterSheetPacket;
import ee.lastpagans.settlements.network.OpenCharacterCreationPacket;
import ee.lastpagans.settlements.network.ProfileSyncPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.SnapshotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public final class SettlementManager {
    public static final double MANPOWER_PER_POPULATION = 0.40D;
    public static final int PROFESSIONAL_WEALTH_COST = 10;
    public static final int SMALL_SETTLEMENT_POPULATION = 50;
    public static final double SMALL_LEVY_SHARE = 0.75D;
    public static final double NORMAL_LEVY_SHARE = 0.50D;

    public static final String TAG_MANAGED = "SettlementsManaged";
    public static final String TAG_KIND = "SettlementsKind";
    public static final String TAG_BATCH = "SettlementsBatch";
    public static final String TAG_SETTLEMENT = "SettlementsSettlement";
    public static final String TAG_RESIDENT = "SettlementsResident";
    public static final String TAG_MERCENARY = "SettlementsMercenary";
    public static final String TAG_MERCENARY_WAGE = "SettlementsMercenaryWage";
    public static final String TAG_MERCENARY_INTERVAL_DAYS = "SettlementsMercenaryIntervalDays";
    public static final String TAG_MERCENARY_NEXT_PAYMENT_DAY = "SettlementsMercenaryNextPaymentDay";
    public static final String TAG_MERCENARY_MISSED_PAYMENTS = "SettlementsMercenaryMissedPayments";
    public static final String TAG_MERCENARY_COMPANY = "SettlementsMercenaryCompany";
    public static final String TAG_UNIT_TYPE = "SettlementsUnitType";
    public static final String TAG_BARD = "SettlementsBard";
    public static final int MERCENARY_DEFAULT_WAGE = 20;
    public static final int MERCENARY_DEFAULT_INTERVAL_DAYS = 7;
    public static final int MERCENARY_DESERTION_MISSES = 3;

    public static final TagKey<Item> CURRENCY = TagKey.create(
            Registries.ITEM,
            new ResourceLocation(Settlements.MOD_ID, "currency")
    );
    public static final TagKey<Item> BARD_INSTRUMENTS = TagKey.create(
            Registries.ITEM, new ResourceLocation(Settlements.MOD_ID, "bard_instruments")
    );
    private static final Map<UUID, PendingSettlementDeletion> PENDING_SETTLEMENT_DELETIONS = new HashMap<>();
    private static final long SETTLEMENT_DELETE_CONFIRM_MILLIS = 30_000L;

    private SettlementManager() {
    }

    public static void open(ServerPlayer player) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord settlement = data.getSelectedSettlement(player.getUUID());
        if (settlement == null) {
            player.sendSystemMessage(Component.literal("No settlement selected. Use /settlements list or /settlements open <id>."));
            return;
        }
        open(player, settlement.getId());
    }

    public static boolean open(ServerPlayer player, String settlementId) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (settlement == null) return false;
        if (!canView(player, settlement)) {
            player.sendSystemMessage(Component.literal("You cannot view that settlement."));
            return false;
        }
        data.selectSettlement(player.getUUID(), settlement.getId());
        sendSnapshot(player, settlement, true, "Settlement ready.");
        return true;
    }

    public static void sendSnapshot(ServerPlayer player, SettlementData.SettlementRecord settlement, boolean open, String message) {
        SettlementNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SnapshotPacket(open, SettlementSnapshot.from(settlement, player, message))
        );
    }

    public static boolean canView(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (player.createCommandSourceStack().hasPermission(2)) return true;
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (extra.settlementProtected(settlement.getId()) || extra.factionProtected(settlement.getFactionTeam())) return false;

        String factionId = settlement.getFactionTeam();
        if (factionId != null && !factionId.isBlank()) {
            return factionId.equals(CampaignManager.factionId(player));
        }

        return settlement.getRole(player.getUUID()) != SettlementData.SettlementRole.OUTSIDER;
    }

    public static boolean canManage(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (player.createCommandSourceStack().hasPermission(2)) return true;
        return canView(player, settlement) && settlement.getRole(player.getUUID()).canManage();
    }

    public static boolean canContribute(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (player.createCommandSourceStack().hasPermission(2)) return true;
        if (!canView(player, settlement)) return false;
        SettlementData.SettlementRole role = settlement.getRole(player.getUUID());
        return role.canManage() || role == SettlementData.SettlementRole.CITIZEN;
    }

    public static void handleAction(ServerPlayer player, String settlementId, SettlementAction action) {
        handleAction(player, settlementId, action, "", 0);
    }

    public static void handleAction(ServerPlayer player, String settlementId, SettlementAction action, String argument, int amount) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (settlement == null) {
            player.sendSystemMessage(Component.literal("That settlement no longer exists."));
            return;
        }
        if (action == SettlementAction.OPEN_CHARACTER) {
            if (canView(player, settlement)) openCharacterSheet(player, true);
            else player.sendSystemMessage(Component.literal("You cannot access this settlement."));
            return;
        }
        if (action == SettlementAction.OPEN_MAIN) {
            CampaignManager.openMain(player);
            return;
        }
        if (action == SettlementAction.SELECT_PREVIOUS || action == SettlementAction.SELECT_NEXT) {
            cycleSettlement(player, data, settlement, action == SettlementAction.SELECT_NEXT, "MILITARY".equalsIgnoreCase(argument));
            return;
        }
        if (action == SettlementAction.DELETE_SETTLEMENT) {
            String result;
            if (!canView(player, settlement)) result = "You cannot access this settlement.";
            else if (!player.getUUID().equals(settlement.getOwnerId())) result = "Only the settlement's actual owner may permanently delete it. Administrators should use the explicit admin reset command instead.";
            else result = deleteSettlement(player, settlement, argument);
            if (data.getSettlement(settlement.getId()) == null) {
                player.sendSystemMessage(Component.literal(result));
                CampaignManager.openMain(player);
                CampaignManager.sendMapMarkers(player);
                return;
            }
            sendSnapshot(player, settlement, false, result);
            return;
        }

        String result;
        if (!canView(player, settlement)) result = "You cannot access this settlement.";
        else result = switch (action) {
            case RECRUIT_ONE -> requireManager(player, settlement, () -> recruitProfessionals(player, settlement, CampaignData.UnitType.RECRUIT, 1));
            case RECRUIT_FIVE -> requireManager(player, settlement, () -> recruitProfessionals(player, settlement, CampaignData.UnitType.RECRUIT, 5));
            case RECRUIT_TEN -> requireManager(player, settlement, () -> recruitProfessionals(player, settlement, CampaignData.UnitType.RECRUIT, 10));
            case RECRUIT_TWENTY_FIVE -> requireManager(player, settlement, () -> recruitProfessionals(player, settlement, CampaignData.UnitType.RECRUIT, 25));
            case RECRUIT_UNIT -> requireManager(player, settlement, () -> {
                CampaignData.UnitType type = CampaignData.UnitType.parse(argument);
                if (type == null || type == CampaignData.UnitType.LEVY) return "Choose a professional unit type.";
                if (amount != 1 && amount != 5 && amount != 10 && amount != 25) return "Recruitment amount must be 1, 5, 10, or 25.";
                return recruitProfessionals(player, settlement, type, amount);
            });
            case RAISE_LEVY -> requireManager(player, settlement, () -> CampaignSystemsManager.raiseLevy(player, settlement));
            case RETURN_LEVY -> requireManager(player, settlement, () -> CampaignSystemsManager.returnLevy(player, settlement));
            case RETURN_TO_BARRACKS -> requireManager(player, settlement, () -> returnToBarracks(player, settlement));
            case TOGGLE_NEARBY_BARD -> requireManager(player, settlement, () -> toggleNearbyBard(player, settlement));
            case RUN_CHECKER -> requireManager(player, settlement, () -> {
                int recovered = settlement.runManpowerChecker();
                return recovered > 0 ? "Recovered " + recovered + " available MP." : "No manpower recovered.";
            });
            case DEPOSIT_FOOD -> requireContributor(player, settlement, () -> depositFood(player, settlement));
            case DEPOSIT_CURRENCY -> requireContributor(player, settlement, () -> depositCurrency(player, settlement));
            case WITHDRAW_CURRENCY_ONE -> requireManager(player, settlement, () -> withdrawCurrency(player, settlement, 1));
            case WITHDRAW_CURRENCY_TEN -> requireManager(player, settlement, () -> withdrawCurrency(player, settlement, 10));
            case SET_TAX_POLICY -> requireManager(player, settlement, () -> setTaxPolicy(player, settlement, argument));
            case CLAIM_CONTRACT -> requireContributor(player, settlement, () -> claimSettlerContract(player, settlement));
            case SET_CUSTOM_BANNER -> requireManager(player, settlement, () -> setSettlementBanner(player, settlement));
            case USE_FACTION_BANNER -> requireManager(player, settlement, () -> useFactionBanner(player, settlement));
            case CLEAR_BANNER -> requireManager(player, settlement, () -> clearSettlementBanner(player, settlement));
            case LINK_CURRENT_CLAIM -> requireManager(player, settlement, () -> linkCurrentClaim(player, settlement));
            case UNLINK_CURRENT_CLAIM -> requireManager(player, settlement, () -> unlinkCurrentClaim(player, settlement));
            case CLEAR_CLAIMS -> requireManager(player, settlement, () -> clearClaims(settlement));
            case RENAME_SETTLEMENT -> requireManager(player, settlement, () -> renameSettlement(player, settlement, argument));
            case TRANSFER_SETTLEMENT -> requireOwner(player, settlement, () -> transferSettlement(player, settlement, argument));
            case ADD_MEMBER_CITIZEN -> requireManager(player, settlement, () -> setSettlementMember(player, settlement, argument, SettlementData.SettlementRole.CITIZEN));
            case ADD_MEMBER_COOWNER -> requireManager(player, settlement, () -> setSettlementMember(player, settlement, argument, SettlementData.SettlementRole.CO_OWNER));
            case REMOVE_MEMBER -> requireManager(player, settlement, () -> removeSettlementMember(player, settlement, argument));
            case SET_TOWN_SQUARE -> requireManager(player, settlement, () -> setTownSquareHere(player, settlement));
            case SET_BARRACKS -> requireManager(player, settlement, () -> setBarracksHere(player, settlement));
            case SET_UPKEEP_CHEST -> requireManager(player, settlement, () -> setUpkeepChest(player, settlement));
            case CLEAR_UPKEEP_CHEST -> requireManager(player, settlement, () -> { settlement.setUpkeepChest("", null); return "City Upkeep Chest cleared."; });
            case HOUSING_POS1 -> requireManager(player, settlement, () -> regionPos1(player, settlement, "Housing"));
            case HOUSING_POS2 -> requireManager(player, settlement, () -> regionPos2(player, settlement, "housing"));
            case MUSTER_POS1 -> requireManager(player, settlement, () -> regionPos1(player, settlement, "Muster Ground"));
            case MUSTER_POS2 -> requireManager(player, settlement, () -> regionPos2(player, settlement, "muster"));
            case PRAYER_POS1 -> requireManager(player, settlement, () -> regionPos1(player, settlement, "Prayer Site"));
            case PRAYER_POS2 -> requireManager(player, settlement, () -> regionPos2(player, settlement, "prayer"));
            case RECALCULATE_HOUSING -> requireManager(player, settlement, () -> "Housing recalculated: " + CampaignSystemsManager.recalculateHousing(player.serverLevel(), settlement) + " bed(s).");
            case DELETE_SETTLEMENT -> "Deletion handled separately.";
            case OPEN_CHARACTER, OPEN_MAIN, SELECT_PREVIOUS, SELECT_NEXT -> "Screen opened.";
            case REFRESH -> "Stats refreshed.";
        };
        sendSnapshot(player, settlement, false, result);
        CampaignManager.sendMapMarkers(player);
    }

    private static void cycleSettlement(ServerPlayer player, SettlementData data, SettlementData.SettlementRecord current,
                                        boolean forward, boolean militaryScreen) {
        List<SettlementData.SettlementRecord> accessible = data.getSettlements().stream()
                .filter(settlement -> canView(player, settlement))
                .sorted(java.util.Comparator.comparing(SettlementData.SettlementRecord::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (accessible.isEmpty()) {
            player.sendSystemMessage(Component.literal("You cannot access any settlements."));
            return;
        }
        int index = accessible.indexOf(current);
        if (index < 0) index = 0;
        int next = Math.floorMod(index + (forward ? 1 : -1), accessible.size());
        SettlementData.SettlementRecord target = accessible.get(next);
        data.selectSettlement(player.getUUID(), target.getId());
        if (militaryScreen) CampaignManager.openMilitary(player, "Selected " + target.getName() + ".");
        else sendSnapshot(player, target, true, "Selected " + target.getName() + ".");
    }

    private static String requireManager(ServerPlayer player, SettlementData.SettlementRecord settlement, ActionSupplier action) {
        return canManage(player, settlement) ? action.get() : "Only the owner, a co-owner, or an administrator may do that.";
    }

    private static String requireContributor(ServerPlayer player, SettlementData.SettlementRecord settlement, ActionSupplier action) {
        return canContribute(player, settlement) ? action.get() : "Only settlement members may do that.";
    }

    private static String requireOwner(ServerPlayer player, SettlementData.SettlementRecord settlement, ActionSupplier action) {
        return player.createCommandSourceStack().hasPermission(2) || player.getUUID().equals(settlement.getOwnerId())
                ? action.get() : "Only the settlement owner or an administrator may do that.";
    }

    public static String recruitProfessionals(ServerPlayer player, SettlementData.SettlementRecord settlement, int amount) {
        return recruitProfessionals(player, settlement, CampaignData.UnitType.RECRUIT, amount);
    }

    public static String recruitProfessionals(ServerPlayer player, SettlementData.SettlementRecord settlement, CampaignData.UnitType unitType, int amount) {
        if (amount <= 0) return "Invalid recruitment amount.";
        String validation = validateMilitarySpawnSetup(player.getServer(), settlement);
        if (validation != null) return validation;
        int wealthPerUnit = professionalCost(unitType);
        int wealthCost = amount * wealthPerUnit;
        CampaignSystemsData campaignSystems = CampaignSystemsData.get(player.serverLevel());
        int foreignAvailable = campaignSystems.foreignManpower(settlement.getId());
        if (settlement.getAvailableManpower() + foreignAvailable < amount)
            return "Not enough recruitable manpower. Available: " + settlement.getAvailableManpower() + " native + " + foreignAvailable + " foreign.";
        if (settlement.getWealth() < wealthCost) return "Not enough wealth. Required: " + wealthCost + ".";

        ServerLevel level = resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        PlayerTeam team = settlement.getEffectiveRecruitsTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam());
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            AbstractRecruitEntity recruit = spawnRecruit(level, player, settlement, unitType, i, team, false, null);
            if (recruit == null) { discardSpawned(level, spawned); return "Recruit spawning failed; no MP or wealth was charged."; }
            spawned.add(recruit.getUUID());
        }
        // Use treaty-transferred foreign manpower first so its stability penalty disappears once those soldiers
        // have actually been absorbed into the army. Native manpower remains governed by the normal MP ceiling.
        int foreignSpent = Math.min(amount, campaignSystems.foreignManpower(settlement.getId()));
        if (foreignSpent > 0) campaignSystems.spendForeignManpower(settlement.getId(), foreignSpent);
        int nativeSpent = amount - foreignSpent;
        if (nativeSpent > 0) settlement.spendAvailableManpower(nativeSpent);
        if (campaignSystems.foreignManpower(settlement.getId()) <= 0) campaignSystems.removeModifier(settlement.getId(), "foreign_soldiers");
        settlement.spendWealth(wealthCost);
        spawned.forEach(settlement::addProfessional);
        settlement.addChronicle("Recruited " + amount + " " + unitType.displayName() + (amount == 1 ? "" : "s") + ".");
        return "Spawned " + amount + " unhired " + unitType.displayName() + (amount == 1 ? "" : "s") + ". Recruits' normal hiring cost still applies.";
    }

    private static int professionalCost(CampaignData.UnitType type) {
        return switch (type) {
            case RECRUIT -> 10;
            case SHIELDMAN, BOWMAN -> 15;
            case CROSSBOWMAN -> 20;
            case HORSEMAN -> 30;
            case NOMAD -> 25;
            case LEVY -> 0;
        };
    }

    public static String raiseLevy(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        String validation = validateMilitarySpawnSetup(player.getServer(), settlement);
        if (validation != null) return validation;
        int available = settlement.getAvailableManpower();
        if (available <= 0) return "No available manpower to mobilise.";
        double share = settlement.getCivilianPopulation() < SMALL_SETTLEMENT_POPULATION ? SMALL_LEVY_SHARE : NORMAL_LEVY_SHARE;
        int manpowerSpent = Math.max(1, (int) Math.floor(available * share));
        int count = manpowerSpent * 2;
        ServerLevel level = resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        PlayerTeam team = settlement.getEffectiveRecruitsTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam());
        UUID temporaryBatch = UUID.randomUUID();
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AbstractRecruitEntity recruit = spawnRecruit(level, player, settlement, CampaignData.UnitType.LEVY, i, team, true, temporaryBatch);
            if (recruit == null) { discardSpawned(level, spawned); return "Levy spawning failed; no manpower was charged."; }
            spawned.add(recruit.getUUID());
        }
        settlement.spendAvailableManpower(manpowerSpent);
        UUID storedBatch = settlement.addLevyBatch(manpowerSpent, spawned);
        for (UUID uuid : spawned) { Entity entity = level.getEntity(uuid); if (entity != null) entity.getPersistentData().putUUID(TAG_BATCH, storedBatch); }
        int percent = (int) Math.round(share * 100.0D);
        return "Raised " + count + " Levymen using " + manpowerSpent + " available MP (" + percent + "% mobilisation).";
    }

    static AbstractRecruitEntity spawnRecruit(ServerLevel level, ServerPlayer actor, SettlementData.SettlementRecord settlement,
                                                       CampaignData.UnitType unitType, int index, PlayerTeam team, boolean levy, UUID batchId) {
        ItemStack template = CampaignData.get(level).resolveUnitTemplate(settlement, unitType);
        return spawnRecruitFromTemplate(level, actor, settlement, unitType, index, team, levy, batchId, template);
    }

    static AbstractRecruitEntity spawnRecruitFromTemplate(ServerLevel level, ServerPlayer actor, SettlementData.SettlementRecord settlement,
                                                            CampaignData.UnitType unitType, int index, PlayerTeam team, boolean levy,
                                                            UUID batchId, ItemStack template) {
        return spawnRecruitFromTemplate(level, actor, settlement, unitType, index, team, levy, batchId, template, "");
    }

    static AbstractRecruitEntity spawnRecruitFromTemplate(ServerLevel level, ServerPlayer actor, SettlementData.SettlementRecord settlement,
                                                            CampaignData.UnitType unitType, int index, PlayerTeam team, boolean levy,
                                                            UUID batchId, ItemStack template, String mercenaryCompanyId) {
        if (template == null) template = ItemStack.EMPTY;
        EntityType<?> entityType = null;
        CompoundTag templateTag = null;
        if (!template.isEmpty() && template.getItem() instanceof SpawnEggItem egg) {
            entityType = egg.getType(template.getTag());
            templateTag = template.getTag() == null ? null : template.getTag().copy();
        }
        if (entityType == null) entityType = fallbackType(unitType);
        Entity raw = entityType.create(level);
        if (!(raw instanceof AbstractRecruitEntity recruit)) return null;
        BlockPos spawnPos = CampaignSystemsManager.findMusterSpawn(level, settlement, index);
        recruit.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        recruit.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.COMMAND, null, null);
        if (templateTag != null) {
            sanitizeTemplate(templateTag);
            EntityType.updateCustomEntityTag(level, actor, recruit, templateTag);
            recruit.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        }
        recruit.getPersistentData().putBoolean(TAG_MANAGED, true);
        recruit.getPersistentData().putString(TAG_KIND, levy ? "levy" : "professional");
        recruit.getPersistentData().putString(TAG_SETTLEMENT, settlement.getId());
        recruit.getPersistentData().putString(TAG_UNIT_TYPE, unitType.name());
        if (mercenaryCompanyId != null && !mercenaryCompanyId.isBlank())
            recruit.getPersistentData().putString(TAG_MERCENARY_COMPANY, mercenaryCompanyId);
        if (batchId != null) recruit.getPersistentData().putUUID(TAG_BATCH, batchId);
        if (template.getTag() != null && template.getTag().getBoolean(TAG_MERCENARY)) {
            int wage = Math.max(1, template.getTag().getInt(TAG_MERCENARY_WAGE));
            int interval = Math.max(1, template.getTag().getInt(TAG_MERCENARY_INTERVAL_DAYS));
            recruit.getPersistentData().putBoolean(TAG_MERCENARY, true);
            recruit.getPersistentData().putInt(TAG_MERCENARY_WAGE, wage);
            recruit.getPersistentData().putInt(TAG_MERCENARY_INTERVAL_DAYS, interval);
            recruit.getPersistentData().putLong(TAG_MERCENARY_NEXT_PAYMENT_DAY, level.getDayTime() / 24000L + interval);
            recruit.getPersistentData().putInt(TAG_MERCENARY_MISSED_PAYMENTS, 0);
        }
        if (settlement.hasBarracks()) {
            BlockPos upkeep = settlement.getBarracksChest() != null ? settlement.getBarracksChest() : settlement.getBarracks();
            recruit.setUpkeepPos(upkeep);
        }
        if (levy) { recruit.setCustomName(Component.literal("Levyman")); recruit.setCustomNameVisible(false); }
        if (!level.addFreshEntity(recruit)) return null;
        if (team != null) {
            try { FactionEvents.addRecruitToTeam(recruit, team, level); }
            catch (RuntimeException exception) { level.getScoreboard().addPlayerToTeam(recruit.getScoreboardName(), team); }
        }
        if (recruit.getPersistentData().getBoolean(TAG_MERCENARY)) {
            registerMercenaryContract(level, recruit, settlement, actor == null ? null : actor.getUUID());
        }
        return recruit;
    }

    private static EntityType<?> fallbackType(CampaignData.UnitType type) {
        return switch (type) {
            case SHIELDMAN -> ModEntityTypes.RECRUIT_SHIELDMAN.get();
            case BOWMAN -> ModEntityTypes.BOWMAN.get();
            case CROSSBOWMAN -> ModEntityTypes.CROSSBOWMAN.get();
            case HORSEMAN -> ModEntityTypes.HORSEMAN.get();
            case NOMAD -> ModEntityTypes.NOMAD.get();
            case RECRUIT, LEVY -> ModEntityTypes.RECRUIT.get();
        };
    }

    private static void sanitizeTemplate(CompoundTag root) {
        if (!root.contains("EntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        CompoundTag entity = root.getCompound("EntityTag");
        entity.remove("UUID"); entity.remove("UUIDMost"); entity.remove("UUIDLeast"); entity.remove("Pos");
        entity.remove("Motion"); entity.remove("Rotation"); entity.remove("Passengers"); entity.remove("Team");
        entity.remove("Owner"); entity.remove("OwnerUUID"); entity.remove("isOwned");
        entity.remove("Group"); entity.remove("MountUUID"); entity.remove("ProtectUUID"); entity.remove("UpkeepUUID");
        entity.remove("UpkeepPosX"); entity.remove("UpkeepPosY"); entity.remove("UpkeepPosZ");
        entity.remove(TAG_SETTLEMENT); entity.remove(TAG_BATCH); entity.remove(TAG_MANAGED); entity.remove(TAG_KIND);
        entity.remove(TAG_UNIT_TYPE);
        if (entity.contains("ForgeData", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag forgeData = entity.getCompound("ForgeData");
            forgeData.remove(TAG_SETTLEMENT); forgeData.remove(TAG_BATCH); forgeData.remove(TAG_MANAGED);
            forgeData.remove(TAG_KIND); forgeData.remove(TAG_UNIT_TYPE);
            entity.put("ForgeData", forgeData);
        }
        root.put("EntityTag", entity);
    }

    public static String linkCurrentClaim(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (ClaimEvents.recruitsClaimManager == null) return "Recruits claim manager is not ready.";
        RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(player.blockPosition()));
        if (claim == null) return "Stand inside the Recruits claim you want to link.";
        String dimension = player.serverLevel().dimension().location().toString();
        SettlementData.SettlementRecord existing = SettlementData.get(player.serverLevel()).findSettlementByClaim(dimension, claim.getUUID());
        if (existing != null && existing != settlement) return "That claim is already linked to " + existing.getName() + ".";
        String expectedOwner = settlement.getEffectiveRecruitsTeam();
        if (expectedOwner.isBlank() && settlement.getFactionTeam().isBlank()) {
            settlement.setBackingFactionTeam(BackingFactionManager.ensureForIndependent(player));
            expectedOwner = settlement.getEffectiveRecruitsTeam();
        }
        String claimOwner = claim.getOwnerFactionStringID();
        if (!expectedOwner.isBlank() && claimOwner != null && !claimOwner.isBlank() && !expectedOwner.equals(claimOwner))
            return "That Recruits claim belongs to a different polity.";
        boolean added = settlement.linkClaim(dimension, claim.getUUID(), claim.getName());
        if (!expectedOwner.isBlank()) BackingFactionManager.migrateClaimOwnership(player.getServer(), settlement, expectedOwner);
        return added ? "Linked claim " + claim.getName() + ". Total claims: " + settlement.getClaimCount() + "."
                : "That claim is already linked to this settlement.";
    }

    public static String unlinkCurrentClaim(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (ClaimEvents.recruitsClaimManager == null) return "Recruits claim manager is not ready.";
        RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(player.blockPosition()));
        if (claim == null) return "Stand inside the linked Recruits claim you want to remove.";
        boolean removed = settlement.unlinkClaim(player.serverLevel().dimension().location().toString(), claim.getUUID());
        return removed ? "Unlinked claim " + claim.getName() + ". Remaining claims: " + settlement.getClaimCount() + "."
                : "That claim is not linked to this settlement.";
    }

    public static String clearClaims(SettlementData.SettlementRecord settlement) {
        int count = settlement.getClaimCount();
        settlement.clearClaimLink();
        return "Removed all " + count + " linked claim(s).";
    }

    private static String setSettlementBanner(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BannerItem)) return "Hold a placed-style Minecraft banner item in your main hand.";
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.visualFor(settlement.getId()).setCustomBanner(held);
        campaign.setDirty(); settlement.addChronicle("Adopted a custom settlement banner.");
        return "Custom settlement banner saved.";
    }

    private static String useFactionBanner(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.visualFor(settlement.getId()).setBannerMode(CampaignData.BannerMode.FACTION); campaign.setDirty();
        return "Settlement now uses its faction banner.";
    }

    private static String clearSettlementBanner(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.visualFor(settlement.getId()).clearCustomBanner(); campaign.setDirty();
        return "Settlement banner cleared.";
    }

    public static void scheduleFirstSettlers(ServerPlayer founder, SettlementData.SettlementRecord settlement) {
        if (founder == null || settlement == null || !SettlementConfig.SPAWN_FIRST_SETTLERS.get()) return;
        int count = Math.max(0, SettlementConfig.FIRST_SETTLER_COUNT.get());
        if (count <= 0) return;
        int min = Math.max(0, SettlementConfig.FIRST_SETTLER_MIN_DELAY_SECONDS.get());
        int max = Math.max(min, SettlementConfig.FIRST_SETTLER_MAX_DELAY_SECONDS.get());
        int delay = min + (max > min ? founder.getRandom().nextInt(max - min + 1) : 0);
        long due = founder.getServer().overworld().getGameTime() + delay * 20L;
        settlement.scheduleFirstSettlers(due, count);
        settlement.addChronicle("The first settlers are on their way.");
    }

    public static void processFirstSettlerArrivals(MinecraftServer server) {
        if (server == null || !SettlementConfig.SPAWN_FIRST_SETTLERS.get()) return;
        SettlementData data = SettlementData.get(server.overworld());
        long now = server.overworld().getGameTime();
        for (SettlementData.SettlementRecord settlement : data.getSettlements()) {
            if (!settlement.hasPendingFirstSettlers() || now < settlement.getFirstSettlersDueGameTime()) continue;
            UUID ownerId = settlement.getOwnerId();
            ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
            if (owner == null || !settlement.hasTownSquare()) continue;
            ServerLevel level = resolveTownSquareLevel(server, settlement);
            if (level == null) continue;

            int before = settlement.getFirstSettlersRemaining();
            int attempts = before;
            for (int index = 0; index < attempts && settlement.hasPendingFirstSettlers(); index++) {
                if (!spawnFirstSettler(level, settlement, index)) break;
                settlement.recordFirstSettlerArrival();
            }
            if (before > 0 && !settlement.hasPendingFirstSettlers()) {
                settlement.addChronicle("The first settlers reached the village.");
                owner.sendSystemMessage(Component.literal("The first settlers have reached your village."));
                owner.sendSystemMessage(Component.literal("Provide free housing and keep at least "
                        + SettlementConfig.SETTLER_CONTRACT_FOOD_RESERVE.get() + " Food with Stability at "
                        + SettlementConfig.SETTLER_CONTRACT_MIN_STABILITY.get() + "+ to attract more settlers."));
            }
        }
    }

    private static boolean spawnFirstSettler(ServerLevel level, SettlementData.SettlementRecord settlement, int index) {
        BlockPos spawn = findTownSquareSpawn(level, settlement.getTownSquare(), index);
        if (spawn == null) return false;
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return false;
        villager.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.COMMAND, null, null);
        villager.getPersistentData().putBoolean(TAG_RESIDENT, true);
        villager.getPersistentData().putString(TAG_SETTLEMENT, settlement.getId());
        if (!level.addFreshEntity(villager)) return false;
        reconcileResidentVillager(level, villager);
        if (!settlement.getEffectiveRecruitsTeam().isBlank()) {
            PlayerTeam team = level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam());
            if (team != null) level.getScoreboard().addPlayerToTeam(villager.getScoreboardName(), team);
        }
        return true;
    }

    private static BlockPos findTownSquareSpawn(ServerLevel level, BlockPos center, int index) {
        if (level == null || center == null) return null;
        int[][] offsets = {
                {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2},
                {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {4, 4}, {-4, 4}, {4, -4}, {-4, -4}
        };
        for (int step = 0; step < offsets.length; step++) {
            int[] offset = offsets[(step + Math.max(0, index)) % offsets.length];
            int x = center.getX() + offset[0];
            int z = center.getZ() + offset[1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeSpawn(level, candidate)) return candidate;
        }
        return null;
    }

    public static ContractUseResult useSettlerContract(ServerPlayer player, String settlementId, UUID contractId) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (settlement == null) return new ContractUseResult(false, "This contract's settlement no longer exists.");
        if (data.isSettlerContractRedeemed(contractId)) return new ContractUseResult(false, "This Settler Contract has already been used.");
        if (!canContribute(player, settlement)) return new ContractUseResult(false, "You are not a member of this settlement.");
        if (!settlement.hasTownSquare()) return new ContractUseResult(false, "The settlement has no town square.");
        if (settlement.getFreeHousing() <= 0) return new ContractUseResult(false, "The settlement has no free housing.");

        ServerLevel level = resolveTownSquareLevel(player.getServer(), settlement);
        if (level == null) return new ContractUseResult(false, "The town-square dimension is unavailable.");
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return new ContractUseResult(false, "Villager spawning failed.");

        // Redeem the unique contract token before spawning so duplicated packets or copied items cannot reuse it.
        if (!data.redeemSettlerContract(contractId)) {
            return new ContractUseResult(false, "This Settler Contract has already been used.");
        }

        BlockPos spawn = settlement.getTownSquare();
        villager.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.COMMAND, null, null);
        villager.getPersistentData().putBoolean(TAG_RESIDENT, true);
        villager.getPersistentData().putString(TAG_SETTLEMENT, settlement.getId());
        if (!level.addFreshEntity(villager)) {
            data.unredeemSettlerContract(contractId);
            return new ContractUseResult(false, "Villager spawning failed.");
        }

        reconcileResidentVillager(level, villager);
        if (!settlement.getEffectiveRecruitsTeam().isBlank()) {
            PlayerTeam team = level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam());
            if (team != null) level.getScoreboard().addPlayerToTeam(villager.getScoreboardName(), team);
        }
        settlement.addChronicle("A new settler arrived.");
        return new ContractUseResult(true, "A registered settler arrived at " + settlement.getName() + ".");
    }

    public static String registerVillager(ServerPlayer player, SettlementData.SettlementRecord settlement, Villager villager) {
        if (!canManage(player, settlement)) return "You cannot register residents here.";

        SettlementData data = SettlementData.get(player.serverLevel());
        UUID villagerId = villager.getUUID();
        SettlementData.SettlementRecord trackedBy = data.findSettlementTrackingVillager(villagerId);
        String taggedSettlementId = villager.getPersistentData().getString(TAG_SETTLEMENT);
        boolean alreadyHere = trackedBy == settlement
                || (villager.getPersistentData().getBoolean(TAG_RESIDENT)
                && settlement.getId().equals(taggedSettlementId));

        // Remove any corrupt duplicate references before applying the one authoritative assignment.
        data.removeVillagerFromOtherSettlements(villagerId, settlement.getId());
        villager.getPersistentData().putBoolean(TAG_RESIDENT, true);
        villager.getPersistentData().putString(TAG_SETTLEMENT, settlement.getId());
        boolean added = settlement.registerVillager(villagerId);

        if (alreadyHere || !added) {
            return "That villager is already registered in " + settlement.getName() + ".";
        }
        if (trackedBy != null || (!taggedSettlementId.isBlank() && !taggedSettlementId.equals(settlement.getId()))) {
            return "Moved villager registration to " + settlement.getName() + ".";
        }
        return "Registered villager in " + settlement.getName() + ".";
    }

    /**
     * Reconciles a resident villager loaded from disk or spawned by a contract.
     * This method is intentionally idempotent and is the only automatic registration path.
     */
    public static void reconcileResidentVillager(ServerLevel level, Villager villager) {
        if (!villager.getPersistentData().getBoolean(TAG_RESIDENT)) return;
        String settlementId = villager.getPersistentData().getString(TAG_SETTLEMENT);
        if (settlementId.isBlank()) return;

        SettlementData data = SettlementData.get(level);
        SettlementData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (settlement == null) return;
        data.removeVillagerFromOtherSettlements(villager.getUUID(), settlement.getId());
        settlement.registerVillager(villager.getUUID());
    }

    private static String depositFood(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty() || !held.isEdible()) return "Hold edible food in your main hand.";
        FoodProperties properties = held.getFoodProperties(player);
        if (properties == null) return "That item has no food value.";
        int count = held.getCount();
        int points = Math.max(1, properties.getNutrition()) * count;
        held.shrink(count);
        settlement.addFood(points, characterOrAccountName(player));
        return "Deposited " + points + " food points.";
    }

    private static String depositCurrency(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty() || !held.is(CURRENCY)) {
            return "Hold an item in the #settlements:currency tag.";
        }
        int coins = held.getCount();
        int value = coins * SettlementConfig.CURRENCY_ITEM_WEALTH_VALUE.get();
        held.shrink(coins);
        settlement.addWealth(value, characterOrAccountName(player));
        CampaignSystemsData.get(player.serverLevel()).recordWealthDeposit(settlement.getId(), value, player.serverLevel().getDayTime() / 24000L);
        return "Converted " + coins + " currency into " + value + " wealth.";
    }

    private static String withdrawCurrency(ServerPlayer player, SettlementData.SettlementRecord settlement, int coins) {
        int cost = coins * SettlementConfig.CURRENCY_ITEM_WEALTH_VALUE.get();
        if (!settlement.spendWealth(cost)) return "Not enough settlement wealth. Required: " + cost + ".";
        Item item = getCurrencyItem();
        ItemStack stack = new ItemStack(item, coins);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        settlement.addContribution(characterOrAccountName(player) + " withdrew " + coins + " currency.");
        return "Withdrew " + coins + " currency for " + cost + " wealth.";
    }

    public static Item getCurrencyItem() {
        Item defaultCoin = ModItems.SILVER_COIN.get();
        Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(CURRENCY);
        if (tag.isEmpty()) return defaultCoin;

        // Prefer the bundled coin when the default tag is in use. If a server/datapack replaces
        // #settlements:currency, choose a deterministic tagged item instead of hardcoding silver.
        if (tag.get().stream().anyMatch(holder -> holder.value() == defaultCoin)) return defaultCoin;
        return tag.get().stream()
                .map(Holder::value)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .findFirst()
                .orElse(defaultCoin);
    }

    private static String setTaxPolicy(ServerPlayer player, SettlementData.SettlementRecord settlement, String rawPolicy) {
        CampaignSystemsData.TaxPolicy policy;
        try { policy = CampaignSystemsData.TaxPolicy.valueOf((rawPolicy == null ? "" : rawPolicy.trim()).toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return "Unknown tax policy. Choose Low, Normal, or High."; }
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        CampaignSystemsData.TaxPolicy previous = extra.taxPolicy(settlement.getId());
        if (previous == policy) return policy.displayName() + " taxation is already active.";
        extra.setTaxPolicy(settlement.getId(), policy);
        CampaignSystemsManager.refreshTaxPolicyModifier(settlement, extra);
        settlement.addChronicle("Tax policy changed from " + previous.displayName() + " to " + policy.displayName() + ".");
        int projected = CampaignSystemsManager.projectedTaxIncome(settlement, extra);
        return "Taxation set to " + policy.displayName() + ". Projected revenue: " + projected + " wealth/day; stability "
                + signed(policy.stabilityPerDay()) + "/day; prosperity " + signed(policy.prosperityModifier()) + ".";
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    private static String claimSettlerContract(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (!settlement.claimContract()) return "No Settler Contract is currently available.";
        ItemStack contract = SettlerContractItem.forSettlement(settlement.getId(), settlement.getName());
        if (!player.getInventory().add(contract)) player.drop(contract, false);
        return "Claimed one Settler Contract.";
    }

    public static void promptCharacterCreation(ServerPlayer player) {
        SettlementNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenCharacterCreationPacket()
        );
    }

    public static void submitCharacter(
            ServerPlayer player,
            String first,
            String middle,
            String last,
            String faith,
            String address,
            String title,
            List<String> selectedTraits,
            int rerollRound
    ) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.CharacterRecord existingCharacter = data.getCharacter(player.getUUID());
        if (existingCharacter != null && existingCharacter.isCreated()) {
            player.sendSystemMessage(Component.literal("Your character is already confirmed. An administrator must reset it before it can be recreated."));
            openCharacterSheet(player);
            return;
        }
        if (!CampaignDefinitions.isValidReligion(faith)
                || !allowedCharacterChoice(address, CampaignDefinitions.ADDRESSES)
                || !allowedCharacterChoice(title, CampaignDefinitions.PERSONAL_TITLES)) {
            player.sendSystemMessage(Component.literal("Invalid faith, form of address, or title selection."));
            promptCharacterCreation(player);
            return;
        }
        String cleanFirst = SettlementData.cleanText(first, "", 24);
        String cleanMiddle = SettlementData.cleanText(middle, "", 24);
        String cleanLast = SettlementData.cleanText(last, "", 24);
        String proposed = (cleanFirst + " " + cleanMiddle + " " + cleanLast).replaceAll("\\s+", " ").trim();
        if (cleanFirst.isBlank() || cleanLast.isBlank()) {
            player.sendSystemMessage(Component.literal("A first and last name are required."));
            promptCharacterCreation(player);
            return;
        }
        boolean duplicate = data.getCharacters().stream()
                .filter(character -> !player.getUUID().equals(character.getPlayerId()))
                .anyMatch(character -> character.isCreated() && character.getDisplayName().equalsIgnoreCase(proposed));
        if (duplicate) {
            player.sendSystemMessage(Component.literal("That character name is already in use."));
            promptCharacterCreation(player);
            return;
        }

        if (!CampaignDefinitions.validateTraitSelection(player.getUUID(), proposed, selectedTraits, rerollRound)) {
            player.sendSystemMessage(Component.literal("Invalid character trait selection. Reopen character creation and choose from the offered cards."));
            promptCharacterCreation(player);
            return;
        }

        SettlementData.CharacterRecord character = data.getOrCreateCharacter(player.getUUID());
        character.create(cleanFirst, cleanMiddle, cleanLast, faith, address, title);
        character.setTraits(selectedTraits);
        data.setDirty();
        player.refreshDisplayName();
        player.refreshTabListName();
        broadcastProfiles(player.getServer());
        openCharacterSheet(player);
    }

    private static boolean allowedCharacterChoice(String value, List<String> allowed) {
        if (value == null) return false;
        return allowed.stream().anyMatch(option -> option.equalsIgnoreCase(value.trim()));
    }

    public static void updateCharacterIdentity(ServerPlayer player, String firstName, String middleName, String lastName, String house, boolean returnToMain) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            promptCharacterCreation(player);
            return;
        }

        String cleanFirst = SettlementData.cleanText(firstName, "", 24);
        String cleanMiddle = SettlementData.cleanText(middleName, "", 24);
        String cleanLast = SettlementData.cleanText(lastName, "", 24);
        if (cleanFirst.isBlank() || cleanLast.isBlank()) {
            player.sendSystemMessage(Component.literal("Character renaming requires both a first and last name."));
            openCharacterSheet(player, returnToMain);
            return;
        }

        String proposed = String.join(" ", List.of(cleanFirst, cleanMiddle, cleanLast).stream().filter(value -> !value.isBlank()).toList()).trim();
        boolean nameChanged = !cleanFirst.equals(character.getFirstName())
                || !cleanMiddle.equals(character.getMiddleName())
                || !cleanLast.equals(character.getLastName());
        if (nameChanged) {
            boolean duplicate = data.getCharacters().stream()
                    .filter(other -> !player.getUUID().equals(other.getPlayerId()))
                    .anyMatch(other -> other.isCreated() && other.getDisplayName().equalsIgnoreCase(proposed));
            if (duplicate) {
                player.sendSystemMessage(Component.literal("That character name is already in use."));
                openCharacterSheet(player, returnToMain);
                return;
            }
            int cost = Math.max(0, SettlementConfig.CHARACTER_RENAME_CURRENCY_COST.get());
            if (!consumeCurrencyItems(player, cost)) {
                player.sendSystemMessage(Component.literal("Renaming costs " + cost + " item(s) from #settlements:currency."));
                openCharacterSheet(player, returnToMain);
                return;
            }
            character.rename(cleanFirst, cleanMiddle, cleanLast);
        }
        character.setHouse(house);
        data.setDirty();
        if (nameChanged) {
            player.refreshDisplayName();
            player.refreshTabListName();
            broadcastProfiles(player.getServer());
            player.sendSystemMessage(Component.literal("Character renamed to " + character.getDisplayName() + "."));
        } else {
            player.sendSystemMessage(Component.literal("Character identity updated."));
        }
        openCharacterSheet(player, returnToMain);
    }

    private static boolean consumeCurrencyItems(ServerPlayer player, int amount) {
        int required = Math.max(0, amount);
        if (required == 0) return true;
        int available = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(CURRENCY)) available += stack.getCount();
        }
        if (available < required) return false;
        int remaining = required;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(CURRENCY)) continue;
            int take = Math.min(remaining, stack.getCount());
            player.getInventory().removeItem(slot, take);
            remaining -= take;
        }
        player.getInventory().setChanged();
        return remaining == 0;
    }

    public static void setMainSeatFromGui(ServerPlayer player, String settlementId, boolean returnToMain) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.CharacterRecord character = data.getCharacter(player.getUUID());
        SettlementData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (character == null || !character.isCreated()) {
            promptCharacterCreation(player);
            return;
        }
        if (settlement == null || !player.getUUID().equals(settlement.getOwnerId())) {
            player.sendSystemMessage(Component.literal("You may only choose a settlement you personally own as your Main Seat."));
            openCharacterSheet(player, returnToMain);
            return;
        }
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (!player.createCommandSourceStack().hasPermission(2)
                && (extra.settlementProtected(settlement.getId()) || extra.factionProtected(settlement.getFactionTeam()))) {
            player.sendSystemMessage(Component.literal("Protected admin settlements cannot be selected as a normal player's Main Seat."));
            openCharacterSheet(player, returnToMain);
            return;
        }
        character.setPrimarySeat(settlement.getId());
        data.setDirty();
        player.sendSystemMessage(Component.literal(settlement.getName() + " is now your Main Seat."));
        openCharacterSheet(player, returnToMain);
    }

    public static void openCharacterSheet(ServerPlayer player) {
        openCharacterSheet(player, false);
    }

    public static void openCharacterSheet(ServerPlayer player, boolean returnToMain) {
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            promptCharacterCreation(player);
            return;
        }
        SettlementNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CharacterSheetPacket(CharacterSnapshot.from(data, player), returnToMain)
        );
    }

    public static void broadcastProfiles(MinecraftServer server) {
        if (server == null) return;
        SettlementData data = SettlementData.get(server.overworld());
        Map<UUID, String> profiles = new HashMap<>();
        data.getCharacters().stream()
                .filter(SettlementData.CharacterRecord::isCreated)
                .forEach(character -> profiles.put(character.getPlayerId(), formattedCharacterName(server, character)));
        SettlementNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new ProfileSyncPacket(profiles));
    }

    public static void syncProfiles(ServerPlayer player) {
        SettlementData data = SettlementData.get(player.serverLevel());
        Map<UUID, String> profiles = new HashMap<>();
        data.getCharacters().stream()
                .filter(SettlementData.CharacterRecord::isCreated)
                .forEach(character -> profiles.put(character.getPlayerId(), formattedCharacterName(player.getServer(), character)));
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ProfileSyncPacket(profiles));
    }

    public static String characterOrAccountName(ServerPlayer player) {
        SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
        return character != null && character.isCreated() ? character.getDisplayName() : player.getGameProfile().getName();
    }

    private static void discardSpawned(ServerLevel level, Collection<UUID> spawned) {
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        spawned.forEach(uuid -> {
            extra.removeMercenaryContract(uuid);
            Entity entity = level.getEntity(uuid);
            if (entity != null) entity.discard();
        });
    }

    private static BlockPos offsetSpawn(BlockPos base, int index) {
        int width = 7;
        int row = index / width;
        int x = (index % width) - (width / 2);
        int z = row - 1;
        return base.offset(x * 2, 0, z * 2);
    }

    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos base, int index) {
        BlockPos preferred = offsetSpawn(base, index);
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos column = preferred.offset(dx, 0, dz);
                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos candidate = column.offset(0, dy, 0);
                        if (isSafeSpawn(level, candidate)) return candidate;
                    }
                }
            }
        }
        return preferred;
    }

    private static boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    static String validateMilitarySpawnSetup(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        if (!settlement.hasMusterGround()) return "Set this settlement's Muster Ground before recruiting troops.";
        ServerLevel level = resolveMusterGroundLevel(server, settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        if (!settlement.getEffectiveRecruitsTeam().isBlank()
                && level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam()) == null) {
            return "The saved Recruits backing team no longer exists: " + settlement.getEffectiveRecruitsTeam();
        }
        return null;
    }

    public static ServerLevel resolveTownSquareLevel(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        return resolveLevel(server, settlement.getTownSquareDimension());
    }

    public static ServerLevel resolveMusterGroundLevel(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        return resolveLevel(server, settlement.getMusterGroundDimension());
    }

    public static ServerLevel resolveBarracksLevel(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        return resolveLevel(server, settlement.getBarracksDimension());
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    public static String returnToBarracks(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (!settlement.hasBarracks()) return "Set this settlement's Barracks first.";
        ServerLevel barracksLevel = resolveBarracksLevel(player.getServer(), settlement);
        if (barracksLevel == null) return "The saved Barracks dimension is unavailable.";
        int ordered = 0;
        BlockPos target = settlement.getBarracks();
        for (UUID uuid : settlement.getAllMilitaryUnits()) {
            Entity entity = barracksLevel.getEntity(uuid);
            if (!(entity instanceof AbstractRecruitEntity recruit)) continue;
            recruit.setMovePos(target);
            recruit.setShouldMovePos(true);
            recruit.setShouldRest(true);
            BlockPos upkeep = settlement.getBarracksChest() != null ? settlement.getBarracksChest() : target;
            recruit.setUpkeepPos(upkeep);
            ordered++;
        }
        return ordered == 0 ? "No loaded soldiers in the Barracks dimension could receive the order."
                : "Ordered " + ordered + " soldiers to return to the Barracks.";
    }

    public static String markHeldTemplateMercenary(ServerPlayer player, int wage, int intervalDays) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof SpawnEggItem)) return "Hold a configured Recruits spawn egg in your main hand.";
        CompoundTag tag = held.getOrCreateTag();
        tag.putBoolean(TAG_MERCENARY, true);
        tag.putInt(TAG_MERCENARY_WAGE, Math.max(1, wage));
        tag.putInt(TAG_MERCENARY_INTERVAL_DAYS, Math.max(1, intervalDays));
        return "Held unit template marked as a mercenary: " + Math.max(1, wage) + " wealth every " + Math.max(1, intervalDays) + " days.";
    }

    public static String clearHeldTemplateMercenary(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof SpawnEggItem)) return "Hold a configured Recruits spawn egg in your main hand.";
        CompoundTag tag = held.getOrCreateTag();
        tag.remove(TAG_MERCENARY);
        tag.remove(TAG_MERCENARY_WAGE);
        tag.remove(TAG_MERCENARY_INTERVAL_DAYS);
        return "Held unit template is now a normal upkeep-free recruit template.";
    }

    /** Nudges Recruits' own morale value and handles only recruits explicitly marked as mercenaries. */
    public static void processMilitarySystems(MinecraftServer server, long day) {
        if (server == null) return;
        SettlementData settlements = SettlementData.get(server.overworld());
        CampaignData campaign = CampaignData.get(server.overworld());
        processMercenaryContracts(server, settlements, campaign, day);
        MercenaryRosterManager.process(server, day);
        processPlagueEvents(server, settlements, campaign, day);
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            List<AbstractRecruitEntity> loadedBards = loadedBards(server, settlement);
            BardStatus bard = bardStatus(settlement, loadedBards);
            if (bard.performing()) settlement.applyBardStability(day, SettlementConfig.BARD_STABILITY_BONUS.get());
            for (UUID uuid : Set.copyOf(settlement.getAllMilitaryUnits())) {
                AbstractRecruitEntity recruit = findLoadedRecruit(server, uuid);
                if (recruit == null) continue;
                maintainBarracksLink(recruit, settlement);
                syncMercenaryEntityFromContract(recruit, CampaignSystemsData.get(server.overworld()).mercenaryContract(uuid));
                applySettlementMorale(recruit, settlement, settlements, campaign, loadedBards);
            }
        }
        if (SettlementConfig.ENABLE_RECRUIT_LEVEL_OVERRIDE.get()) {
            int cap = SettlementConfig.RECRUIT_LEVEL_CAP.get();
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof AbstractRecruitEntity recruit && recruit.getXpLevel() > cap) recruit.setXpLevel(cap);
                }
            }
        }
    }

    private static void processPlagueEvents(MinecraftServer server, SettlementData settlements, CampaignData campaign, long day) {
        CampaignSystemsData extra = CampaignSystemsData.get(server.overworld());
        int baseChance = SettlementConfig.PLAGUE_DAILY_CHANCE_PER_10000.get();
        if (baseChance <= 0) return;
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            if (!settlement.beginPlagueCheck(day) || settlement.getVillagerCount() <= 0) continue;
            extra.expireModifiers(settlement.getId());
            if (extra.modifiers(settlement.getId()).stream().anyMatch(value -> value.id.equals("plague"))) continue;
            int chance = baseChance;
            SettlementData.CharacterRecord ruler = settlement.getOwnerId() == null ? null : settlements.getCharacter(settlement.getOwnerId());
            if (ruler != null && ruler.isCreated() && ruler.getTraits().stream().anyMatch("healthy"::equals))
                chance = (int)Math.round(chance * SettlementConfig.HEALTHY_PLAGUE_CHANCE_PERCENT.get() / 100.0D);
            long seed = 31L * day + settlement.getId().hashCode() * 0x9E3779B9L;
            if (new java.util.Random(seed).nextInt(10000) >= Math.max(0, chance)) continue;
            long duration = SettlementConfig.hoursMillis(SettlementConfig.PLAGUE_DURATION_HOURS);
            extra.addModifier(settlement.getId(), CampaignSystemsManager.standardModifier(server.overworld(), settlement.getId(), "plague", duration));
            settlement.addChronicle("Plague has broken out in the settlement.");
            CampaignToastSender.settlementManagers(server, settlement.getId(), "PLAGUE", "Disease has broken out in " + settlement.getName() + ".");
            campaign.addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.NORMAL, "Plague outbreak",
                    "Disease has broken out in " + settlement.getName() + ".", day, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
        }
    }

    /** Admin/debug hook: makes every registered mercenary in a faction immediately due and processes payment once. */
    public static int forceMercenaryPaymentCheck(MinecraftServer server, String faction) {
        if (server == null) return 0;
        CampaignSystemsData extra = CampaignSystemsData.get(server.overworld());
        String wanted = faction == null ? "" : faction.trim();
        long day = server.overworld().getDayTime() / 24000L;
        int count = 0;
        for (CampaignSystemsData.MercenaryContractRecord contract : List.copyOf(extra.mercenaryContracts())) {
            if (!wanted.isBlank() && !wanted.equals(contract.factionId())) continue;
            extra.putMercenaryContract(contract.update(day, contract.missedPayments(), contract.desertPending()));
            count++;
        }
        if (count > 0) processMercenaryContracts(server, SettlementData.get(server.overworld()), CampaignData.get(server.overworld()), day);
        return count;
    }

    public static void registerMercenaryContract(ServerLevel level, AbstractRecruitEntity recruit, SettlementData.SettlementRecord settlement, UUID originalOwner) {
        if (level == null || recruit == null || settlement == null || !recruit.getPersistentData().getBoolean(TAG_MERCENARY)) return;
        CompoundTag tag = recruit.getPersistentData();
        int wage = Math.max(1, tag.getInt(TAG_MERCENARY_WAGE));
        int interval = Math.max(1, tag.getInt(TAG_MERCENARY_INTERVAL_DAYS));
        long next = tag.contains(TAG_MERCENARY_NEXT_PAYMENT_DAY) ? tag.getLong(TAG_MERCENARY_NEXT_PAYMENT_DAY) : level.getDayTime() / 24000L + interval;
        int missed = Math.max(0, tag.getInt(TAG_MERCENARY_MISSED_PAYMENTS));
        tag.putLong(TAG_MERCENARY_NEXT_PAYMENT_DAY, next);
        CampaignSystemsData.get(level).putMercenaryContract(new CampaignSystemsData.MercenaryContractRecord(
                recruit.getUUID(), originalOwner, settlement.getId(), settlement.getFactionTeam(), wage, interval, next, missed, missed >= MERCENARY_DESERTION_MISSES));
    }

    private static void processMercenaryContracts(MinecraftServer server, SettlementData settlements, CampaignData campaign, long day) {
        CampaignSystemsData extra = CampaignSystemsData.get(server.overworld());
        java.util.Set<String> paidFactions = new java.util.HashSet<>();
        java.util.Set<String> missedFactions = new java.util.HashSet<>();
        for (CampaignSystemsData.MercenaryContractRecord original : List.copyOf(extra.mercenaryContracts())) {
            SettlementData.SettlementRecord settlement = settlements.getSettlement(original.settlementId());
            if (settlement == null) { extra.removeMercenaryContract(original.unitId()); continue; }
            CampaignSystemsData.MercenaryContractRecord contract = original;
            long next = contract.nextPaymentDay();
            int missed = contract.missedPayments();
            boolean pending = contract.desertPending();
            if (day >= next && !pending) {
                int dueCycles = (int) Math.min(64L, 1L + Math.max(0L, day - next) / contract.intervalDays());
                for (int cycle = 0; cycle < dueCycles; cycle++) {
                    boolean paid = !contract.factionId().isBlank()
                            ? extra.spendFactionWealth(contract.factionId(), contract.wage())
                            : settlement.spendWealth(contract.wage());
                    if (paid) {
                        missed = 0;
                        if (!contract.factionId().isBlank()) paidFactions.add(contract.factionId());
                    } else {
                        missed++;
                        if (!contract.factionId().isBlank()) missedFactions.add(contract.factionId());
                    }
                    next += contract.intervalDays();
                    if (missed >= MERCENARY_DESERTION_MISSES) { pending = true; break; }
                }
                contract = contract.update(next, missed, pending);
                extra.putMercenaryContract(contract);
                if (missed > 0) settlement.addChronicle("Mercenary wages were missed (" + missed + "/" + MERCENARY_DESERTION_MISSES + ").");
            }
            AbstractRecruitEntity recruit = findLoadedRecruit(server, contract.unitId());
            if (recruit == null) continue;
            syncMercenaryEntityFromContract(recruit, contract);
            if (contract.desertPending()) {
                desertMercenary(recruit, settlement, campaign, day);
                extra.removeMercenaryContract(contract.unitId());
            } else if (contract.missedPayments() == 0) {
                recruit.setMoral(Math.min(100.0F, recruit.getMorale() + 1.0F));
            }
        }
        // Reliability is faction reputation, not a per-soldier score. Award/penalize at most
        // once per processing pass regardless of how many mercenary contracts were due.
        for (String faction : paidFactions) if (!missedFactions.contains(faction)) CampaignManager.changeReliability(server.overworld(), campaign, faction, 1);
        for (String faction : missedFactions) CampaignManager.changeReliability(server.overworld(), campaign, faction, -2);
    }

    private static void syncMercenaryEntityFromContract(AbstractRecruitEntity recruit, CampaignSystemsData.MercenaryContractRecord contract) {
        if (recruit == null || contract == null) return;
        CompoundTag tag = recruit.getPersistentData();
        tag.putBoolean(TAG_MERCENARY, true);
        tag.putInt(TAG_MERCENARY_WAGE, contract.wage());
        tag.putInt(TAG_MERCENARY_INTERVAL_DAYS, contract.intervalDays());
        tag.putLong(TAG_MERCENARY_NEXT_PAYMENT_DAY, contract.nextPaymentDay());
        tag.putInt(TAG_MERCENARY_MISSED_PAYMENTS, contract.missedPayments());
    }


    private static AbstractRecruitEntity findLoadedRecruit(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractRecruitEntity recruit) return recruit;
        }
        return null;
    }

    private static void maintainBarracksLink(AbstractRecruitEntity recruit, SettlementData.SettlementRecord settlement) {
        if (!settlement.hasBarracks()) return;
        BlockPos upkeep = settlement.getBarracksChest() != null ? settlement.getBarracksChest() : settlement.getBarracks();
        if (upkeep != null && !upkeep.equals(recruit.getUpkeepPos())) recruit.setUpkeepPos(upkeep);
    }

    private static void desertMercenary(AbstractRecruitEntity recruit, SettlementData.SettlementRecord settlement,
                                         CampaignData campaign, long day) {
        CompoundTag data = recruit.getPersistentData();
        UUID unitId = recruit.getUUID();
        CampaignSystemsData.get((ServerLevel) recruit.level()).removeMercenaryContract(unitId);
        boolean professional = settlement.removeProfessional(unitId);
        if (!professional) settlement.removeLevyman(unitId);
        data.remove(TAG_MANAGED);
        data.remove(TAG_KIND);
        data.remove(TAG_SETTLEMENT);
        data.remove(TAG_BATCH);
        recruit.clearUpkeepPos();
        recruit.setShouldRest(false);
        recruit.setFleeing(true);
        PlayerTeam team = recruit.getTeam() instanceof PlayerTeam playerTeam ? playerTeam : null;
        if (team != null && recruit.level() instanceof ServerLevel level) {
            try {
                FactionEvents.removeRecruitFromTeam(recruit, team, level);
            } catch (RuntimeException ignored) {
                level.getScoreboard().removePlayerFromTeam(recruit.getScoreboardName(), team);
            }
        }
        settlement.addChronicle("An unpaid mercenary deserted.");
        campaign.addNews(CampaignData.NewsCategory.MILITARY, CampaignData.NewsImportance.MINOR,
                "Mercenary desertion", "An unpaid mercenary deserted " + settlement.getName() + ".", day,
                settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.FACTION);
    }

    private static void applySettlementMorale(AbstractRecruitEntity recruit, SettlementData.SettlementRecord settlement,
                                               SettlementData settlements, CampaignData campaign, List<AbstractRecruitEntity> loadedBards) {
        int target = 50 + Math.round((settlement.getStability() - 50) * 0.30F);
        SettlementData.CharacterRecord ruler = settlement.getOwnerId() == null ? null : settlements.getCharacter(settlement.getOwnerId());
        if (ruler != null) target += CampaignDefinitions.totalEffect(ruler.getTraits(), CampaignDefinitions.EffectKind.MORALE);
        CampaignData.MarriageRecord marriage = settlement.getOwnerId() == null ? null : campaign.getMarriage(settlement.getOwnerId());
        if (marriage != null && (settlement.getRank() == SettlementData.SettlementRank.CAPITAL || settlement.getRank() == SettlementData.SettlementRank.CITY_STATE)) {
            target += marriage.effectiveMoraleBonus(settlements);
            int spouseIndex = 1;
            for (CampaignData.MarriageRecord additional : campaign.getAdditionalNpcMarriages(settlement.getOwnerId()))
                target += CampaignData.scaledSpouseEffect(additional.moraleBonus(), spouseIndex++);
        }
        if (settlement.hasBarracks() && recruit.level().dimension().location().toString().equals(settlement.getBarracksDimension())
                && recruit.blockPosition().closerThan(settlement.getBarracks(), settlement.getBarracksRadius())) target += 8;
        target += bardMoraleStacks(recruit, settlement, loadedBards) * SettlementConfig.BARD_MORALE_BONUS.get();
        CampaignSystemsData extra = CampaignSystemsData.get((ServerLevel) recruit.level());
        for (CampaignSystemsData.ModifierRecord modifier : extra.modifiers(settlement.getId())) target += modifier.morale;
        if (recruit.getPersistentData().getBoolean(TAG_MERCENARY)) {
            target -= recruit.getPersistentData().getInt(TAG_MERCENARY_MISSED_PAYMENTS) * 12;
        }
        target = Math.max(10, Math.min(90, target));
        float current = recruit.getMorale();
        if (current < target - 1) recruit.setMoral(Math.min(target, current + 1.0F));
        else if (current > target + 1) recruit.setMoral(Math.max(target, current - 1.0F));
    }

    private static String setTownSquareHere(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        return "Town Square moved to your current position.";
    }

    private static String toggleNearbyBard(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        AbstractRecruitEntity recruit = player.serverLevel().getEntitiesOfClass(AbstractRecruitEntity.class, player.getBoundingBox().inflate(8.0D), entity ->
                settlement.getId().equals(entity.getPersistentData().getString(TAG_SETTLEMENT)) && entity.getPersistentData().getBoolean(TAG_MANAGED))
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (recruit == null) return "No managed recruit from this settlement is within 8 blocks.";
        boolean bard = !recruit.getPersistentData().getBoolean(TAG_BARD);
        recruit.getPersistentData().putBoolean(TAG_BARD, bard);
        return bard ? "Nearest recruit designated as a Bard. Give them an item in #settlements:bard_instruments and place them near the Town Square to perform."
                : "Nearest recruit is no longer designated as a Bard.";
    }

    public static BardStatus bardStatus(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        if (server == null || settlement == null) return new BardStatus(0, false);
        return bardStatus(settlement, loadedBards(server, settlement));
    }

    private static List<AbstractRecruitEntity> loadedBards(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        List<AbstractRecruitEntity> result = new ArrayList<>();
        if (server == null || settlement == null) return result;
        for (UUID uuid : settlement.getAllMilitaryUnits()) {
            AbstractRecruitEntity recruit = findLoadedRecruit(server, uuid);
            if (recruit != null && recruit.getPersistentData().getBoolean(TAG_BARD)) result.add(recruit);
        }
        return result;
    }

    private static BardStatus bardStatus(SettlementData.SettlementRecord settlement, List<AbstractRecruitEntity> bards) {
        if (settlement == null || bards == null || bards.isEmpty()) return new BardStatus(0, false);
        boolean performing = false;
        for (AbstractRecruitEntity recruit : bards) {
            if (!bardIsInTown(recruit, settlement)) continue;
            if (settlement.hasTownSquare()
                    && recruit.level().dimension().location().toString().equals(settlement.getTownSquareDimension())
                    && recruit.blockPosition().closerThan(settlement.getTownSquare(), SettlementConfig.BARD_TOWN_SQUARE_RADIUS.get())
                    && (recruit.getMainHandItem().is(BARD_INSTRUMENTS) || recruit.getOffhandItem().is(BARD_INSTRUMENTS))) {
                performing = true;
                break;
            }
        }
        return new BardStatus(bards.size(), performing);
    }

    private static int bardMoraleStacks(AbstractRecruitEntity target, SettlementData.SettlementRecord settlement, List<AbstractRecruitEntity> bards) {
        if (target == null || settlement == null || bards == null || bards.isEmpty()) return 0;
        Set<UUID> sources = new HashSet<>();
        double radius = SettlementConfig.BARD_FIELD_RADIUS.get();
        double radiusSq = radius * radius;
        for (AbstractRecruitEntity bard : bards) {
            if (bard == null || !bard.isAlive()) continue;
            if (bardIsInTown(bard, settlement)) {
                // A Bard stationed in town represents the settlement's organised musical corps
                // and supports the polity's whole managed army.
                sources.add(bard.getUUID());
                continue;
            }
            if (bard.level() == target.level() && bard.distanceToSqr(target) <= radiusSq) sources.add(bard.getUUID());
        }
        return Math.min(sources.size(), SettlementConfig.BARD_MAX_MORALE_STACKS.get());
    }

    public record BardStatus(int count, boolean performing) {}

    private static boolean bardIsInTown(AbstractRecruitEntity recruit, SettlementData.SettlementRecord settlement) {
        String dimension = recruit.level().dimension().location().toString();
        if (settlement.hasClaim() && ClaimEvents.recruitsClaimManager != null) {
            RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(recruit.blockPosition()));
            if (claim != null && settlement.containsClaim(dimension, claim.getUUID())) return true;
        }
        return settlement.hasTownSquare()
                && dimension.equals(settlement.getTownSquareDimension())
                && recruit.blockPosition().closerThan(settlement.getTownSquare(), Math.max(16, SettlementConfig.BARD_TOWN_SQUARE_RADIUS.get() * 2));
    }

    private static String setUpkeepChest(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(6.0D));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return "Look directly at a chest or other inventory container within 6 blocks.";
        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof Container)) return "That block is not a supported inventory container.";
        settlement.setUpkeepChest(level.dimension().location().toString(), pos);
        return "City Upkeep Chest linked. Edible items placed inside will automatically maintain the settlement Food stockpile.";
    }

    public static void processUpkeepChests(MinecraftServer server) {
        if (server == null) return;
        for (SettlementData.SettlementRecord settlement : SettlementData.get(server.overworld()).getSettlements()) {
            BlockPos pos = settlement.getUpkeepChest();
            if (pos == null || settlement.getUpkeepChestDimension().isBlank()) continue;
            ServerLevel level = resolveLevel(server, settlement.getUpkeepChestDimension());
            if (level == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof Container container)) continue;
            int mouths = settlement.getVillagerCount() + settlement.getProfessionalCount() + settlement.getMercenaryCompanyCount();
            if (mouths <= 0) continue;
            int target = Math.max(SettlementConfig.SETTLER_CONTRACT_FOOD_RESERVE.get(), mouths * SettlementConfig.UPKEEP_CHEST_TARGET_DAYS.get());
            int needed = Math.max(0, target - settlement.getFood());
            if (needed <= 0) continue;
            int itemBudget = SettlementConfig.UPKEEP_CHEST_MAX_ITEMS_PER_SWEEP.get();
            int supplied = 0;
            for (int slot = 0; slot < container.getContainerSize() && needed > 0 && itemBudget > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !stack.isEdible()) continue;
                FoodProperties properties = stack.getFoodProperties(null);
                if (properties == null) continue;
                int perItem = Math.max(1, properties.getNutrition());
                int take = Math.min(stack.getCount(), Math.min(itemBudget, (needed + perItem - 1) / perItem));
                if (take <= 0) continue;
                stack.shrink(take);
                container.setItem(slot, stack);
                int points = take * perItem;
                supplied += points;
                needed -= points;
                itemBudget -= take;
            }
            if (supplied > 0) {
                container.setChanged();
                settlement.addFoodStockpile(supplied);
            }
        }
    }

    private static String setBarracksHere(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        settlement.setBarracks(player.serverLevel().dimension().location().toString(), player.blockPosition());
        return "Barracks centre moved to your current position.";
    }

    private static String regionPos1(ServerPlayer player, SettlementData.SettlementRecord settlement, String label) {
        CampaignSystemsManager.setRegionPos1(player);
        return label + " pos1 set. Stand at the opposite corner and press pos2.";
    }

    private static String regionPos2(ServerPlayer player, SettlementData.SettlementRecord settlement, String type) {
        CampaignSystemsData.RegionRecord region = CampaignSystemsManager.completeRegion(player);
        if (region == null || !region.valid()) return "Set pos1 first, in the same dimension.";
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        return switch (type) {
            case "housing" -> { extra.addHousingArea(settlement.getId(), region); int beds = CampaignSystemsManager.recalculateHousing(player.serverLevel(), settlement); yield "Dwelling registered: " + beds + " total housing bed(s)."; }
            case "muster" -> { extra.setMusterArea(settlement.getId(), region); settlement.setMusterGround(region.dimension(), region.min()); yield "Muster Ground registered with capacity for about " + region.horizontalArea() + " soldiers."; }
            case "prayer" -> { extra.setPrayerSite(settlement.getId(), region); yield "Prayer Site registered."; }
            default -> "Unknown region type.";
        };
    }

    private static String renameSettlement(ServerPlayer player, SettlementData.SettlementRecord settlement, String value) {
        String clean = SettlementData.cleanText(value, settlement.getName(), 64);
        if (clean.isBlank()) return "Settlement name cannot be blank.";
        settlement.rename(clean);
        CampaignManager.broadcastMapMarkers(player.getServer());
        return "Settlement renamed to " + settlement.getName() + ".";
    }

    private static String setSettlementMember(ServerPlayer actor, SettlementData.SettlementRecord settlement, String rawPlayerId, SettlementData.SettlementRole role) {
        UUID targetId;
        try { targetId = UUID.fromString(rawPlayerId == null ? "" : rawPlayerId.trim()); }
        catch (IllegalArgumentException exception) { return "Select an online player first."; }
        if (targetId.equals(settlement.getOwnerId())) return "The settlement owner already has full authority.";
        if (role != SettlementData.SettlementRole.CITIZEN && role != SettlementData.SettlementRole.CO_OWNER) return "Invalid settlement role.";
        ServerPlayer target = actor.getServer() == null ? null : actor.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return "That player must be online to receive a settlement role.";
        boolean admin = actor.createCommandSourceStack().hasPermission(2);
        if (!admin) {
            String faction = settlement.getFactionTeam();
            String targetFaction = CampaignManager.factionId(target);
            if (faction.isBlank()) {
                if (!targetFaction.isBlank()) return "Independent settlements may only add independent players.";
            } else if (!faction.equals(targetFaction)) {
                return "Faction settlements may only add members of their own faction.";
            }
        }
        settlement.setRole(targetId, role);
        settlement.addChronicle(target.getGameProfile().getName() + " became " + (role == SettlementData.SettlementRole.CO_OWNER ? "a co-owner" : "a citizen") + ".");
        return target.getGameProfile().getName() + " is now " + (role == SettlementData.SettlementRole.CO_OWNER ? "a co-owner" : "a citizen") + ".";
    }

    private static String removeSettlementMember(ServerPlayer actor, SettlementData.SettlementRecord settlement, String rawPlayerId) {
        UUID targetId;
        try { targetId = UUID.fromString(rawPlayerId == null ? "" : rawPlayerId.trim()); }
        catch (IllegalArgumentException exception) { return "Select a settlement member first."; }
        if (targetId.equals(settlement.getOwnerId())) return "The owner cannot be removed. Transfer ownership first.";
        SettlementData.SettlementRole existing = settlement.getRole(targetId);
        if (existing == SettlementData.SettlementRole.OUTSIDER) return "That player is not a settlement member.";
        settlement.setRole(targetId, SettlementData.SettlementRole.OUTSIDER);
        ServerPlayer online = actor.getServer() == null ? null : actor.getServer().getPlayerList().getPlayer(targetId);
        String name = online == null ? targetId.toString().substring(0, 8) : online.getGameProfile().getName();
        settlement.addChronicle(name + " was removed from the settlement membership.");
        return name + " was removed from the settlement.";
    }

    private static String transferSettlement(ServerPlayer player, SettlementData.SettlementRecord settlement, String accountName) {
        if (player.getServer() == null) return "Server unavailable.";
        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(accountName == null ? "" : accountName.trim());
        if (target == null) return "That player must be online to receive settlement ownership.";
        SettlementData data = SettlementData.get(player.serverLevel());
        String settlementFaction = settlement.getFactionTeam() == null ? "" : settlement.getFactionTeam();
        String targetFaction = CampaignManager.factionId(target);
        if (!settlementFaction.equals(targetFaction)) {
            return "Settlement ownership can only be transferred within its current polity. Use diplomacy for cross-polity territorial transfers.";
        }
        if (targetFaction.isBlank() && data.getSettlements().stream()
                .filter(value -> target.getUUID().equals(value.getOwnerId()) && value.getFactionTeam().isBlank() && value != settlement).count() >= 2) {
            return target.getGameProfile().getName() + " already owns the maximum two independent settlements.";
        }
        settlement.setOwner(target.getUUID());
        if (targetFaction.isBlank()) BackingFactionManager.assignIndependentSettlement(target, settlement);
        data.selectSettlement(target.getUUID(), settlement.getId());
        settlement.addChronicle("Ownership transferred to " + target.getGameProfile().getName() + ".");
        return "Ownership transferred to " + target.getGameProfile().getName() + ".";
    }

    private static String deleteSettlement(ServerPlayer player, SettlementData.SettlementRecord settlement, String argument) {
        if (player.getServer() == null) return "Server unavailable.";
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (extra.settlementProtected(settlement.getId()) || extra.factionProtected(settlement.getFactionTeam())) {
            return "Protected admin settlements cannot be deleted through normal settlement management.";
        }

        long now = System.currentTimeMillis();
        PendingSettlementDeletion pending = PENDING_SETTLEMENT_DELETIONS.get(player.getUUID());
        if (!"CONFIRM".equalsIgnoreCase(argument)) {
            PENDING_SETTLEMENT_DELETIONS.put(player.getUUID(), new PendingSettlementDeletion(settlement.getId(), now + SETTLEMENT_DELETE_CONFIRM_MILLIS));
            return "DANGER: deleting this settlement is permanent. All Settlements stats, treasury wealth, food, residents, linked Recruits claims, modifiers, housing, military tracking and contracts will be erased. Press CONFIRM DELETE within 30 seconds to proceed.";
        }
        if (pending == null || pending.expiresAtMillis() < now || !settlement.getId().equals(pending.settlementId())) {
            PENDING_SETTLEMENT_DELETIONS.remove(player.getUUID());
            return "Deletion confirmation expired or belongs to a different settlement. Arm deletion again first.";
        }

        CampaignData campaign = CampaignData.get(player.serverLevel());
        boolean activeWar = campaign.getWars().stream()
                .anyMatch(war -> war.status() == CampaignData.WarStatus.ACTIVE
                        && (settlement.getId().equals(war.attackerSettlement()) || settlement.getId().equals(war.defenderSettlement())));
        if (activeWar) {
            PENDING_SETTLEMENT_DELETIONS.remove(player.getUUID());
            return "This settlement is part of an active war. End the war before permanently deleting the settlement.";
        }
        if (settlement.getClaimCount() > 0 && ClaimEvents.recruitsClaimManager == null) {
            PENDING_SETTLEMENT_DELETIONS.remove(player.getUUID());
            return "Recruits claim data is not ready. The settlement was NOT deleted because its linked claims could not be removed safely.";
        }

        String settlementId = settlement.getId();
        String settlementName = settlement.getName();
        String backingFaction = settlement.getBackingFactionTeam();

        MercenaryRosterManager.clearSettlementContracts(player.getServer(), settlementId);
        clearLoadedSettlementEntityData(player.getServer(), settlement);
        if (ClaimEvents.recruitsClaimManager != null) {
            for (SettlementData.ClaimLink link : List.copyOf(settlement.getClaimLinks())) {
                if (link.id() == null) continue;
                ServerLevel level = resolveLevel(player.getServer(), link.dimension());
                if (level == null) level = player.getServer().overworld();
                try { ClaimEvents.recruitsClaimManager.removeClaim(level, link.id()); }
                catch (RuntimeException ignored) { }
            }
            ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(player.getServer().overworld());
        }

        extra.clearSettlementData(settlementId);
        campaign.clearSettlementVisual(settlementId);
        SettlementData.get(player.serverLevel()).deleteSettlement(settlementId);
        BackingFactionManager.retireIfUnused(player.getServer(), backingFaction);
        PENDING_SETTLEMENT_DELETIONS.remove(player.getUUID());
        broadcastProfiles(player.getServer());
        CampaignManager.broadcastMapMarkers(player.getServer());
        return "Settlement " + settlementName + " was permanently deleted. This action cannot be undone.";
    }

    private static void clearLoadedSettlementEntityData(MinecraftServer server, SettlementData.SettlementRecord settlement) {
        Set<UUID> ids = new HashSet<>(settlement.getVillagerIds());
        ids.addAll(settlement.getAllMilitaryUnits());
        for (UUID id : ids) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity == null) continue;
                clearOrphanedSettlementTags(entity);
                break;
            }
        }
    }

    public static void clearOrphanedSettlementTags(Entity entity) {
        if (entity == null) return;
        CompoundTag data = entity.getPersistentData();
        data.remove(TAG_SETTLEMENT);
        data.remove(TAG_RESIDENT);
        data.remove(TAG_MANAGED);
        data.remove(TAG_KIND);
        data.remove(TAG_BATCH);
        data.remove(TAG_MERCENARY);
        data.remove(TAG_MERCENARY_WAGE);
        data.remove(TAG_MERCENARY_INTERVAL_DAYS);
        data.remove(TAG_MERCENARY_NEXT_PAYMENT_DAY);
        data.remove(TAG_MERCENARY_MISSED_PAYMENTS);
        data.remove(TAG_MERCENARY_COMPANY);
        data.remove(TAG_UNIT_TYPE);
        data.remove(TAG_BARD);
        if (entity instanceof AbstractRecruitEntity recruit) {
            recruit.clearUpkeepPos();
            recruit.setShouldRest(false);
        }
    }

    private static String formattedCharacterName(MinecraftServer server, SettlementData.CharacterRecord character) {
        if (server == null || character == null) return character == null ? "" : character.getDisplayName();
        ServerPlayer online = server.getPlayerList().getPlayer(character.getPlayerId());
        if (online == null) return character.getDisplayName();
        String faction = CampaignManager.factionId(online);
        if (faction.isBlank()) return character.getDisplayName();
        CampaignData campaign = CampaignData.get(server.overworld());
        boolean leader = FactionEvents.recruitsFactionManager != null
                && FactionEvents.recruitsFactionManager.getFactionByStringID(faction) != null
                && character.getPlayerId().equals(FactionEvents.recruitsFactionManager.getFactionByStringID(faction).getTeamLeaderUUID());
        String rank = campaign.factionRankTitle(faction, character.getPlayerId(), leader);
        return rank.isBlank() ? character.getDisplayName() : "[" + rank + "] " + character.getDisplayName();
    }

    public enum SettlementAction {
        RECRUIT_ONE, RECRUIT_FIVE, RECRUIT_TEN, RECRUIT_TWENTY_FIVE, RECRUIT_UNIT,
        RAISE_LEVY, RETURN_LEVY, RETURN_TO_BARRACKS, TOGGLE_NEARBY_BARD, RUN_CHECKER, DEPOSIT_FOOD, DEPOSIT_CURRENCY,
        WITHDRAW_CURRENCY_ONE, WITHDRAW_CURRENCY_TEN, SET_TAX_POLICY, CLAIM_CONTRACT,
        SET_CUSTOM_BANNER, USE_FACTION_BANNER, CLEAR_BANNER, LINK_CURRENT_CLAIM, UNLINK_CURRENT_CLAIM, CLEAR_CLAIMS,
        RENAME_SETTLEMENT, TRANSFER_SETTLEMENT, DELETE_SETTLEMENT, ADD_MEMBER_CITIZEN, ADD_MEMBER_COOWNER, REMOVE_MEMBER,
        SET_TOWN_SQUARE, SET_BARRACKS, SET_UPKEEP_CHEST, CLEAR_UPKEEP_CHEST, HOUSING_POS1, HOUSING_POS2, MUSTER_POS1, MUSTER_POS2,
        PRAYER_POS1, PRAYER_POS2, RECALCULATE_HOUSING, OPEN_CHARACTER, OPEN_MAIN, SELECT_PREVIOUS, SELECT_NEXT, REFRESH
    }

    public record ContractUseResult(boolean success, String message) {
    }

    private record PendingSettlementDeletion(String settlementId, long expiresAtMillis) { }

    @FunctionalInterface
    private interface ActionSupplier {
        String get();
    }
}
