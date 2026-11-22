package ee.lastpagans.settlements;

import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import ee.lastpagans.settlements.network.MilitarySnapshotPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Training, reserve and one-soldier-per-second muster authority. */
public final class MilitaryManager {
    private static final int ARRIVAL_INTERVAL_TICKS = 20;
    public static final String TAG_MUSTER_OWNER = "SettlementsMusterOwner";
    public static final String TAG_MUSTER_ORDER = "SettlementsMusterOrder";
    public static final String TAG_MUSTER_GROUP = "SettlementsMusterGroup";
    public static final String TAG_MUSTER_INDEX = "SettlementsMusterIndex";
    public static final String TAG_DEPLOYMENT_RELEASED = "SettlementsDeploymentReleased";
    private MilitaryManager() {}

    public static void open(ServerPlayer player, SettlementData.SettlementRecord settlement, String message) {
        send(player, settlement, true, message);
    }

    public static void send(ServerPlayer player, SettlementData.SettlementRecord settlement, boolean open, String message) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MilitarySnapshotPacket(open, MilitarySnapshot.from(player, settlement, message)));
    }

    public static String train(ServerPlayer player, SettlementData.SettlementRecord settlement, CampaignData.UnitType type, int quantity) {
        if (type == null || !type.selectable() || quantity < 1 || quantity > 100000) return "Choose a valid troop type and quantity.";
        MilitaryData data = MilitaryData.get(player.serverLevel());
        if (data.training(settlement.getId()).size() >= 256) return "This settlement's training queue is full.";
        MilitaryData.ReservePool pool = data.pool(settlement.getId(), type);
        int queued = data.training(settlement.getId()).stream().filter(value -> value.type == type).mapToInt(MilitaryData.TrainingOrder::remaining).sum();
        int capacity = SettlementConfig.RESERVE_CAPACITY.get(type).get();
        if ((long)pool.total() + queued + quantity > capacity)
            return "Reserve capacity allows only " + Math.max(0, capacity - pool.total() - queued) + " more " + type.displayName() + ".";

        int mpEach = SettlementConfig.TRAINING_MP_MILLI_COST.get(type).get();
        int silverEach = SettlementConfig.TRAINING_SILVER_COST.get(type).get();
        long silverCost = (long)silverEach * quantity;
        if (silverCost > Integer.MAX_VALUE || StateTreasury.balance(player.serverLevel(), settlement) < silverCost)
            return "Training requires " + silverCost + " Silver; the treasury has " + StateTreasury.balance(player.serverLevel(), settlement) + ".";

        int nativeMilli = nativeMilli(data, settlement);
        int foreignMilli = foreignMilli(data, player.serverLevel(), settlement);
        int nativeSoldiers = Math.min(quantity, nativeMilli / mpEach);
        int foreignSoldiers = quantity - nativeSoldiers;
        if (foreignSoldiers > foreignMilli / mpEach)
            return "Training requires " + formatMp((long)mpEach * quantity) + " MP; available: "
                    + formatMp(nativeMilli) + " native and " + formatMp(foreignMilli) + " foreign.";

        if (!StateTreasury.spend(player.serverLevel(), settlement, (int)silverCost)) return "The treasury changed; no order was created.";
        setNativeMilli(data, settlement, nativeMilli - nativeSoldiers * mpEach);
        setForeignMilli(data, player.serverLevel(), settlement, foreignMilli - foreignSoldiers * mpEach);
        int duration = SettlementConfig.TRAINING_SECONDS.get(type).get() * 20;
        MilitaryData.TrainingOrder order = new MilitaryData.TrainingOrder(UUID.randomUUID(), player.getUUID(), settlement.getId(), type,
                quantity, nativeSoldiers, foreignSoldiers, silverEach, mpEach, duration, 0, 0, data.nextSequence());
        data.mutableTraining(settlement.getId()).add(order); data.setDirty();
        refreshForeignPressure(player.serverLevel(), settlement, data);
        settlement.addChronicle(quantity + " " + plural(type, quantity) + " entered training.");
        return quantity + " added to training queue.";
    }

    public static String cancelTraining(ServerPlayer player, SettlementData.SettlementRecord settlement, UUID orderId) {
        MilitaryData data = MilitaryData.get(player.serverLevel());
        MilitaryData.TrainingOrder order = data.training(settlement.getId()).stream().filter(value -> value.id.equals(orderId)).findFirst().orElse(null);
        if (order == null) return "That training order is already complete or canceled.";
        int remaining = order.remaining();
        if (!data.removeTraining(settlement.getId(), order.id)) return "That training order was already canceled.";
        setNativeMilli(data, settlement, nativeMilli(data, settlement) + order.remainingNative() * order.mpMilliPerSoldier);
        setForeignMilli(data, player.serverLevel(), settlement,
                foreignMilli(data, player.serverLevel(), settlement) + order.remainingForeign() * order.mpMilliPerSoldier);
        StateTreasury.change(player.serverLevel(), settlement, remaining * order.silverPerSoldier);
        refreshForeignPressure(player.serverLevel(), settlement, data);
        settlement.addChronicle("Canceled the unfinished portion of a " + order.type.displayName() + " training order.");
        return "Canceled " + remaining + " untrained " + plural(order.type, remaining) + "; unused MP and Silver were refunded once.";
    }

    public static String muster(ServerPlayer player, SettlementData.SettlementRecord settlement, CampaignData.UnitType type, int quantity) {
        if (type == null || !type.selectable() || quantity < 1 || quantity > 100000) return "Choose a valid troop type and quantity.";
        if (!settlement.hasMusterGround()) return "Set this settlement's Muster Ground before deploying reserves.";
        ServerLevel level = SettlementManager.resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return "The saved Muster Ground dimension is unavailable.";
        MilitaryData data = MilitaryData.get(player.serverLevel());
        if (data.musters(settlement.getId()).size() >= 256) return "This Muster Ground queue is full.";
        MilitaryData.ReservePool pool = data.pool(settlement.getId(), type);
        if (pool.total() < quantity) return "Only " + pool.total() + " " + plural(type, pool.total()) + " are ready in reserve.";
        MilitaryData.DeploymentCount count = data.deployed(player.getUUID());
        int remainingCap = SettlementConfig.PLAYER_DEPLOYED_TROOP_CAP.get() - count.total() - data.pendingForPlayer(player.getUUID());
        if (quantity > remainingCap) return "Deployment cap allows only " + Math.max(0, remainingCap) + " more soldiers.";
        UUID orderId = UUID.randomUUID(), groupId = UUID.randomUUID();
        RecruitsGroup group = createGroup(level, player, groupId, settlement.getName() + " " + type.displayName());
        if (group == null) return "Recruits group management is unavailable; reserves were not changed.";
        int nativeCount = pool.takeNativeFirst(quantity);
        List<CampaignData.UnitType> units = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) units.add(type);
        data.mutableMusters(settlement.getId()).add(new MilitaryData.MusterOrder(orderId, groupId, player.getUUID(), settlement.getId(), "",
                type.displayName(), units, nativeCount, quantity - nativeCount, false, MilitaryData.MusterPhase.MUSTERING,
                0, 0, data.nextSequence(), player.serverLevel().getGameTime() + ARRIVAL_INTERVAL_TICKS));
        data.setDirty();
        refreshForeignPressure(player.serverLevel(), settlement, data);
        settlement.addChronicle(quantity + " " + plural(type, quantity) + " were committed to muster under " + player.getGameProfile().getName() + ".");
        return quantity + " " + plural(type, quantity) + " committed to one owned group. One soldier will arrive per second.";
    }

    public static String ransomForeign(ServerPlayer player, SettlementData.SettlementRecord settlement, int requested) {
        CampaignSystemsData systems = CampaignSystemsData.get(player.serverLevel());
        int available = systems.foreignManpower(settlement.getId());
        int quantity = requested <= 0 ? available : Math.min(requested, available);
        if (quantity <= 0) return "There is no foreign manpower to ransom.";
        int value = SettlementConfig.FOREIGN_MANPOWER_SELL_VALUE.get();
        long proceeds = (long)quantity * value;
        if (proceeds > Integer.MAX_VALUE - StateTreasury.balance(player.serverLevel(), settlement)) return "The treasury cannot hold those ransom proceeds.";
        if (!systems.spendForeignManpower(settlement.getId(), quantity)) return "Foreign manpower changed; refresh and try again.";
        StateTreasury.change(player.serverLevel(), settlement, (int)proceeds);
        refreshForeignPressure(player.serverLevel(), settlement, MilitaryData.get(player.serverLevel()));
        return "Ransomed " + quantity + " foreign MP for " + proceeds + " Silver.";
    }

    public static String integrateForeign(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        CampaignSystemsData systems = CampaignSystemsData.get(player.serverLevel());
        MilitaryData data = MilitaryData.get(player.serverLevel());
        int availableMilli = capturedForeignMilli(data, player.serverLevel(), settlement);
        if (availableMilli <= 0) return "There is no foreign manpower to integrate.";
        int integratedMilli = (int)((long)availableMilli * SettlementConfig.FOREIGN_MANPOWER_INTEGRATION_PERCENT.get() / 100L);
        systems.setForeignManpower(settlement.getId(), 0);
        data.setCapturedForeignCredit(settlement.getId(), 0);
        setForeignMilli(data, player.serverLevel(), settlement, foreignMilli(data, player.serverLevel(), settlement) + integratedMilli);
        refreshForeignPressure(player.serverLevel(), settlement, data);
        return "Integrated " + formatMp(integratedMilli) + " foreign-origin MP; "
                + formatMp(availableMilli - integratedMilli) + " MP was lost during integration.";
    }

    public static void process(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        MilitaryData data = MilitaryData.get(overworld);
        long now = overworld.getGameTime();
        long previous = data.lastProcessedGameTime();
        if (previous == Long.MIN_VALUE || now < previous) { data.setLastProcessedGameTime(now); return; }
        int elapsed = (int)Math.min(1200L, Math.max(0L, now - previous));
        if (elapsed <= 0) return;
        data.setLastProcessedGameTime(now);
        SettlementData settlements = SettlementData.get(overworld);
        Set<String> changed = new HashSet<>();

        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            List<MilitaryData.TrainingOrder> queue = data.mutableTraining(settlement.getId());
            if (!queue.isEmpty()) {
                MilitaryData.TrainingOrder active = queue.get(0); active.progressTicks += elapsed;
                int completedNow = 0;
                while (active.progressTicks >= active.durationTicks && active.completed < active.total) {
                    active.progressTicks -= active.durationTicks;
                    boolean foreign = active.completed >= active.nativeTotal;
                    if (foreign) data.pool(settlement.getId(), active.type).addForeign(1); else data.pool(settlement.getId(), active.type).addNative(1);
                    active.completed++; completedNow++;
                }
                if (completedNow > 0) {
                    changed.add(settlement.getId()); data.setDirty();
                    CampaignToastSender.settlementManagers(server, settlement.getId(), "READY TO MUSTER",
                            completedNow + " " + plural(active.type, completedNow) + " ready to muster at " + settlement.getName() + ".");
                }
                if (active.completed >= active.total) { queue.remove(0); data.setDirty(); changed.add(settlement.getId()); }
            }

            List<MilitaryData.MusterOrder> orders = data.mutableMusters(settlement.getId());
            for (MilitaryData.MusterOrder order : orders) {
                if (order.phase != MilitaryData.MusterPhase.ACCEPTING_CONTRACT) continue;
                order.acceptanceTicks = Math.max(0, order.acceptanceTicks - elapsed);
                if (order.acceptanceTicks == 0) { order.phase = MilitaryData.MusterPhase.MUSTERING; order.queueSequence = data.nextSequence(); order.nextArrivalGameTime = now + ARRIVAL_INTERVAL_TICKS; changed.add(settlement.getId()); }
            }
            MilitaryData.MusterOrder activeMuster = orders.stream().filter(value -> value.phase == MilitaryData.MusterPhase.MUSTERING)
                    .min(Comparator.comparingLong(value -> value.queueSequence)).orElse(null);
            if (activeMuster != null && now >= activeMuster.nextArrivalGameTime) {
                if (spawnNext(server, settlement, activeMuster)) {
                    activeMuster.spawned++; data.addWorldDeployment(activeMuster.ownerId, 1); changed.add(settlement.getId());
                    activeMuster.nextArrivalGameTime += ARRIVAL_INTERVAL_TICKS;
                    if (activeMuster.spawned >= activeMuster.total()) {
                        orders.remove(activeMuster); data.setDirty();
                        settlement.addChronicle(activeMuster.total() + " " + activeMuster.displayName + " finished mustering.");
                        CampaignToastSender.settlementManagers(server, settlement.getId(), "MUSTER COMPLETE",
                                activeMuster.total() + " " + activeMuster.displayName.toLowerCase(Locale.ROOT) + " mustered at " + settlement.getName() + ".");
                    }
                } else activeMuster.nextArrivalGameTime = now + ARRIVAL_INTERVAL_TICKS;
            }
        }
        if (!changed.isEmpty()) broadcast(server, changed);
    }

    private static boolean spawnNext(MinecraftServer server, SettlementData.SettlementRecord settlement, MilitaryData.MusterOrder order) {
        ServerLevel level = SettlementManager.resolveMusterGroundLevel(server, settlement);
        if (level == null || settlement.getMusterGround() == null || RecruitEvents.recruitsGroupsManager == null) return false;
        RecruitsGroup group = RecruitEvents.recruitsGroupsManager.getGroup(order.groupId);
        if (group == null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(order.ownerId);
            String ownerName = owner == null ? server.getProfileCache().get(order.ownerId).map(com.mojang.authlib.GameProfile::getName).orElse(order.ownerId.toString()) : owner.getGameProfile().getName();
            group = new RecruitsGroup(order.displayName, order.ownerId, ownerName, 0); group.setUUID(order.groupId);
            RecruitEvents.recruitsGroupsManager.addOrUpdateGroup(level, owner, group);
        }
        CampaignData.UnitType type = order.nextType(); if (type == null) return false;
        AABB arrivalArea = new AABB(settlement.getMusterGround()).inflate(64D);
        boolean alreadySpawned = level.getEntitiesOfClass(AbstractRecruitEntity.class, arrivalArea, Entity::isAlive).stream()
                .anyMatch(value -> value.getPersistentData().hasUUID(TAG_MUSTER_ORDER)
                        && order.id.equals(value.getPersistentData().getUUID(TAG_MUSTER_ORDER))
                        && value.getPersistentData().getInt(TAG_MUSTER_INDEX) == order.spawned);
        if (alreadySpawned) return true;
        ServerPlayer owner = server.getPlayerList().getPlayer(order.ownerId);
        PlayerTeam team = settlement.getEffectiveRecruitsTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam());
        ItemStack template = CampaignData.get(level).resolveUnitTemplate(settlement, type);
        if (order.mercenary && !order.companyId.isBlank()) {
            MercenaryRosterRegistry.Definition definition = MercenaryRosterRegistry.get(order.companyId);
            CampaignData.RosterRecord roster = definition == null ? null : CampaignData.get(level).getRoster(definition.templateRoster());
            if (roster != null) template = roster.getTemplate(type);
        }
        AbstractRecruitEntity recruit = SettlementManager.spawnMusteredRecruit(level, owner, settlement, type, order.spawned,
                team, template, order.ownerId, order.groupId, order.id, order.mercenary ? order.companyId : "", order.nextForeign());
        if (recruit == null) return false;
        if (order.mercenary) {
            settlement.addMercenaryCompanyUnit(recruit.getUUID()); MercenaryCompanyData.get(level).addUnit(order.companyId, recruit.getUUID());
        } else settlement.addProfessional(recruit.getUUID());
        return true;
    }

    private static RecruitsGroup createGroup(ServerLevel level, ServerPlayer owner, UUID id, String name) {
        if (RecruitEvents.recruitsGroupsManager == null) return null;
        try { RecruitsGroup group = new RecruitsGroup(name, owner, 0); group.setUUID(id); RecruitEvents.recruitsGroupsManager.addOrUpdateGroup(level, owner, group); return group; }
        catch (RuntimeException exception) { return null; }
    }

    public static boolean queueMercenary(ServerPlayer player, SettlementData.SettlementRecord settlement, MercenaryRosterRegistry.Definition definition) {
        ServerLevel level = SettlementManager.resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return false;
        MilitaryData data = MilitaryData.get(level); UUID group = UUID.randomUUID();
        if (data.musters(settlement.getId()).size() >= 256) return false;
        if (createGroup(level, player, group, definition.name()) == null) return false;
        List<CampaignData.UnitType> units = new ArrayList<>();
        for (MercenaryRosterRegistry.UnitStack stack : definition.units()) for (int i = 0; i < stack.count(); i++) units.add(stack.type());
        data.mutableMusters(settlement.getId()).add(new MilitaryData.MusterOrder(UUID.randomUUID(), group, player.getUUID(), settlement.getId(),
                definition.id(), definition.name(), units, 0, 0, true, MilitaryData.MusterPhase.ACCEPTING_CONTRACT, 0,
                SettlementConfig.MERCENARY_ACCEPTANCE_SECONDS.get() * 20, Long.MAX_VALUE, 0L)); data.setDirty();
        return true;
    }

    public static void cancelMercenary(String settlementId, String companyId, ServerLevel level) {
        MilitaryData data = MilitaryData.get(level); data.mutableMusters(settlementId).removeIf(value -> value.mercenary && companyId.equals(value.companyId)); data.setDirty();
    }

    public static void onTrackedUnitDeath(ServerLevel level, UUID unitId, CompoundTag tag) {
        if (level == null || tag == null || !tag.hasUUID(TAG_MUSTER_OWNER) || tag.getBoolean(TAG_DEPLOYMENT_RELEASED)) return;
        tag.putBoolean(TAG_DEPLOYMENT_RELEASED, true);
        MilitaryData data = MilitaryData.get(level); MilitaryData.DeploymentCount count = data.deployed(tag.getUUID(TAG_MUSTER_OWNER));
        data.setDeployed(tag.getUUID(TAG_MUSTER_OWNER), Math.max(0, count.world() - 1), count.campaign());
    }

    public static void reportDeployment(ServerLevel level, UUID player, int world, int campaign) {
        if (level != null) MilitaryData.get(level).setDeployed(player, world, campaign);
    }

    public static int convertReservesToForeign(ServerLevel level, SettlementData.SettlementRecord settlement) {
        MilitaryData data = MilitaryData.get(level); long milli = 0L;
        for (CampaignData.UnitType type : CampaignData.UnitType.values()) {
            MilitaryData.ReservePool pool = data.pool(settlement.getId(), type);
            milli += (long)pool.total() * SettlementConfig.TRAINING_MP_MILLI_COST.get(type).get(); pool.set(0, 0);
        }
        long combined = Math.max(0L, data.capturedForeignCredit(settlement.getId()) + milli);
        int value = (int)Math.min(Integer.MAX_VALUE, combined / 1000L);
        if (value > 0) CampaignSystemsData.get(level).addForeignManpower(settlement.getId(), value);
        data.setCapturedForeignCredit(settlement.getId(), (int)(combined % 1000L)); data.setDirty();
        refreshForeignPressure(level, settlement, data); return value;
    }

    private static void broadcast(MinecraftServer server, Set<String> settlements) {
        SettlementData store = SettlementData.get(server.overworld());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SettlementData.SettlementRecord selected = store.getSelectedSettlement(player.getUUID());
            if (selected != null && settlements.contains(selected.getId()) && SettlementManager.canView(player, selected)) {
                send(player, selected, false, "");
                MercenaryRosterManager.refresh(player, selected);
            }
        }
    }

    private static int nativeMilli(MilitaryData data, SettlementData.SettlementRecord settlement) { return settlement.getAvailableManpower() * 1000 + data.nativeCredit(settlement.getId()); }
    private static int foreignMilli(MilitaryData data, ServerLevel level, SettlementData.SettlementRecord settlement) {
        return data.integratedForeignMilli(settlement.getId());
    }
    private static int capturedForeignMilli(MilitaryData data, ServerLevel level, SettlementData.SettlementRecord settlement) {
        long value = (long)CampaignSystemsData.get(level).foreignManpower(settlement.getId()) * 1000L
                + data.capturedForeignCredit(settlement.getId());
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }
    private static void setNativeMilli(MilitaryData data, SettlementData.SettlementRecord settlement, int total) {
        int safe = Math.max(0, total); settlement.setAvailableManpower(safe / 1000); data.setNativeCredit(settlement.getId(), safe % 1000);
    }
    private static void setForeignMilli(MilitaryData data, ServerLevel level, SettlementData.SettlementRecord settlement, int total) {
        data.setIntegratedForeignMilli(settlement.getId(), Math.max(0, total));
    }
    private static void refreshForeignPressure(ServerLevel level, SettlementData.SettlementRecord settlement, MilitaryData data) {
        if (level == null || settlement == null || data == null) return;
        long exposure = (long)foreignMilli(data, level, settlement) + capturedForeignMilli(data, level, settlement);
        for (CampaignData.UnitType type : CampaignData.UnitType.values()) {
            int cost = SettlementConfig.TRAINING_MP_MILLI_COST.get(type).get();
            exposure += (long)data.pool(settlement.getId(), type).foreignCount() * cost;
        }
        for (MilitaryData.TrainingOrder order : data.training(settlement.getId()))
            exposure += (long)order.remainingForeign() * order.mpMilliPerSoldier;
        CampaignSystemsData systems = CampaignSystemsData.get(level);
        if (exposure <= 0L) { systems.removeModifier(settlement.getId(), "foreign_soldiers"); return; }
        int penalty = -Math.max(1, Math.min(12, (int)((exposure + 9_999L) / 10_000L)));
        systems.addModifier(settlement.getId(), new CampaignSystemsData.ModifierRecord("foreign_soldiers", "Foreign Soldiers",
                "minecraft:iron_sword", "MIXED", "Foreign manpower",
                "Foreign-origin manpower and trained reserves create local unrest in proportion to their concentration.",
                penalty, 0, 0L));
    }
    private static String formatMp(long milli) { return milli % 1000L == 0L ? Long.toString(milli / 1000L) : String.format(Locale.ROOT, "%.1f", milli / 1000.0D); }
    private static String plural(CampaignData.UnitType type, int count) { return type.displayName() + (count == 1 ? "" : "s"); }
}
