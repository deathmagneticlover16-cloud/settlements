package ee.lastpagans.settlements;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsFactionManager;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import ee.lastpagans.settlements.network.CampaignSnapshotPacket;
import ee.lastpagans.settlements.network.DiplomacySnapshotPacket;
import ee.lastpagans.settlements.network.FactionSnapshotPacket;
import ee.lastpagans.settlements.network.FactionCreatePacket;
import ee.lastpagans.settlements.network.FamilySnapshotPacket;
import ee.lastpagans.settlements.network.MapMarkersPacket;
import ee.lastpagans.settlements.network.MilitarySnapshotPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.WorldNewsSnapshotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative campaign, faction, diplomacy, family and world-news actions. */
public final class CampaignManager {
    private CampaignManager() {}

    /** Applies faction Reliability while respecting ruler traits such as Reserved. */
    public static int changeReliability(ServerLevel level, CampaignData data, String factionId, int delta) {
        if (data == null || factionId == null || factionId.isBlank() || delta == 0) return 0;
        int adjusted = delta;
        if (level != null && FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
            UUID leader = faction == null ? null : faction.getTeamLeaderUUID();
            SettlementData.CharacterRecord character = leader == null ? null : SettlementData.get(level).getCharacter(leader);
            if (character != null && character.isCreated() && character.getTraits().stream().anyMatch("reserved"::equals)) {
                int percent = SettlementConfig.RESERVED_RELIABILITY_MAGNITUDE_PERCENT.get();
                int magnitude = Math.max(1, (int)Math.round(Math.abs(delta) * percent / 100.0D));
                adjusted = delta < 0 ? -magnitude : magnitude;
            }
        }
        data.changeFactionReliability(factionId, adjusted);
        return adjusted;
    }

    public static void openMain(ServerPlayer player) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CampaignSnapshotPacket(CampaignSnapshot.from(player)));
    }

    public static void openFaction(ServerPlayer player, String message) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FactionSnapshotPacket(FactionSnapshot.from(player, message)));
    }

    public static void openMilitary(ServerPlayer player, String message) {
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSelectedSettlement(player.getUUID());
        if (settlement == null || !SettlementManager.canView(player, settlement)) {
            player.sendSystemMessage(Component.literal("Select a settlement before opening the Military screen."));
            openMain(player);
            return;
        }
        if (!SettlementManager.canContribute(player, settlement)) {
            player.sendSystemMessage(Component.literal("Military information is private to settlement members."));
            SettlementManager.open(player, settlement.getId());
            return;
        }
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MilitarySnapshotPacket(SettlementSnapshot.from(settlement, player, message)));
    }

    public static void openDiplomacy(ServerPlayer player, String message) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DiplomacySnapshotPacket(DiplomacySnapshot.from(player, message)));
    }

    public static void openFamily(ServerPlayer player, String message) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FamilySnapshotPacket(FamilySnapshot.from(player, message)));
    }

    public static void openWorldNews(ServerPlayer player) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new WorldNewsSnapshotPacket(WorldNewsSnapshot.from(player)));
    }

    public static void sendMapMarkers(ServerPlayer player) {
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MapMarkersPacket(MapMarkerSnapshot.from(player.serverLevel())));
    }

    public static void broadcastMapMarkers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendMapMarkers(player);
    }

    public static void handleFactionAction(ServerPlayer player, String actionRaw, UUID targetPlayer, int tier, String value) {
        String action = actionRaw == null ? "" : actionRaw.toUpperCase(Locale.ROOT);
        if ("CREATE_FACTION".equals(action)) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            ChatFormatting teamColor = ChatFormatting.WHITE;
            String name = value;
            if (parts.length == 2) {
                try { teamColor = ChatFormatting.valueOf(parts[0].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) {}
                name = parts[1];
            }
            // Recruits 1.15.2 exposes 25 faction unit colors (indices 0-24), not the 16 DyeColor values.
            openFaction(player, createFaction(player, name, teamColor, (byte)Math.max(0, Math.min(24, tier))));
            return;
        }
        if ("CREATE_SETTLEMENT".equals(action)) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            String id = parts.length > 0 ? SettlementData.normalizeId(parts[0]) : "";
            String name = parts.length > 1 ? SettlementData.cleanText(parts[1], id, 64) : id;
            String result = createSettlementFromGui(player, id, name);
            player.sendSystemMessage(Component.literal(result));
            openMain(player);
            return;
        }
        if ("REQUEST_JOIN".equals(action) || "CANCEL_JOIN_REQUEST".equals(action)) {
            String result = "REQUEST_JOIN".equals(action) ? requestFactionJoin(player, value) : cancelFactionJoinRequest(player, value);
            openFaction(player, result);
            return;
        }
        if ("ACCEPT_FACTION_INVITE".equals(action) || "DECLINE_FACTION_INVITE".equals(action)) {
            String result = "ACCEPT_FACTION_INVITE".equals(action) ? acceptFactionInvitation(player, value) : declineFactionInvitation(player, value);
            openFaction(player, result);
            return;
        }
        String factionId = factionId(player);
        if (factionId.isBlank()) {
            openFaction(player, "You are not a member of a faction. Create one below or ask to join an existing faction.");
            return;
        }
        CampaignData data = CampaignData.get(player.serverLevel());
        boolean leader = isFactionLeader(player, factionId);
        boolean administrator = player.createCommandSourceStack().hasPermission(2);
        boolean protectedFaction = CampaignSystemsData.get(player.serverLevel()).factionProtected(factionId);
        if (protectedFaction && !administrator && !"LEAVE_FACTION".equals(action)) {
            openFaction(player, "Protected admin factions cannot be administered through normal faction controls.");
            return;
        }
        String result;
        switch (action) {
            case "ACCEPT_JOIN_REQUEST" -> result = acceptFactionJoinRequest(player, factionId, value);
            case "REJECT_JOIN_REQUEST" -> result = rejectFactionJoinRequest(player, factionId, value);
            case "LEAVE_FACTION" -> result = leaveFaction(player, factionId);
            case "KICK_MEMBER" -> result = kickFactionMember(player, factionId, targetPlayer);
            case "SEND_FACTION_INVITE" -> result = sendFactionInvitation(player, factionId, targetPlayer);
            case "TRANSFER_LEADERSHIP" -> result = transferFactionLeadership(player, factionId, targetPlayer);
            case "SET_MEMBER_RANK" -> {
                if (!leader) result = "Only the faction leader may assign ranks.";
                else if (targetPlayer == null) result = "Select a faction member.";
                else {
                    RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null
                            : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
                    if (faction == null) result = "Faction data is unavailable.";
                    else if (targetPlayer.equals(faction.getTeamLeaderUUID())) result = "The faction leader always holds the highest rank.";
                    else if (faction.getMembers().stream().noneMatch(info -> info != null && targetPlayer.equals(info.getUUID()))) result = "That player is no longer a member of your faction.";
                    else {
                        data.setFactionMemberRank(factionId, targetPlayer, Math.max(0, Math.min(3, tier)));
                        SettlementManager.broadcastProfiles(player.getServer());
                        result = "Faction rank updated.";
                    }
                }
            }
            case "SET_RANK_TITLE" -> {
                if (!leader) result = "Only the faction leader may rename faction ranks.";
                else {
                    data.setFactionRankTitle(factionId, Math.max(0, Math.min(4, tier)), value);
                    SettlementManager.broadcastProfiles(player.getServer());
                    result = "Tier " + Math.max(0, Math.min(4, tier)) + " title updated.";
                }
            }
            case "SET_CAPITAL" -> {
                if (!leader) result = "Only the faction leader may designate the capital.";
                else {
                    SettlementData settlements = SettlementData.get(player.serverLevel());
                    SettlementData.SettlementRecord target = settlements.getSettlement(value);
                    if (target == null || !factionId.equals(target.getFactionTeam())) result = "Select one of your faction's settlements.";
                    else {
                        for (SettlementData.SettlementRecord settlement : settlements.settlementsForFaction(factionId)) {
                            if (settlement != target) settlement.setCapital(false);
                        }
                        target.setCapital(true);
                        result = target.getName() + " is now the faction capital.";
                    }
                }
            }
            default -> result = "Unknown faction action.";
        }
        openFaction(player, result);
    }

    private static String requestFactionJoin(ServerPlayer player, String requestedFactionId) {
        if (!factionId(player).isBlank()) return "Leave your current faction before requesting to join another one.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        String targetId = requestedFactionId == null ? "" : requestedFactionId.trim();
        RecruitsFaction target = FactionEvents.recruitsFactionManager.getFactionByStringID(targetId);
        if (target == null) return "That faction no longer exists.";
        if (CampaignSystemsData.get(player.serverLevel()).factionProtected(targetId)) return "Protected admin factions do not accept player applications.";
        if (!target.canAddPlayer()) return target.getTeamDisplayName() + " has reached its member limit.";

        String applicant = player.getDisplayName().getString();
        for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
            if (faction == null) continue;
            boolean pending = faction.getJoinRequests().stream().anyMatch(name -> name.equalsIgnoreCase(applicant));
            if (!pending) continue;
            if (targetId.equals(faction.getStringID())) return "Your application to " + target.getTeamDisplayName() + " is already pending.";
            return "You already have a pending application to " + faction.getTeamDisplayName() + ". Cancel it before applying elsewhere.";
        }

        FactionEvents.sendJoinRequest(player.serverLevel(), player, targetId);
        boolean recorded = target.getJoinRequests().stream().anyMatch(name -> name.equalsIgnoreCase(applicant));
        if (!recorded) return "Recruits did not record the faction application.";
        CampaignToastSender.send(player, "FACTION APPLICATION", "Application sent to " + target.getTeamDisplayName() + ".");
        CampaignToastSender.faction(player.getServer(), targetId, "FACTION APPLICATION",
                player.getGameProfile().getName() + " has requested to join your faction.");
        return "Application sent to " + target.getTeamDisplayName() + ".";
    }

    private static String cancelFactionJoinRequest(ServerPlayer player, String requestedFactionId) {
        if (!factionId(player).isBlank()) return "You are already a faction member.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        RecruitsFaction target = FactionEvents.recruitsFactionManager.getFactionByStringID(requestedFactionId == null ? "" : requestedFactionId.trim());
        if (target == null) return "That faction no longer exists.";
        String applicant = player.getDisplayName().getString();
        String stored = target.getJoinRequests().stream().filter(name -> name.equalsIgnoreCase(applicant)).findFirst().orElse("");
        if (stored.isBlank()) return "You do not have a pending application to " + target.getTeamDisplayName() + ".";
        target.removeJoinRequest(stored);
        FactionEvents.recruitsFactionManager.save(player.serverLevel());
        FactionEvents.recruitsFactionManager.broadcastFactionsToAll(player.serverLevel());
        CampaignToastSender.send(player, "APPLICATION WITHDRAWN", "Your application to " + target.getTeamDisplayName() + " was withdrawn.");
        return "Application to " + target.getTeamDisplayName() + " withdrawn.";
    }

    private static String acceptFactionJoinRequest(ServerPlayer player, String factionId, String applicantName) {
        if (!canProcessFactionApplications(player, factionId)) return "Your faction rank cannot process membership applications.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) return "Faction data is unavailable.";
        String requested = applicantName == null ? "" : applicantName.trim();
        String stored = faction.getJoinRequests().stream().filter(name -> name.equalsIgnoreCase(requested)).findFirst().orElse("");
        if (stored.isBlank()) return "That membership application no longer exists.";
        ServerPlayer applicant = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayerByName(stored);
        if (applicant == null) return stored + " must be online before their application can be accepted.";
        if (!factionId(applicant).isBlank()) {
            faction.removeJoinRequest(stored);
            FactionEvents.recruitsFactionManager.save(player.serverLevel());
            return stored + " has already joined another faction.";
        }
        if (!faction.canAddPlayer()) return faction.getTeamDisplayName() + " has reached its member limit.";

        BackingFactionManager.PoliticalTransition transition = BackingFactionManager.beginPoliticalJoin(applicant);
        FactionEvents.addPlayerToTeam(player, player.serverLevel(), factionId, applicant.getGameProfile().getName());
        boolean joined = applicant.getTeam() != null && factionId.equals(applicant.getTeam().getName());
        BackingFactionManager.finishPoliticalJoin(applicant, transition, factionId, joined);
        if (!joined) return "Recruits did not confirm " + stored + " as a faction member.";
        CampaignData.get(player.serverLevel()).setFactionMemberRank(factionId, applicant.getUUID(), 0);
        CampaignSystemsData.get(player.serverLevel()).clearFactionInvitations(applicant.getUUID());
        clearFactionApplications(player.serverLevel(), applicant.getDisplayName().getString());
        SettlementManager.broadcastProfiles(player.getServer());
        CampaignToastSender.send(applicant, "APPLICATION ACCEPTED", "You have joined " + faction.getTeamDisplayName() + ".");
        CampaignToastSender.faction(player.getServer(), factionId, "NEW FACTION MEMBER", applicant.getGameProfile().getName() + " joined the faction.");
        return applicant.getGameProfile().getName() + " joined " + faction.getTeamDisplayName() + ".";
    }

    private static String rejectFactionJoinRequest(ServerPlayer player, String factionId, String applicantName) {
        if (!canProcessFactionApplications(player, factionId)) return "Your faction rank cannot process membership applications.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) return "Faction data is unavailable.";
        String requested = applicantName == null ? "" : applicantName.trim();
        String stored = faction.getJoinRequests().stream().filter(name -> name.equalsIgnoreCase(requested)).findFirst().orElse("");
        if (stored.isBlank()) return "That membership application no longer exists.";
        faction.removeJoinRequest(stored);
        FactionEvents.recruitsFactionManager.save(player.serverLevel());
        FactionEvents.recruitsFactionManager.broadcastFactionsToAll(player.serverLevel());
        ServerPlayer applicant = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayerByName(stored);
        if (applicant != null) CampaignToastSender.send(applicant, "APPLICATION DECLINED", faction.getTeamDisplayName() + " declined your membership application.");
        return stored + "'s application was declined.";
    }

    private static String sendFactionInvitation(ServerPlayer player, String factionId, UUID targetId) {
        if (!canProcessFactionApplications(player, factionId)) return "Your faction rank cannot invite new members.";
        if (targetId == null || targetId.equals(player.getUUID())) return "Select an independent player to invite.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) return "Faction data is unavailable.";
        if (CampaignSystemsData.get(player.serverLevel()).factionProtected(factionId)) return "Protected admin factions cannot invite normal players.";
        if (!faction.canAddPlayer()) return faction.getTeamDisplayName() + " has reached its member limit.";
        ServerPlayer target = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return "That player is no longer online.";
        if (!factionId(target).isBlank()) return target.getGameProfile().getName() + " is already a faction member.";
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (extra.hasFactionInvitation(factionId, targetId)) return target.getGameProfile().getName() + " already has an invitation from your faction.";
        extra.addFactionInvitation(factionId, targetId, player.getUUID(), System.currentTimeMillis());
        CampaignToastSender.send(target, "FACTION INVITATION", faction.getTeamDisplayName() + " has invited you to join.");
        return "Invitation sent to " + target.getGameProfile().getName() + ".";
    }

    private static String acceptFactionInvitation(ServerPlayer player, String factionIdRaw) {
        if (!factionId(player).isBlank()) return "Leave your current faction before accepting another faction's invitation.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        String factionId = factionIdRaw == null ? "" : factionIdRaw.trim();
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (!extra.hasFactionInvitation(factionId, player.getUUID())) return "That faction invitation no longer exists.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) { extra.removeFactionInvitation(factionId, player.getUUID()); return "That faction no longer exists."; }
        if (extra.factionProtected(factionId)) { extra.removeFactionInvitation(factionId, player.getUUID()); return "That faction is now protected from player membership."; }
        if (!faction.canAddPlayer()) return faction.getTeamDisplayName() + " has reached its member limit.";
        ServerPlayer leader = FactionEvents.recruitsFactionManager.getTeamLeader(faction, player.serverLevel());
        BackingFactionManager.PoliticalTransition transition = BackingFactionManager.beginPoliticalJoin(player);
        FactionEvents.addPlayerToTeam(leader, player.serverLevel(), factionId, player.getGameProfile().getName());
        boolean joined = player.getTeam() != null && factionId.equals(player.getTeam().getName());
        BackingFactionManager.finishPoliticalJoin(player, transition, factionId, joined);
        if (!joined) return "Recruits did not confirm the faction invitation.";
        extra.clearFactionInvitations(player.getUUID());
        clearFactionApplications(player.serverLevel(), player.getDisplayName().getString());
        CampaignData.get(player.serverLevel()).setFactionMemberRank(factionId, player.getUUID(), 0);
        SettlementManager.broadcastProfiles(player.getServer());
        CampaignToastSender.send(player, "FACTION JOINED", "You accepted the invitation to " + faction.getTeamDisplayName() + ".");
        CampaignToastSender.faction(player.getServer(), factionId, "NEW FACTION MEMBER", player.getGameProfile().getName() + " accepted a faction invitation.");
        return "You joined " + faction.getTeamDisplayName() + ".";
    }

    private static String declineFactionInvitation(ServerPlayer player, String factionIdRaw) {
        String factionId = factionIdRaw == null ? "" : factionIdRaw.trim();
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        if (!extra.removeFactionInvitation(factionId, player.getUUID())) return "That faction invitation no longer exists.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        String name = faction == null ? factionId : faction.getTeamDisplayName();
        if (faction != null) CampaignToastSender.faction(player.getServer(), factionId, "INVITATION DECLINED", player.getGameProfile().getName() + " declined your faction invitation.");
        return "Invitation from " + name + " declined.";
    }

    private static void clearFactionApplications(ServerLevel level, String applicantName) {
        if (FactionEvents.recruitsFactionManager == null || applicantName == null || applicantName.isBlank()) return;
        boolean changed = false;
        for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
            if (faction == null) continue;
            String stored = faction.getJoinRequests().stream().filter(name -> name.equalsIgnoreCase(applicantName)).findFirst().orElse("");
            if (!stored.isBlank()) { faction.removeJoinRequest(stored); changed = true; }
        }
        if (changed) {
            FactionEvents.recruitsFactionManager.save(level);
            FactionEvents.recruitsFactionManager.broadcastFactionsToAll(level);
        }
    }

    private static String leaveFaction(ServerPlayer player, String factionId) {
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) return "Faction data is unavailable.";
        if (player.getUUID().equals(faction.getTeamLeaderUUID())) return "The faction leader cannot leave. Transfer or dissolve leadership first.";
        boolean ownsSettlement = SettlementData.get(player.serverLevel()).settlementsForFaction(factionId).stream()
                .anyMatch(settlement -> player.getUUID().equals(settlement.getOwnerId()));
        if (ownsSettlement) return "Transfer ownership of your faction settlements before leaving the faction.";
        String name = player.getGameProfile().getName();
        FactionEvents.leaveTeam(false, player, factionId, player.serverLevel(), false);
        if (player.getTeam() != null && factionId.equals(player.getTeam().getName())) return "Recruits did not confirm that you left the faction.";
        CampaignData.get(player.serverLevel()).clearFactionMemberRank(factionId, player.getUUID());
        SettlementData.get(player.serverLevel()).clearSelection(player.getUUID());
        SettlementManager.broadcastProfiles(player.getServer());
        CampaignToastSender.send(player, "FACTION LEFT", "You left " + faction.getTeamDisplayName() + ".");
        CampaignToastSender.faction(player.getServer(), factionId, "FACTION MEMBER LEFT", name + " left the faction.");
        return "You left " + faction.getTeamDisplayName() + ".";
    }

    private static String kickFactionMember(ServerPlayer player, String factionId, UUID targetId) {
        if (!isFactionLeader(player, factionId)) return "Only the faction leader may remove members.";
        if (targetId == null || targetId.equals(player.getUUID())) return "Select another faction member.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager == null ? null : FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) return "Faction data is unavailable.";
        if (targetId.equals(faction.getTeamLeaderUUID())) return "The faction leader cannot be removed.";
        String account = faction.getMembers().stream().filter(info -> info != null && targetId.equals(info.getUUID()))
                .map(info -> info.getName()).findFirst().orElse("");
        if (account.isBlank()) return "That player is no longer a member of your faction.";
        boolean ownsSettlement = SettlementData.get(player.serverLevel()).settlementsForFaction(factionId).stream()
                .anyMatch(settlement -> targetId.equals(settlement.getOwnerId()));
        if (ownsSettlement) return "Transfer that player's settlement ownership before removing them from the faction.";

        ServerPlayer target = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            FactionEvents.tryToRemoveFromTeam(player.getTeam(), player, target, player.serverLevel(), target.getDisplayName().getString(), true);
        } else {
            FactionEvents.removeOfflinePlayerFromTeam(player, account, player.serverLevel());
        }
        boolean remains = faction.getMembers().stream().anyMatch(info -> info != null && targetId.equals(info.getUUID()));
        if (remains) return "Recruits did not confirm removal of " + account + ".";
        CampaignData.get(player.serverLevel()).clearFactionMemberRank(factionId, targetId);
        SettlementManager.broadcastProfiles(player.getServer());
        if (target != null) CampaignToastSender.send(target, "REMOVED FROM FACTION", "You were removed from " + faction.getTeamDisplayName() + ".");
        CampaignToastSender.faction(player.getServer(), factionId, "FACTION MEMBER REMOVED", account + " was removed from the faction.");
        return account + " was removed from " + faction.getTeamDisplayName() + ".";
    }

    private static String transferFactionLeadership(ServerPlayer player, String factionId, UUID targetId) {
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) return "Faction data is unavailable.";
        if (!player.getUUID().equals(faction.getTeamLeaderUUID())) return "Only the actual faction leader may transfer leadership.";
        if (targetId == null || targetId.equals(player.getUUID())) return "Select another faction member.";
        String account = faction.getMembers().stream().filter(info -> info != null && targetId.equals(info.getUUID()))
                .map(info -> info.getName()).findFirst().orElse("");
        if (account.isBlank()) return "That player is no longer a member of your faction.";

        RecruitsFaction edited = RecruitsFaction.fromNBT(faction.toNBT());
        edited.setTeamLeaderID(targetId);
        edited.setTeamLeaderName(account);
        FactionEvents.modifyTeam(player.serverLevel(), factionId, edited, player, 0);
        RecruitsFaction confirmed = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (confirmed == null || !targetId.equals(confirmed.getTeamLeaderUUID())) return "Recruits did not confirm the leadership transfer.";

        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.setFactionMemberRank(factionId, player.getUUID(), 3);
        campaign.clearFactionMemberRank(factionId, targetId);
        SettlementManager.broadcastProfiles(player.getServer());
        CampaignToastSender.faction(player.getServer(), factionId, "NEW FACTION LEADER", account + " is now the leader of " + confirmed.getTeamDisplayName() + ".");
        return "Faction leadership transferred to " + account + ".";
    }

    private static boolean canProcessFactionApplications(ServerPlayer player, String factionId) {
        if (isFactionLeader(player, factionId)) return true;
        return CampaignData.get(player.serverLevel()).factionRankTier(factionId, player.getUUID(), false) >= 3;
    }

    public static void handleDiplomacyAction(ServerPlayer player, String actionRaw, String targetFaction,
                                             UUID proposalId, UUID warId, String extra) {
        String action = actionRaw == null ? "" : actionRaw.toUpperCase(Locale.ROOT);
        String own = factionId(player);
        CampaignData data = CampaignData.get(player.serverLevel());
        long day = player.serverLevel().getDayTime() / 24000L;
        if ("DECLARE_WAR".equals(action) && extra != null && extra.toUpperCase(Locale.ROOT).startsWith("SETTLEMENT_WAR|")) {
            openDiplomacy(player, declareSettlementWar(player, data, extra, day));
            return;
        }
        if ("OFFER_PEACE_TERMS".equals(action)) {
            openDiplomacy(player, offerPeaceTerms(player, data, warId, extra, day));
            return;
        }
        if ("ACCEPT_PEACE_TERMS".equals(action) || "REJECT_PEACE_TERMS".equals(action)) {
            openDiplomacy(player, resolvePeaceTerms(player, data, proposalId, "ACCEPT_PEACE_TERMS".equals(action), day));
            return;
        }
        if (own.isBlank()) {
            openDiplomacy(player, "Independent rulers may declare Settlement Wars from a settlement they manage; faction treaties require a faction.");
            return;
        }
        String result = switch (action) {
            case "PROPOSE_NAP" -> propose(player, data, CampaignData.ProposalType.NON_AGGRESSION, own, targetFaction, null, day);
            case "PROPOSE_DEFENSIVE" -> propose(player, data, CampaignData.ProposalType.DEFENSIVE_ALLIANCE, own, targetFaction, null, day);
            case "PROPOSE_MILITARY" -> propose(player, data, CampaignData.ProposalType.MILITARY_ALLIANCE, own, targetFaction, null, day);
            case "DECLARE_WAR" -> declareWar(player, data, own, targetFaction, extra, day);
            case "OFFER_PEACE" -> offerPeace(player, data, own, warId, day);
            case "CALL_TO_WAR" -> callToWar(player, data, own, targetFaction, warId, day);
            case "JOIN_SETTLEMENT_WAR" -> joinSettlementWar(player, data, own, warId, day);
            case "IGNORE_SETTLEMENT_WAR" -> ignoreSettlementWar(player, data, own, warId);
            case "SEND_AID" -> sendWarAid(player, data, own, warId, extra, day);
            case "CANCEL_AID" -> cancelWarAid(player, data, own, warId, proposalId, day);
            case "ACCEPT_PROPOSAL" -> acceptProposal(player, data, own, proposalId, day);
            case "REJECT_PROPOSAL" -> rejectProposal(player, data, own, proposalId);
            case "BREAK_TREATY" -> breakTreaty(player, data, own, targetFaction, day);
            default -> "Unknown diplomacy action.";
        };
        openDiplomacy(player, result);
    }

    private static String propose(ServerPlayer player, CampaignData data, CampaignData.ProposalType type,
                                  String own, String target, UUID war, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot send treaties.";
        if (!validOtherFaction(own, target)) return "Select another faction.";
        CampaignSystemsData protectedData = CampaignSystemsData.get(player.serverLevel());
        if (protectedData.factionProtected(own) || protectedData.factionProtected(target)) return "Protected admin factions are excluded from ordinary diplomacy.";
        if (data.relation(own, target).state() == CampaignData.TreatyState.WAR) return "End the war before proposing a treaty.";
        data.createProposal(type, own, target, player.getUUID(), war, day);
        changeRelationship(player.getServer(), data, own, target, 2);
        data.addNews(CampaignData.NewsCategory.DIPLOMACY, CampaignData.NewsImportance.NORMAL,
                "Diplomatic proposal", factionName(own) + " sent a " + pretty(type.name()) + " proposal to " + factionName(target) + ".",
                day, target, "", CampaignData.NewsVisibility.FACTION);
        CampaignToastSender.faction(player.getServer(), target, "DIPLOMATIC PROPOSAL", factionName(own) + " sent a " + pretty(type.name()) + " proposal.");
        return "Proposal sent to " + factionName(target) + ".";
    }

    private static String declareWar(ServerPlayer player, CampaignData data, String own, String target,
                                     String extra, long day) {
        if (!isFactionLeader(player, own)) return "Only the faction leader may declare war.";
        if (!validOtherFaction(own, target)) return "Select another faction.";
        CampaignSystemsData extraData = CampaignSystemsData.get(player.serverLevel());
        if (extraData.factionProtected(own) || extraData.factionProtected(target)) return "Protected admin factions cannot participate in wars.";
        if (extraData.marriageAlliance(own, target)) return "A Marriage Alliance currently binds these factions. End the marriage before declaring a faction war.";
        CampaignData.TreatyState previousState = data.relation(own, target).state();
        if (previousState == CampaignData.TreatyState.WAR) return "Your factions are already at war.";

        String[] split = extra == null ? new String[0] : extra.split("\\|", -1);
        CampaignData.WarType type = parseWarType(split.length > 0 ? split[0] : "");
        if (type == CampaignData.WarType.SETTLEMENT_WAR) {
            // Settlement Wars are deliberately settlement-v-settlement. Never allow the old
            // faction-targeted declaration packet to recreate the confusing 2.0 behaviour
            // or immediately mark both parent factions as enemies.
            return "Settlement Wars must be declared through the settlement-v-settlement declaration screen.";
        }
        if (type == CampaignData.WarType.INDEPENDENCE_WAR) {
            return "Independence wars are reserved for the later settlement-sovereignty system and cannot yet be declared from this screen.";
        }
        String settlementId = split.length > 1 ? SettlementData.normalizeId(split[1]) : "";
        CampaignData.WarGoal goal = parseWarGoal(split.length > 2 ? split[2] : "", type);
        String customCasusBelli = split.length > 3 ? SettlementData.cleanText(split[3], "", 100) : "";
        if (goal == CampaignData.WarGoal.CUSTOM && customCasusBelli.isBlank()) return "Enter a custom Casus Belli before declaring war.";

        SettlementData.SettlementRecord targetSettlement = settlementId.isBlank() ? null
                : SettlementData.get(player.serverLevel()).getSettlement(settlementId);
        boolean needsSettlement = type == CampaignData.WarType.RAID || type == CampaignData.WarType.SKIRMISH
                || type == CampaignData.WarType.INDEPENDENCE_WAR;
        if (needsSettlement && targetSettlement == null) return "This conflict tier requires a valid target settlement.";
        if (targetSettlement != null) {
            if (type == CampaignData.WarType.INDEPENDENCE_WAR) {
                if (!own.equals(targetSettlement.getFactionTeam())) return "An independence war must name one of your faction's settlements.";
            } else if (!target.equals(targetSettlement.getFactionTeam())) {
                return "The chosen settlement does not belong to the target faction.";
            }
        }
        if (!goal.appliesTo(type)) return "The selected Casus Belli does not match the conflict tier.";
        if (goal == CampaignData.WarGoal.HOLY_WAR) {
            String ownFaith = factionFaith(player.serverLevel(), own);
            String targetFaith = factionFaith(player.serverLevel(), target);
            if (ownFaith.isBlank() || targetFaith.isBlank()) return "Holy War requires both factions to have a recognized leader faith.";
            if (CampaignDefinitions.religionRelationModifier(ownFaith, targetFaith) > -8) {
                return "Holy War requires strongly incompatible faiths. " + ownFaith + " and " + targetFaith + " are not religiously hostile enough for this Casus Belli.";
            }
        }

        CampaignData.WarRecord war = data.createWar(type, goal, own, target, settlementId, player.getUUID(), day);
        if (goal == CampaignData.WarGoal.CUSTOM) data.setWarCustomCasusBelli(war.id(), customCasusBelli);
        int reliabilityPenalty = switch (previousState) {
            case MILITARY_ALLIANCE, DEFENSIVE_ALLIANCE -> -25;
            case NON_AGGRESSION -> -15;
            default -> -5;
        };
        changeReliability(player.serverLevel(), data, own, reliabilityPenalty);
        if (!syncRelation(player.serverLevel(), own, target, CampaignData.TreatyState.WAR)) {
            data.endWar(war.id(), day, "Declaration aborted: Recruits relation synchronization failed.");
            return "War declaration aborted because Recruits did not confirm ENEMY relations in both directions.";
        }
        CampaignSystemsManager.applyEmergencyMobilization(player.serverLevel(), target);
        CampaignToastSender.warDeclared(player.getServer(), own, target, type.displayName(), war.casusBelliName());
        String targetText = targetSettlement == null ? factionName(target) : targetSettlement.getName() + " (" + factionName(target) + ")";
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR,
                type.displayName() + " declared", factionName(own) + " declared a " + type.displayName() + " against " + targetText
                        + ". Casus Belli: " + war.casusBelliName() + ".", day, own, settlementId, CampaignData.NewsVisibility.PUBLIC);
        return type.displayName() + " declared against " + targetText + ". Casus Belli: “" + war.casusBelliName() + "”. War ID: " + war.id();
    }

    private static String declareSettlementWar(ServerPlayer player, CampaignData data, String extra, long day) {
        String[] split = extra == null ? new String[0] : extra.split("\\|", -1);
        if (split.length < 4) return "Settlement War declaration is incomplete.";
        String sourceId = SettlementData.normalizeId(split[1]);
        String targetId = SettlementData.normalizeId(split[2]);
        CampaignData.WarGoal goal = parseWarGoal(split[3], CampaignData.WarType.SETTLEMENT_WAR);
        String custom = split.length > 4 ? SettlementData.cleanText(split[4], "", 100) : "";
        SettlementData settlements = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord source = settlements.getSettlement(sourceId);
        SettlementData.SettlementRecord target = settlements.getSettlement(targetId);
        if (source == null || target == null) return "Select both your settlement and the target settlement.";
        if (source.getId().equals(target.getId())) return "A settlement cannot declare war on itself.";
        if (!canRepresentSettlement(player, source)) return "You do not hold political authority to declare war for " + source.getName() + ".";
        CampaignSystemsData extraData = CampaignSystemsData.get(player.serverLevel());
        if (extraData.settlementProtected(source.getId()) || extraData.settlementProtected(target.getId())
                || extraData.factionProtected(source.getFactionTeam()) || extraData.factionProtected(target.getFactionTeam())) {
            return "Protected admin settlements and factions cannot participate in wars.";
        }
        if (!source.getFactionTeam().isBlank() && !target.getFactionTeam().isBlank()
                && extraData.marriageAlliance(source.getFactionTeam(), target.getFactionTeam())) {
            return "These settlements belong to factions bound by a Marriage Alliance.";
        }
        boolean duplicate = data.getWars().stream().anyMatch(war -> war.status() == CampaignData.WarStatus.ACTIVE
                && war.type() == CampaignData.WarType.SETTLEMENT_WAR
                && ((sourceId.equals(war.attackerSettlement()) && targetId.equals(war.defenderSettlement()))
                || (targetId.equals(war.attackerSettlement()) && sourceId.equals(war.defenderSettlement()))));
        if (duplicate) return "Those settlements are already in an active Settlement War.";
        CampaignData.WarRecord war = data.createSettlementWar(sourceId, targetId, source.getFactionTeam(), target.getFactionTeam(), goal, custom, player.getUUID(), day);
        String cb = war.casusBelliName();
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR, "Settlement War declared",
                source.getName() + " declared war on " + target.getName() + ". Casus Belli: " + cb + ".",
                day, target.getFactionTeam(), targetId, CampaignData.NewsVisibility.PUBLIC);
        CampaignToastSender.settlementWar(player.getServer(), source, target, cb);
        CampaignSystemsManager.applyEmergencyMobilization(player.serverLevel(), target);
        return source.getName() + " declared a Settlement War on " + target.getName() + ". Casus Belli: " + cb + ". War ID: " + war.id();
    }

    private static String joinSettlementWar(ServerPlayer player, CampaignData data, String own, UUID warId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot commit the faction to a war.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || war.type() != CampaignData.WarType.SETTLEMENT_WAR) return "Select an active Settlement War.";
        if (!war.attackerFaction().isBlank() && war.attackerFaction().equals(war.defenderFaction()))
            return "This is an internal settlement conflict. The shared parent faction cannot formally join either side.";
        boolean attackerSide;
        if (own.equals(war.attackerFaction())) attackerSide = true;
        else if (own.equals(war.defenderFaction())) attackerSide = false;
        else return "Your faction is not the parent faction of either settlement.";
        if ((attackerSide && war.attackerFactionJoined()) || (!attackerSide && war.defenderFactionJoined())) return "Your faction has already joined this war.";

        boolean completesFactionWar = (attackerSide ? war.defenderFactionJoined() : war.attackerFactionJoined())
                && !war.attackerFaction().isBlank() && !war.defenderFaction().isBlank()
                && !war.attackerFaction().equals(war.defenderFaction());
        // The second parent joining is the moment the local conflict becomes faction-v-faction.
        // Verify Recruits can actually become ENEMY before committing that escalation to our save.
        if (completesFactionWar
                && !syncRelation(player.serverLevel(), war.attackerFaction(), war.defenderFaction(), CampaignData.TreatyState.WAR)) {
            return "War escalation aborted because Recruits did not confirm ENEMY relations in both directions.";
        }

        war.joinParentFaction(attackerSide);
        CampaignSystemsData.get(player.serverLevel()).setIgnoredWarIntervention(war.id(), own, false);
        data.setDirty();
        String side = attackerSide ? "attacking" : "defending";
        String settlementId = attackerSide ? war.attackerSettlement() : war.defenderSettlement();
        SettlementData.SettlementRecord settlement = SettlementData.get(player.serverLevel()).getSettlement(settlementId);
        String settlementName = settlement == null ? settlementId : settlement.getName();
        String escalation = war.escalationName();
        if (completesFactionWar) {
            CampaignData.DiplomacyRecord relation = data.relation(war.attackerFaction(), war.defenderFaction());
            relation.setState(CampaignData.TreatyState.WAR, day);
            changeRelationship(player.getServer(), data, war.attackerFaction(), war.defenderFaction(), -40);
        }
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR, "War escalated",
                factionName(own) + " joined the " + side + " settlement, escalating the conflict to " + escalation + ".",
                day, own, settlementId, CampaignData.NewsVisibility.PUBLIC);
        toastPoliticalSide(player.getServer(), war.attackerFaction(), war.attackerSettlement(), "WAR ESCALATED", factionName(own) + " joined " + settlementName + ". Conflict: " + escalation);
        toastPoliticalSide(player.getServer(), war.defenderFaction(), war.defenderSettlement(), "WAR ESCALATED", factionName(own) + " joined " + settlementName + ". Conflict: " + escalation);
        CampaignSystemsManager.applyEmergencyMobilization(player.serverLevel(), attackerSide ? war.defenderFaction() : war.attackerFaction());
        return "Your faction joined the war. Conflict is now " + escalation + ".";
    }

    private static String ignoreSettlementWar(ServerPlayer player, CampaignData data, String own, UUID warId) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot answer this settlement war.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || war.type() != CampaignData.WarType.SETTLEMENT_WAR) return "Select an active Settlement War.";
        if (!war.attackerFaction().isBlank() && war.attackerFaction().equals(war.defenderFaction()))
            return "This is an internal settlement conflict; there is no separate parent-faction intervention decision.";
        if (!own.equals(war.defenderFaction())) return "Only the defending settlement's parent faction may formally ignore the conflict.";
        if (war.defenderFactionJoined()) return "Your faction has already joined this war.";
        CampaignSystemsData.get(player.serverLevel()).setIgnoredWarIntervention(war.id(), own, true);
        String defenderSettlement = war.defenderSettlement();
        String response = factionName(own) + " has chosen not to formally intervene in this Settlement War.";
        CampaignToastSender.faction(player.getServer(), own, "WAR RESPONSE", "Your faction will not formally intervene. Aid or later escalation remain possible.");
        toastSettlementManagers(player.getServer(), defenderSettlement, "PARENT FACTION DECLINES", response);
        toastSettlementManagers(player.getServer(), war.attackerSettlement(), "WAR RESPONSE", response);
        return "Your faction will not intervene in this Settlement War. You may still send aid or join later if circumstances change.";
    }

    private static String sendWarAid(ServerPlayer player, CampaignData data, String own, UUID warId, String extra, long day) {
        if (!canManageDiplomacy(player, own, 2)) return "Your faction rank cannot authorize war aid.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "Select an active war.";
        if (war.type() == CampaignData.WarType.SETTLEMENT_WAR && !war.attackerFaction().isBlank()
                && war.attackerFaction().equals(war.defenderFaction()))
            return "The shared parent faction cannot use faction war aid to choose a side in its own internal settlement conflict.";
        if (!own.equals(war.attackerFaction()) && !own.equals(war.defenderFaction())) return "Only a parent faction may send settlement aid through this panel.";
        String targetSettlement = own.equals(war.attackerFaction()) ? war.attackerSettlement() : war.defenderSettlement();
        if (targetSettlement.isBlank()) return "This war has no settlement aid target.";
        String[] split = extra == null ? new String[0] : extra.split("\\|", -1);
        int manpower = parsePositive(split, 0); int treasury = parsePositive(split, 1); int food = parsePositive(split, 2);
        if (split.length < 6) return "Your client is using an outdated war-aid packet. Reconnect with the current Settlements build.";
        boolean manpowerRecurring = Boolean.parseBoolean(split[3]);
        boolean treasuryRecurring = Boolean.parseBoolean(split[4]);
        boolean foodRecurring = Boolean.parseBoolean(split[5]);
        if (manpower + treasury + food <= 0) return "Enter at least one aid amount.";
        SettlementData.SettlementRecord target = SettlementData.get(player.serverLevel()).getSettlement(targetSettlement);
        if (target == null) return "The aided settlement no longer exists.";
        String payment = transferFactionAid(player.serverLevel(), own, target, manpower, treasury, food);
        if (payment != null) return payment;
        CampaignSystemsData extraData = CampaignSystemsData.get(player.serverLevel());
        if (manpowerRecurring && manpower > 0) extraData.addWarAid(war.id(),
                new CampaignSystemsData.AidRecord(UUID.randomUUID(), own, targetSettlement, manpower, 0, 0, true, day + 1));
        if (treasuryRecurring && treasury > 0) extraData.addWarAid(war.id(),
                new CampaignSystemsData.AidRecord(UUID.randomUUID(), own, targetSettlement, 0, treasury, 0, true, day + 1));
        if (foodRecurring && food > 0) extraData.addWarAid(war.id(),
                new CampaignSystemsData.AidRecord(UUID.randomUUID(), own, targetSettlement, 0, 0, food, true, day + 1));
        // Supporting one of your settlements is a small positive Reliability signal. It is
        // intentionally awarded per aid order rather than per resource/daily tick to prevent
        // trivial recurring transfers from becoming a reputation farm.
        changeReliability(player.serverLevel(), data, own, 1);
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.NORMAL, "War aid sent",
                factionName(own) + " sent aid to " + target.getName() + ".", day, own, targetSettlement, CampaignData.NewsVisibility.PUBLIC);
        if (target.getFactionTeam().isBlank()) CampaignToastSender.settlementManagers(player.getServer(), target.getId(), "AID RECEIVED", factionName(own) + " sent aid to " + target.getName() + ".");
        else CampaignToastSender.faction(player.getServer(), target.getFactionTeam(), "AID RECEIVED", factionName(own) + " sent aid to " + target.getName() + ".");
        java.util.List<String> recurringLabels = new java.util.ArrayList<>();
        if (manpowerRecurring && manpower > 0) recurringLabels.add("manpower");
        if (treasuryRecurring && treasury > 0) recurringLabels.add("treasury");
        if (foodRecurring && food > 0) recurringLabels.add("food");
        return "Aid delivered to " + target.getName() + (recurringLabels.isEmpty() ? "."
                : ". Daily: " + String.join(", ", recurringLabels) + ".");
    }

    private static String cancelWarAid(ServerPlayer player, CampaignData data, String own, UUID warId, UUID aidId, long day) {
        if (!canManageDiplomacy(player, own, 2)) return "Your faction rank cannot cancel war aid.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "That war is no longer active.";
        if (aidId == null) return "Select an active recurring aid commitment to cancel.";
        CampaignSystemsData extraData = CampaignSystemsData.get(player.serverLevel());
        CampaignSystemsData.AidRecord aid = extraData.warAid(war.id()).stream()
                .filter(value -> aidId.equals(value.id())).findFirst().orElse(null);
        if (aid == null || !aid.recurring()) return "That recurring aid commitment no longer exists.";
        if (!own.equals(aid.fromFaction())) return "You may only cancel your own faction's aid commitments.";
        SettlementData.SettlementRecord target = SettlementData.get(player.serverLevel()).getSettlement(aid.targetSettlement());
        if (!extraData.removeWarAid(war.id(), aid.id())) return "That recurring aid commitment no longer exists.";

        String targetName = target == null ? aid.targetSettlement() : target.getName();
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MINOR, "War aid cancelled",
                factionName(own) + " cancelled recurring aid to " + targetName + ".", day, own, aid.targetSettlement(), CampaignData.NewsVisibility.PUBLIC);
        CampaignToastSender.faction(player.getServer(), own, "AID CANCELLED", "Recurring aid to " + targetName + " has been cancelled.");
        if (target != null) {
            if (target.getFactionTeam().isBlank()) CampaignToastSender.settlementManagers(player.getServer(), target.getId(), "AID CANCELLED", factionName(own) + " cancelled recurring aid to your settlement.");
            else CampaignToastSender.faction(player.getServer(), target.getFactionTeam(), "AID CANCELLED", factionName(own) + " cancelled recurring aid to " + targetName + ".");
        }
        return "Recurring aid to " + targetName + " cancelled.";
    }

    private static int parsePositive(String[] split, int index) {
        if (index >= split.length) return 0;
        try { return Math.max(0, Integer.parseInt(split[index])); } catch (NumberFormatException ignored) { return 0; }
    }

    static String transferFactionAid(ServerLevel level, String faction, SettlementData.SettlementRecord target, int manpower, int treasury, int food) {
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        if (extra.treasuryBalance(faction) < treasury) return "Your State Treasury does not have enough funds.";
        List<SettlementData.SettlementRecord> sources = SettlementData.get(level).settlementsForFaction(faction).stream()
                .filter(source -> !source.getId().equals(target.getId())).toList();
        int availableMp = sources.stream().mapToInt(SettlementData.SettlementRecord::getAvailableManpower).sum();
        int availableFood = sources.stream().mapToInt(SettlementData.SettlementRecord::getFood).sum();
        int recipientRoom = Math.max(0, target.getMaximumManpower() - target.getUsedManpower()
                - target.getLevyReservedManpower() - target.getAvailableManpower());
        if (availableMp < manpower) return "Your faction settlements do not have enough available manpower.";
        if (manpower > recipientRoom) return "The aided settlement only has room for " + recipientRoom + " more manpower.";
        if (availableFood < food) return "Your faction settlements do not have enough food.";
        extra.spendTreasury(faction, treasury);
        int mpLeft = manpower, foodLeft = food;
        for (SettlementData.SettlementRecord source : sources) {
            int mpTake = Math.min(mpLeft, source.getAvailableManpower());
            if (mpTake > 0) { source.spendAvailableManpower(mpTake); mpLeft -= mpTake; }
            int foodTake = Math.min(foodLeft, source.getFood());
            if (foodTake > 0) { source.spendFood(foodTake); foodLeft -= foodTake; }
            if (mpLeft <= 0 && foodLeft <= 0) break;
        }
        target.changeAvailableManpower(manpower);
        target.changeFood(food);
        StateTreasury.change(level, target, treasury);
        return null;
    }

    private static CampaignData.WarType parseWarType(String raw) {
        try { return CampaignData.WarType.valueOf(raw.toUpperCase(Locale.ROOT).replace(' ', '_')); }
        catch (IllegalArgumentException ignored) { return CampaignData.WarType.FACTION_WAR; }
    }

    private static CampaignData.WarGoal parseWarGoal(String raw, CampaignData.WarType type) {
        try {
            CampaignData.WarGoal goal = CampaignData.WarGoal.valueOf(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
            return goal.appliesTo(type) ? goal : CampaignData.WarGoal.defaultFor(type);
        } catch (IllegalArgumentException ignored) {
            return CampaignData.WarGoal.defaultFor(type);
        }
    }

    /** Returns the faction that is actually a belligerent on this side. Settlement Wars keep
     * their parent faction ids for observer/escalation purposes, but those parents are not war
     * parties until they explicitly Join War. */
    private static String participatingFaction(CampaignData.WarRecord war, boolean attackerSide) {
        if (war == null) return "";
        if (war.type() != CampaignData.WarType.SETTLEMENT_WAR) return attackerSide ? war.attackerFaction() : war.defenderFaction();
        if (attackerSide ? war.attackerFactionJoined() : war.defenderFactionJoined())
            return attackerSide ? war.attackerFaction() : war.defenderFaction();
        return "";
    }

    private static boolean factionParticipatesOnSide(CampaignData.WarRecord war, String factionId, boolean attackerSide) {
        if (war == null || factionId == null || factionId.isBlank()) return false;
        String parent = participatingFaction(war, attackerSide);
        return factionId.equals(parent) || (attackerSide ? war.supportingAttackers() : war.supportingDefenders()).contains(factionId);
    }

    /** Principal belligerent faction only. Supporting allies may fight and send aid, but they cannot bind
     * either side to peace terms. */
    private static boolean factionIsPrincipalOnSide(CampaignData.WarRecord war, String factionId, boolean attackerSide) {
        if (war == null || factionId == null || factionId.isBlank()) return false;
        String principal = participatingFaction(war, attackerSide);
        return !principal.isBlank() && factionId.equals(principal);
    }

    /**
     * Ordinary diplomacy must be based on the player's real political authority, not operator permission.
     * Operators can administer every settlement through admin tools, but that must never make them a
     * belligerent on both sides of a player war or turn every foreign settlement into "your settlement".
     */
    static boolean canRepresentSettlement(ServerPlayer player, SettlementData.SettlementRecord settlement) {
        if (player == null || settlement == null) return false;
        SettlementData.SettlementRole role = settlement.getRole(player.getUUID());
        if (!role.canManage()) return false;
        String playerFaction = factionId(player);
        String settlementFaction = settlement.getFactionTeam() == null ? "" : settlement.getFactionTeam();
        if (settlementFaction.isBlank()) return playerFaction.isBlank();
        return settlementFaction.equals(playerFaction);
    }

    /** Identifies a structured-peace party without relying on blank settlement ids in faction wars. */
    static boolean peacePartyIsSide(CampaignData.WarRecord war, String factionId, String settlementId, boolean attackerSide) {
        if (war == null) return false;
        boolean hasFaction = factionId != null && !factionId.isBlank();
        boolean hasSettlement = settlementId != null && !settlementId.isBlank();
        if (!hasFaction && !hasSettlement) return false;
        if (hasFaction && !factionIsPrincipalOnSide(war, factionId, attackerSide)) return false;
        if (hasSettlement) {
            String expected = attackerSide ? war.attackerSettlement() : war.defenderSettlement();
            if (expected == null || expected.isBlank() || !settlementId.equals(expected)) return false;
        }
        return true;
    }


    private static String offerPeaceTerms(ServerPlayer player, CampaignData data, UUID warId, String encoded, long day) {
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "That war is not active.";
        SettlementData settlements = SettlementData.get(player.serverLevel());
        String ownFaction = factionId(player);
        SettlementData.SettlementRecord attackerSettlementRecord = settlements.getSettlement(war.attackerSettlement());
        SettlementData.SettlementRecord defenderSettlementRecord = settlements.getSettlement(war.defenderSettlement());
        boolean managesAttackerSettlement = attackerSettlementRecord != null && canRepresentSettlement(player, attackerSettlementRecord);
        boolean managesDefenderSettlement = defenderSettlementRecord != null && canRepresentSettlement(player, defenderSettlementRecord);
        boolean attackerByFaction = factionIsPrincipalOnSide(war, ownFaction, true);
        boolean defenderByFaction = factionIsPrincipalOnSide(war, ownFaction, false);
        boolean attacker = attackerByFaction || managesAttackerSettlement;
        boolean defender = defenderByFaction || managesDefenderSettlement;
        if (attacker && defender) return "You hold political authority on both belligerent settlements. Remove the conflicting settlement role before negotiating peace.";
        if (!attacker && !defender) return "You are not an actual belligerent in this war. A parent faction must Join War before negotiating on behalf of its settlement.";
        boolean actingAsFaction = attacker ? attackerByFaction : defenderByFaction;
        if (actingAsFaction && !ownFaction.isBlank() && !canManageDiplomacy(player, ownFaction, 3))
            return "Your faction rank cannot negotiate peace. Administrator permission does not count as political authority; use admin recovery tools for administrative intervention.";
        String proposerFaction = actingAsFaction ? ownFaction : "";
        String targetFaction = participatingFaction(war, !attacker);
        String proposerSettlement = attacker ? war.attackerSettlement() : war.defenderSettlement();
        String targetSettlement = attacker ? war.defenderSettlement() : war.attackerSettlement();
        List<CampaignSystemsData.PeaceTerm> terms = parsePeaceTerms(encoded);
        UUID proposal = UUID.randomUUID();
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        CampaignSystemsData.PeaceOfferRecord draft = new CampaignSystemsData.PeaceOfferRecord(proposal, war.id(), proposerFaction, targetFaction,
                proposerSettlement, targetSettlement, terms, System.currentTimeMillis());
        String validationError = validatePeaceTerms(player.serverLevel(), draft, war);
        if (validationError != null) return "Peace treaty could not be offered: " + validationError + ".";
        int termCost = peaceTermsWarScoreCost(player.serverLevel(), terms);
        extra.putPeaceOffer(draft);
        String targetLabel = !targetFaction.isBlank() ? factionName(targetFaction) : settlementName(player.serverLevel(), targetSettlement);
        toastPoliticalSide(player.getServer(), targetFaction, targetSettlement, "PEACE OFFER RECEIVED",
                (proposerFaction.isBlank() ? settlementName(player.serverLevel(), proposerSettlement) : factionName(proposerFaction))
                        + " proposed peace with " + terms.size() + " term(s).");
        data.addNews(CampaignData.NewsCategory.DIPLOMACY, CampaignData.NewsImportance.NORMAL, "Peace offer sent",
                (proposerFaction.isBlank() ? settlementName(player.serverLevel(), proposerSettlement) : factionName(proposerFaction)) + " sent peace terms to " + targetLabel + ".",
                day, targetFaction, targetSettlement, CampaignData.NewsVisibility.FACTION);
        return "Peace treaty sent to " + targetLabel + " with " + terms.size() + " term(s). War-score cost: " + termCost + ".";
    }

    private static List<CampaignSystemsData.PeaceTerm> parsePeaceTerms(String encoded) {
        List<CampaignSystemsData.PeaceTerm> terms = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) return terms;
        for (String raw : encoded.split(";")) {
            if (terms.size() >= 48) break;
            String[] p = raw.split("~", -1); if (p.length < 5) continue;
            int amount = parseInt(p[2], 0, 1_000_000); int duration = parseInt(p[3], 0, 3650);
            String type = p[0].toUpperCase(Locale.ROOT); String target = SettlementData.cleanText(p[1], "", 80); String text = SettlementData.cleanText(p[4], "", 120);
            if (List.of("TREASURY","MANPOWER","REPARATIONS","NAP","CONVERT_FAITH","TRANSFER_SETTLEMENT","TRANSFER_CLAIM","RENOUNCE_CLAIM","RECOGNIZE_INDEPENDENCE").contains(type))
                terms.add(new CampaignSystemsData.PeaceTerm(type, target, amount, duration, text));
        }
        return List.copyOf(terms);
    }

    private static String resolvePeaceTerms(ServerPlayer player, CampaignData data, UUID offerId, boolean accept, long day) {
        CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
        CampaignSystemsData.PeaceOfferRecord offer = offerId == null ? null : extra.peaceOffer(offerId);
        if (offer == null) return "That peace offer is no longer available.";
        CampaignData.WarRecord war = offer.warId() == null ? null : data.getWar(offer.warId());
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) { extra.removePeaceOffer(offerId); return "That war has already ended."; }
        String own = factionId(player);
        SettlementData.SettlementRecord targetSettlement = SettlementData.get(player.serverLevel()).getSettlement(offer.targetSettlement());
        boolean offerTargetsAttacker = peacePartyIsSide(war, offer.targetFaction(), offer.targetSettlement(), true);
        boolean offerTargetsDefender = peacePartyIsSide(war, offer.targetFaction(), offer.targetSettlement(), false);
        if (offerTargetsAttacker == offerTargetsDefender) return "That peace offer no longer identifies exactly one target side.";
        boolean targetFactionActuallyParticipates = !offer.targetFaction().isBlank()
                && factionIsPrincipalOnSide(war, offer.targetFaction(), offerTargetsAttacker);
        boolean authorized = targetFactionActuallyParticipates
                ? offer.targetFaction().equals(own) && canManageDiplomacy(player, own, 3)
                : offer.targetFaction().isBlank() && targetSettlement != null && canRepresentSettlement(player, targetSettlement);
        if (!authorized) return "You are not authorized to answer those peace terms.";
        int score = extra.warScore(war.id());
        boolean proposerAttacker = peacePartyIsSide(war, offer.proposerFaction(), offer.proposerSettlement(), true);
        boolean proposerDefender = peacePartyIsSide(war, offer.proposerFaction(), offer.proposerSettlement(), false);
        if (proposerAttacker == proposerDefender) return "That peace offer no longer identifies exactly one proposing side.";
        int enforcementScore = proposerAttacker ? score : -score;
        int termCost = peaceTermsWarScoreCost(player.serverLevel(), offer.terms());
        boolean compulsory = enforcementScore >= 90 && termCost <= enforcementScore;
        if (!accept && compulsory) return "Your side has capitulated and cannot reject peace terms within the dominant side's war-score budget.";
        if (!accept) {
            extra.removePeaceOffer(offerId);
            toastPoliticalSide(player.getServer(), offer.proposerFaction(), offer.proposerSettlement(),
                    "PEACE REJECTED", "Your peace terms were rejected.");
            return "Peace terms rejected.";
        }
        String error = validatePeaceTerms(player.serverLevel(), offer, war);
        if (error != null) return "Peace treaty could not be applied: " + error;
        applyPeaceTerms(player.serverLevel(), offer, war, day);
        data.endWar(war.id(), day, compulsory ? "Capitulation peace terms enforced." : "Negotiated peace terms accepted.");
        extra.clearWarInterventionState(war.id());
        synchronizeWarPeace(player.serverLevel(), data, war, day);
        extra.removeWarAid(war.id()); extra.removePeaceOffer(offerId);
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR, "Peace treaty concluded",
                "The war ended under negotiated peace terms.", day, "", offer.targetSettlement(), CampaignData.NewsVisibility.PUBLIC);
        toastPoliticalSide(player.getServer(), offer.proposerFaction(), offer.proposerSettlement(),
                "PEACE ACCEPTED", "The opposing side accepted your peace treaty.");
        toastPoliticalSide(player.getServer(), offer.targetFaction(), offer.targetSettlement(),
                "PEACE CONCLUDED", "The peace treaty is now in force.");
        return "Peace treaty accepted and applied.";
    }

    static int peaceTermsWarScoreCost(ServerLevel level, List<CampaignSystemsData.PeaceTerm> terms) {
        if (terms == null || terms.isEmpty()) return 0;
        long total = 0L;
        for (CampaignSystemsData.PeaceTerm term : terms) total += peaceTermWarScoreCost(level, term);
        return (int)Math.min(999L, total);
    }

    private static int peaceTermWarScoreCost(ServerLevel level, CampaignSystemsData.PeaceTerm term) {
        if (term == null) return 0;
        return switch (term.type()) {
            case "TREASURY" -> ceilDiv(term.amount(), 100);
            case "MANPOWER" -> ceilDiv(term.amount(), 5);
            case "REPARATIONS" -> (int)Math.min(100L, ceilDiv((long)term.amount() * (long)term.durationDays(), 100L));
            case "NAP" -> Math.min(20, ceilDiv(term.durationDays(), 7));
            case "CONVERT_FAITH" -> 25;
            case "TRANSFER_CLAIM" -> 8;
            case "RENOUNCE_CLAIM" -> 6;
            case "TRANSFER_SETTLEMENT" -> settlementPeaceCost(level, term.targetId(), false);
            case "RECOGNIZE_INDEPENDENCE" -> settlementPeaceCost(level, term.targetId(), true);
            default -> 0;
        };
    }

    private static int settlementPeaceCost(ServerLevel level, String settlementId, boolean independence) {
        SettlementData.SettlementRecord settlement = level == null ? null : SettlementData.get(level).getSettlement(settlementId);
        int base = settlement == null ? 25 : switch (settlement.getRank()) {
            case MAJOR_CITY -> 60;
            case CITY -> 50;
            case TOWN -> 40;
            case VILLAGE -> 25;
        };
        if (settlement != null && settlement.isCapital()) base += 5;
        return independence ? Math.min(65, base + 5) : base;
    }

    private static int ceilDiv(int value, int divisor) { return value <= 0 ? 0 : (value + divisor - 1) / divisor; }
    private static long ceilDiv(long value, long divisor) { return value <= 0L ? 0L : (value + divisor - 1L) / divisor; }

    private static String validatePeaceTerms(ServerLevel level, CampaignSystemsData.PeaceOfferRecord offer, CampaignData.WarRecord war) {
        CampaignSystemsData extra = CampaignSystemsData.get(level);
        SettlementData settlements = SettlementData.get(level);
        boolean proposerAttacker = peacePartyIsSide(war, offer.proposerFaction(), offer.proposerSettlement(), true);
        boolean proposerDefender = peacePartyIsSide(war, offer.proposerFaction(), offer.proposerSettlement(), false);
        if (proposerAttacker == proposerDefender) return "the peace offer no longer identifies exactly one side of the war";
        boolean targetAttacker = peacePartyIsSide(war, offer.targetFaction(), offer.targetSettlement(), true);
        boolean targetDefender = peacePartyIsSide(war, offer.targetFaction(), offer.targetSettlement(), false);
        if (targetAttacker == targetDefender || proposerAttacker == targetAttacker)
            return "the peace offer no longer targets the opposing war side";
        if (!offer.proposerFaction().isBlank() && !factionIsPrincipalOnSide(war, offer.proposerFaction(), proposerAttacker))
            return "the proposing faction is not the principal belligerent on that side";
        if (!offer.targetFaction().isBlank() && !factionIsPrincipalOnSide(war, offer.targetFaction(), targetAttacker))
            return "the target faction is not the principal belligerent on that side";

        String winnerFaction = offer.proposerFaction();
        String loserFaction = offer.targetFaction();
        SettlementData.SettlementRecord winnerSettlement = settlements.getSettlement(offer.proposerSettlement());
        SettlementData.SettlementRecord loserSettlement = settlements.getSettlement(offer.targetSettlement());
        int treasuryNeed = 0, manpowerNeed = 0;
        Set<String> transferredSettlements = new HashSet<>();
        Set<String> releasedSettlements = new HashSet<>();
        Set<String> transferredClaims = new HashSet<>();
        Set<String> renouncedClaims = new HashSet<>();
        for (CampaignSystemsData.PeaceTerm term : offer.terms()) {
            if ("TRANSFER_SETTLEMENT".equals(term.type()) && !transferredSettlements.add(term.targetId()))
                return "the same settlement is listed for transfer more than once";
            if ("RECOGNIZE_INDEPENDENCE".equals(term.type()) && !releasedSettlements.add(term.targetId()))
                return "the same settlement is listed for independence more than once";
            if ("TRANSFER_CLAIM".equals(term.type()) && !transferredClaims.add(term.targetId()))
                return "the same linked claim is listed for transfer more than once";
            if ("RENOUNCE_CLAIM".equals(term.type()) && !renouncedClaims.add(term.targetId()))
                return "the same linked claim is listed for renunciation more than once";
        }
        for (String settlementId : transferredSettlements) if (releasedSettlements.contains(settlementId))
            return "a settlement cannot be transferred to the victor and released as independent in the same treaty";
        for (String claimId : transferredClaims) if (renouncedClaims.contains(claimId))
            return "a claim cannot be both transferred and renounced in the same treaty";

        for (CampaignSystemsData.PeaceTerm term : offer.terms()) {
            switch (term.type()) {
                case "TREASURY" -> treasuryNeed += term.amount();
                case "MANPOWER" -> manpowerNeed += term.amount();
                case "REPARATIONS" -> {
                    if (winnerFaction.isBlank() || loserFaction.isBlank()) return "recurring reparations require two factions";
                }
                case "NAP" -> {
                    if (winnerFaction.isBlank() || loserFaction.isBlank()) return "a forced NAP requires two factions";
                }
                case "CONVERT_FAITH" -> {
                    if (!CampaignDefinitions.isValidReligion(term.text())) return "the demanded faith is not a valid configured religion";
                }
                case "TRANSFER_SETTLEMENT" -> {
                    SettlementData.SettlementRecord transferred = settlements.getSettlement(term.targetId());
                    if (transferred == null) return "a transferred settlement no longer exists";
                    if (!settlementBelongsToLosingSide(transferred, loserFaction, loserSettlement)) {
                        return transferred.getName() + " does not belong to the side receiving these demands";
                    }
                    if (war.type() == CampaignData.WarType.SETTLEMENT_WAR && loserSettlement != null
                            && !transferred.getId().equals(loserSettlement.getId())) {
                        return "a Settlement War may only transfer its defending/negotiating settlement, not unrelated territory";
                    }
                    UUID recipientOwner = treatyRecipientOwner(level, winnerFaction, winnerSettlement, war);
                    if (recipientOwner == null) return "the winning side has no valid ruler to receive the transferred settlement";
                    if (winnerFaction.isBlank() && !recipientOwner.equals(transferred.getOwnerId())) {
                        long independentHoldings = settlements.getSettlements().stream()
                                .filter(value -> recipientOwner.equals(value.getOwnerId()) && value.getFactionTeam().isBlank()
                                        && !value.getId().equals(transferred.getId())).count();
                        if (independentHoldings >= 2) return "the winning ruler already owns the maximum two independent settlements";
                    }
                }
                case "TRANSFER_CLAIM" -> {
                    UUID claimId;
                    try { claimId = UUID.fromString(term.targetId()); }
                    catch (IllegalArgumentException e) { return "an invalid claim was requested"; }
                    SettlementData.SettlementRecord source = settlementContainingClaim(settlements, claimId);
                    if (source == null) return "the demanded claim is no longer linked to a settlement";
                    if (!settlementBelongsToLosingSide(source, loserFaction, loserSettlement)) {
                        return "the demanded claim is not controlled by the side receiving these demands";
                    }
                    SettlementData.SettlementRecord destination = settlements.getSettlement(term.text());
                    if (destination == null) return "the destination settlement for a transferred claim no longer exists";
                    if (!settlementBelongsToWinningSide(destination, winnerFaction, winnerSettlement)) {
                        return "the claim destination is not controlled by the side proposing these terms";
                    }
                    if (source.getId().equals(destination.getId())) return "a claim cannot be transferred back into the same settlement";
                }
                case "RENOUNCE_CLAIM" -> {
                    UUID claimId;
                    try { claimId = UUID.fromString(term.targetId()); }
                    catch (IllegalArgumentException e) { return "an invalid claim was requested for renunciation"; }
                    SettlementData.SettlementRecord source = settlementContainingClaim(settlements, claimId);
                    if (source == null) return "the claim requested for renunciation is no longer linked to a settlement";
                    if (!settlementBelongsToLosingSide(source, loserFaction, loserSettlement))
                        return "the claim requested for renunciation is not controlled by the side receiving these demands";
                }
                case "RECOGNIZE_INDEPENDENCE" -> {
                    SettlementData.SettlementRecord released = settlements.getSettlement(term.targetId());
                    if (released == null) return "the settlement requested for independence no longer exists";
                    if (loserFaction.isBlank() || !loserFaction.equals(released.getFactionTeam())) {
                        return "the side receiving these demands does not rule the settlement requested for independence";
                    }
                    if (released.getOwnerId() != null) {
                        long independentHoldings = settlements.getSettlements().stream()
                                .filter(value -> released.getOwnerId().equals(value.getOwnerId()) && value.getFactionTeam().isBlank()
                                        && !value.getId().equals(released.getId())).count();
                        if (independentHoldings >= 2) return "that settlement's ruler already owns the maximum two independent settlements";
                    }
                }
                default -> { }
            }
        }

        int availableTreasury = loserSettlement != null ? StateTreasury.balance(level, loserSettlement) : !loserFaction.isBlank() ? extra.treasuryBalance(loserFaction) : 0;
        int availableMp = !loserFaction.isBlank() ? settlements.settlementsForFaction(loserFaction).stream().mapToInt(SettlementData.SettlementRecord::getAvailableManpower).sum()
                : loserSettlement == null ? 0 : loserSettlement.getAvailableManpower();
        if (treasuryNeed > availableTreasury) return "the losing side no longer has the demanded treasury";
        if (manpowerNeed > availableMp) return "the losing side no longer has the demanded manpower";
        return null;
    }

    private static SettlementData.SettlementRecord settlementContainingClaim(SettlementData settlements, UUID claimId) {
        if (claimId == null) return null;
        return settlements.getSettlements().stream()
                .filter(value -> value.getClaimLinks().stream().anyMatch(link -> claimId.equals(link.id())))
                .findFirst().orElse(null);
    }

    private static boolean settlementBelongsToLosingSide(SettlementData.SettlementRecord settlement, String loserFaction,
                                                          SettlementData.SettlementRecord loserSettlement) {
        if (settlement == null) return false;
        if (loserFaction != null && !loserFaction.isBlank()) return loserFaction.equals(settlement.getFactionTeam());
        return loserSettlement != null && loserSettlement.getId().equals(settlement.getId());
    }

    private static boolean settlementBelongsToWinningSide(SettlementData.SettlementRecord settlement, String winnerFaction,
                                                           SettlementData.SettlementRecord winnerSettlement) {
        if (settlement == null) return false;
        if (winnerFaction != null && !winnerFaction.isBlank()) return winnerFaction.equals(settlement.getFactionTeam());
        return winnerSettlement != null && winnerSettlement.getId().equals(settlement.getId());
    }

    private static void applyPeaceTerms(ServerLevel level, CampaignSystemsData.PeaceOfferRecord offer, CampaignData.WarRecord war, long day) {
        CampaignSystemsData extra = CampaignSystemsData.get(level); SettlementData settlements = SettlementData.get(level);
        String winnerFaction = offer.proposerFaction(), loserFaction = offer.targetFaction();
        SettlementData.SettlementRecord winnerSettlement = settlements.getSettlement(offer.proposerSettlement());
        if (winnerSettlement == null && !winnerFaction.isBlank()) winnerSettlement = settlements.settlementsForFaction(winnerFaction).stream().findFirst().orElse(null);
        SettlementData.SettlementRecord loserSettlement = settlements.getSettlement(offer.targetSettlement());
        UUID recipientOwner = treatyRecipientOwner(level, winnerFaction, winnerSettlement, war);
        for (CampaignSystemsData.PeaceTerm term : offer.terms()) {
            switch (term.type()) {
                case "TREASURY" -> {
                    takeTreasury(level, extra, loserFaction, loserSettlement, term.amount());
                    giveTreasury(level, extra, winnerFaction, winnerSettlement, term.amount());
                }
                case "MANPOWER" -> {
                    int moved = takeManpower(settlements, loserFaction, loserSettlement, term.amount());
                    if (winnerSettlement != null && moved > 0) {
                        extra.addForeignManpower(winnerSettlement.getId(), moved);
                        // Foreign manpower is a temporary recruitable pool. It deliberately does NOT increase the
                        // settlement's native maximum manpower, and the social penalty persists until the pool is used.
                        extra.addModifier(winnerSettlement.getId(), CampaignSystemsManager.standardModifier(level, winnerSettlement.getId(), "foreign_soldiers", 0L));
                    }
                }
                case "REPARATIONS" -> { if (!loserFaction.isBlank() && !winnerFaction.isBlank() && term.amount() > 0 && term.durationDays() > 0) extra.addReparation(new CampaignSystemsData.ReparationRecord(UUID.randomUUID(), loserFaction, winnerFaction, term.amount(), term.durationDays(), day + 1)); }
                case "NAP" -> { if (!winnerFaction.isBlank() && !loserFaction.isBlank() && term.durationDays() > 0) { dataRelationNap(level, winnerFaction, loserFaction, day); extra.setForcedNap(winnerFaction, loserFaction, day + term.durationDays()); } }
                case "CONVERT_FAITH" -> convertSideFaith(level, loserFaction, loserSettlement, term.text());
                case "TRANSFER_SETTLEMENT" -> transferSettlementByTreaty(level, term.targetId(), winnerFaction, recipientOwner);
                case "TRANSFER_CLAIM" -> transferClaimByTreaty(level, term.targetId(), term.text());
                case "RENOUNCE_CLAIM" -> renounceClaimByTreaty(level, term.targetId());
                case "RECOGNIZE_INDEPENDENCE" -> releaseSettlementByTreaty(settlements.getSettlement(term.targetId()));
            }
        }
    }

    private static void takeTreasury(ServerLevel level, CampaignSystemsData extra, String faction, SettlementData.SettlementRecord settlement, int amount) {
        if (amount <= 0) return;
        if (settlement != null) StateTreasury.change(level, settlement, -amount);
        else if (!faction.isBlank()) extra.changeTreasury(faction, -amount);
    }
    private static void giveTreasury(ServerLevel level, CampaignSystemsData extra, String faction, SettlementData.SettlementRecord settlement, int amount) {
        if (amount <= 0) return;
        if (settlement != null) StateTreasury.change(level, settlement, amount);
        else if (!faction.isBlank()) extra.changeTreasury(faction, amount);
    }
    private static int takeManpower(SettlementData settlements, String faction, SettlementData.SettlementRecord settlement, int amount) {
        int left = Math.max(0, amount); if (!faction.isBlank()) {
            for (SettlementData.SettlementRecord s : settlements.settlementsForFaction(faction)) { int take = Math.min(left, s.getAvailableManpower()); if (take > 0) { s.spendAvailableManpower(take); left -= take; } if (left <= 0) break; }
        } else if (settlement != null) { int take = Math.min(left, settlement.getAvailableManpower()); settlement.spendAvailableManpower(take); left -= take; }
        return amount - left;
    }
    private static void dataRelationNap(ServerLevel level, String a, String b, long day) { CampaignData.get(level).relation(a,b).setState(CampaignData.TreatyState.NON_AGGRESSION, day); syncRelation(level,a,b,CampaignData.TreatyState.NON_AGGRESSION); }
    private static void convertSideFaith(ServerLevel level, String faction, SettlementData.SettlementRecord settlement, String faith) {
        if (!CampaignDefinitions.isValidReligion(faith)) return; UUID ruler = null;
        if (!faction.isBlank() && FactionEvents.recruitsFactionManager != null) { RecruitsFaction f = FactionEvents.recruitsFactionManager.getFactionByStringID(faction); if (f != null) ruler = f.getTeamLeaderUUID(); }
        if (ruler == null && settlement != null) ruler = settlement.getOwnerId();
        SettlementData.CharacterRecord character = ruler == null ? null : SettlementData.get(level).getCharacter(ruler); if (character != null) character.setFaith(faith);
    }
    private static UUID treatyRecipientOwner(ServerLevel level, String winnerFaction, SettlementData.SettlementRecord winnerSettlement,
                                             CampaignData.WarRecord war) {
        if (war != null && war.type() == CampaignData.WarType.SETTLEMENT_WAR && winnerSettlement != null && winnerSettlement.getOwnerId() != null)
            return winnerSettlement.getOwnerId();
        if (winnerFaction != null && !winnerFaction.isBlank() && FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(winnerFaction);
            if (faction != null && faction.getTeamLeaderUUID() != null) return faction.getTeamLeaderUUID();
        }
        return winnerSettlement == null ? null : winnerSettlement.getOwnerId();
    }

    private static void transferSettlementByTreaty(ServerLevel level, String settlementId, String winnerFaction, UUID recipientOwner) {
        SettlementData.SettlementRecord settlement = SettlementData.get(level).getSettlement(settlementId); if (settlement == null || recipientOwner == null) return;
        UUID previousOwner = settlement.getOwnerId();
        settlement.setFactionTeam(winnerFaction);
        if (!recipientOwner.equals(previousOwner)) {
            settlement.setOwner(recipientOwner);
            if (previousOwner != null) settlement.setRole(previousOwner, SettlementData.SettlementRole.OUTSIDER);
        }
        // Physical city tier survives political conquest; only the political capital designation is lost.
        settlement.setCapital(false);
        if (!winnerFaction.isBlank() && FactionEvents.recruitsFactionManager != null && ClaimEvents.recruitsClaimManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(winnerFaction);
            if (faction != null) for (SettlementData.ClaimLink link : settlement.getClaimLinks()) {
                RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(link.id());
                if (claim != null) {
                    claim.setOwnerFaction(faction);
                    if (faction.getTeamLeaderUUID() != null) claim.setPlayer(new RecruitsPlayerInfo(faction.getTeamLeaderUUID(), faction.getTeamLeaderName(), faction));
                    ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
                }
            }
            ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(level);
        }
        settlement.addChronicle("Settlement transferred by peace treaty to " + (winnerFaction.isBlank() ? "the opposing ruler" : factionName(winnerFaction)) + ".");
    }
    private static void releaseSettlementByTreaty(SettlementData.SettlementRecord settlement) {
        if (settlement == null) return;
        settlement.setFactionTeam("");
        settlement.setCapital(false);
        settlement.addChronicle("Independence recognized by peace treaty.");
    }

    private static void transferClaimByTreaty(ServerLevel level, String claimIdRaw, String destinationSettlementId) {
        UUID claimId; try { claimId = UUID.fromString(claimIdRaw); } catch (IllegalArgumentException e) { return; }
        SettlementData data = SettlementData.get(level); SettlementData.SettlementRecord destination = data.getSettlement(destinationSettlementId); if (destination == null) return;
        SettlementData.SettlementRecord source = data.getSettlements().stream().filter(s -> s.getClaimLinks().stream().anyMatch(c -> c.id().equals(claimId))).findFirst().orElse(null);
        if (source == null) return; SettlementData.ClaimLink link = source.getClaimLinks().stream().filter(c -> c.id().equals(claimId)).findFirst().orElse(null); if (link == null) return;
        source.unlinkClaim(link.dimension(), claimId); destination.linkClaim(link.dimension(), claimId, link.name());
        if (ClaimEvents.recruitsClaimManager != null && FactionEvents.recruitsFactionManager != null && !destination.getFactionTeam().isBlank()) {
            RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(claimId); RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(destination.getFactionTeam());
            if (claim != null && faction != null) {
                claim.setOwnerFaction(faction);
                if (faction.getTeamLeaderUUID() != null) claim.setPlayer(new RecruitsPlayerInfo(faction.getTeamLeaderUUID(), faction.getTeamLeaderName(), faction));
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
                ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(level);
            }
        }
    }
    private static void renounceClaimByTreaty(ServerLevel level, String claimIdRaw) {
        UUID claimId; try { claimId = UUID.fromString(claimIdRaw); } catch (IllegalArgumentException e) { return; }
        SettlementData data = SettlementData.get(level);
        SettlementData.SettlementRecord source = settlementContainingClaim(data, claimId);
        if (source == null) return;
        SettlementData.ClaimLink link = source.getClaimLinks().stream().filter(value -> claimId.equals(value.id())).findFirst().orElse(null);
        if (link == null) return;
        source.unlinkClaim(link.dimension(), claimId);
        source.addChronicle("Territorial claim " + (link.name().isBlank() ? claimId : link.name()) + " was renounced by peace treaty.");
        if (ClaimEvents.recruitsClaimManager != null) {
            ClaimEvents.recruitsClaimManager.removeClaim(level, claimId);
            ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(level);
        }
    }

    private static void toastSettlementManagers(MinecraftServer server, String settlementId, String title, String body) {
        SettlementData.SettlementRecord settlement = SettlementData.get(server.overworld()).getSettlement(settlementId); if (settlement == null) return;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) if (SettlementManager.canManage(online, settlement)) CampaignToastSender.send(online, title, body);
    }

    /** Notify whichever political entity actually owns a side of a war. Settlement wars can
     * remain independent of faction participation, so diplomacy feedback must not disappear
     * just because a faction id is blank. */
    private static void toastPoliticalSide(MinecraftServer server, String factionId, String settlementId, String title, String body) {
        if (factionId != null && !factionId.isBlank()) CampaignToastSender.faction(server, factionId, title, body);
        else if (settlementId != null && !settlementId.isBlank()) toastSettlementManagers(server, settlementId, title, body);
    }
    private static String settlementName(ServerLevel level, String id) { SettlementData.SettlementRecord s = SettlementData.get(level).getSettlement(id); return s == null ? id : s.getName(); }
    private static int parseInt(String raw, int min, int max) { try { return Math.max(min, Math.min(max, Integer.parseInt(raw))); } catch (Exception ignored) { return min; } }

    private static String offerPeace(ServerPlayer player, CampaignData data, String own, UUID warId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot offer peace.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || !war.involves(own)) return "That war is not active.";
        if (war.type() == CampaignData.WarType.SETTLEMENT_WAR) {
            return "Settlement Wars use negotiated Peace Terms. A parent faction cannot end a local war with the obsolete one-click peace action.";
        }
        String target = own.equals(war.attackerFaction()) ? war.defenderFaction() : war.attackerFaction();
        data.createProposal(CampaignData.ProposalType.PEACE, own, target, player.getUUID(), war.id(), day);
        CampaignToastSender.faction(player.getServer(), target, "PEACE OFFER RECEIVED", factionName(own) + " has offered peace.");
        return "Peace offer sent to " + factionName(target) + ".";
    }

    private static String callToWar(ServerPlayer player, CampaignData data, String own, String target, UUID warId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot call allies to war.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || !war.involves(own)) return "Select an active war.";
        if (war.type() == CampaignData.WarType.SETTLEMENT_WAR
                && !(war.attackerFactionJoined() && war.defenderFactionJoined())) {
            return "Calls to war become available only after a Settlement War escalates to Faction vs Faction. Until then, parent factions may send aid or join directly.";
        }
        if (!validOtherFaction(own, target)) return "Select an allied faction.";
        if (war.involves(target)) return factionName(target) + " is already participating in this war.";
        CampaignData.TreatyState alliance = data.relation(own, target).state();
        if (!alliance.isAlliance()) return "Only an ally can be called to war.";
        boolean ownOnAttackerSide = war.attackerFaction().equals(own) || war.supportingAttackers().contains(own);
        if (alliance == CampaignData.TreatyState.DEFENSIVE_ALLIANCE && ownOnAttackerSide) {
            return "A defensive ally can only be called when your side is defending.";
        }
        data.createProposal(CampaignData.ProposalType.CALL_TO_WAR, own, target, player.getUUID(), war.id(), day);
        CampaignToastSender.faction(player.getServer(), target, "CALL TO WAR", factionName(own) + " requests your aid in " + war.escalationName() + ".");
        return "Call to war sent to " + factionName(target) + ".";
    }

    private static String acceptProposal(ServerPlayer player, CampaignData data, String own, UUID proposalId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot accept treaties.";
        CampaignData.DiplomacyProposal proposal = proposalId == null ? null : data.getProposal(proposalId);
        if (proposal == null || proposal.status() != CampaignData.ProposalStatus.PENDING || !own.equals(proposal.toFaction())) return "That proposal is unavailable.";
        CampaignSystemsData protectedData = CampaignSystemsData.get(player.serverLevel());
        if (protectedData.factionProtected(proposal.fromFaction()) || protectedData.factionProtected(proposal.toFaction()))
            return "Protected admin factions are excluded from ordinary diplomacy.";
        if (proposal.type() == CampaignData.ProposalType.CALL_TO_WAR && proposal.linkedWar() != null) {
            CampaignData.WarRecord war = data.getWar(proposal.linkedWar());
            if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "The linked war has already ended.";
            if (war.type() == CampaignData.WarType.SETTLEMENT_WAR
                    && !(war.attackerFactionJoined() && war.defenderFactionJoined())) {
                return "That Settlement War has not yet escalated to Faction vs Faction, so allied factions cannot formally join it.";
            }
            boolean attackerSide = war.attackerFaction().equals(proposal.fromFaction()) || war.supportingAttackers().contains(proposal.fromFaction());
            String enemy = attackerSide ? war.defenderFaction() : war.attackerFaction();
            if (enemy == null || enemy.isBlank() || !syncRelation(player.serverLevel(), own, enemy, CampaignData.TreatyState.WAR)) {
                return "Call-to-war acceptance aborted because Recruits did not confirm ENEMY relations with the opposing faction.";
            }
            data.resolveProposal(proposal.id(), CampaignData.ProposalStatus.ACCEPTED);
            war.addSupporter(own, attackerSide);
            CampaignData.DiplomacyRecord relation = data.relation(own, enemy);
            relation.setState(CampaignData.TreatyState.WAR, day);
            changeRelationship(player.getServer(), data, own, enemy, -30);
            changeRelationship(player.getServer(), data, proposal.fromFaction(), own, 15);
            // Reliability is earned from actual diplomatic conduct. Answering an ally's
            // legitimate call to war is one of the clearest positive signals.
            changeReliability(player.serverLevel(), data, own, 8);
            data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR,
                    "Faction joined a war", factionName(own) + " answered " + factionName(proposal.fromFaction()) + "'s call to war.",
                    day, own, war.targetSettlement(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.faction(player.getServer(), proposal.fromFaction(), "CALL TO WAR ACCEPTED", factionName(own) + " has joined your war.");
            CampaignToastSender.faction(player.getServer(), own, "WAR JOINED", "Your faction joined " + factionName(proposal.fromFaction()) + "'s war.");
            return "Call to war accepted. Your faction has joined the conflict.";
        }
        if (proposal.type() == CampaignData.ProposalType.PEACE && proposal.linkedWar() != null) {
            CampaignData.WarRecord linked = data.getWar(proposal.linkedWar());
            if (linked != null && linked.type() == CampaignData.WarType.SETTLEMENT_WAR) {
                return "This Settlement War must be resolved through negotiated Peace Terms, not a obsolete one-click faction peace proposal.";
            }
        }
        data.resolveProposal(proposal.id(), CampaignData.ProposalStatus.ACCEPTED);
        CampaignData.TreatyState state = switch (proposal.type()) {
            case NON_AGGRESSION -> CampaignData.TreatyState.NON_AGGRESSION;
            case DEFENSIVE_ALLIANCE -> CampaignData.TreatyState.DEFENSIVE_ALLIANCE;
            case MILITARY_ALLIANCE, CALL_TO_WAR -> CampaignData.TreatyState.MILITARY_ALLIANCE;
            case PEACE -> CampaignData.TreatyState.NEUTRAL;
        };
        if (proposal.type() == CampaignData.ProposalType.PEACE && proposal.linkedWar() != null) {
            CampaignData.WarRecord war = data.getWar(proposal.linkedWar());
            data.endWar(proposal.linkedWar(), day, "Peace accepted by both factions.");
            CampaignSystemsData.get(player.serverLevel()).clearWarInterventionState(proposal.linkedWar());
            if (war != null) synchronizeWarPeace(player.serverLevel(), data, war, day);
            data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR,
                    "Peace concluded", factionName(proposal.fromFaction()) + " and " + factionName(proposal.toFaction()) + " have made peace.",
                    day, own, war == null ? "" : war.targetSettlement(), CampaignData.NewsVisibility.PUBLIC);
        } else {
            data.addNews(CampaignData.NewsCategory.DIPLOMACY, CampaignData.NewsImportance.NORMAL,
                    state.displayName() + " formed", factionName(proposal.fromFaction()) + " and " + factionName(proposal.toFaction())
                            + " entered a " + state.displayName() + ".", day, own, "", CampaignData.NewsVisibility.PUBLIC);
        }
        CampaignData.DiplomacyRecord relation = data.relation(proposal.fromFaction(), proposal.toFaction());
        relation.setState(state, day);
        changeRelationship(player.getServer(), data, proposal.fromFaction(), proposal.toFaction(), state.isAlliance() ? 15 : 8);
        syncRelation(player.serverLevel(), proposal.fromFaction(), proposal.toFaction(), state);
        CampaignToastSender.relationChanged(player.getServer(), proposal.fromFaction(), proposal.toFaction(), state.displayName());
        return pretty(proposal.type().name()) + " accepted.";
    }

    private static String rejectProposal(ServerPlayer player, CampaignData data, String own, UUID proposalId) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot reject treaties.";
        CampaignData.DiplomacyProposal proposal = proposalId == null ? null : data.getProposal(proposalId);
        if (proposal == null || proposal.status() != CampaignData.ProposalStatus.PENDING || !own.equals(proposal.toFaction())) return "That proposal is unavailable.";
        CampaignSystemsData protectedData = CampaignSystemsData.get(player.serverLevel());
        if (protectedData.factionProtected(proposal.fromFaction()) || protectedData.factionProtected(proposal.toFaction()))
            return "Protected admin factions are excluded from ordinary diplomacy.";
        data.resolveProposal(proposal.id(), CampaignData.ProposalStatus.REJECTED);
        changeRelationship(player.getServer(), data, proposal.fromFaction(), proposal.toFaction(), -5);
        CampaignToastSender.faction(player.getServer(), proposal.fromFaction(), "PROPOSAL REJECTED", factionName(own) + " rejected your " + pretty(proposal.type().name()) + " proposal.");
        return "Proposal rejected.";
    }

    private static String breakTreaty(ServerPlayer player, CampaignData data, String own, String target, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot break treaties.";
        if (!validOtherFaction(own, target)) return "Select another faction.";
        CampaignSystemsData protectedData = CampaignSystemsData.get(player.serverLevel());
        if (protectedData.factionProtected(own) || protectedData.factionProtected(target)) return "Protected admin factions are excluded from ordinary diplomacy.";
        CampaignData.DiplomacyRecord relation = data.relation(own, target);
        if (relation.state() == CampaignData.TreatyState.WAR || relation.state() == CampaignData.TreatyState.NEUTRAL) return "There is no treaty to break.";
        relation.setState(CampaignData.TreatyState.NEUTRAL, day);
        changeRelationship(player.getServer(), data, own, target, -20);
        changeReliability(player.serverLevel(), data, own, -15);
        syncRelation(player.serverLevel(), own, target, CampaignData.TreatyState.NEUTRAL);
        CampaignToastSender.relationChanged(player.getServer(), own, target, "Neutral — treaty broken");
        data.addNews(CampaignData.NewsCategory.DIPLOMACY, CampaignData.NewsImportance.NORMAL,
                "Treaty broken", factionName(own) + " broke its treaty with " + factionName(target) + ".",
                day, own, "", CampaignData.NewsVisibility.PUBLIC);
        return "Treaty with " + factionName(target) + " broken.";
    }

    public static String relationshipCategory(int relationship) {
        if (relationship <= -25) return "Hostile";
        if (relationship >= 25) return "Friendly";
        return "Neutral";
    }

    static void changeRelationship(MinecraftServer server, CampaignData data, String first, String second, int delta) {
        if (data == null || first == null || second == null || first.isBlank() || second.isBlank() || first.equals(second) || delta == 0) return;
        CampaignData.DiplomacyRecord relation = data.relation(first, second);
        String before = relationshipCategory(relation.relationship());
        relation.changeRelationship(delta);
        String after = relationshipCategory(relation.relationship());
        if (server != null && !before.equals(after)) {
            CampaignToastSender.relationChanged(server, first, second, after + " (" + relation.relationship() + ")");
        }
    }

    private static void synchronizeWarPeace(ServerLevel level, CampaignData data, CampaignData.WarRecord war, long day) {
        // A local Settlement War stores parent faction ids so they can observe/escalate, but those ids are
        // not belligerents until Join War is used. Ending a local war must therefore never reset an
        // uninvolved parent faction's existing Recruits/campaign relation.
        List<String> attackers = new ArrayList<>();
        String attackerParent = participatingFaction(war, true);
        if (!attackerParent.isBlank()) attackers.add(attackerParent);
        attackers.addAll(war.supportingAttackers().stream().filter(value -> value != null && !value.isBlank()).toList());
        List<String> defenders = new ArrayList<>();
        String defenderParent = participatingFaction(war, false);
        if (!defenderParent.isBlank()) defenders.add(defenderParent);
        defenders.addAll(war.supportingDefenders().stream().filter(value -> value != null && !value.isBlank()).toList());
        for (String attacker : attackers) for (String defender : defenders) {
            data.relation(attacker, defender).setState(CampaignData.TreatyState.NEUTRAL, day);
            syncRelation(level, attacker, defender, CampaignData.TreatyState.NEUTRAL);
        }
        data.setDirty();
    }

    public static void handleFamilyAction(ServerPlayer player, String actionRaw, String value, UUID targetPlayer) {
        CampaignData data = CampaignData.get(player.serverLevel());
        long day = player.serverLevel().getDayTime() / 24000L;
        String action = actionRaw == null ? "" : actionRaw.toUpperCase(Locale.ROOT);
        String result;
        switch (action) {
            case "DONATE_FAITH" -> {
                SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
                CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
                ItemStack held = player.getMainHandItem();
                if (character == null || !character.isCreated()) result = "Create a character before making a religious donation.";
                else if (extra.devotion(player.getUUID()) >= 100) result = "Your Devotion is already at its maximum.";
                else if (held.isEmpty() || !held.is(SettlementManager.CURRENCY)) result = "Hold one item from #settlements:currency to donate it to your faith.";
                else {
                    held.shrink(1);
                    int gain = SettlementConfig.CURRENCY_DONATION_DEVOTION_GAIN.get();
                    extra.changeDevotion(player.getUUID(), gain);
                    result = "Religious donation made: +" + gain + " Devotion (" + extra.devotion(player.getUUID()) + "/100).";
                }
            }
            case "MARRY_NPC" -> {
                CampaignData.NpcSpouse spouse = data.getCandidate(player.getUUID(), value);
                if (spouse == null) result = "That generated candidate is no longer available.";
                else {
                    CampaignData.MarriageRecord existing = data.getMarriage(player.getUUID());
                    if (existing == null) {
                        data.marryNpc(player.getUUID(), spouse, day);
                        data.addNews(CampaignData.NewsCategory.CHARACTERS, CampaignData.NewsImportance.NORMAL,
                                "Marriage", SettlementManager.characterOrAccountName(player) + " married " + spouse.name() + ".",
                                day, factionId(player), "", CampaignData.NewsVisibility.PUBLIC);
                        result = "You married " + spouse.name() + ".";
                    } else {
                        SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
                        int maximumSpouses = character == null ? 1 : SettlementConfig.maxSpousesForFaith(character.getFaith());
                        boolean permitsAdditionalWives = maximumSpouses > 1;
                        if (!permitsAdditionalWives) result = "Your faith permits only one active spouse.";
                        else if (character != null && character.getFaith().equalsIgnoreCase("Islam") && !spouse.gender().equalsIgnoreCase("Female")) result = "Additional spouses under this faith rule must be female candidates.";
                        else if (data.spouseCount(player.getUUID()) >= maximumSpouses) result = "You already have the maximum of " + maximumSpouses + " spouses permitted by your faith.";
                        else if (data.marryAdditionalNpc(player.getUUID(), spouse, day)) {
                            int count = data.spouseCount(player.getUUID());
                            int percent = CampaignData.spouseEffectPercent(count - 1);
                            data.addNews(CampaignData.NewsCategory.CHARACTERS, CampaignData.NewsImportance.NORMAL,
                                    "Marriage", SettlementManager.characterOrAccountName(player) + " married " + spouse.name() + " as an additional spouse.",
                                    day, factionId(player), "", CampaignData.NewsVisibility.PUBLIC);
                            result = "You married " + spouse.name() + ". As spouse " + count + ", their settlement effects operate at " + percent + "%.";
                        } else result = "That additional marriage cannot be made.";
                    }
                }
            }
            case "REFRESH" -> {
                if (!data.canRefreshCandidates(player.getUUID(), day)) result = "New candidates become available on day " + data.nextCandidateRefreshDay(player.getUUID()) + ".";
                else {
                    CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
                    CampaignSystemsData.CandidateHistory history = extra.candidateHistory(player.getUUID());
                    SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
                    int quality = character == null ? 0 : CampaignDefinitions.totalEffect(character.getTraits(), CampaignDefinitions.EffectKind.SPOUSE_QUALITY);
                    data.refreshCandidates(player.getUUID(), day, history.rejectedIds(), history.seenIds(), quality, character == null ? "" : character.getFaith());
                    result = quality > 0 ? "A new set of spouse candidates has arrived. Your Charisma improved the selection." : "A new set of spouse candidates has arrived.";
                }
            }
            case "SET_GENDER" -> {
                String gender = value == null ? "ANY" : value.toUpperCase(Locale.ROOT);
                if (!gender.equals("ANY") && !gender.equals("MALE") && !gender.equals("FEMALE")) result = "Use Any, Male, or Female.";
                else { CampaignSystemsData.get(player.serverLevel()).setCandidateGender(player.getUUID(), gender); result = "Marriage candidate filter set to " + gender.toLowerCase(Locale.ROOT) + "."; }
            }
            case "REJECT_CANDIDATE" -> {
                CampaignData.NpcSpouse spouse = data.getCandidate(player.getUUID(), value);
                if (spouse == null) result = "That candidate is no longer available.";
                else { CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel()); extra.rejectCandidate(player.getUUID(), spouse.id()); extra.rejectCandidate(player.getUUID(), spouse.name()); data.rejectCandidate(player.getUUID(), spouse.id()); result = spouse.name() + " was rejected and will not be generated for you again."; }
            }
            case "PROPOSE_PLAYER" -> {
                ServerPlayer target = targetPlayer == null || player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(targetPlayer);
                if (target == null) result = "Select an online player.";
                else {
                    SettlementData.CharacterRecord targetCharacter = SettlementData.get(player.serverLevel()).getCharacter(target.getUUID());
                    if (targetCharacter == null || !targetCharacter.isCreated()) result = "That player has not created a character.";
                    else if (data.proposePlayerMarriage(player.getUUID(), target.getUUID())) {
                        result = "Marriage proposal sent.";
                        target.sendSystemMessage(Component.literal(SettlementManager.characterOrAccountName(player) + " has proposed marriage. Open the Family screen to answer."));
                    } else result = "That proposal cannot be made.";
                }
            }
            case "ACCEPT" -> {
                UUID proposer = data.pendingMarriageProposer(player.getUUID());
                boolean accepted = data.acceptPlayerMarriage(player.getUUID(), day);
                if (accepted) {
                    String other = proposer == null ? "another character" : characterName(player.serverLevel(), proposer);
                    String firstFaction = proposer == null ? "" : factionForPlayerId(player.getServer(), proposer);
                    String secondFaction = factionId(player);
                    if (!firstFaction.isBlank() && !secondFaction.isBlank() && !firstFaction.equals(secondFaction)) {
                        CampaignSystemsData.get(player.serverLevel()).addMarriageAlliance(firstFaction, secondFaction);
                        CampaignToastSender.faction(player.getServer(), firstFaction, "MARRIAGE ALLIANCE", "A political marriage now binds your faction to " + factionName(secondFaction) + ".");
                        CampaignToastSender.faction(player.getServer(), secondFaction, "MARRIAGE ALLIANCE", "A political marriage now binds your faction to " + factionName(firstFaction) + ".");
                    }
                    data.addNews(CampaignData.NewsCategory.CHARACTERS, CampaignData.NewsImportance.NORMAL,
                            "Marriage", SettlementManager.characterOrAccountName(player) + " married " + other + ".",
                            day, factionId(player), "", CampaignData.NewsVisibility.PUBLIC);
                }
                result = accepted ? "Marriage proposal accepted." : "No valid proposal found.";
            }
            case "REJECT" -> { data.rejectPlayerMarriage(player.getUUID()); result = "Marriage proposal rejected."; }
            case "DIVORCE" -> {
                CampaignData.MarriageRecord marriage = data.getMarriage(player.getUUID());
                if (marriage == null) result = "You are not married.";
                else if (day - marriage.marriedDay() < 7L || data.getAdditionalNpcMarriages(player.getUUID()).stream().anyMatch(extraMarriage -> day - extraMarriage.marriedDay() < 7L))
                    result = "A voluntary divorce is unavailable until every active marriage has lasted at least 7 Minecraft days.";
                else {
                    UUID spouseId = marriage.spousePlayerId();
                    String firstFaction = factionId(player);
                    String secondFaction = spouseId == null ? "" : factionForPlayerId(player.getServer(), spouseId);
                    data.divorce(player.getUUID());
                    CampaignSystemsData extra = CampaignSystemsData.get(player.serverLevel());
                    int divorces = extra.incrementDivorceCount(player.getUUID());
                    SettlementData.SettlementRecord capital = SettlementData.get(player.serverLevel()).getOwnedSettlements(player.getUUID()).stream()
                            .filter(s -> s.isCapital() || (s.getFactionTeam().isBlank() && s.getRank().ordinal() >= SettlementData.SettlementRank.CITY.ordinal()))
                            .findFirst().orElse(null);
                    if (capital != null) {
                        int penalty = -Math.min(8, 2 * divorces);
                        long expires = extra.nowMillis(capital.getId()) + 3L * 24L * 60L * 60L * 1000L;
                        extra.addModifier(capital.getId(), new CampaignSystemsData.ModifierRecord("marital_scandal", "Marital Scandal", "minecraft:torn_banner_pattern", "NEGATIVE",
                                "Repeated divorce", "Repeated political marriages and divorces have damaged confidence in the ruler.", penalty, 0, expires));
                    }
                    if (!firstFaction.isBlank() && !secondFaction.isBlank() && !firstFaction.equals(secondFaction)) {
                        CampaignSystemsData.get(player.serverLevel()).removeMarriageAlliance(firstFaction, secondFaction);
                        // The faction whose ruler initiates a political divorce takes the reputation hit.
                        // The other spouse's faction did not choose to break the marriage alliance.
                        int reliabilityChange = changeReliability(player.serverLevel(), data, firstFaction, -8);
                        CampaignToastSender.faction(player.getServer(), firstFaction, "MARRIAGE ALLIANCE ENDED", "The marriage alliance with " + factionName(secondFaction) + " has ended. Reliability " + reliabilityChange + ".");
                        CampaignToastSender.faction(player.getServer(), secondFaction, "MARRIAGE ALLIANCE ENDED", "The marriage alliance with " + factionName(firstFaction) + " has ended.");
                    }
                    result = "Marriage ended.";
                }
            }
            default -> result = "Unknown family action.";
        }
        if ("DONATE_FAITH".equals(action)) {
            player.sendSystemMessage(Component.literal(result));
            SettlementManager.openCharacterSheet(player, true);
        } else openFamily(player, result);
    }


    private static String createSettlementFromGui(ServerPlayer player, String id, String name) {
        SettlementData data = SettlementData.get(player.serverLevel());
        CampaignSystemsData systems = CampaignSystemsData.get(player.serverLevel());
        long gameTime = player.serverLevel().getGameTime();
        long cooldownTicks = Math.max(0L, SettlementConfig.SETTLEMENT_FOUNDING_COOLDOWN_MINUTES.get()) * 60L * 20L;
        long elapsed = gameTime - systems.lastSettlementFoundingGameTime(player.getUUID());
        if (cooldownTicks > 0L && elapsed >= 0L && elapsed < cooldownTicks) {
            long seconds = Math.max(1L, (cooldownTicks - elapsed + 19L) / 20L);
            return "You recently founded a settlement. You may found another in " + (seconds / 60L) + "m " + (seconds % 60L) + "s.";
        }
        if (id.isBlank()) return "Settlement creation failed: enter a short settlement ID.";
        if (factionId(player).isBlank() && data.getSettlements().stream()
                .filter(value -> player.getUUID().equals(value.getOwnerId()) && value.getFactionTeam().isBlank()).count() >= 2) {
            return "Independent rulers may own at most two settlements. Create a faction before founding another.";
        }
        SettlementData.SettlementRecord settlement = data.createSettlement(id, name, player.getUUID());
        if (settlement == null) return "Settlement creation failed: that ID is invalid or already used.";
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        String politicalFaction = factionId(player);
        if (!politicalFaction.isBlank()) settlement.setFactionTeam(politicalFaction);
        else settlement.setBackingFactionTeam(BackingFactionManager.ensureForIndependent(player));
        SettlementCommands.linkClaimAtPlayer(player, settlement, false);
        SettlementManager.scheduleFirstSettlers(player, settlement);
        CaptureZoneManager.ensureVillageMainZone(player.serverLevel(), settlement);
        data.selectSettlement(player.getUUID(), settlement.getId());
        settlement.addChronicle("Settlement founded by " + SettlementManager.characterOrAccountName(player) + ".");
        CampaignData.get(player.serverLevel()).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.NORMAL,
                "Settlement founded", settlement.getName() + " was founded as a Village.",
                player.serverLevel().getDayTime() / 24000L, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
        systems.markSettlementFounded(player.getUUID(), gameTime);
        broadcastMapMarkers(player.getServer());
        return "Founded " + settlement.getName() + " as a Village. Its Town Square and initial Capture Zone were prepared at your current position.";
    }

    public static String createFaction(ServerPlayer player, String rawName, ChatFormatting teamColor, byte unitColor) {
        if (player == null) return "No player supplied.";
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord foundingSeat = foundingSettlement(player, data);
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BannerItem)) return "Faction creation failed: You are not holding a banner!";
        return createFactionInternal(player, rawName, teamColor, unitColor, foundingSeat, held.copy(),
                CampaignData.GovernmentType.MONARCHY, "white", "white");
    }

    public static void createFactionFromGui(ServerPlayer player, FactionCreatePacket packet) {
        if (player == null || packet == null) return;
        SettlementData data = SettlementData.get(player.serverLevel());
        SettlementData.SettlementRecord foundingSeat = data.getSettlement(packet.capitalSettlementId());
        if (foundingSeat == null || !player.getUUID().equals(foundingSeat.getOwnerId()) || !foundingSeat.getFactionTeam().isBlank()) {
            openFaction(player, "Choose one of your independent settlements as the faction capital.");
            return;
        }
        ItemStack banner = packet.banner() == null ? ItemStack.EMPTY : packet.banner().copy();
        if (!(banner.getItem() instanceof BannerItem)) {
            openFaction(player, "Design a valid faction banner before creating the faction.");
            return;
        }
        banner.setCount(1);
        if (RecruitsFactionManager.isBannerBlank(banner)) {
            openFaction(player, "Faction banner must contain at least one pattern layer.");
            return;
        }
        CompoundTag blockEntityTag = banner.getTagElement("BlockEntityTag");
        int patternCount = blockEntityTag == null ? 0 : blockEntityTag.getList("Patterns", Tag.TAG_COMPOUND).size();
        if (patternCount < 1 || patternCount > 6) {
            openFaction(player, "Faction banners must contain between 1 and 6 pattern layers.");
            return;
        }
        net.minecraft.world.item.DyeColor teamDye = FactionColorUtil.dye(packet.teamDye());
        net.minecraft.world.item.DyeColor unitDye = FactionColorUtil.dye(packet.unitDye());
        if (!FactionColorUtil.isKnown(packet.teamDye()) || !FactionColorUtil.isKnown(packet.unitDye())) {
            openFaction(player, "A selected dye is unavailable on this server. Check that client and server have matching color mods.");
            return;
        }
        CampaignData.GovernmentType government = CampaignData.GovernmentType.parse(packet.government());
        String result = createFactionInternal(player, packet.name(), FactionColorUtil.closestChatFormatting(teamDye),
                FactionColorUtil.closestRecruitsUnitColor(unitDye), foundingSeat, banner, government,
                teamDye.getName(), unitDye.getName());
        openFaction(player, result);
    }

    private static String createFactionInternal(ServerPlayer player, String rawName, ChatFormatting teamColor, byte unitColor,
                                                SettlementData.SettlementRecord foundingSeat, ItemStack banner,
                                                CampaignData.GovernmentType government, String teamDye, String unitDye) {
        if (player == null) return "No player supplied.";
        if (!SettlementConfig.FACTION_FOUNDING_ENABLED.get()) return "Faction founding is disabled by the server configuration.";
        String currentPoliticalFaction = factionId(player);
        if (!currentPoliticalFaction.isBlank()) return "Leave your current faction before creating another one.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        String displayName = SettlementData.cleanText(rawName, "", 32);
        if (displayName.isBlank()) return "Enter a faction name.";

        SettlementData settlementData = SettlementData.get(player.serverLevel());
        if (foundingSeat == null || !player.getUUID().equals(foundingSeat.getOwnerId()) || !foundingSeat.getFactionTeam().isBlank())
            return "Found and develop an independent settlement before creating a political faction.";
        int population = foundingSeat.getCivilianPopulation();
        if (SettlementConfig.FACTION_FOUNDING_REQUIRE_TOWN.get() && foundingSeat.getRank() == SettlementData.SettlementRank.VILLAGE) {
            return "A full faction requires an established Town or City. " + foundingSeat.getName() + " is still a Village.";
        }
        if (population < SettlementConfig.FACTION_FOUNDING_MIN_POPULATION.get())
            return "Faction founding requires " + SettlementConfig.FACTION_FOUNDING_MIN_POPULATION.get() + " population; " + foundingSeat.getName() + " has " + population + ".";
        if (foundingSeat.getStability() < SettlementConfig.FACTION_FOUNDING_MIN_STABILITY.get())
            return "Faction founding requires " + SettlementConfig.FACTION_FOUNDING_MIN_STABILITY.get() + " Stability; " + foundingSeat.getName() + " has " + foundingSeat.getStability() + ".";
        int treasuryCost = SettlementConfig.FACTION_FOUNDING_TREASURY_COST.get();
        int foundingTreasury = StateTreasury.balance(player.serverLevel(), foundingSeat);
        if (foundingTreasury < treasuryCost)
            return "Faction founding costs " + treasuryCost + " from the State Treasury; the independent polity currently has " + foundingTreasury + ".";

        if (banner == null || !(banner.getItem() instanceof BannerItem)) return "Faction creation failed: design a banner first.";
        banner = FactionBannerUtil.sanitize(banner);
        if (banner.isEmpty()) return "Faction creation failed: choose 1–6 registered banner patterns with valid dye colors.";
        if (FactionEvents.recruitsFactionManager.isDisplayNameInUse(displayName)) return "Faction creation failed: that faction display name is already in use.";
        if (FactionEvents.recruitsFactionManager.isBannerInUse(banner.serializeNBT())) return "Faction creation failed: that banner is already used by another faction.";
        String baseId = SettlementData.normalizeId(displayName);
        if (baseId.isBlank()) return "That faction name cannot produce a valid internal ID.";
        String newFactionId = baseId;
        int suffix = 2;
        while (FactionEvents.recruitsFactionManager.getFactionByStringID(newFactionId) != null
                || player.getServer().getScoreboard().getPlayerTeam(newFactionId) != null) {
            newFactionId = baseId + "_" + suffix++;
            if (suffix > 99) return "Too many factions already use that name.";
        }
        try {
            FactionEvents.createTeam(true, player, player.serverLevel(), newFactionId, displayName,
                    player.getScoreboardName(), banner, teamColor == null ? ChatFormatting.WHITE : teamColor, unitColor);
        } catch (RuntimeException exception) {
            return "Faction creation failed: " + exception.getClass().getSimpleName();
        }
        RecruitsFaction created = FactionEvents.recruitsFactionManager.getFactionByStringID(newFactionId);
        if (created == null) return "Faction creation failed: Recruits did not confirm creation. Check its configured creation requirements/cost and try again.";

        StateTreasury.spend(player.serverLevel(), foundingSeat, treasuryCost);
        foundingSeat.addChronicle("The State Treasury contributed " + treasuryCost + " to the founding of " + displayName + ".");
        BackingFactionManager.migrateOwnedSettlementsToPoliticalFaction(player, newFactionId);
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.setFactionGovernment(newFactionId, government);
        campaign.setFactionColors(newFactionId, teamDye, unitDye);
        for (SettlementData.SettlementRecord settlement : settlementData.getSettlements()) {
            if (newFactionId.equals(settlement.getFactionTeam())) settlement.setCapital(settlement.getId().equals(foundingSeat.getId()));
        }
        long day = player.serverLevel().getDayTime() / 24000L;
        campaign.addNews(CampaignData.NewsCategory.FACTIONS, CampaignData.NewsImportance.MAJOR,
                "Faction founded", displayName + " was founded by " + SettlementManager.characterOrAccountName(player) + ".",
                day, newFactionId, foundingSeat.getId(), CampaignData.NewsVisibility.PUBLIC);
        SettlementManager.broadcastProfiles(player.getServer());
        CampaignToastSender.factionFounded(player, displayName);
        return "Created " + government.displayName() + " " + displayName + " with " + foundingSeat.getName() + " as its capital.";
    }

    private static SettlementData.SettlementRecord foundingSettlement(ServerPlayer player, SettlementData data) {
        SettlementData.SettlementRecord selected = data.getSelectedSettlement(player.getUUID());
        if (selected != null && player.getUUID().equals(selected.getOwnerId()) && selected.getFactionTeam().isBlank()) return selected;
        return data.getOwnedSettlements(player.getUUID()).stream()
                .filter(value -> value.getFactionTeam().isBlank())
                .max(java.util.Comparator
                        .comparingInt((SettlementData.SettlementRecord value) -> value.getRank().ordinal())
                        .thenComparingInt(SettlementData.SettlementRecord::getCivilianPopulation)
                        .thenComparingInt(SettlementData.SettlementRecord::getStability))
                .orElse(null);
    }

    private static String characterName(ServerLevel level, UUID uuid) {
        SettlementData.CharacterRecord character = SettlementData.get(level).getCharacter(uuid);
        return character != null && character.isCreated() ? character.getDisplayName() : uuid.toString();
    }

    public static boolean isFactionLeader(ServerPlayer player, String factionId) {
        // Political office is deliberately independent from operator permission. Admins use explicit
        // recovery/admin commands; being OP must never silently make a player the ruler of a faction.
        if (player == null || factionId == null || factionId.isBlank() || FactionEvents.recruitsFactionManager == null) return false;
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        return faction != null && player.getUUID().equals(faction.getTeamLeaderUUID());
    }

    private static boolean canManageDiplomacy(ServerPlayer player, String factionId, int minimumTier) {
        if (isFactionLeader(player, factionId)) return true;
        return CampaignData.get(player.serverLevel()).factionRankTier(factionId, player.getUUID(), false) >= minimumTier;
    }

    public static void enforceAllDiplomacy(ServerLevel level) {
        if (FactionEvents.recruitsDiplomacyManager == null) return;
        CampaignData data = CampaignData.get(level);
        for (CampaignData.DiplomacyRecord record : data.getDiplomacy()) syncRelation(level, record.firstFaction(), record.secondFaction(), record.state());
    }

    public static boolean syncRelation(ServerLevel level, String first, String second, CampaignData.TreatyState state) {
        if (FactionEvents.recruitsDiplomacyManager == null || first == null || second == null || first.isBlank() || second.isBlank()) return false;
        RecruitsDiplomacyManager.DiplomacyStatus status = switch (state) {
            case DEFENSIVE_ALLIANCE, MILITARY_ALLIANCE -> RecruitsDiplomacyManager.DiplomacyStatus.ALLY;
            case WAR -> RecruitsDiplomacyManager.DiplomacyStatus.ENEMY;
            default -> RecruitsDiplomacyManager.DiplomacyStatus.NEUTRAL;
        };
        FactionEvents.recruitsDiplomacyManager.setRelation(first, second, status, level, false);
        FactionEvents.recruitsDiplomacyManager.setRelation(second, first, status, level, false);
        boolean verified = FactionEvents.recruitsDiplomacyManager.getRelation(first, second) == status
                && FactionEvents.recruitsDiplomacyManager.getRelation(second, first) == status;
        if (verified) FactionEvents.recruitsDiplomacyManager.broadcastDiplomacyMapToAll(level);
        return verified;
    }

    private static String factionForPlayerId(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return "";
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            String political = factionId(online);
            if (!political.isBlank()) return political;
        }
        if (FactionEvents.recruitsFactionManager != null) {
            for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
                if (faction == null || BackingFactionManager.isBackingFaction(faction.getStringID())) continue;
                if (playerId.equals(faction.getTeamLeaderUUID())) return faction.getStringID();
                if (faction.getMembers().stream().anyMatch(info -> info != null && playerId.equals(info.getUUID()))) return faction.getStringID();
            }
        }
        return "";
    }

    public static String factionId(ServerPlayer player) {
        String raw = BackingFactionManager.rawTeam(player);
        return BackingFactionManager.isBackingFaction(raw) ? "" : raw;
    }

    private static String factionFaith(ServerLevel level, String factionId) {
        if (level == null || factionId == null || factionId.isBlank() || FactionEvents.recruitsFactionManager == null) return "";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null || faction.getTeamLeaderUUID() == null) return "";
        SettlementData.CharacterRecord leader = SettlementData.get(level).getCharacter(faction.getTeamLeaderUUID());
        return leader == null || !leader.isCreated() ? "" : leader.getFaith();
    }

    public static String factionName(String id) {
        if (id == null || id.isBlank() || BackingFactionManager.isBackingFaction(id)) return "Independent";
        if (FactionEvents.recruitsFactionManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(id);
            if (faction != null) return faction.getTeamDisplayName();
        }
        return id;
    }

    private static boolean validOtherFaction(String own, String target) {
        if (target == null || target.isBlank() || target.equals(own)) return false;
        return FactionEvents.recruitsFactionManager != null && FactionEvents.recruitsFactionManager.getFactionByStringID(target) != null;
    }

    private static String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return value.isBlank() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
