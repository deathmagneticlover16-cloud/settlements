package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import ee.lastpagans.settlementprototype.item.ModItems;
import ee.lastpagans.settlementprototype.item.SettlerContractItem;
import ee.lastpagans.settlementprototype.network.CharacterSheetPacket;
import ee.lastpagans.settlementprototype.network.OpenCharacterCreationPacket;
import ee.lastpagans.settlementprototype.network.ProfileSyncPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.SnapshotPacket;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public final class PrototypeManager {
    public static final double MANPOWER_PER_POPULATION = 0.40D;
    public static final int PROFESSIONAL_WEALTH_COST = 10;
    public static final int SMALL_SETTLEMENT_POPULATION = 50;
    public static final double SMALL_LEVY_SHARE = 0.75D;
    public static final double NORMAL_LEVY_SHARE = 0.50D;

    public static final int CONTRACT_INTERVAL_DAYS = 3;
    public static final int CONTRACT_MIN_STABILITY = 60;
    public static final int CONTRACT_FOOD_RESERVE = 20;
    public static final int CONTRACT_FOOD_COST = 10;
    public static final int MAX_PENDING_CONTRACTS = 3;
    public static final int SILVER_TO_WEALTH = 10;

    public static final String TAG_MANAGED = "SettlementPrototypeManaged";
    public static final String TAG_KIND = "SettlementPrototypeKind";
    public static final String TAG_BATCH = "SettlementPrototypeBatch";
    public static final String TAG_SETTLEMENT = "SettlementPrototypeSettlement";
    public static final String TAG_RESIDENT = "SettlementPrototypeResident";
    public static final String TAG_MERCENARY = "SettlementPrototypeMercenary";
    public static final String TAG_MERCENARY_WAGE = "SettlementPrototypeMercenaryWage";
    public static final String TAG_MERCENARY_INTERVAL_DAYS = "SettlementPrototypeMercenaryIntervalDays";
    public static final String TAG_MERCENARY_NEXT_PAYMENT_DAY = "SettlementPrototypeMercenaryNextPaymentDay";
    public static final String TAG_MERCENARY_MISSED_PAYMENTS = "SettlementPrototypeMercenaryMissedPayments";
    public static final String TAG_UNIT_TYPE = "SettlementPrototypeUnitType";
    public static final int MERCENARY_DEFAULT_WAGE = 20;
    public static final int MERCENARY_DEFAULT_INTERVAL_DAYS = 7;
    public static final int MERCENARY_DESERTION_MISSES = 3;

    public static final TagKey<Item> SILVER_CURRENCY = TagKey.create(
            Registries.ITEM,
            new ResourceLocation(SettlementPrototype.MOD_ID, "silver_currency")
    );

    private PrototypeManager() {
    }

    public static void open(ServerPlayer player) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord settlement = data.getSelectedSettlement(player.getUUID());
        if (settlement == null) {
            player.sendSystemMessage(Component.literal("No settlement selected. Use /settlements list or /settlements open <id>."));
            return;
        }
        open(player, settlement.getId());
    }

    public static boolean open(ServerPlayer player, String settlementId) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (settlement == null) return false;
        if (!canView(player, settlement)) {
            player.sendSystemMessage(Component.literal("You cannot view that settlement."));
            return false;
        }
        data.selectSettlement(player.getUUID(), settlement.getId());
        sendSnapshot(player, settlement, true, "Settlement ready.");
        return true;
    }

    public static void sendSnapshot(ServerPlayer player, PrototypeData.SettlementRecord settlement, boolean open, String message) {
        PrototypeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SnapshotPacket(open, PrototypeSnapshot.from(settlement, player, message))
        );
    }

    public static boolean canView(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        if (player.createCommandSourceStack().hasPermission(2)) return true;
        if (settlement.getRole(player.getUUID()) != PrototypeData.SettlementRole.OUTSIDER) return true;
        Team team = player.getTeam();
        return team != null && team.getName().equals(settlement.getFactionTeam());
    }

    public static boolean canManage(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        return player.createCommandSourceStack().hasPermission(2)
                || settlement.getRole(player.getUUID()).canManage();
    }

    public static boolean canContribute(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        return canManage(player, settlement)
                || settlement.getRole(player.getUUID()) == PrototypeData.SettlementRole.CITIZEN;
    }

    public static void handleAction(ServerPlayer player, String settlementId, SettlementAction action) {
        handleAction(player, settlementId, action, "", 0);
    }

    public static void handleAction(ServerPlayer player, String settlementId, SettlementAction action, String argument, int amount) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord settlement = data.getSettlement(settlementId);
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
            case RAISE_LEVY -> requireManager(player, settlement, () -> Campaign23Manager.raiseLevy(player, settlement));
            case RETURN_LEVY -> requireManager(player, settlement, () -> Campaign23Manager.returnLevy(player, settlement));
            case RETURN_TO_BARRACKS -> requireManager(player, settlement, () -> returnToBarracks(player, settlement));
            case RUN_CHECKER -> requireManager(player, settlement, () -> {
                int recovered = settlement.runManpowerChecker();
                return recovered > 0 ? "Recovered " + recovered + " available MP." : "No manpower recovered.";
            });
            case DEPOSIT_FOOD -> requireContributor(player, settlement, () -> depositFood(player, settlement));
            case DEPOSIT_SILVER -> requireContributor(player, settlement, () -> depositSilver(player, settlement));
            case WITHDRAW_SILVER_ONE -> requireManager(player, settlement, () -> withdrawSilver(player, settlement, 1));
            case WITHDRAW_SILVER_TEN -> requireManager(player, settlement, () -> withdrawSilver(player, settlement, 10));
            case CLAIM_CONTRACT -> requireContributor(player, settlement, () -> claimSettlerContract(player, settlement));
            case SET_CUSTOM_BANNER -> requireManager(player, settlement, () -> setSettlementBanner(player, settlement));
            case USE_FACTION_BANNER -> requireManager(player, settlement, () -> useFactionBanner(player, settlement));
            case CLEAR_BANNER -> requireManager(player, settlement, () -> clearSettlementBanner(player, settlement));
            case LINK_CURRENT_CLAIM -> requireManager(player, settlement, () -> linkCurrentClaim(player, settlement));
            case UNLINK_CURRENT_CLAIM -> requireManager(player, settlement, () -> unlinkCurrentClaim(player, settlement));
            case CLEAR_CLAIMS -> requireManager(player, settlement, () -> clearClaims(settlement));
            case OPEN_CHARACTER, OPEN_MAIN, SELECT_PREVIOUS, SELECT_NEXT -> "Screen opened.";
            case REFRESH -> "Stats refreshed.";
        };
        sendSnapshot(player, settlement, false, result);
        CampaignManager.sendMapMarkers(player);
    }

    private static void cycleSettlement(ServerPlayer player, PrototypeData data, PrototypeData.SettlementRecord current,
                                        boolean forward, boolean militaryScreen) {
        List<PrototypeData.SettlementRecord> accessible = data.getSettlements().stream()
                .filter(settlement -> canView(player, settlement))
                .sorted(java.util.Comparator.comparing(PrototypeData.SettlementRecord::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (accessible.isEmpty()) {
            player.sendSystemMessage(Component.literal("You cannot access any settlements."));
            return;
        }
        int index = accessible.indexOf(current);
        if (index < 0) index = 0;
        int next = Math.floorMod(index + (forward ? 1 : -1), accessible.size());
        PrototypeData.SettlementRecord target = accessible.get(next);
        data.selectSettlement(player.getUUID(), target.getId());
        if (militaryScreen) CampaignManager.openMilitary(player, "Selected " + target.getName() + ".");
        else sendSnapshot(player, target, true, "Selected " + target.getName() + ".");
    }

    private static String requireManager(ServerPlayer player, PrototypeData.SettlementRecord settlement, ActionSupplier action) {
        return canManage(player, settlement) ? action.get() : "Only the owner, a co-owner, or an administrator may do that.";
    }

    private static String requireContributor(ServerPlayer player, PrototypeData.SettlementRecord settlement, ActionSupplier action) {
        return canContribute(player, settlement) ? action.get() : "Only settlement members may do that.";
    }

    public static String recruitProfessionals(ServerPlayer player, PrototypeData.SettlementRecord settlement, int amount) {
        return recruitProfessionals(player, settlement, CampaignData.UnitType.RECRUIT, amount);
    }

    public static String recruitProfessionals(ServerPlayer player, PrototypeData.SettlementRecord settlement, CampaignData.UnitType unitType, int amount) {
        if (amount <= 0) return "Invalid recruitment amount.";
        String validation = validateMilitarySpawnSetup(player.getServer(), settlement);
        if (validation != null) return validation;
        int wealthPerUnit = professionalCost(unitType);
        int wealthCost = amount * wealthPerUnit;
        if (settlement.getAvailableManpower() < amount) return "Not enough available manpower.";
        if (settlement.getWealth() < wealthCost) return "Not enough wealth. Required: " + wealthCost + ".";

        ServerLevel level = resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        PlayerTeam team = settlement.getFactionTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getFactionTeam());
        List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            AbstractRecruitEntity recruit = spawnRecruit(level, player, settlement, unitType, i, team, false, null);
            if (recruit == null) { discardSpawned(level, spawned); return "Recruit spawning failed; no MP or wealth was charged."; }
            spawned.add(recruit.getUUID());
        }
        settlement.spendAvailableManpower(amount);
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

    public static String raiseLevy(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        String validation = validateMilitarySpawnSetup(player.getServer(), settlement);
        if (validation != null) return validation;
        int available = settlement.getAvailableManpower();
        if (available <= 0) return "No available manpower to mobilise.";
        double share = settlement.getCivilianPopulation() < SMALL_SETTLEMENT_POPULATION ? SMALL_LEVY_SHARE : NORMAL_LEVY_SHARE;
        int manpowerSpent = Math.max(1, (int) Math.floor(available * share));
        int count = manpowerSpent * 2;
        ServerLevel level = resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        PlayerTeam team = settlement.getFactionTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getFactionTeam());
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

    static AbstractRecruitEntity spawnRecruit(ServerLevel level, ServerPlayer actor, PrototypeData.SettlementRecord settlement,
                                                       CampaignData.UnitType unitType, int index, PlayerTeam team, boolean levy, UUID batchId) {
        ItemStack template = CampaignData.get(level).resolveUnitTemplate(settlement, unitType);
        EntityType<?> entityType = null;
        CompoundTag templateTag = null;
        if (!template.isEmpty() && template.getItem() instanceof SpawnEggItem egg) {
            entityType = egg.getType(template.getTag());
            templateTag = template.getTag() == null ? null : template.getTag().copy();
        }
        if (entityType == null) entityType = fallbackType(unitType);
        Entity raw = entityType.create(level);
        if (!(raw instanceof AbstractRecruitEntity recruit)) return null;
        BlockPos spawnPos = Campaign23Manager.findMusterSpawn(level, settlement, index);
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

    public static String linkCurrentClaim(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        if (ClaimEvents.recruitsClaimManager == null) return "Recruits claim manager is not ready.";
        RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(player.blockPosition()));
        if (claim == null) return "Stand inside the Recruits claim you want to link.";
        String dimension = player.serverLevel().dimension().location().toString();
        PrototypeData.SettlementRecord existing = PrototypeData.get(player.serverLevel()).findSettlementByClaim(dimension, claim.getUUID());
        if (existing != null && existing != settlement) return "That claim is already linked to " + existing.getName() + ".";
        boolean added = settlement.linkClaim(dimension, claim.getUUID(), claim.getName());
        if (claim.getOwnerFactionStringID() != null && settlement.getFactionTeam().isBlank()) settlement.setFactionTeam(claim.getOwnerFactionStringID());
        return added ? "Linked claim " + claim.getName() + ". Total claims: " + settlement.getClaimCount() + "."
                : "That claim is already linked to this settlement.";
    }

    public static String unlinkCurrentClaim(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        if (ClaimEvents.recruitsClaimManager == null) return "Recruits claim manager is not ready.";
        RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(player.blockPosition()));
        if (claim == null) return "Stand inside the linked Recruits claim you want to remove.";
        boolean removed = settlement.unlinkClaim(player.serverLevel().dimension().location().toString(), claim.getUUID());
        return removed ? "Unlinked claim " + claim.getName() + ". Remaining claims: " + settlement.getClaimCount() + "."
                : "That claim is not linked to this settlement.";
    }

    public static String clearClaims(PrototypeData.SettlementRecord settlement) {
        int count = settlement.getClaimCount();
        settlement.clearClaimLink();
        return "Removed all " + count + " linked claim(s).";
    }

    private static String setSettlementBanner(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BannerItem)) return "Hold a placed-style Minecraft banner item in your main hand.";
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.visualFor(settlement.getId()).setCustomBanner(held);
        campaign.setDirty(); settlement.addChronicle("Adopted a custom settlement banner.");
        return "Custom settlement banner saved.";
    }

    private static String useFactionBanner(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.visualFor(settlement.getId()).setBannerMode(CampaignData.BannerMode.FACTION); campaign.setDirty();
        return "Settlement now uses its faction banner.";
    }

    private static String clearSettlementBanner(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.visualFor(settlement.getId()).clearCustomBanner(); campaign.setDirty();
        return "Settlement banner cleared.";
    }

    public static ContractUseResult useSettlerContract(ServerPlayer player, String settlementId, UUID contractId) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord settlement = data.getSettlement(settlementId);
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
        if (!settlement.getFactionTeam().isBlank()) {
            PlayerTeam team = level.getScoreboard().getPlayerTeam(settlement.getFactionTeam());
            if (team != null) level.getScoreboard().addPlayerToTeam(villager.getScoreboardName(), team);
        }
        settlement.addChronicle("A new settler arrived.");
        return new ContractUseResult(true, "A registered settler arrived at " + settlement.getName() + ".");
    }

    public static String registerVillager(ServerPlayer player, PrototypeData.SettlementRecord settlement, Villager villager) {
        if (!canManage(player, settlement)) return "You cannot register residents here.";

        PrototypeData data = PrototypeData.get(player.serverLevel());
        UUID villagerId = villager.getUUID();
        PrototypeData.SettlementRecord trackedBy = data.findSettlementTrackingVillager(villagerId);
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

        PrototypeData data = PrototypeData.get(level);
        PrototypeData.SettlementRecord settlement = data.getSettlement(settlementId);
        if (settlement == null) return;
        data.removeVillagerFromOtherSettlements(villager.getUUID(), settlement.getId());
        settlement.registerVillager(villager.getUUID());
    }

    private static String depositFood(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
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

    private static String depositSilver(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty() || !held.is(SILVER_CURRENCY)) {
            return "Hold an item in the #settlementprototype:silver_currency tag.";
        }
        int coins = held.getCount();
        int value = coins * SILVER_TO_WEALTH;
        held.shrink(coins);
        settlement.addWealth(value, characterOrAccountName(player));
        return "Converted " + coins + " silver into " + value + " wealth.";
    }

    private static String withdrawSilver(ServerPlayer player, PrototypeData.SettlementRecord settlement, int coins) {
        int cost = coins * SILVER_TO_WEALTH;
        if (!settlement.spendWealth(cost)) return "Not enough settlement wealth. Required: " + cost + ".";
        Item item = getSilverCurrencyItem();
        ItemStack stack = new ItemStack(item, coins);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        settlement.addContribution(characterOrAccountName(player) + " withdrew " + coins + " silver.");
        return "Withdrew " + coins + " silver for " + cost + " wealth.";
    }

    private static Item getSilverCurrencyItem() {
        Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(SILVER_CURRENCY);
        if (tag.isPresent()) {
            Optional<Holder<Item>> first = tag.get().stream().findFirst();
            if (first.isPresent()) return first.get().value();
        }
        return ModItems.SILVER_COIN.get();
    }

    private static String claimSettlerContract(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
        if (!settlement.claimContract()) return "No Settler Contract is currently available.";
        ItemStack contract = SettlerContractItem.forSettlement(settlement.getId(), settlement.getName());
        if (!player.getInventory().add(contract)) player.drop(contract, false);
        return "Claimed one Settler Contract.";
    }

    public static void promptCharacterCreation(ServerPlayer player) {
        PrototypeNetwork.CHANNEL.send(
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
            String dynasty,
            List<String> selectedTraits,
            int rerollRound
    ) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.CharacterRecord existingCharacter = data.getCharacter(player.getUUID());
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
        String cleanFirst = PrototypeData.cleanText(first, "", 24);
        String cleanMiddle = PrototypeData.cleanText(middle, "", 24);
        String cleanLast = PrototypeData.cleanText(last, "", 24);
        String cleanDynasty = PrototypeData.cleanText(dynasty, "", 32);
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

        PrototypeData.CharacterRecord character = data.getOrCreateCharacter(player.getUUID());
        character.create(cleanFirst, cleanMiddle, cleanLast, faith, address, title, cleanDynasty);
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

    public static void updateCharacterIdentity(ServerPlayer player, String dynasty, String house, boolean returnToMain) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            promptCharacterCreation(player);
            return;
        }
        character.setDynastyAndHouse(dynasty, house);
        data.setDirty();
        player.sendSystemMessage(Component.literal("Character dynasty and main house updated."));
        openCharacterSheet(player, returnToMain);
    }

    public static void openCharacterSheet(ServerPlayer player) {
        openCharacterSheet(player, false);
    }

    public static void openCharacterSheet(ServerPlayer player, boolean returnToMain) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        PrototypeData.CharacterRecord character = data.getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            promptCharacterCreation(player);
            return;
        }
        PrototypeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CharacterSheetPacket(CharacterSnapshot.from(data, player), returnToMain)
        );
    }

    public static void broadcastProfiles(MinecraftServer server) {
        if (server == null) return;
        PrototypeData data = PrototypeData.get(server.overworld());
        Map<UUID, String> profiles = new HashMap<>();
        data.getCharacters().stream()
                .filter(PrototypeData.CharacterRecord::isCreated)
                .forEach(character -> profiles.put(character.getPlayerId(), formattedCharacterName(server, character)));
        PrototypeNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new ProfileSyncPacket(profiles));
    }

    public static void syncProfiles(ServerPlayer player) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        Map<UUID, String> profiles = new HashMap<>();
        data.getCharacters().stream()
                .filter(PrototypeData.CharacterRecord::isCreated)
                .forEach(character -> profiles.put(character.getPlayerId(), formattedCharacterName(player.getServer(), character)));
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ProfileSyncPacket(profiles));
    }

    public static String characterOrAccountName(ServerPlayer player) {
        PrototypeData.CharacterRecord character = PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID());
        return character != null && character.isCreated() ? character.getDisplayName() : player.getGameProfile().getName();
    }

    private static void discardSpawned(ServerLevel level, Collection<UUID> spawned) {
        spawned.forEach(uuid -> {
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

    private static String validateMilitarySpawnSetup(MinecraftServer server, PrototypeData.SettlementRecord settlement) {
        if (!settlement.hasMusterGround()) return "Set this settlement's Muster Ground before recruiting troops.";
        ServerLevel level = resolveMusterGroundLevel(server, settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        if (!settlement.getFactionTeam().isBlank()
                && level.getScoreboard().getPlayerTeam(settlement.getFactionTeam()) == null) {
            return "The saved faction team no longer exists: " + settlement.getFactionTeam();
        }
        return null;
    }

    public static ServerLevel resolveTownSquareLevel(MinecraftServer server, PrototypeData.SettlementRecord settlement) {
        return resolveLevel(server, settlement.getTownSquareDimension());
    }

    public static ServerLevel resolveMusterGroundLevel(MinecraftServer server, PrototypeData.SettlementRecord settlement) {
        return resolveLevel(server, settlement.getMusterGroundDimension());
    }

    public static ServerLevel resolveBarracksLevel(MinecraftServer server, PrototypeData.SettlementRecord settlement) {
        return resolveLevel(server, settlement.getBarracksDimension());
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    public static String returnToBarracks(ServerPlayer player, PrototypeData.SettlementRecord settlement) {
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
        PrototypeData settlements = PrototypeData.get(server.overworld());
        CampaignData campaign = CampaignData.get(server.overworld());
        for (PrototypeData.SettlementRecord settlement : settlements.getSettlements()) {
            for (UUID uuid : Set.copyOf(settlement.getAllMilitaryUnits())) {
                AbstractRecruitEntity recruit = findLoadedRecruit(server, uuid);
                if (recruit == null) continue;
                maintainBarracksLink(recruit, settlement);
                processMercenaryPayment(recruit, settlement, campaign, day);
                applySettlementMorale(recruit, settlement, settlements, campaign);
            }
        }
    }

    /** Admin/debug hook: makes every loaded tracked mercenary in a faction immediately due and processes payment once. */
    public static int forceMercenaryPaymentCheck(MinecraftServer server, String faction) {
        if (server == null) return 0;
        String wanted = faction == null ? "" : faction.trim();
        PrototypeData settlements = PrototypeData.get(server.overworld());
        CampaignData campaign = CampaignData.get(server.overworld());
        long day = server.overworld().getDayTime() / 24000L;
        int count = 0;
        for (PrototypeData.SettlementRecord settlement : settlements.getSettlements()) {
            if (!wanted.isBlank() && !wanted.equals(settlement.getFactionTeam())) continue;
            for (UUID uuid : Set.copyOf(settlement.getAllMilitaryUnits())) {
                AbstractRecruitEntity recruit = findLoadedRecruit(server, uuid);
                if (recruit == null || !recruit.getPersistentData().getBoolean(TAG_MERCENARY)) continue;
                int interval = Math.max(1, recruit.getPersistentData().getInt(TAG_MERCENARY_INTERVAL_DAYS));
                recruit.getPersistentData().putLong(TAG_MERCENARY_NEXT_PAYMENT_DAY, day);
                processMercenaryPayment(recruit, settlement, campaign, day);
                count++;
            }
        }
        return count;
    }


    private static AbstractRecruitEntity findLoadedRecruit(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractRecruitEntity recruit) return recruit;
        }
        return null;
    }

    private static void maintainBarracksLink(AbstractRecruitEntity recruit, PrototypeData.SettlementRecord settlement) {
        if (!settlement.hasBarracks()) return;
        BlockPos upkeep = settlement.getBarracksChest() != null ? settlement.getBarracksChest() : settlement.getBarracks();
        if (upkeep != null && !upkeep.equals(recruit.getUpkeepPos())) recruit.setUpkeepPos(upkeep);
    }

    private static void processMercenaryPayment(AbstractRecruitEntity recruit, PrototypeData.SettlementRecord settlement,
                                                 CampaignData campaign, long day) {
        CompoundTag data = recruit.getPersistentData();
        if (!data.getBoolean(TAG_MERCENARY)) return;
        int wage = Math.max(1, data.getInt(TAG_MERCENARY_WAGE));
        int interval = Math.max(1, data.getInt(TAG_MERCENARY_INTERVAL_DAYS));
        long next = data.contains(TAG_MERCENARY_NEXT_PAYMENT_DAY)
                ? data.getLong(TAG_MERCENARY_NEXT_PAYMENT_DAY)
                : day + interval;
        if (!data.contains(TAG_MERCENARY_NEXT_PAYMENT_DAY)) {
            data.putLong(TAG_MERCENARY_NEXT_PAYMENT_DAY, next);
            return;
        }
        if (day < next) return;

        // Catch up missed intervals when a recruit or its chunk has been unloaded. The cap prevents
        // one very old unit from doing unbounded work in a single server tick; remaining cycles are
        // handled on the next military-system pass.
        int dueCycles = (int) Math.min(64L, 1L + Math.max(0L, day - next) / interval);
        int missedThisPass = 0;
        Campaign23Data extra = Campaign23Data.get((ServerLevel) recruit.level());
        String payerFaction = settlement.getFactionTeam();
        for (int cycle = 0; cycle < dueCycles; cycle++) {
            boolean paid = !payerFaction.isBlank()
                    ? extra.spendFactionWealth(payerFaction, wage)
                    : settlement.spendWealth(wage);
            if (paid) {
                data.putInt(TAG_MERCENARY_MISSED_PAYMENTS, 0);
                recruit.setMoral(Math.min(100.0F, recruit.getMorale() + 5.0F));
            } else {
                int missed = data.getInt(TAG_MERCENARY_MISSED_PAYMENTS) + 1;
                data.putInt(TAG_MERCENARY_MISSED_PAYMENTS, missed);
                missedThisPass++;
                recruit.setMoral(Math.max(0.0F, recruit.getMorale() - 12.0F));
                if (missed >= MERCENARY_DESERTION_MISSES) {
                    desertMercenary(recruit, settlement, campaign, day);
                    return;
                }
            }
            next += interval;
        }
        data.putLong(TAG_MERCENARY_NEXT_PAYMENT_DAY, next);
        if (missedThisPass > 0) {
            int totalMissed = data.getInt(TAG_MERCENARY_MISSED_PAYMENTS);
            settlement.addChronicle("Mercenary wages were missed (" + totalMissed + "/" + MERCENARY_DESERTION_MISSES + ").");
        }
    }

    private static void desertMercenary(AbstractRecruitEntity recruit, PrototypeData.SettlementRecord settlement,
                                         CampaignData campaign, long day) {
        CompoundTag data = recruit.getPersistentData();
        UUID unitId = recruit.getUUID();
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

    private static void applySettlementMorale(AbstractRecruitEntity recruit, PrototypeData.SettlementRecord settlement,
                                               PrototypeData settlements, CampaignData campaign) {
        int target = 50 + Math.round((settlement.getStability() - 50) * 0.30F);
        PrototypeData.CharacterRecord ruler = settlement.getOwnerId() == null ? null : settlements.getCharacter(settlement.getOwnerId());
        if (ruler != null) target += CampaignDefinitions.totalEffect(ruler.getTraits(), CampaignDefinitions.EffectKind.MORALE);
        CampaignData.MarriageRecord marriage = settlement.getOwnerId() == null ? null : campaign.getMarriage(settlement.getOwnerId());
        if (marriage != null) target += marriage.moraleBonus();
        if (settlement.hasBarracks() && recruit.level().dimension().location().toString().equals(settlement.getBarracksDimension())
                && recruit.blockPosition().closerThan(settlement.getBarracks(), settlement.getBarracksRadius())) target += 8;
        if (recruit.getPersistentData().getBoolean(TAG_MERCENARY)) {
            target -= recruit.getPersistentData().getInt(TAG_MERCENARY_MISSED_PAYMENTS) * 12;
        }
        target = Math.max(10, Math.min(90, target));
        float current = recruit.getMorale();
        if (current < target - 1) recruit.setMoral(Math.min(target, current + 1.0F));
        else if (current > target + 1) recruit.setMoral(Math.max(target, current - 1.0F));
    }

    private static String formattedCharacterName(MinecraftServer server, PrototypeData.CharacterRecord character) {
        if (server == null || character == null) return character == null ? "" : character.getDisplayName();
        ServerPlayer online = server.getPlayerList().getPlayer(character.getPlayerId());
        if (online == null || online.getTeam() == null) return character.getDisplayName();
        String faction = online.getTeam().getName();
        CampaignData campaign = CampaignData.get(server.overworld());
        boolean leader = FactionEvents.recruitsFactionManager != null
                && FactionEvents.recruitsFactionManager.getFactionByStringID(faction) != null
                && character.getPlayerId().equals(FactionEvents.recruitsFactionManager.getFactionByStringID(faction).getTeamLeaderUUID());
        String rank = campaign.factionRankTitle(faction, character.getPlayerId(), leader);
        return rank.isBlank() ? character.getDisplayName() : "[" + rank + "] " + character.getDisplayName();
    }

    public enum SettlementAction {
        RECRUIT_ONE, RECRUIT_FIVE, RECRUIT_TEN, RECRUIT_TWENTY_FIVE, RECRUIT_UNIT,
        RAISE_LEVY, RETURN_LEVY, RETURN_TO_BARRACKS, RUN_CHECKER, DEPOSIT_FOOD, DEPOSIT_SILVER,
        WITHDRAW_SILVER_ONE, WITHDRAW_SILVER_TEN, CLAIM_CONTRACT,
        SET_CUSTOM_BANNER, USE_FACTION_BANNER, CLEAR_BANNER, LINK_CURRENT_CLAIM, UNLINK_CURRENT_CLAIM, CLEAR_CLAIMS,
        OPEN_CHARACTER, OPEN_MAIN, SELECT_PREVIOUS, SELECT_NEXT, REFRESH
    }

    public record ContractUseResult(boolean success, String message) {
    }

    @FunctionalInterface
    private interface ActionSupplier {
        String get();
    }
}
