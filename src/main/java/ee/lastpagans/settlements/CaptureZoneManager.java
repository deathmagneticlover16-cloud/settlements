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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative settlement tactical objectives and occupation bridge. */
public final class CaptureZoneManager {
    private static final Map<UUID, Draft> MAIN_DRAFTS = new HashMap<>();
    private static final Map<UUID, Draft> SECONDARY_DRAFTS = new HashMap<>();
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
        if (rank == null || rank == SettlementData.SettlementRank.VILLAGE) return new ZoneRequirement(1, 0);
        if (rank == SettlementData.SettlementRank.TOWN) return new ZoneRequirement(1, 1);
        return new ZoneRequirement(1, 2);
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
        long secondaries = zones.stream().filter(z -> z.type() == CaptureZoneData.ZoneType.SECONDARY).count();
        return mains >= requirement.main() && secondaries >= requirement.secondary();
    }

    public static String summary(ServerLevel level, SettlementData.SettlementRecord settlement) {
        ZoneRequirement needed = requirement(settlement);
        List<CaptureZoneData.CaptureZoneRecord> zones = CaptureZoneData.get(level).zonesFor(settlement.getId());
        long main = zones.stream().filter(z -> z.type()==CaptureZoneData.ZoneType.MAIN).count();
        long secondary = zones.size()-main;
        return "Capture Zones: " + main + "/" + needed.main() + " Main · " + secondary + "/" + needed.secondary() + " Secondary";
    }

    /** First click stores pos1; second click validates and creates/replaces the zone. */
    public static String setPosition(ServerPlayer player, SettlementData.SettlementRecord settlement, CaptureZoneData.ZoneType type, boolean first) {
        if (player == null || settlement == null) return "Invalid settlement.";
        if (editingBlocked(player.serverLevel(), settlement)) return "Capture Zones cannot be changed during war or occupation.";
        Map<UUID, Draft> drafts = type == CaptureZoneData.ZoneType.MAIN ? MAIN_DRAFTS : SECONDARY_DRAFTS;
        if (first) {
            drafts.put(player.getUUID(), new Draft(settlement.getId(), player.serverLevel().dimension().location().toString(), player.blockPosition()));
            return (type == CaptureZoneData.ZoneType.MAIN ? "Main" : "Secondary") + " Capture Zone pos1 set. Move to the opposite corner and set pos2.";
        }
        Draft draft = drafts.remove(player.getUUID());
        if (draft == null || !draft.settlementId.equals(settlement.getId())) return "Set pos1 for this Capture Zone first.";
        String dimension = player.serverLevel().dimension().location().toString();
        if (!dimension.equals(draft.dimension)) return "Both Capture Zone corners must be in the same dimension.";
        CaptureZoneData.CaptureZoneRecord candidate = new CaptureZoneData.CaptureZoneRecord(UUID.randomUUID(), settlement.getId(), type, dimension, draft.pos, player.blockPosition());
        CaptureZoneData.CaptureZoneRecord oldMain = CaptureZoneData.get(player.serverLevel()).zonesFor(settlement.getId()).stream()
                .filter(z -> z.type() == CaptureZoneData.ZoneType.MAIN).findFirst().orElse(null);
        String invalid = validate(player.serverLevel(), settlement, candidate,
                type == CaptureZoneData.ZoneType.MAIN && oldMain != null ? oldMain.id() : null);
        if (invalid != null) return invalid;
        CaptureZoneData data = CaptureZoneData.get(player.serverLevel());
        if (type == CaptureZoneData.ZoneType.MAIN) {
            for (CaptureZoneData.CaptureZoneRecord zone : data.zonesFor(settlement.getId())) if (zone.type()==CaptureZoneData.ZoneType.MAIN) data.remove(zone.id());
        } else {
            int max = Math.max(2, requirement(settlement).secondary() + 1);
            long existing = data.zonesFor(settlement.getId()).stream().filter(z -> z.type()==CaptureZoneData.ZoneType.SECONDARY).count();
            if (existing >= max) return "Remove a surplus Secondary before adding another. One spare objective is allowed for relocation.";
        }
        data.put(candidate);
        settlement.addChronicle((type == CaptureZoneData.ZoneType.MAIN ? "Main" : "Secondary") + " Capture Zone designated.");
        broadcast(player.getServer());
        return (type == CaptureZoneData.ZoneType.MAIN ? "Main" : "Secondary") + " Capture Zone created (" + candidate.width() + "×" + candidate.depth() + ").";
    }

    public static String removeNearest(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (player == null || settlement == null) return "Invalid settlement.";
        if (editingBlocked(player.serverLevel(), settlement)) return "Capture Zones cannot be changed during war or occupation.";
        String dim = player.serverLevel().dimension().location().toString();
        CaptureZoneData data = CaptureZoneData.get(player.serverLevel());
        CaptureZoneData.CaptureZoneRecord nearest = data.zonesFor(settlement.getId()).stream().filter(z -> dim.equals(z.dimension()))
                .min(java.util.Comparator.comparingDouble(z -> z.center().distSqr(player.blockPosition()))).orElse(null);
        if (nearest == null || nearest.center().distSqr(player.blockPosition()) > 64D*64D) return "No Capture Zone from this settlement is within 64 blocks.";
        ZoneRequirement needed = requirement(settlement);
        long sameType = data.zonesFor(settlement.getId()).stream().filter(z -> z.type() == nearest.type()).count();
        int minimum = nearest.type() == CaptureZoneData.ZoneType.MAIN ? needed.main() : needed.secondary();
        if (sameType <= minimum) return "That objective is required by this settlement's tier. Replace the Main in place, or add a replacement Secondary before removing this one.";
        data.remove(nearest.id());
        settlement.addChronicle("A " + nearest.type().name().toLowerCase() + " Capture Zone was removed.");
        broadcast(player.getServer());
        return "Removed nearest " + nearest.type().name().toLowerCase() + " Capture Zone. " + summary(player.serverLevel(), settlement);
    }

    public static void ensureVillageMainZone(ServerLevel level, SettlementData.SettlementRecord settlement) {
        if (level == null || settlement == null || !settlement.hasTownSquare()) return;
        CaptureZoneData data = CaptureZoneData.get(level);
        if (data.zonesFor(settlement.getId()).stream().anyMatch(z -> z.type()==CaptureZoneData.ZoneType.MAIN)) return;
        int min = Math.max(6, SettlementConfig.CAPTURE_ZONE_MIN_SIZE.get());
        int half = Math.max(3, min / 2);
        BlockPos center = settlement.getTownSquare();
        CaptureZoneData.CaptureZoneRecord candidate = new CaptureZoneData.CaptureZoneRecord(UUID.randomUUID(), settlement.getId(), CaptureZoneData.ZoneType.MAIN,
                settlement.getTownSquareDimension(), center.offset(-half, 0, -half), center.offset(half, 4, half));
        if (validate(level, settlement, candidate, null) == null) {
            data.put(candidate);
            settlement.addChronicle("The Town Square became the settlement's first Main Capture Zone.");
            broadcast(level.getServer());
        } else {
            settlement.addChronicle("A Main Capture Zone is required. Designate one from Manage → Capture Zones.");
        }
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

    public static void process(MinecraftServer server) {
        if (server == null) return;
        ServerLevel root = server.overworld();
        CaptureZoneData data = CaptureZoneData.get(root);
        SettlementData settlements = SettlementData.get(root);
        CampaignData campaign = CampaignData.get(root);
        boolean anyChanged=false;
        for (CaptureZoneData.CaptureZoneRecord zone : new ArrayList<>(data.allZones())) {
            SettlementData.SettlementRecord settlement = settlements.getSettlement(zone.settlementId());
            if (settlement == null) { data.remove(zone.id()); anyChanged=true; continue; }
            ServerLevel level = resolveLevel(server, zone.dimension());
            if (level == null) continue;
            CampaignData.WarRecord war = activeAttackOn(campaign, settlement.getId());
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
            TeamSets teams = teamsForWar(settlements, war);
            if (!zone.attackerTeam().isBlank() && !zone.attackerTeam().equals(teams.primaryAttacker)) {
                if (zone.type() == CaptureZoneData.ZoneType.MAIN) liberate(level, settlement);
                anyChanged |= zone.setProgress(0D);
                anyChanged |= zone.setCaptured(false);
                anyChanged |= zone.setAttackerTeam("");
                anyChanged |= zone.setState(CaptureZoneData.ZoneState.SECURE);
            }
            boolean secondariesRemain = zone.type() == CaptureZoneData.ZoneType.MAIN && !zone.captured()
                    && (!requirementsMet(root, settlement) || data.zonesFor(settlement.getId()).stream()
                    .anyMatch(other -> other.type() == CaptureZoneData.ZoneType.SECONDARY && !other.captured()));
            CapturePresence presence = presence(level, zone, teams.attackers, teams.defenders);
            CaptureZoneData.ZoneState before = zone.state();
            if (presence.attackers>0 && presence.defenders>0) {
                anyChanged |= zone.setState(CaptureZoneData.ZoneState.CONTESTED);
                if (before != CaptureZoneData.ZoneState.CONTESTED) MinecraftForge.EVENT_BUS.post(new CaptureZoneStateEvent(level, CaptureZoneView.from(zone), CaptureZoneStateEvent.Transition.CONTESTED));
                continue;
            }
            if (presence.attackers>0) {
                if (secondariesRemain) { anyChanged |= zone.setState(CaptureZoneData.ZoneState.LOCKED); continue; }
                String attacker = teams.primaryAttacker;
                anyChanged |= zone.setAttackerTeam(attacker);
                int baseSeconds = captureSeconds(root, settlement, zone);
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
                    : secondariesRemain ? CaptureZoneData.ZoneState.LOCKED
                    : zone.progress() > 0D ? CaptureZoneData.ZoneState.UNDER_ATTACK : CaptureZoneData.ZoneState.SECURE);
        }
        if (anyChanged) { data.setDirty(); broadcast(server); }
    }

    private static int captureSeconds(ServerLevel root, SettlementData.SettlementRecord settlement, CaptureZoneData.CaptureZoneRecord zone) {
        if (zone.type()==CaptureZoneData.ZoneType.SECONDARY) return SettlementConfig.CAPTURE_SECONDARY_SECONDS.get();
        boolean major = settlement.getRank() == SettlementData.SettlementRank.MAJOR_CITY;
        return major ? SettlementConfig.CAPTURE_MAIN_MAJOR_CITY_SECONDS.get() : SettlementConfig.CAPTURE_MAIN_SECONDS.get();
    }

    private static void onCaptured(ServerLevel level, SettlementData.SettlementRecord settlement, CaptureZoneData.CaptureZoneRecord zone, CampaignData.WarRecord war, String attacker) {
        CampaignSystemsData.get(level).changeWarScore(war.id(), zone.type()==CaptureZoneData.ZoneType.MAIN ? 30 : 10);
        MinecraftForge.EVENT_BUS.post(new CaptureZoneStateEvent(level, CaptureZoneView.from(zone), CaptureZoneStateEvent.Transition.CAPTURED));
        CampaignToastSender.settlementManagers(level.getServer(), settlement.getId(), "CAPTURE ZONE LOST", zone.type()==CaptureZoneData.ZoneType.MAIN ? "The Main Capture Zone has fallen!" : "A Secondary Capture Zone has fallen.");
        if (zone.type()==CaptureZoneData.ZoneType.MAIN) {
            CaptureZoneData data=CaptureZoneData.get(level); data.setOccupyingTeam(settlement.getId(), attacker);
            settlement.addChronicle("The settlement was militarily occupied after its Main Capture Zone fell.");
            MinecraftForge.EVENT_BUS.post(new SettlementOccupationEvent(level, settlement.getId(), attacker, SettlementOccupationEvent.Transition.OCCUPIED));
        }
    }

    private static void onRecaptured(ServerLevel level, SettlementData.SettlementRecord settlement, CaptureZoneData.CaptureZoneRecord zone) {
        MinecraftForge.EVENT_BUS.post(new CaptureZoneStateEvent(level, CaptureZoneView.from(zone), CaptureZoneStateEvent.Transition.RECAPTURED));
        if (zone.type()==CaptureZoneData.ZoneType.MAIN) {
            liberate(level, settlement);
        }
    }

    private static void liberate(ServerLevel level, SettlementData.SettlementRecord settlement) {
        CaptureZoneData data = CaptureZoneData.get(level);
        String old = data.occupyingTeam(settlement.getId());
        if (old.isBlank()) return;
        data.setOccupyingTeam(settlement.getId(), "");
        settlement.addChronicle("The military occupation was lifted.");
        MinecraftForge.EVENT_BUS.post(new SettlementOccupationEvent(level, settlement.getId(), old, SettlementOccupationEvent.Transition.LIBERATED));
    }

    private static CampaignData.WarRecord activeAttackOn(CampaignData campaign, String settlementId) {
        return campaign.getWars().stream().filter(w -> w.status()==CampaignData.WarStatus.ACTIVE)
                .filter(w -> settlementId.equals(w.defenderSettlement())).findFirst().orElse(null);
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
            if (!player.isAlive() || player.isSpectator() || !box.contains(player.position())) continue;
            String team=teamId(player); if (attackers.contains(team)) a++; else if(defenders.contains(team)) d++;
        }
        for (AbstractRecruitEntity recruit : level.getEntitiesOfClass(AbstractRecruitEntity.class, box, Entity::isAlive)) {
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
        List<CaptureZoneView> views=CaptureZoneData.get(player.serverLevel()).allZones().stream().map(CaptureZoneView::from).toList();
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaptureZoneSnapshotPacket(views));
    }
    public static void broadcast(MinecraftServer server) {
        if(server==null)return;
        List<CaptureZoneView> views=CaptureZoneData.get(server.overworld()).allZones().stream().map(CaptureZoneView::from).toList();
        SettlementNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new CaptureZoneSnapshotPacket(views));
    }

    private record Draft(String settlementId,String dimension,BlockPos pos){}
    private record CapturePresence(int attackers,int defenders){}
    private record TeamSets(Set<String> attackers, Set<String> defenders, String primaryAttacker, String primaryDefender){}
}
