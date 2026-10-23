package ee.lastpagans.settlements;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import ee.lastpagans.settlements.network.MercenaryRosterSnapshotPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Named, polity-level mercenary companies layered over Villager Recruits entities. */
public final class MercenaryRosterManager {
    private static final String TAG_LIFECYCLE_VERSION = "SettlementsMercenaryLifecycle";
    private static final String TAG_PENDING_SPAWN = "SettlementsMercenaryPendingSpawn";
    private static final int LIFECYCLE_VERSION = 1;
    private MercenaryRosterManager() {}

    public static void open(ServerPlayer player, String message) {
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSelectedSettlement(player.getUUID());
        if (settlement == null || !SettlementManager.canView(player, settlement)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Select a settlement you can view before opening Mercenary Rosters."));
            return;
        }
        send(player, settlement, message);
    }

    private static void send(ServerPlayer player, SettlementData.SettlementRecord settlement, String message) {
        String factionId = settlement.getFactionTeam();
        int treasury = StateTreasury.balance(player.serverLevel(), settlement);
        int reliability = factionId.isBlank() ? 0 : CampaignData.get(player.serverLevel()).getFactionReliability(factionId);
        String employerKey = employerKey(settlement);
        MercenaryCompanyData companyData = MercenaryCompanyData.get(player.serverLevel());
        List<MercenaryRosterSnapshot.Entry> entries = new ArrayList<>();
        for (MercenaryRosterRegistry.Definition definition : MercenaryRosterRegistry.definitions()) {
            MercenaryCompanyData.CompanyContract contract = companyData.contract(definition.id());
            boolean yours = contract != null && employerKey.equals(contract.employerKey());
            int loyalty = contract == null
                    ? companyData.trust(definition.id(), employerKey, definition.startingLoyalty())
                    : companyData.trust(definition.id(), contract.employerKey(), definition.startingLoyalty());
            int price = effectiveHireCost(player.serverLevel(), definition, contract, settlement, loyalty);
            boolean canManage = SettlementManager.canManage(player, settlement);
            boolean canHire = canManage;
            String status = contract == null ? "Available" : contract.status();
            String employer = contract == null ? "None" : employerName(player.getServer(), contract);
            String action = contract == null ? "Hire Company" : yours ? "End Contract" : "Buy Out";
            String reason = "";
            if (!canManage) { canHire = false; reason = "You do not manage this settlement."; }
            else if (contract != null && !yours && refusesPoaching(player.serverLevel(), definition, contract, loyalty)) {
                canHire = false; reason = "The company refuses to break loyalty with " + employer + ".";
            } else if (!yours && treasury < price) {
                canHire = false; reason = "Requires " + price + " State Treasury; available " + treasury + ".";
            }
            entries.add(new MercenaryRosterSnapshot.Entry(definition.id(), definition.name(), definition.iconItem(), definition.composition(),
                    definition.size(), definition.hireCost(), price, definition.upkeep(), definition.paymentIntervalDays(), loyalty,
                    status, employer, canHire, yours, action, reason));
        }
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MercenaryRosterSnapshotPacket(new MercenaryRosterSnapshot(settlement.getId(), settlement.getName(),
                        factionId.isBlank() ? "Independent" : CampaignManager.factionName(factionId), treasury, reliability, List.copyOf(entries), message == null ? "" : message)));
    }

    public static void handleAction(ServerPlayer player, String companyId, String action) {
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSelectedSettlement(player.getUUID());
        if (settlement == null || !SettlementManager.canManage(player, settlement)) { open(player, "You do not manage the selected settlement."); return; }
        String result = "Unknown mercenary action.";
        if ("HIRE".equalsIgnoreCase(action)) result = hire(player, settlement, companyId);
        else if ("RELEASE".equalsIgnoreCase(action)) result = release(player, settlement, companyId);
        send(player, settlement, result);
    }

    public static String hire(ServerPlayer player, SettlementData.SettlementRecord settlement, String companyId) {
        MercenaryRosterRegistry.Definition definition = MercenaryRosterRegistry.get(companyId);
        if (definition == null) return "That mercenary company no longer exists in the roster configuration.";
        String validation = SettlementManager.validateMilitarySpawnSetup(player.getServer(), settlement);
        if (validation != null) return validation;
        MercenaryCompanyData data = MercenaryCompanyData.get(player.serverLevel());
        MercenaryCompanyData.CompanyContract existing = data.contract(definition.id());
        String newKey = employerKey(settlement);
        if (existing != null && newKey.equals(existing.employerKey())) return definition.name() + " is already under your contract.";
        int currentLoyalty = existing == null ? definition.startingLoyalty() : data.trust(definition.id(), existing.employerKey(), definition.startingLoyalty());
        if (existing != null && refusesPoaching(player.serverLevel(), definition, existing, currentLoyalty)) {
            return definition.name() + " refuses to break its loyalty with " + employerName(player.getServer(), existing) + ".";
        }
        int price = effectiveHireCost(player.serverLevel(), definition, existing, settlement, currentLoyalty);
        String factionId = settlement.getFactionTeam();
        int available = StateTreasury.balance(player.serverLevel(), settlement);
        if (available < price) return "Hiring " + definition.name() + " costs " + price + " State Treasury; only " + available + " is available.";

        ServerLevel level = SettlementManager.resolveMusterGroundLevel(player.getServer(), settlement);
        if (level == null) return "The Muster Ground dimension is unavailable.";
        CampaignSystemsData.RegionRecord area = CampaignSystemsData.get(level).musterArea(settlement.getId());
        if (area != null && area.valid()) {
            int capacity = CampaignSystemsManager.usableMusterCapacity(level, area, definition.size());
            if (capacity < definition.size()) return "The Muster Ground has only " + capacity + " safe standing positions; " + definition.size() + " are required for this company.";
        }

        CampaignData campaign = CampaignData.get(level);
        CampaignData.RosterRecord templateRoster = campaign.getRoster(definition.templateRoster());
        PlayerTeam team = settlement.getEffectiveRecruitsTeam().isBlank() ? null : level.getScoreboard().getPlayerTeam(settlement.getEffectiveRecruitsTeam());
        List<AbstractRecruitEntity> spawned = new ArrayList<>();
        int index = 0;
        for (MercenaryRosterRegistry.UnitStack unit : definition.units()) {
            ItemStack template = templateRoster == null ? ItemStack.EMPTY : templateRoster.getTemplate(unit.type());
            for (int i = 0; i < unit.count(); i++, index++) {
                AbstractRecruitEntity recruit = SettlementManager.spawnRecruitFromTemplate(level, player, settlement, unit.type(), index, team, false, null, template, definition.id());
                if (recruit == null) {
                    rollbackSpawnedRecruits(player.getServer(), spawned);
                    return "Mercenary spawning failed; no Treasury was charged.";
                }
                // Company contracts are paid once per company, never once per soldier.
                CampaignSystemsData.get(level).removeMercenaryContract(recruit.getUUID());
                recruit.getPersistentData().remove(SettlementManager.TAG_MERCENARY_WAGE);
                recruit.getPersistentData().remove(SettlementManager.TAG_MERCENARY_INTERVAL_DAYS);
                recruit.getPersistentData().remove(SettlementManager.TAG_MERCENARY_NEXT_PAYMENT_DAY);
                recruit.getPersistentData().remove(SettlementManager.TAG_MERCENARY_MISSED_PAYMENTS);
                recruit.getPersistentData().putBoolean(SettlementManager.TAG_MERCENARY, true);
                recruit.getPersistentData().putString(SettlementManager.TAG_MERCENARY_COMPANY, definition.id());
                spawned.add(recruit);
            }
        }

        boolean paid = StateTreasury.spend(level, settlement, price);
        if (!paid) {
            rollbackSpawnedRecruits(player.getServer(), spawned);
            return "The treasury changed before the contract completed; no company was hired.";
        }

        if (existing != null) terminateContract(player.getServer(), existing, false, "The company accepted a rival contract.");
        Set<UUID> units = new HashSet<>();
        for (AbstractRecruitEntity recruit : spawned) { units.add(recruit.getUUID()); settlement.addMercenaryCompanyUnit(recruit.getUUID()); }
        long day = level.getDayTime() / 24000L;
        data.put(new MercenaryCompanyData.CompanyContract(definition.id(), settlement.getId(), factionId, player.getUUID(), day,
                day + definition.paymentIntervalDays(), 0, units));
        int bidderReliability = factionId.isBlank() ? 0 : campaign.getFactionReliability(factionId);
        int starting = Math.max(definition.startingLoyalty(), definition.startingLoyalty() + bidderReliability / 10);
        data.setTrust(definition.id(), newKey, Math.max(data.trust(definition.id(), newKey, definition.startingLoyalty()), starting));
        settlement.addChronicle(definition.name() + " was contracted for " + price + " Treasury (" + definition.size() + " mercenaries)." );
        campaign.addNews(CampaignData.NewsCategory.MILITARY, CampaignData.NewsImportance.MINOR, "Mercenaries hired",
                settlement.getName() + " contracted " + definition.name() + ".", day, factionId, settlement.getId(), CampaignData.NewsVisibility.FACTION);
        return "Hired " + definition.name() + " for " + price + " Treasury. Upkeep: " + definition.upkeep() + " every " + definition.paymentIntervalDays() + " day(s).";
    }

    public static String release(ServerPlayer player, SettlementData.SettlementRecord settlement, String companyId) {
        MercenaryRosterRegistry.Definition definition = MercenaryRosterRegistry.get(companyId);
        MercenaryCompanyData data = MercenaryCompanyData.get(player.serverLevel());
        MercenaryCompanyData.CompanyContract contract = data.contract(companyId);
        if (definition == null || contract == null) return "That company is not currently contracted.";
        if (!employerKey(settlement).equals(contract.employerKey())) return "That company is employed by another polity.";
        if (contract.missedPayments() == 0) data.changeTrust(definition.id(), contract.employerKey(), 5, definition.startingLoyalty());
        terminateContract(player.getServer(), contract, true, "The contract was concluded.");
        settlement.addChronicle(definition.name() + " completed its contract and departed.");
        return "Contract with " + definition.name() + " ended.";
    }

    public static void process(MinecraftServer server, long day) {
        if (server == null) return;
        MercenaryCompanyData data = MercenaryCompanyData.get(server.overworld());
        SettlementData settlements = SettlementData.get(server.overworld());
        CampaignData campaign = CampaignData.get(server.overworld());
        CampaignSystemsData systems = CampaignSystemsData.get(server.overworld());
        for (MercenaryCompanyData.CompanyContract original : List.copyOf(data.contracts())) {
            MercenaryRosterRegistry.Definition definition = MercenaryRosterRegistry.get(original.companyId());
            SettlementData.SettlementRecord settlement = settlements.getSettlement(original.settlementId());
            if (original.unitIds().isEmpty()) { data.remove(original.companyId()); continue; }
            if (definition == null) {
                terminateContract(server, original, true, "The mercenary company was removed from the roster configuration; its contract ended.");
                continue;
            }
            if (settlement == null) {
                terminateContract(server, original, true, definition.name() + " departed because its contracted settlement no longer exists.");
                continue;
            }
            MercenaryCompanyData.CompanyContract contract = original;
            String currentFaction = settlement.getFactionTeam();
            if (!currentFaction.equals(contract.factionId())) {
                // Founding a proper faction should not throw away an independent ruler's existing company.
                // A genuine change of political control, however, ends the old polity's contract cleanly.
                if (contract.factionId().isBlank() && !currentFaction.isBlank()
                        && contract.hiringPlayer() != null && contract.hiringPlayer().equals(settlement.getOwnerId())) {
                    int oldTrust = data.trust(definition.id(), contract.employerKey(), definition.startingLoyalty());
                    contract = contract.withFaction(currentFaction);
                    data.put(contract);
                    data.setTrust(definition.id(), contract.employerKey(), Math.max(oldTrust,
                            data.trust(definition.id(), contract.employerKey(), definition.startingLoyalty())));
                } else {
                    terminateContract(server, contract, true, definition.name() + " departed after political control of its contracted settlement changed.");
                    continue;
                }
            }
            long next = contract.nextPaymentDay(); int missed = contract.missedPayments();
            if (day >= next) {
                int cycles = (int)Math.min(64L, 1L + Math.max(0L, day - next) / definition.paymentIntervalDays());
                for (int i = 0; i < cycles; i++) {
                    boolean paid = StateTreasury.spend(server.overworld(), settlement, definition.upkeep());
                    if (paid) {
                        missed = 0; data.changeTrust(definition.id(), contract.employerKey(), 2, definition.startingLoyalty());
                        if (!contract.factionId().isBlank()) CampaignManager.changeReliability(server.overworld(), campaign, contract.factionId(), 1);
                    } else {
                        missed++; data.changeTrust(definition.id(), contract.employerKey(), -10, definition.startingLoyalty());
                        if (!contract.factionId().isBlank()) CampaignManager.changeReliability(server.overworld(), campaign, contract.factionId(), -2);
                    }
                    next += definition.paymentIntervalDays();
                    if (missed >= 3) break;
                }
                contract = contract.update(next, missed); data.put(contract);
                if (missed > 0) settlement.addChronicle(definition.name() + " wages were missed (" + missed + "/3)." );
            }
            if (contract.missedPayments() >= 3) {
                terminateContract(server, contract, true, definition.name() + " deserted after repeated missed wages.");
                settlement.addChronicle(definition.name() + " deserted after repeated missed wages.");
                campaign.addNews(CampaignData.NewsCategory.MILITARY, CampaignData.NewsImportance.NORMAL, "Mercenary company deserted",
                        definition.name() + " abandoned " + settlement.getName() + " after going unpaid.", day, contract.factionId(), settlement.getId(), CampaignData.NewsVisibility.FACTION);
            }
        }
    }

    /** Releases independent contracts personally hired by a player. Faction contracts belong to the faction and survive a personal reset. */
    public static int clearIndependentContractsHiredBy(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return 0;
        MercenaryCompanyData data = MercenaryCompanyData.get(server.overworld());
        List<MercenaryCompanyData.CompanyContract> matching = data.contracts().stream()
                .filter(value -> value.factionId().isBlank() && playerId.equals(value.hiringPlayer())).toList();
        for (MercenaryCompanyData.CompanyContract contract : matching) {
            terminateContract(server, contract, true, "The independent employer's Settlements data was reset; the company departed.");
        }
        return matching.size();
    }

    /** Releases all named company contracts based from one settlement. Used by destructive admin recovery. */
    public static int clearSettlementContracts(MinecraftServer server, String settlementId) {
        if (server == null || settlementId == null || settlementId.isBlank()) return 0;
        MercenaryCompanyData data = MercenaryCompanyData.get(server.overworld());
        List<MercenaryCompanyData.CompanyContract> matching = data.contracts().stream()
                .filter(value -> settlementId.equals(value.settlementId())).toList();
        for (MercenaryCompanyData.CompanyContract contract : matching) {
            terminateContract(server, contract, true, "The mercenary contract was cleared administratively.");
        }
        return matching.size();
    }

    public static void onUnitDeath(ServerLevel level, UUID unitId) {
        if (level == null || unitId == null) return;
        MercenaryCompanyData data = MercenaryCompanyData.get(level);
        for (MercenaryCompanyData.CompanyContract contract : List.copyOf(data.contracts())) {
            if (!contract.unitIds().contains(unitId)) continue;
            MercenaryCompanyData.CompanyContract updated = contract.withoutUnit(unitId);
            if (updated.unitIds().isEmpty()) data.remove(contract.companyId()); else data.put(updated);
            return;
        }
    }

    public static boolean handleRetiredEntityJoin(ServerLevel level, Entity entity) {
        if (!(entity instanceof AbstractRecruitEntity recruit)) return false;
        if (recruit.getPersistentData().getBoolean(TAG_PENDING_SPAWN)) return false;
        MercenaryCompanyData data = MercenaryCompanyData.get(level);
        String company = recruit.getPersistentData().getString(SettlementManager.TAG_MERCENARY_COMPANY);
        if (data.consumeRetiredUnit(recruit.getUUID()) || (!company.isBlank() && !data.isActiveUnit(recruit.getUUID()))) {
            recruit.discard(); return true;
        }
        return false;
    }

    /** Completes Recruits' normal hire lifecycle for a newly spawned named-company soldier. */
    static boolean activateSpawnedRecruit(AbstractRecruitEntity recruit, ServerPlayer owner) {
        if (recruit == null || owner == null) return false;
        try {
            resetRuntimeState(recruit);
            recruit.setIsOwned(false);
            recruit.setOwnerUUID(Optional.empty());
            String companyId = recruit.getPersistentData().getString(SettlementManager.TAG_MERCENARY_COMPANY);
            MercenaryRosterRegistry.Definition definition = MercenaryRosterRegistry.get(companyId);
            String groupName = definition == null ? companyId : definition.name();
            RecruitsGroup group = ensureCompanyGroup((ServerLevel) recruit.level(), owner.getUUID(),
                    owner.getGameProfile().getName(), groupName, owner);
            if (group == null || !recruit.hire(owner, group, false)) return false;
            resetOrdersAfterHire(recruit);
            recruit.getPersistentData().putInt(TAG_LIFECYCLE_VERSION, LIFECYCLE_VERSION);
            return true;
        } finally {
            recruit.getPersistentData().remove(TAG_PENDING_SPAWN);
        }
    }

    static void markPendingSpawn(AbstractRecruitEntity recruit) {
        if (recruit != null) recruit.getPersistentData().putBoolean(TAG_PENDING_SPAWN, true);
    }

    private static void rollbackSpawnedRecruits(MinecraftServer server, Collection<AbstractRecruitEntity> recruits) {
        for (AbstractRecruitEntity recruit : recruits) {
            if (recruit == null) continue;
            if (recruit.getPersistentData().getInt(TAG_LIFECYCLE_VERSION) >= LIFECYCLE_VERSION) {
                UUID ownerId = recruit.getOwnerUUID();
                ServerPlayer owner = ownerId == null || server == null ? null : server.getPlayerList().getPlayer(ownerId);
                recruit.disband(owner, false, false);
            }
            recruit.discard();
        }
    }

    private static RecruitsGroup ensureCompanyGroup(ServerLevel level, UUID ownerId, String ownerName,
                                                     String groupName, ServerPlayer onlineOwner) {
        if (level == null || ownerId == null || RecruitEvents.recruitsGroupsManager == null) return null;
        String name = groupName == null || groupName.isBlank() ? "Mercenary Company" : groupName.trim();
        RecruitsGroup existing = RecruitEvents.recruitsGroupsManager.getAllGroups().stream()
                .filter(group -> ownerId.equals(group.getPlayerUUID()) && name.equalsIgnoreCase(group.getName()))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        RecruitsGroup created = new RecruitsGroup(name, ownerId,
                ownerName == null || ownerName.isBlank() ? ownerId.toString() : ownerName, 0);
        RecruitEvents.recruitsGroupsManager.addOrUpdateGroup(level, onlineOwner, created);
        return created;
    }

    private static void resetRuntimeState(AbstractRecruitEntity recruit) {
        recruit.setNoAi(false);
        recruit.setNoGravity(false);
        recruit.setInvulnerable(false);
        recruit.setSilent(false);
        recruit.setFleeing(false);
        recruit.setDeltaMovement(Vec3.ZERO);
        recruit.clearTarget();
        recruit.getNavigation().stop();
        recruit.clearHoldPos();
        recruit.clearMovePos();
        recruit.setShouldHoldPos(false);
        recruit.setShouldMovePos(false);
        recruit.setShouldProtect(false);
        recruit.setProtectUUID(Optional.empty());
        recruit.setShouldMount(false);
        recruit.setMountUUID(Optional.empty());
        recruit.setMountTimer(0);
        recruit.setShouldBlock(false);
        recruit.setListen(true);
        recruit.setGroupUUID(null);
        recruit.setShouldRest(false);
        recruit.clearUpkeepEntity();
        recruit.setUpkeepTimer(0);
        recruit.resetPaymentTimer();
        recruit.despawnTimer = -1;
        recruit.formationPos = -1;
        recruit.isInFormation = false;
        recruit.holdFormation = false;
        recruit.setPersistenceRequired();
    }

    private static void resetOrdersAfterHire(AbstractRecruitEntity recruit) {
        recruit.setFollowState(2);
        recruit.setAggroState(0);
        recruit.setShouldFollow(false);
        recruit.setIsFollowing(false);
    }

    private static void terminateContract(MinecraftServer server, MercenaryCompanyData.CompanyContract contract, boolean depart, String reason) {
        if (server == null || contract == null) return;
        MercenaryCompanyData data = MercenaryCompanyData.get(server.overworld());
        SettlementData.SettlementRecord settlement = SettlementData.get(server.overworld()).getSettlement(contract.settlementId());
        Set<UUID> unavailable = new HashSet<>();
        for (UUID unit : contract.unitIds()) {
            if (settlement != null) settlement.removeMercenaryCompanyUnit(unit);
            AbstractRecruitEntity recruit = findLoaded(server, unit);
            if (recruit == null) { unavailable.add(unit); continue; }
            CompoundTag tag = recruit.getPersistentData();
            boolean registered = tag.getInt(TAG_LIFECYCLE_VERSION) >= LIFECYCLE_VERSION;
            if (registered) {
                ServerPlayer owner = recruit.getOwnerUUID() == null ? null : server.getPlayerList().getPlayer(recruit.getOwnerUUID());
                recruit.disband(owner, false, false);
            } else {
                recruit.setIsOwned(false);
                recruit.setOwnerUUID(Optional.empty());
                PlayerTeam team = recruit.getTeam() instanceof PlayerTeam playerTeam ? playerTeam : null;
                if (team != null && recruit.level() instanceof ServerLevel level) {
                    try { FactionEvents.removeRecruitFromTeam(recruit, team, level); }
                    catch (RuntimeException exception) { level.getScoreboard().removePlayerFromTeam(recruit.getScoreboardName(), team); }
                }
            }
            recruit.clearUpkeepPos();
            recruit.setShouldRest(false);
            tag.remove(SettlementManager.TAG_MANAGED); tag.remove(SettlementManager.TAG_SETTLEMENT);
            tag.remove(SettlementManager.TAG_MERCENARY); tag.remove(SettlementManager.TAG_MERCENARY_COMPANY);
            tag.remove(SettlementManager.TAG_MERCENARY_WAGE); tag.remove(SettlementManager.TAG_MERCENARY_INTERVAL_DAYS);
            tag.remove(SettlementManager.TAG_MERCENARY_NEXT_PAYMENT_DAY); tag.remove(SettlementManager.TAG_MERCENARY_MISSED_PAYMENTS);
            tag.remove(TAG_LIFECYCLE_VERSION);
            // A named company leaving is a roster transaction, not a new unowned ambient recruit.
            // Remove the entity after Recruits has unregistered ownership to avoid zombie/floating units.
            recruit.discard();
        }
        if (!unavailable.isEmpty()) data.retireUnits(unavailable);
        data.remove(contract.companyId());
        if (settlement != null && reason != null && !reason.isBlank()) settlement.addChronicle(reason);
    }

    private static AbstractRecruitEntity findLoaded(MinecraftServer server, UUID unit) {
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(unit); if (entity instanceof AbstractRecruitEntity recruit) return recruit; }
        return null;
    }

    private static String employerKey(SettlementData.SettlementRecord settlement) {
        return settlement.getFactionTeam().isBlank() ? "settlement:" + settlement.getId() : "faction:" + settlement.getFactionTeam();
    }

    private static String employerName(MinecraftServer server, MercenaryCompanyData.CompanyContract contract) {
        if (contract == null) return "None";
        if (!contract.factionId().isBlank()) return CampaignManager.factionName(contract.factionId());
        SettlementData.SettlementRecord settlement = server == null ? null : SettlementData.get(server.overworld()).getSettlement(contract.settlementId());
        return settlement == null ? contract.settlementId() : settlement.getName();
    }

    private static boolean refusesPoaching(ServerLevel level, MercenaryRosterRegistry.Definition definition,
                                           MercenaryCompanyData.CompanyContract current, int loyalty) {
        if (current == null || current.missedPayments() > 0) return false;
        int reliability = current.factionId().isBlank() ? 0 : CampaignData.get(level).getFactionReliability(current.factionId());
        int effective = Math.max(0, Math.min(100, loyalty + reliability / 10));
        return effective >= 65;
    }

    private static int effectiveHireCost(ServerLevel level, MercenaryRosterRegistry.Definition definition,
                                         MercenaryCompanyData.CompanyContract current, SettlementData.SettlementRecord bidder,
                                         int currentLoyalty) {
        int bidderReliability = bidder.getFactionTeam().isBlank() ? 0 : CampaignData.get(level).getFactionReliability(bidder.getFactionTeam());
        double multiplier = bidderReliability >= 50 ? 0.90D : bidderReliability <= -50 ? 1.25D : 1.0D - bidderReliability * 0.002D;
        if (current != null && !employerKey(bidder).equals(current.employerKey())) {
            int oldReliability = current.factionId().isBlank() ? 0 : CampaignData.get(level).getFactionReliability(current.factionId());
            int effectiveLoyalty = Math.max(0, Math.min(100, currentLoyalty + oldReliability / 10 - current.missedPayments() * 25));
            multiplier *= 1.50D + effectiveLoyalty / 100.0D;
        }
        return Math.max(0, (int)Math.ceil(definition.hireCost() * multiplier));
    }
}
