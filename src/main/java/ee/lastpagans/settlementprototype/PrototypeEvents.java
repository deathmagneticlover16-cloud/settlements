package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.ClaimEvent;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsClaim;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PrototypeEvents {
    private static final Map<UUID, Integer> PENDING_CHARACTER_SCREENS = new HashMap<>();
    private static int checkerTicks;

    private PrototypeEvents() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        PrototypeData data = PrototypeData.get(level);

        String taggedSettlement = entity.getPersistentData().getString(PrototypeManager.TAG_SETTLEMENT);
        PrototypeData.SettlementRecord settlement = taggedSettlement.isBlank()
                ? null
                : data.getSettlement(taggedSettlement);

        if (entity instanceof Villager) {
            if (settlement == null) settlement = data.findSettlementTrackingVillager(entity.getUUID());
            if (settlement != null) {
                settlement.removeVillager(entity.getUUID());
                settlement.addChronicle("A registered villager died.");
            }
            return;
        }

        if (settlement == null) settlement = data.findSettlementTrackingUnit(entity.getUUID());
        if (settlement == null) return;
        Campaign23Data extra = Campaign23Data.get(level);
        extra.removeMercenaryContract(entity.getUUID());
        extra.consumeRetiredLevy(entity.getUUID());
        Campaign23Manager.onMilitaryDeath(level, entity, settlement);
        Campaign23Manager.onLevyDeath(level, entity);
        if (settlement.removeProfessional(entity.getUUID())) {
            settlement.addChronicle("A professional soldier died; one MP became vacant.");
            return;
        }
        settlement.removeLevyman(entity.getUUID());
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        Campaign23Data extra = Campaign23Data.get(level);
        if (extra.isRetiredLevy(entity.getUUID())
                && "levy".equals(entity.getPersistentData().getString(PrototypeManager.TAG_KIND))) {
            extra.consumeRetiredLevy(entity.getUUID());
            entity.discard();
            return;
        }
        String settlementId = entity.getPersistentData().getString(PrototypeManager.TAG_SETTLEMENT);
        if (settlementId.isBlank()) return;
        PrototypeData.SettlementRecord settlement = PrototypeData.get(level).getSettlement(settlementId);
        if (settlement == null) return;

        if (entity instanceof Villager villager && entity.getPersistentData().getBoolean(PrototypeManager.TAG_RESIDENT)) {
            PrototypeManager.reconcileResidentVillager(level, villager);
        } else if (entity.getPersistentData().getBoolean(PrototypeManager.TAG_MANAGED)
                && "professional".equals(entity.getPersistentData().getString(PrototypeManager.TAG_KIND))) {
            settlement.addProfessional(entity.getUUID());
            if (entity instanceof com.talhanation.recruits.entities.AbstractRecruitEntity recruit
                    && entity.getPersistentData().getBoolean(PrototypeManager.TAG_MERCENARY)
                    && Campaign23Data.get(level).mercenaryContract(entity.getUUID()) == null) {
                PrototypeManager.registerMercenaryContract(level, recruit, settlement, null);
            }
        }
    }

    public static void onClaimRemoved(ClaimEvent.Removed event) {
        RecruitsClaim claim = event.getClaim();
        if (claim == null) return;
        PrototypeData data = PrototypeData.get(event.getLevel());
        PrototypeData.SettlementRecord settlement = data.findSettlementByClaim(
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
        PrototypeData data = PrototypeData.get(event.getLevel());
        PrototypeData.SettlementRecord settlement = data.findSettlementByClaim(
                event.getLevel().dimension().location().toString(),
                claim.getUUID()
        );
        if (settlement != null) {
            settlement.updateClaimName(event.getLevel().dimension().location().toString(), claim.getUUID(), claim.getName());
            // A multi-claim city may temporarily contain claims with different Recruits owners during a war.
            // Do not let one claim update silently transfer the entire Settlements polity. Whole-settlement
            // ownership changes are handled explicitly by Settlements diplomacy/admin actions.
            if (settlement.getClaimCount() == 1 && claim.getOwnerFactionStringID() != null
                    && !claim.getOwnerFactionStringID().isBlank()) {
                settlement.setFactionTeam(claim.getOwnerFactionStringID());
            }
        }
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrototypeManager.syncProfiles(player);
        CampaignManager.sendMapMarkers(player);
        player.refreshDisplayName();
        player.refreshTabListName();
        PrototypeData.CharacterRecord character = PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID());
        if (character == null || !character.isCreated()) {
            PENDING_CHARACTER_SCREENS.put(player.getUUID(), 40);
        }
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_CHARACTER_SCREENS.remove(event.getEntity().getUUID());
    }

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        PrototypeData.CharacterRecord character = PrototypeData.get(level).getCharacter(event.getEntity().getUUID());
        if (character == null || !character.isCreated()) return;
        ChatFormatting color = event.getEntity().getTeam() == null
                ? ChatFormatting.WHITE
                : event.getEntity().getTeam().getColor();
        event.setDisplayname(Component.literal(rankedName(event.getEntity(), level, character)).withStyle(color));
    }

    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        PrototypeData.CharacterRecord character = PrototypeData.get(level).getCharacter(event.getEntity().getUUID());
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
                PrototypeManager.promptCharacterCreation(player);
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        checkerTicks++;
        if (checkerTicks < 600) return;
        checkerTicks = 0;

        PrototypeData data = PrototypeData.get(server.overworld());
        CampaignData campaign = CampaignData.get(server.overworld());
        long day = server.overworld().getDayTime() / 24000L;
        data.getSettlements().forEach(settlement -> settlement.processDaily(day, campaign));
        data.runAllCheckers();
        PrototypeManager.processMilitarySystems(server, day);
        Campaign23Manager.processCampaignSystems(server, day);
        CampaignManager.enforceAllDiplomacy(server.overworld());
        CampaignManager.broadcastMapMarkers(server);
    }

    private static String rankedName(Player player, ServerLevel level, PrototypeData.CharacterRecord character) {
        if (player.getTeam() == null) return character.getDisplayName();
        String factionId = player.getTeam().getName();
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null
                : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        boolean leader = faction != null && character.getPlayerId().equals(faction.getTeamLeaderUUID());
        String rank = CampaignData.get(level).factionRankTitle(factionId, character.getPlayerId(), leader);
        return rank.isBlank() ? character.getDisplayName() : "[" + rank + "] " + character.getDisplayName();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        checkerTicks = 0;
        PENDING_CHARACTER_SCREENS.clear();
    }
}
