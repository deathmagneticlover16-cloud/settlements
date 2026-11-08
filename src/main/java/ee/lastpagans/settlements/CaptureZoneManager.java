package ee.lastpagans.settlements;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsClaim;
import ee.lastpagans.settlements.api.CaptureZoneStateEvent;
import ee.lastpagans.settlements.api.CaptureZoneView;
import ee.lastpagans.settlements.api.SettlementOccupationEvent;
import ee.lastpagans.settlements.network.CaptureZoneSnapshotPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative settlement tactical objectives and occupation bridge. */
public final class CaptureZoneManager {
    private CaptureZoneManager() {}

    public record ZoneRequirement(int main, int secondary) {
        public int total() { return main + secondary; }
        public String display() { return main + " Main + " + secondary + " Secondary"; }
    }

    public static ZoneRequirement requirement(SettlementData.SettlementRecord settlement) {
        if (settlement == null) return new ZoneRequirement(1, 0);
        return requirement(settlement.getRank());
    }

    public static ZoneRequirement requirement(SettlementData.SettlementRank rank) {
        return new ZoneRequirement(1, 0);
    }

    public static boolean requirementsMet(ServerLevel level, SettlementData.SettlementRecord settlement) {
        return requirementsMet(level, settlement, requirement(settlement));
    }

    public static boolean requirementsMet(ServerLevel level, SettlementData.SettlementRecord settlement, SettlementData.SettlementRank prospectiveRank) {
        return requirementsMet(level, settlement, requirement(prospectiveRank));
    }

    private static boolean requirementsMet(ServerLevel level, SettlementData.SettlementRecord settlement, ZoneRequirement requirement) {
        if (level == null || settlement == null) return false;
        List<CaptureZoneData.CaptureZoneRecord> zones = CaptureZoneData.get(level).zonesFor(settlement.getId());
        long mains = zones.stream().filter(z -> z.type() == CaptureZoneData.ZoneType.MAIN).count();
        return mains >= requirement.main();
    }

    public static String summary(ServerLevel level, SettlementData.SettlementRecord settlement) {
        List<CaptureZoneData.CaptureZoneRecord> zones = CaptureZoneData.get(level).zonesFor(settlement.getId());
        long main = zones.stream().filter(z -> z.type()==CaptureZoneData.ZoneType.MAIN).count();
        return "Town Square objective: " + (main == 1 ? "anchored" : "missing");
    }

    public static int mainRadius(SettlementData.SettlementRank rank) {
        int tier = rank == null ? 0 : rank.ordinal();
        return Math.max(3, SettlementConfig.TOWN_SQUARE_CAPTURE_RADIUS.get() + tier * 4);
    }

    public static boolean syncTownSquareMainZone(ServerLevel level, SettlementData.SettlementRecord settlement) {
        if (level == null || settlement == null || !settlement.hasTownSquare()) return false;
        ServerLevel targetLevel = resolveLevel(level.getServer(), settlement.getTownSquareDimension());
        if (targetLevel == null) return false;
        CaptureZoneData data = CaptureZoneData.get(targetLevel);
        CaptureZoneData.CaptureZoneRecord oldMain = data.zonesFor(settlement.getId()).stream()
                .filter(z -> z.type() == CaptureZoneData.ZoneType.MAIN).findFirst().orElse(null);
        int half = mainRadius(settlement.getRank());
        int height = Math.max(3, SettlementConfig.TOWN_SQUARE_CAPTURE_HEIGHT.get());
        BlockPos center = settlement.getTownSquare();
        CaptureZoneData.CaptureZoneRecord candidate = new CaptureZoneData.CaptureZoneRecord(
                oldMain == null ? UUID.randomUUID() : oldMain.id(), settlement.getId(), CaptureZoneData.ZoneType.MAIN,
                settlement.getTownSquareDimension(), center.offset(-half, 0, -half), center.offset(half, height - 1, half));
        for (CaptureZoneData.CaptureZoneRecord zone : data.zonesFor(settlement.getId()))
            if (zone.type() == CaptureZoneData.ZoneType.MAIN) data.remove(zone.id());
        data.put(candidate);
        broadcast(targetLevel.getServer());
        return true;
    }

    public static String relocateTownSquare(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (player == null || settlement == null) return "Invalid settlement.";
        if (editingBlocked(player.serverLevel(), settlement)) return "The Town Square cannot move during war or occupation.";
        String dimension = player.serverLevel().dimension().location().toString();
        BlockPos center = player.blockPosition();
        int half = mainRadius(settlement.getRank());
        int height = Math.max(3, SettlementConfig.TOWN_SQUARE_CAPTURE_HEIGHT.get());
        CaptureZoneData data = CaptureZoneData.get(player.serverLevel());
        CaptureZoneData.CaptureZoneRecord oldMain = data.zonesFor(settlement.getId()).stream()
                .filter(z -> z.type() == CaptureZoneData.ZoneType.MAIN).findFirst().orElse(null);
        CaptureZoneData.CaptureZoneRecord candidate = new CaptureZoneData.CaptureZoneRecord(
                oldMain == null ? UUID.randomUUID() : oldMain.id(), settlement.getId(), CaptureZoneData.ZoneType.MAIN,
                dimension, center.offset(-half, 0, -half), center.offset(half, height - 1, half));
        String invalid = validate(player.serverLevel(), settlement, candidate, oldMain == null ? null : oldMain.id());
        if (invalid != null) return invalid;
        settlement.setTownSquare(dimension, center);
        for (CaptureZoneData.CaptureZoneRecord zone : data.zonesFor(settlement.getId()))
            if (zone.type() == CaptureZoneData.ZoneType.MAIN) data.remove(zone.id());
        data.put(candidate);
        settlement.addChronicle("Town Square relocated; its Main objective moved atomically with it.");
        broadcast(player.getServer());
        return "Town Square and Main objective relocated to " + center.toShortString() + ".";
    }

    /** Kept as an internal alias for older call sites; it no longer creates a separately editable zone. */
    public static void ensureVillageMainZone(ServerLevel level, SettlementData.SettlementRecord settlement) {
        syncTownSquareMainZone(level, settlement);
    }

    public static boolean usesClaim(ServerLevel level, SettlementData.SettlementRecord settlement, UUID claimId) {
        if (level == null || settlement == null || claimId == null || ClaimEvents.recruitsClaimManager == null) return false;
        String dimension = level.dimension().location().toString();
        for (CaptureZoneData.CaptureZoneRecord zone : CaptureZoneData.get(level).zonesFor(settlement.getId())) {
            if (!dimension.equals(zone.dimension())) continue;
            int minChunkX = Math.floorDiv(zone.pos1().getX(),16), maxChunkX = Math.floorDiv(zone.pos2().getX(),16);
            int minChunkZ = Math.floorDiv(zone.pos1().getZ(),16), maxChunkZ = Math.floorDiv(zone.pos2().getZ(),16);
            for (int x=minChunkX;x<=maxChunkX;x++) for(int z=minChunkZ;z<=maxChunkZ;z++) {
                RecruitsClaim current = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(x,z));
                if (current != null && claimId.equals(current.getUUID())) return true;
            }
        }
        return false;
    }

    private static String validate(ServerLevel level, SettlementData.SettlementRecord settlement, CaptureZoneData.CaptureZoneRecord candidate, UUID replacing) {
        int min = SettlementConfig.CAPTURE_ZONE_MIN_SIZE.get();
        if (candidate.width() < min || candidate.depth() < min) return "Capture Zones must be at least " + min + "×" + min + " blocks.";
        if (!level.dimension().location().toString().equals(candidate.dimension())) return "The Capture Zone must be in this dimension.";
        if (ClaimEvents.recruitsClaimManager == null) return "Recruits claim data is unavailable; Capture Zone creation was cancelled.";
        int minChunkX = Math.floorDiv(candidate.pos1().getX(), 16), maxChunkX = Math.floorDiv(candidate.pos2().getX(),16);
        int minChunkZ = Math.floorDiv(candidate.pos1().getZ(), 16), maxChunkZ = Math.floorDiv(candidate.pos2().getZ(),16);
        for (int x=minChunkX;x<=maxChunkX;x++) for (int z=minChunkZ;z<=maxChunkZ;z++) {
            RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(new ChunkPos(x,z));
            if (claim == null || !settlement.containsClaim(candidate.dimension(), claim.getUUID())) return "Every part of a Capture Zone must remain inside this settlement's linked Recruits claims.";
        }
        for (CaptureZoneData.CaptureZoneRecord existing : CaptureZoneData.get(level).allZones()) {
            if (replacing != null && replacing.equals(existing.id())) continue;
            if (candidate.intersects(existing)) return "Capture Zones cannot overlap or be placed inside one another.";
            if (candidate.settlementId().equals(existing.settlementId()) && candidate.horizontalDistance(existing) < SettlementConfig.CAPTURE_ZONE_MIN_SPACING.get()) return "Capture Zones must be spread out. Keep their centres at least " + SettlementConfig.CAPTURE_ZONE_MIN_SPACING.get() + " blocks apart.";
        }
        return null;
    }

    private static boolean editingBlocked(ServerLevel level, SettlementData.SettlementRecord settlement) {
        if (!CaptureZoneData.get(level).occupyingTeam(settlement.getId()).isBlank()) return true;
        String team = effectiveTeam(settlement);
        return CampaignData.get(level).getWars().stream().anyMatch(w -> w.status() == CampaignData.WarStatus.ACTIVE
                && (settlement.getId().equals(w.attackerSettlement()) || settlement.getId().equals(w.defenderSettlement())
                || (!team.isBlank() && (team.equals(w.attackerFaction()) || team.equals(w.defenderFaction())
                || w.supportingAttackers().contains(team) || w.supportingDefenders().contains(team)))));
    }

    private static boolean matchesTownSquare(SettlementData.SettlementRecord settlement,
                                             CaptureZoneData.CaptureZoneRecord zone) {
        int radius = mainRadius(settlement.getRank());
        int height = Math.max(3, SettlementConfig.TOWN_SQUARE_CAPTURE_HEIGHT.get());
        BlockPos center = settlement.getTownSquare();
        return settlement.getTownSquareDimension().equals(zone.dimension())
                && center.offset(-radius, 0, -radius).equals(zone.pos1())
                && center.offset(radius, height - 1, radius).equals(zone.pos2());
    }

    public static void process(MinecraftServer server) {
        if (server == null) return;
        ServerLevel root = server.overworld();
        CaptureZoneData data = CaptureZoneData.get(root);
        SettlementData settlements = SettlementData.get(root);
        CampaignData campaign = CampaignData.get(root);
        boolean anyChanged = false;
        for (CaptureZoneData.CaptureZoneRecord zone : data.allZones()) {
            if (zone.type() == CaptureZoneData.ZoneType.SECONDARY || settlements.getSettlement(zone.settlementId()) == null) {
                data.remove(zone.id());
                anyChanged = true;
            }
        }
        for (SettlementData.SettlementRecord settlement : settlements.getSettlements()) {
            List<CaptureZoneData.CaptureZoneRecord> mains = data.zonesFor(settlement.getId()).stream()
                    .filter(zone -> zone.type() == CaptureZoneData.ZoneType.MAIN).toList();
            if (settlement.hasTownSquare() && (mains.size() != 1 || !matchesTownSquare(settlement, mains.get(0)))) {
                syncTownSquareMainZone(root, settlement);
            }
        }
        List<CaptureZoneData.CaptureZoneRecord> zones = data.allZones();
        Map<String, CampaignData.WarRecord> activeAttackBySettlement = new HashMap<>();
        for (CampaignData.WarRecord war : campaign.getWars()) {
            if (war.status() == CampaignData.WarStatus.ACTIVE && !war.defenderSettlement().isBlank()) {
                activeAttackBySettlement.putIfAbsent(war.defenderSettlement(), war);
            }
        }
        Map<UUID, TeamSets> teamsByWar = new HashMap<>();
        for (CaptureZoneData.CaptureZoneRecord zone : zones) {
            SettlementData.SettlementRecord settlement = settlements.getSettlement(zone.settlementId());
            if (settlement == null) { data.remove(zone.id()); anyChanged=true; continue; }
            ServerLevel level = resolveLevel(server, zone.dimension());
            if (level == null) continue;
            CampaignData.WarRecord war = activeAttackBySettlement.get(settlement.getId());
            if (war == null) {
                liberate(level, settlement);
                if (zone.progress() > 0) anyChanged |= zone.setProgress(Math.max(0D, zone.progress()-0.02D));
                if (zone.progress() <= 0D) {
                    boolean wasCaptured = zone.captured();
                    anyChanged |= zone.setCaptured(false);
                    anyChanged |= zone.setState(CaptureZoneData.ZoneState.SECURE);
                    anyChanged |= zone.setAttackerTeam("");
                    if (wasCaptured) onRecaptured(level, settlement, zone);
                }
                continue;
            }
            TeamSets teams = teamsByWar.computeIfAbsent(war.id(), ignored -> teamsForWar(settlements, war));
            if (!zone.attackerTeam().isBlank() && !zone.attackerTeam().equals(teams.primaryAttacker)) {
                if (zone.type() == CaptureZoneData.ZoneType.MAIN) liberate(level, settlement);
                anyChanged |= zone.setProgress(0D);
                anyChanged |= zone.setCaptured(false);
                anyChanged |= zone.setAttackerTeam("");
                anyChanged |= zone.setState(CaptureZoneData.ZoneState.SECURE);
            }
            CapturePresence presence = presence(level, zone, teams.attackers, teams.defenders);
            CaptureZoneData.ZoneState before = zone.state();
            if (presence.attackers>0 && presence.defenders>0) {
                anyChanged |= zone.setState(CaptureZoneData.ZoneState.CONTESTED);
                if (before != CaptureZoneData.ZoneState.CONTESTED) MinecraftForge.EVENT_BUS.post(new CaptureZoneStateEvent(level, CaptureZoneView.from(zone), CaptureZoneStateEvent.Transition.CONTESTED));
                continue;
            }
            if (presence.attackers>0) {
                String attacker = teams.primaryAttacker;
                anyChanged |= zone.setAttackerTeam(attacker);
                int baseSeconds = captureSeconds(settlement);
                int capped = Math.min(SettlementConfig.CAPTURE_MAX_SPEED_TROOPS.get(), presence.attackers);
                double speed = 1.0D + Math.max(0, capped-1) * 0.08D;
                anyChanged |= zone.setProgress(zone.progress() + speed / Math.max(1D, baseSeconds)); // process called once/sec
                if (zone.progress() >= 1D) {
                    anyChanged |= zone.setState(CaptureZoneData.ZoneState.CAPTURED);
                    if (!zone.captured()) {
                        anyChanged |= zone.setCaptured(true);
                        onCaptured(level, settlement, zone, war, attacker);
                    }
                } else anyChanged |= zone.setState(CaptureZoneData.ZoneState.UNDER_ATTACK);
            } else if (presence.defenders>0 && zone.progress()>0D) {
                anyChanged |= zone.setProgress(zone.progress()-0.0125D * Math.min(4, presence.defenders));
                if (zone.progress() <= 0D) {
                    anyChanged |= zone.setState(CaptureZoneData.ZoneState.SECURE); anyChanged |= zone.setAttackerTeam("");
                    boolean wasCaptured = zone.captured();
                    anyChanged |= zone.setCaptured(false);
                    if (wasCaptured) onRecaptured(level, settlement, zone);
                } else anyChanged |= zone.setState(zone.captured() ? CaptureZoneData.ZoneState.CAPTURED : CaptureZoneData.ZoneState.UNDER_ATTACK);
            } else anyChanged |= zone.setState(zone.captured() ? CaptureZoneData.ZoneState.CAPTURED
                    : zone.progress() > 0D ? CaptureZoneData.ZoneState.UNDER_ATTACK : CaptureZoneData.ZoneState.SECURE);
        }
        if (anyChanged) { data.setDirty(); broadcast(server); }
    }

    private static int captureSeconds(SettlementData.SettlementRecord settlement) {
        boolean major = settlement.getRank() == SettlementData.SettlementRank.MAJOR_CITY;
        return major ? SettlementConfig.CAPTURE_MAIN_MAJOR_CITY_SECONDS.get() : SettlementConfig.CAPTURE_MAIN_SECONDS.get();
    }

    private static void onCaptured(ServerLevel level, SettlementData.SettlementRecord settlement, CaptureZoneData.CaptureZoneRecord zone, CampaignData.WarRecord war, String attacker) {
        CampaignSystemsData.get(level).changeWarScore(war.id(), 30);
        MinecraftForge.EVENT_BUS.post(new CaptureZoneStateEvent(level, CaptureZoneView.from(zone), CaptureZoneStateEvent.Transition.CAPTURED));
        CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "CAPTURE ZONE LOST", "The Main Capture Zone has fallen!");
        CaptureZoneData data=CaptureZoneData.get(level); data.setOccupyingTeam(settlement.getId(), attacker);
        settlement.addChronicle("The settlement was militarily occupied after its Main Capture Zone fell.");
        MinecraftForge.EVENT_BUS.post(new SettlementOccupationEvent(level, settlement.getId(), attacker, SettlementOccupationEvent.Transition.OCCUPIED));
    }

    private static void onRecaptured(ServerLevel level, SettlementData.SettlementRecord settlement, CaptureZoneData.CaptureZoneRecord zone) {
        MinecraftForge.EVENT_BUS.post(new CaptureZoneStateEvent(level, CaptureZoneView.from(zone), CaptureZoneStateEvent.Transition.RECAPTURED));
        liberate(level, settlement);
    }

    private static void liberate(ServerLevel level, SettlementData.SettlementRecord settlement) {
        CaptureZoneData data = CaptureZoneData.get(level);
        String old = data.occupyingTeam(settlement.getId());
        if (old.isBlank()) return;
        data.setOccupyingTeam(settlement.getId(), "");
        settlement.addChronicle("The military occupation was lifted.");
        MinecraftForge.EVENT_BUS.post(new SettlementOccupationEvent(level, settlement.getId(), old, SettlementOccupationEvent.Transition.LIBERATED));
    }

    private static TeamSets teamsForWar(SettlementData data, CampaignData.WarRecord war) {
        Set<String> attackers=new HashSet<>(), defenders=new HashSet<>();
        SettlementData.SettlementRecord attacker=data.getSettlement(war.attackerSettlement());
        SettlementData.SettlementRecord defender=data.getSettlement(war.defenderSettlement());
        String primaryAttacker=effectiveTeam(attacker); String primaryDefender=effectiveTeam(defender);
        add(attackers, primaryAttacker); add(defenders, primaryDefender);
        if (war.type()!=CampaignData.WarType.SETTLEMENT_WAR || war.attackerFactionJoined()) add(attackers, war.attackerFaction());
        if (war.type()!=CampaignData.WarType.SETTLEMENT_WAR || war.defenderFactionJoined()) add(defenders, war.defenderFaction());
        war.supportingAttackers().forEach(v -> add(attackers,v)); war.supportingDefenders().forEach(v -> add(defenders,v));
        return new TeamSets(attackers,defenders,primaryAttacker,primaryDefender);
    }
    private static String effectiveTeam(SettlementData.SettlementRecord settlement) {
        if (settlement==null) return "";
        if (!settlement.getFactionTeam().isBlank()) return settlement.getFactionTeam();
        return settlement.getBackingFactionTeam();
    }
    private static void add(Set<String> set,String value){ if(value!=null&&!value.isBlank())set.add(value); }

    private static CapturePresence presence(ServerLevel level, CaptureZoneData.CaptureZoneRecord zone, Set<String> attackers, Set<String> defenders) {
        AABB box=new AABB(zone.pos1().getX(), level.getMinBuildHeight(), zone.pos1().getZ(), zone.pos2().getX()+1D, level.getMaxBuildHeight(), zone.pos2().getZ()+1D);
        int a=0,d=0;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || !box.contains(player.position()) || !zone.contains(player.blockPosition())) continue;
            String team=teamId(player); if (attackers.contains(team)) a++; else if(defenders.contains(team)) d++;
        }
        for (AbstractRecruitEntity recruit : level.getEntitiesOfClass(AbstractRecruitEntity.class, box, Entity::isAlive)) {
            if (!zone.contains(recruit.blockPosition())) continue;
            String team=teamId(recruit); if (attackers.contains(team)) a++; else if(defenders.contains(team)) d++;
        }
        return new CapturePresence(a,d);
    }
    private static String teamId(Entity entity) { return entity != null && entity.getTeam()!=null ? entity.getTeam().getName() : ""; }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        ResourceLocation id=ResourceLocation.tryParse(dimension); if(id==null)return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
    }

    public static void sync(ServerPlayer player) {
        if (player==null) return;
        List<CaptureZoneView> views=CaptureZoneData.get(player.serverLevel()).allZones().stream().limit(4096).map(CaptureZoneView::from).toList();
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaptureZoneSnapshotPacket(views));
    }
    public static void broadcast(MinecraftServer server) {
        if(server==null)return;
        List<CaptureZoneView> views=CaptureZoneData.get(server.overworld()).allZones().stream().limit(4096).map(CaptureZoneView::from).toList();
        SettlementNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new CaptureZoneSnapshotPacket(views));
    }

    private record CapturePresence(int attackers,int defenders){}
    private record TeamSets(Set<String> attackers, Set<String> defenders, String primaryAttacker, String primaryDefender){}
}
