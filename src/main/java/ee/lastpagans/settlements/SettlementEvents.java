package ee.lastpagans.settlements;

import com.talhanation.recruits.ClaimEvent;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsClaim;
import com.mojang.logging.LogUtils;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireAuthority;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SettlementEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Integer> PENDING_CHARACTER_SCREENS = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_BODY_REASSERTIONS = new HashMap<>();
    private static int checkerTicks;
    private static int residentBoundaryTicks;
    private static int firstSettlerTicks;
    private static int researchTicks;
    private static int captureZoneTicks;
    private static int wildfireTicks;

    private SettlementEvents() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        SettlementData data = SettlementData.get(level);

        String taggedSettlement = entity.getPersistentData().getString(SettlementManager.TAG_SETTLEMENT);
        SettlementData.SettlementRecord settlement = taggedSettlement.isBlank()
                ? null
                : data.getSettlement(taggedSettlement);

        if (entity instanceof Villager villager) {
            removeResidentVillager(level, villager, "A registered villager died.");
            return;
        }

        if (settlement == null) settlement = data.findSettlementTrackingUnit(entity.getUUID());
        if (settlement == null) return;
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        extra.removeMercenaryContract(entity.getUUID());
        MercenaryRosterManager.onUnitDeath(level, entity.getUUID());
        extra.consumeRetiredLevy(entity.getUUID());
        CampaignSystemsManager.onMilitaryDeath(level, entity, settlement);
        CampaignSystemsManager.onLevyDeath(level, entity);
        if (settlement.removeMercenaryCompanyUnit(entity.getUUID())) {
            settlement.addChronicle("A mercenary company soldier died.");
            return;
        }
        if (settlement.removeProfessional(entity.getUUID())) {
            settlement.addChronicle("A professional soldier died; one MP became vacant.");
            return;
        }
        UUID levyBatch = entity.getPersistentData().hasUUID(SettlementManager.TAG_BATCH)
                ? entity.getPersistentData().getUUID(SettlementManager.TAG_BATCH) : null;
        SettlementData.LevyDeathResult levyDeath = settlement.removeLevyman(entity.getUUID());
        if (levyDeath.batchEnded() && levyBatch != null) {
            CampaignSystemsManager.onLevyBatchDestroyed(level, settlement, levyBatch);
        }
    }


    /**
     * Backup cleanup for resident villagers that are removed without a normal LivingDeathEvent
     * (for example conversion/discard paths used by Minecraft or another mod). Chunk unloads and
     * dimension changes must never unregister a living resident.
     */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        Entity.RemovalReason reason = villager.getRemovalReason();
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) return;
        removeResidentVillager(level, villager,
                reason == Entity.RemovalReason.KILLED
                        ? "A registered villager died."
                        : "A registered villager was removed from the settlement population.");
    }

    private static void removeResidentVillager(ServerLevel level, Villager villager, String chronicle) {
        SettlementData data = SettlementData.get(level);
        String taggedSettlement = villager.getPersistentData().getString(SettlementManager.TAG_SETTLEMENT);
        SettlementData.SettlementRecord settlement = taggedSettlement.isBlank()
                ? null
                : data.getSettlement(taggedSettlement);
        if (settlement == null || !settlement.hasVillager(villager.getUUID())) {
            settlement = data.findSettlementTrackingVillager(villager.getUUID());
        }
        if (settlement == null) return;

        // removeVillager() is idempotent. LivingDeathEvent and EntityLeaveLevelEvent may both fire
        // for a normal kill; only the first successful removal changes population / writes news.
        if (settlement.removeVillager(villager.getUUID())) {
            settlement.addChronicle(chronicle);
            CampaignManager.broadcastMapMarkers(level.getServer());
        }
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (MercenaryRosterManager.handleRetiredEntityJoin(level, entity)) return;
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        if (extra.isRetiredLevy(entity.getUUID())
                && "levy".equals(entity.getPersistentData().getString(SettlementManager.TAG_KIND))) {
            extra.consumeRetiredLevy(entity.getUUID());
            entity.discard();
            return;
        }
        String settlementId = entity.getPersistentData().getString(SettlementManager.TAG_SETTLEMENT);
        if (settlementId.isBlank()) return;
        SettlementData.SettlementRecord settlement = SettlementData.get(level).getSettlement(settlementId);
        if (settlement == null) {
            SettlementManager.clearOrphanedSettlementTags(entity);
            return;
        }

        if (entity instanceof Villager villager && entity.getPersistentData().getBoolean(SettlementManager.TAG_RESIDENT)) {
            SettlementManager.reconcileResidentVillager(level, villager);
        } else if (entity.getPersistentData().getBoolean(SettlementManager.TAG_MANAGED)
                && "professional".equals(entity.getPersistentData().getString(SettlementManager.TAG_KIND))) {
            String companyId = entity.getPersistentData().getString(SettlementManager.TAG_MERCENARY_COMPANY);
            if (!companyId.isBlank()) settlement.addMercenaryCompanyUnit(entity.getUUID());
            else settlement.addProfessional(entity.getUUID());
            if (entity instanceof com.talhanation.recruits.entities.AbstractRecruitEntity recruit
                    && entity.getPersistentData().getBoolean(SettlementManager.TAG_MERCENARY)
                    && companyId.isBlank()
                    && CampaignSystemsData.get(level).mercenaryContract(entity.getUUID()) == null) {
                SettlementManager.registerMercenaryContract(level, recruit, settlement, null);
            }
        }
    }

    public static void onClaimRemoved(ClaimEvent.Removed event) {
        RecruitsClaim claim = event.getClaim();
        if (claim == null) return;
        SettlementData data = SettlementData.get(event.getLevel());
        SettlementData.SettlementRecord settlement = data.findSettlementByClaim(
                event.getLevel().dimension().location().toString(),
                claim.getUUID()
        );
        if (settlement != null) {
            settlement.unlinkClaim(event.getLevel().dimension().location().toString(), claim.getUUID());
            settlement.addChronicle("A linked Recruits claim was removed: " + claim.getName() + ".");
        }
    }

    public static void onClaimUpdated(ClaimEvent.Updated event) {
        RecruitsClaim claim = event.getClaim();
        if (claim == null) return;
        SettlementData data = SettlementData.get(event.getLevel());
        SettlementData.SettlementRecord settlement = data.findSettlementByClaim(
                event.getLevel().dimension().location().toString(),
                claim.getUUID()
        );
        if (settlement != null) {
            settlement.updateClaimName(event.getLevel().dimension().location().toString(), claim.getUUID(), claim.getName());
            // Recruits claim ownership is transport/territory state, not Settlements political identity.
            // Never let an external claim update silently move the settlement into a faction (or expose
            // the hidden independent backing faction as a political faction). Explicit Settlements
            // diplomacy/admin actions own polity transitions.
        }
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CampaignManager.factionId(player).isBlank() && SettlementData.get(player.serverLevel()).getOwnedSettlements(player.getUUID()).stream()
                .anyMatch(settlement -> settlement.getFactionTeam().isBlank())) {
            BackingFactionManager.ensureForIndependent(player);
        }
        SettlementManager.syncProfiles(player);
        CampaignManager.sendMapMarkers(player);
        player.refreshDisplayName();
        player.refreshTabListName();
        SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            PENDING_CHARACTER_SCREENS.put(player.getUUID(), 40);
        } else if (character.hasBodyData()) {
            SettlementsWildfireAuthority.reassert(player, true);
            PENDING_BODY_REASSERTIONS.put(player.getUUID(), 40);
        } else {
            LOGGER.info("[Settlements/Wildfire] Legacy character {} has no Body data; authority remains disabled", player.getUUID());
        }
        ResearchManager.syncCompletedResearch(player);
        CaptureZoneManager.sync(player);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_CHARACTER_SCREENS.remove(event.getEntity().getUUID());
        PENDING_BODY_REASSERTIONS.remove(event.getEntity().getUUID());
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SettlementsWildfireAuthority.reassert(player, true);
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SettlementsWildfireAuthority.reassert(player, true);
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer subject)
            SettlementsWildfireAuthority.syncToObserver(subject, observer);
    }

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        SettlementData.CharacterRecord character = SettlementData.get(level).getCharacter(event.getEntity().getUUID());
        if (character == null || !character.isCreated()) return;
        ChatFormatting color = event.getEntity().getTeam() == null
                ? ChatFormatting.WHITE
                : event.getEntity().getTeam().getColor();
        event.setDisplayname(Component.literal(rankedName(event.getEntity(), level, character)).withStyle(color));
    }

    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        SettlementData.CharacterRecord character = SettlementData.get(level).getCharacter(event.getEntity().getUUID());
        if (character == null || !character.isCreated()) return;
        ChatFormatting color = event.getEntity().getTeam() == null
                ? ChatFormatting.WHITE
                : event.getEntity().getTeam().getColor();
        String account = event.getEntity().getGameProfile().getName();
        String roleplayName = rankedName(event.getEntity(), level, character);
        event.setDisplayName(Component.literal(roleplayName + " (" + account + ")").withStyle(color));
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_CHARACTER_SCREENS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
            } else if (remaining <= 0) {
                SettlementManager.promptCharacterCreation(player);
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        Iterator<Map.Entry<UUID, Integer>> bodyIterator = PENDING_BODY_REASSERTIONS.entrySet().iterator();
        while (bodyIterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = bodyIterator.next();
            int remaining = entry.getValue() - 1;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) bodyIterator.remove();
            else if (remaining <= 0) {
                SettlementsWildfireAuthority.reassert(player, true);
                bodyIterator.remove();
            } else entry.setValue(remaining);
        }

        if (++wildfireTicks >= 20) {
            wildfireTicks = 0;
            server.getPlayerList().getPlayers().forEach(SettlementsWildfireAuthority::reassert);
        }

        firstSettlerTicks++;
        if (firstSettlerTicks >= 20) {
            firstSettlerTicks = 0;
            SettlementManager.processFirstSettlerArrivals(server);
        }

        researchTicks++;
        if (researchTicks >= 20) {
            researchTicks = 0;
            ResearchManager.processResearch(server);
        }

        captureZoneTicks++;
        if (captureZoneTicks >= 20) {
            captureZoneTicks = 0;
            CaptureZoneManager.process(server);
        }

        residentBoundaryTicks++;
        if (residentBoundaryTicks >= SettlementConfig.RESIDENT_BOUNDARY_CHECK_TICKS.get()) {
            residentBoundaryTicks = 0;
            enforceResidentClaimBoundaries(server);
        }

        checkerTicks++;
        if (checkerTicks < 600) return;
        checkerTicks = 0;

        SettlementData data = SettlementData.get(server.overworld());
        CampaignData campaign = CampaignData.get(server.overworld());
        SettlementManager.processUpkeepChests(server);
        long day = server.overworld().getDayTime() / 24000L;
        long gameTime = server.overworld().getGameTime();
        data.getSettlements().forEach(settlement -> {
            settlement.processDaily(server.overworld(), day, campaign);
            settlement.processSettlerContracts(gameTime, day, campaign);
        });
        data.runAllCheckers();
        SettlementManager.processMilitarySystems(server, day);
        CampaignSystemsManager.processCampaignSystems(server, day);
        CampaignManager.enforceAllDiplomacy(server.overworld());
        CampaignManager.broadcastMapMarkers(server);
    }

    /**
     * Registered civilian residents are territorial settlement population, not free-roaming global
     * villagers. Every five seconds, loaded residents are checked against every linked Recruits
     * claim belonging to their settlement. A resident outside all linked claims is returned to the
     * nearest linked claim chunk. Settlements without a usable linked claim are left untouched.
     */
    private static void enforceResidentClaimBoundaries(MinecraftServer server) {
        if (server == null || ClaimEvents.recruitsClaimManager == null) return;
        SettlementData data = SettlementData.get(server.overworld());
        for (SettlementData.SettlementRecord settlement : data.getSettlements()) {
            if (settlement.getClaimLinks().isEmpty() || settlement.getVillagerCount() == 0) continue;
            for (UUID villagerId : settlement.getVillagerIds()) {
                Villager villager = findLoadedResident(server, villagerId);
                if (villager == null || !villager.isAlive() || !(villager.level() instanceof ServerLevel level)) continue;

                String dimension = level.dimension().location().toString();
                ChunkPos current = new ChunkPos(villager.blockPosition());
                ChunkPos nearest = null;
                long nearestDistance = Long.MAX_VALUE;
                boolean inside = false;

                for (SettlementData.ClaimLink link : settlement.getClaimLinks()) {
                    if (!dimension.equals(link.dimension()) || link.id() == null) continue;
                    RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(link.id());
                    if (claim == null) continue;
                    if (claim.containsChunk(current)) { inside = true; break; }
                    for (ChunkPos chunk : claim.getClaimedChunks()) {
                        long dx = (long) chunk.x - current.x;
                        long dz = (long) chunk.z - current.z;
                        long distance = dx * dx + dz * dz;
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = chunk;
                        }
                    }
                }

                if (inside || nearest == null) continue;
                int x = nearest.x * 16 + 8;
                int z = nearest.z * 16 + 8;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                villager.getNavigation().stop();
                villager.teleportTo(x + 0.5D, y, z + 0.5D);
            }
        }
    }

    private static Villager findLoadedResident(MinecraftServer server, UUID villagerId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(villagerId);
            if (entity instanceof Villager villager) return villager;
        }
        return null;
    }

    private static String rankedName(Player player, ServerLevel level, SettlementData.CharacterRecord character) {
        if (!(player instanceof ServerPlayer serverPlayer)) return character.getDisplayName();
        String factionId = CampaignManager.factionId(serverPlayer);
        if (factionId.isBlank()) return character.getDisplayName();
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null
                : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        boolean leader = faction != null && character.getPlayerId().equals(faction.getTeamLeaderUUID());
        String rank = CampaignData.get(level).factionRankTitle(factionId, character.getPlayerId(), leader);
        return rank.isBlank() ? character.getDisplayName() : "[" + rank + "] " + character.getDisplayName();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        checkerTicks = 0;
        residentBoundaryTicks = 0;
        firstSettlerTicks = 0;
        researchTicks = 0;
        wildfireTicks = 0;
        PENDING_CHARACTER_SCREENS.clear();
        PENDING_BODY_REASSERTIONS.clear();
    }
}
