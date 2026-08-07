package ee.lastpagans.settlementprototype;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsFactionManager;
import ee.lastpagans.settlementprototype.network.CampaignSnapshotPacket;
import ee.lastpagans.settlementprototype.network.DiplomacySnapshotPacket;
import ee.lastpagans.settlementprototype.network.FactionSnapshotPacket;
import ee.lastpagans.settlementprototype.network.FamilySnapshotPacket;
import ee.lastpagans.settlementprototype.network.MapMarkersPacket;
import ee.lastpagans.settlementprototype.network.MilitarySnapshotPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.WorldNewsSnapshotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Server-authoritative campaign, faction, diplomacy, family and world-news actions. */
public final class CampaignManager {
    private CampaignManager() {}

    public static void openMain(ServerPlayer player) {
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CampaignSnapshotPacket(CampaignSnapshot.from(player)));
    }

    public static void openFaction(ServerPlayer player, String message) {
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FactionSnapshotPacket(FactionSnapshot.from(player, message)));
    }

    public static void openMilitary(ServerPlayer player, String message) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player.serverLevel()).getSelectedSettlement(player.getUUID());
        if (settlement == null || !PrototypeManager.canView(player, settlement)) {
            player.sendSystemMessage(Component.literal("Select a settlement before opening the Military screen."));
            openMain(player);
            return;
        }
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MilitarySnapshotPacket(PrototypeSnapshot.from(settlement, player, message)));
    }

    public static void openDiplomacy(ServerPlayer player, String message) {
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DiplomacySnapshotPacket(DiplomacySnapshot.from(player, message)));
    }

    public static void openFamily(ServerPlayer player, String message) {
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FamilySnapshotPacket(FamilySnapshot.from(player, message)));
    }

    public static void openWorldNews(ServerPlayer player) {
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new WorldNewsSnapshotPacket(WorldNewsSnapshot.from(player)));
    }

    public static void sendMapMarkers(ServerPlayer player) {
        PrototypeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
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
            openFaction(player, createFaction(player, name, teamColor, (byte)Math.max(0, Math.min(15, tier))));
            return;
        }
        if ("CREATE_SETTLEMENT".equals(action)) {
            String[] parts = value == null ? new String[0] : value.split("\\|", 2);
            String id = parts.length > 0 ? PrototypeData.normalizeId(parts[0]) : "";
            String name = parts.length > 1 ? PrototypeData.cleanText(parts[1], id, 64) : id;
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
                    if (faction != null && targetPlayer.equals(faction.getTeamLeaderUUID())) result = "The faction leader always holds the highest rank.";
                    else {
                        data.setFactionMemberRank(factionId, targetPlayer, Math.max(0, Math.min(3, tier)));
                        PrototypeManager.broadcastProfiles(player.getServer());
                        result = "Faction rank updated.";
                    }
                }
            }
            case "SET_RANK_TITLE" -> {
                if (!leader) result = "Only the faction leader may rename faction ranks.";
                else {
                    data.setFactionRankTitle(factionId, Math.max(0, Math.min(4, tier)), value);
                    PrototypeManager.broadcastProfiles(player.getServer());
                    result = "Tier " + Math.max(0, Math.min(4, tier)) + " title updated.";
                }
            }
            case "SET_CAPITAL" -> {
                if (!leader) result = "Only the faction leader may designate the capital.";
                else {
                    PrototypeData settlements = PrototypeData.get(player.serverLevel());
                    PrototypeData.SettlementRecord target = settlements.getSettlement(value);
                    if (target == null || !factionId.equals(target.getFactionTeam())) result = "Select one of your faction's settlements.";
                    else {
                        for (PrototypeData.SettlementRecord settlement : settlements.settlementsForFaction(factionId)) {
                            if (settlement.getRank() == PrototypeData.SettlementRank.CAPITAL && settlement != target) settlement.setRank(PrototypeData.SettlementRank.TOWN);
                        }
                        target.setRank(PrototypeData.SettlementRank.CAPITAL);
                        target.addChronicle("Designated as the faction capital.");
                        result = target.getName() + " is now the faction capital.";
                    }
                }
            }
            case "TREASURY_DEPOSIT" -> {
                PrototypeData.SettlementRecord target = PrototypeData.get(player.serverLevel()).getSettlement(value);
                int amount = Math.max(1, tier);
                if (!leader) result = "Only the faction leader may manage the faction treasury.";
                else if (target == null || !factionId.equals(target.getFactionTeam())) result = "Select one of your faction's settlements.";
                else if (!PrototypeManager.canManage(player, target)) result = "You must manage that settlement to move its wealth into the faction treasury.";
                else if (!target.spendWealth(amount)) result = target.getName() + " does not have " + amount + " wealth available.";
                else { Campaign23Data.get(player.serverLevel()).changeFactionWealth(factionId, amount); result = amount + " wealth moved from " + target.getName() + " to the faction treasury."; }
            }
            case "TREASURY_WITHDRAW" -> {
                PrototypeData.SettlementRecord target = PrototypeData.get(player.serverLevel()).getSettlement(value);
                int amount = Math.max(1, tier);
                Campaign23Data extra = Campaign23Data.get(player.serverLevel());
                if (!leader) result = "Only the faction leader may manage the faction treasury.";
                else if (target == null || !factionId.equals(target.getFactionTeam())) result = "Select one of your faction's settlements.";
                else if (!extra.spendFactionWealth(factionId, amount)) result = "The faction treasury does not contain " + amount + " wealth.";
                else {
                    target.changeWealth(amount);
                    extra.recordWealthDeposit(target.getId(), amount, player.serverLevel().getDayTime() / 24000L);
                    result = amount + " wealth moved from the faction treasury to " + target.getName() + ".";
                }
            }
            default -> result = "Unknown faction action.";
        }
        openFaction(player, result);
    }

    private static String requestFactionJoin(ServerPlayer player, String requestedFactionId) {
        if (player.getTeam() != null) return "Leave your current faction before requesting to join another one.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        String targetId = requestedFactionId == null ? "" : requestedFactionId.trim();
        RecruitsFaction target = FactionEvents.recruitsFactionManager.getFactionByStringID(targetId);
        if (target == null) return "That faction no longer exists.";
        if (Campaign23Data.get(player.serverLevel()).factionProtected(targetId)) return "Protected admin factions do not accept player applications.";
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
        if (player.getTeam() != null) return "You are already a faction member.";
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
        if (applicant.getTeam() != null) {
            faction.removeJoinRequest(stored);
            FactionEvents.recruitsFactionManager.save(player.serverLevel());
            return stored + " has already joined another faction.";
        }
        if (!faction.canAddPlayer()) return faction.getTeamDisplayName() + " has reached its member limit.";

        FactionEvents.addPlayerToTeam(player, player.serverLevel(), factionId, applicant.getGameProfile().getName());
        if (applicant.getTeam() == null || !factionId.equals(applicant.getTeam().getName())) {
            return "Recruits did not confirm " + stored + " as a faction member.";
        }
        CampaignData.get(player.serverLevel()).setFactionMemberRank(factionId, applicant.getUUID(), 0);
        Campaign23Data.get(player.serverLevel()).clearFactionInvitations(applicant.getUUID());
        clearFactionApplications(player.serverLevel(), applicant.getDisplayName().getString());
        PrototypeManager.broadcastProfiles(player.getServer());
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
        if (Campaign23Data.get(player.serverLevel()).factionProtected(factionId)) return "Protected admin factions cannot invite normal players.";
        if (!faction.canAddPlayer()) return faction.getTeamDisplayName() + " has reached its member limit.";
        ServerPlayer target = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return "That player is no longer online.";
        if (target.getTeam() != null) return target.getGameProfile().getName() + " is already a faction member.";
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        if (extra.hasFactionInvitation(factionId, targetId)) return target.getGameProfile().getName() + " already has an invitation from your faction.";
        extra.addFactionInvitation(factionId, targetId, player.getUUID(), System.currentTimeMillis());
        CampaignToastSender.send(target, "FACTION INVITATION", faction.getTeamDisplayName() + " has invited you to join.");
        return "Invitation sent to " + target.getGameProfile().getName() + ".";
    }

    private static String acceptFactionInvitation(ServerPlayer player, String factionIdRaw) {
        if (player.getTeam() != null) return "Leave your current faction before accepting another faction's invitation.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        String factionId = factionIdRaw == null ? "" : factionIdRaw.trim();
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        if (!extra.hasFactionInvitation(factionId, player.getUUID())) return "That faction invitation no longer exists.";
        RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(factionId);
        if (faction == null) { extra.removeFactionInvitation(factionId, player.getUUID()); return "That faction no longer exists."; }
        if (extra.factionProtected(factionId)) { extra.removeFactionInvitation(factionId, player.getUUID()); return "That faction is now protected from player membership."; }
        if (!faction.canAddPlayer()) return faction.getTeamDisplayName() + " has reached its member limit.";
        ServerPlayer leader = FactionEvents.recruitsFactionManager.getTeamLeader(faction, player.serverLevel());
        FactionEvents.addPlayerToTeam(leader, player.serverLevel(), factionId, player.getGameProfile().getName());
        if (player.getTeam() == null || !factionId.equals(player.getTeam().getName())) return "Recruits did not confirm the faction invitation.";
        extra.clearFactionInvitations(player.getUUID());
        clearFactionApplications(player.serverLevel(), player.getDisplayName().getString());
        CampaignData.get(player.serverLevel()).setFactionMemberRank(factionId, player.getUUID(), 0);
        PrototypeManager.broadcastProfiles(player.getServer());
        CampaignToastSender.send(player, "FACTION JOINED", "You accepted the invitation to " + faction.getTeamDisplayName() + ".");
        CampaignToastSender.faction(player.getServer(), factionId, "NEW FACTION MEMBER", player.getGameProfile().getName() + " accepted a faction invitation.");
        return "You joined " + faction.getTeamDisplayName() + ".";
    }

    private static String declineFactionInvitation(ServerPlayer player, String factionIdRaw) {
        String factionId = factionIdRaw == null ? "" : factionIdRaw.trim();
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
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
        boolean ownsSettlement = PrototypeData.get(player.serverLevel()).settlementsForFaction(factionId).stream()
                .anyMatch(settlement -> player.getUUID().equals(settlement.getOwnerId()));
        if (ownsSettlement) return "Transfer ownership of your faction settlements before leaving the faction.";
        String name = player.getGameProfile().getName();
        FactionEvents.leaveTeam(false, player, factionId, player.serverLevel(), false);
        if (player.getTeam() != null && factionId.equals(player.getTeam().getName())) return "Recruits did not confirm that you left the faction.";
        CampaignData.get(player.serverLevel()).clearFactionMemberRank(factionId, player.getUUID());
        PrototypeData.get(player.serverLevel()).clearSelection(player.getUUID());
        PrototypeManager.broadcastProfiles(player.getServer());
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
        boolean ownsSettlement = PrototypeData.get(player.serverLevel()).settlementsForFaction(factionId).stream()
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
        PrototypeManager.broadcastProfiles(player.getServer());
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
        PrototypeManager.broadcastProfiles(player.getServer());
        CampaignToastSender.faction(player.getServer(), factionId, "NEW FACTION LEADER", account + " is now the leader of " + confirmed.getTeamDisplayName() + ".");
        return "Faction leadership transferred to " + account + ".";
    }

    private static boolean canProcessFactionApplications(ServerPlayer player, String factionId) {
        if (player.createCommandSourceStack().hasPermission(2) || isFactionLeader(player, factionId)) return true;
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
        if (data.relation(own, target).state() == CampaignData.TreatyState.WAR) return "End the war before proposing a treaty.";
        data.createProposal(type, own, target, player.getUUID(), war, day);
        data.relation(own, target).changeRelationship(2);
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
        Campaign23Data extraData = Campaign23Data.get(player.serverLevel());
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
        String settlementId = split.length > 1 ? PrototypeData.normalizeId(split[1]) : "";
        CampaignData.WarGoal goal = parseWarGoal(split.length > 2 ? split[2] : "", type);
        String customCasusBelli = split.length > 3 ? PrototypeData.cleanText(split[3], "", 100) : "";
        if (goal == CampaignData.WarGoal.CUSTOM && customCasusBelli.isBlank()) return "Enter a custom Casus Belli before declaring war.";

        PrototypeData.SettlementRecord targetSettlement = settlementId.isBlank() ? null
                : PrototypeData.get(player.serverLevel()).getSettlement(settlementId);
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

        CampaignData.WarRecord war = data.createWar(type, goal, own, target, settlementId, player.getUUID(), day);
        if (goal == CampaignData.WarGoal.CUSTOM) data.setWarCustomCasusBelli(war.id(), customCasusBelli);
        int reliabilityPenalty = switch (previousState) {
            case MILITARY_ALLIANCE, DEFENSIVE_ALLIANCE -> -25;
            case NON_AGGRESSION -> -15;
            default -> -5;
        };
        data.changeFactionReliability(own, reliabilityPenalty);
        if (!syncRelation(player.serverLevel(), own, target, CampaignData.TreatyState.WAR)) {
            data.endWar(war.id(), day, "Declaration aborted: Recruits relation synchronization failed.");
            return "War declaration aborted because Recruits did not confirm ENEMY relations in both directions.";
        }
        Campaign23Manager.applyEmergencyMobilization(player.serverLevel(), target);
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
        String sourceId = PrototypeData.normalizeId(split[1]);
        String targetId = PrototypeData.normalizeId(split[2]);
        CampaignData.WarGoal goal = parseWarGoal(split[3], CampaignData.WarType.SETTLEMENT_WAR);
        String custom = split.length > 4 ? PrototypeData.cleanText(split[4], "", 100) : "";
        PrototypeData settlements = PrototypeData.get(player.serverLevel());
        PrototypeData.SettlementRecord source = settlements.getSettlement(sourceId);
        PrototypeData.SettlementRecord target = settlements.getSettlement(targetId);
        if (source == null || target == null) return "Select both your settlement and the target settlement.";
        if (source.getId().equals(target.getId())) return "A settlement cannot declare war on itself.";
        if (!PrototypeManager.canManage(player, source)) return "You do not have permission to declare war for " + source.getName() + ".";
        Campaign23Data extraData = Campaign23Data.get(player.serverLevel());
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
        Campaign23Manager.applyEmergencyMobilization(player.serverLevel(), target);
        return source.getName() + " declared a Settlement War on " + target.getName() + ". Casus Belli: " + cb + ". War ID: " + war.id();
    }

    private static String joinSettlementWar(ServerPlayer player, CampaignData data, String own, UUID warId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot commit the faction to a war.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || war.type() != CampaignData.WarType.SETTLEMENT_WAR) return "Select an active Settlement War.";
        boolean attackerSide;
        if (own.equals(war.attackerFaction())) attackerSide = true;
        else if (own.equals(war.defenderFaction())) attackerSide = false;
        else return "Your faction is not the parent faction of either settlement.";
        if ((attackerSide && war.attackerFactionJoined()) || (!attackerSide && war.defenderFactionJoined())) return "Your faction has already joined this war.";
        war.joinParentFaction(attackerSide);
        Campaign23Data.get(player.serverLevel()).setIgnoredWarIntervention(war.id(), own, false);
        data.setDirty();
        String side = attackerSide ? "attacking" : "defending";
        String settlementId = attackerSide ? war.attackerSettlement() : war.defenderSettlement();
        PrototypeData.SettlementRecord settlement = PrototypeData.get(player.serverLevel()).getSettlement(settlementId);
        String settlementName = settlement == null ? settlementId : settlement.getName();
        String escalation = war.escalationName();
        if (war.attackerFactionJoined() && war.defenderFactionJoined()
                && !war.attackerFaction().isBlank() && !war.defenderFaction().isBlank()
                && !war.attackerFaction().equals(war.defenderFaction())) {
            CampaignData.DiplomacyRecord relation = data.relation(war.attackerFaction(), war.defenderFaction());
            relation.setState(CampaignData.TreatyState.WAR, day);
            relation.changeRelationship(-40);
            if (!syncRelation(player.serverLevel(), war.attackerFaction(), war.defenderFaction(), CampaignData.TreatyState.WAR)) {
                return "Campaign escalation was recorded, but Recruits relation synchronization failed. An administrator should inspect the war.";
            }
        }
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR, "War escalated",
                factionName(own) + " joined the " + side + " settlement, escalating the conflict to " + escalation + ".",
                day, own, settlementId, CampaignData.NewsVisibility.PUBLIC);
        CampaignToastSender.faction(player.getServer(), war.attackerFaction(), "WAR ESCALATED", factionName(own) + " joined " + settlementName + ". Conflict: " + escalation);
        CampaignToastSender.faction(player.getServer(), war.defenderFaction(), "WAR ESCALATED", factionName(own) + " joined " + settlementName + ". Conflict: " + escalation);
        Campaign23Manager.applyEmergencyMobilization(player.serverLevel(), attackerSide ? war.defenderFaction() : war.attackerFaction());
        return "Your faction joined the war. Conflict is now " + escalation + ".";
    }

    private static String ignoreSettlementWar(ServerPlayer player, CampaignData data, String own, UUID warId) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot answer this settlement war.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || war.type() != CampaignData.WarType.SETTLEMENT_WAR) return "Select an active Settlement War.";
        if (!own.equals(war.defenderFaction())) return "Only the defending settlement's parent faction may formally ignore the conflict.";
        if (war.defenderFactionJoined()) return "Your faction has already joined this war.";
        Campaign23Data.get(player.serverLevel()).setIgnoredWarIntervention(war.id(), own, true);
        return "Your faction will not intervene in this Settlement War. You may still send aid or join later if circumstances change.";
    }

    private static String sendWarAid(ServerPlayer player, CampaignData data, String own, UUID warId, String extra, long day) {
        if (!canManageDiplomacy(player, own, 2)) return "Your faction rank cannot authorize war aid.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "Select an active war.";
        if (!own.equals(war.attackerFaction()) && !own.equals(war.defenderFaction())) return "Only a parent faction may send settlement aid through this panel.";
        String targetSettlement = own.equals(war.attackerFaction()) ? war.attackerSettlement() : war.defenderSettlement();
        if (targetSettlement.isBlank()) return "This war has no settlement aid target.";
        String[] split = extra == null ? new String[0] : extra.split("\\|", -1);
        int manpower = parsePositive(split, 0); int wealth = parsePositive(split, 1); int food = parsePositive(split, 2);
        boolean recurring = split.length > 3 && Boolean.parseBoolean(split[3]);
        if (manpower + wealth + food <= 0) return "Enter at least one aid amount.";
        PrototypeData.SettlementRecord target = PrototypeData.get(player.serverLevel()).getSettlement(targetSettlement);
        if (target == null) return "The aided settlement no longer exists.";
        String payment = transferFactionAid(player.serverLevel(), own, target, manpower, wealth, food);
        if (payment != null) return payment;
        if (recurring) Campaign23Data.get(player.serverLevel()).addWarAid(war.id(),
                new Campaign23Data.AidRecord(UUID.randomUUID(), own, targetSettlement, manpower, wealth, food, true, day + 1));
        data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.NORMAL, "War aid sent",
                factionName(own) + " sent aid to " + target.getName() + ".", day, own, targetSettlement, CampaignData.NewsVisibility.PUBLIC);
        if (target.getFactionTeam().isBlank()) CampaignToastSender.settlementManagers(player.getServer(), target.getId(), "AID RECEIVED", factionName(own) + " sent aid to " + target.getName() + ".");
        else CampaignToastSender.faction(player.getServer(), target.getFactionTeam(), "AID RECEIVED", factionName(own) + " sent aid to " + target.getName() + ".");
        return "Aid delivered to " + target.getName() + (recurring ? " and scheduled daily." : ".");
    }

    private static int parsePositive(String[] split, int index) {
        if (index >= split.length) return 0;
        try { return Math.max(0, Integer.parseInt(split[index])); } catch (NumberFormatException ignored) { return 0; }
    }

    static String transferFactionAid(ServerLevel level, String faction, PrototypeData.SettlementRecord target, int manpower, int wealth, int food) {
        Campaign23Data extra = Campaign23Data.get(level);
        if (extra.factionWealth(faction) < wealth) return "Your faction treasury does not have enough wealth.";
        List<PrototypeData.SettlementRecord> sources = PrototypeData.get(level).settlementsForFaction(faction).stream()
                .filter(source -> !source.getId().equals(target.getId())).toList();
        int availableMp = sources.stream().mapToInt(PrototypeData.SettlementRecord::getAvailableManpower).sum();
        int availableFood = sources.stream().mapToInt(PrototypeData.SettlementRecord::getFood).sum();
        if (availableMp < manpower) return "Your faction settlements do not have enough available manpower.";
        if (availableFood < food) return "Your faction settlements do not have enough food.";
        extra.spendFactionWealth(faction, wealth);
        int mpLeft = manpower, foodLeft = food;
        for (PrototypeData.SettlementRecord source : sources) {
            int mpTake = Math.min(mpLeft, source.getAvailableManpower());
            if (mpTake > 0) { source.spendAvailableManpower(mpTake); mpLeft -= mpTake; }
            int foodTake = Math.min(foodLeft, source.getFood());
            if (foodTake > 0) { source.spendFood(foodTake); foodLeft -= foodTake; }
            if (mpLeft <= 0 && foodLeft <= 0) break;
        }
        target.changeAvailableManpower(manpower);
        target.changeFood(food);
        target.changeWealth(wealth);
        if (wealth > 0) extra.recordWealthDeposit(target.getId(), wealth, level.getDayTime() / 24000L);
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


    private static String offerPeaceTerms(ServerPlayer player, CampaignData data, UUID warId, String encoded, long day) {
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "That war is not active.";
        PrototypeData settlements = PrototypeData.get(player.serverLevel());
        String ownFaction = factionId(player);
        boolean attacker = (!ownFaction.isBlank() && (ownFaction.equals(war.attackerFaction()) || war.supportingAttackers().contains(ownFaction)))
                || (!war.attackerSettlement().isBlank() && settlements.getSettlement(war.attackerSettlement()) != null && PrototypeManager.canManage(player, settlements.getSettlement(war.attackerSettlement())));
        boolean defender = (!ownFaction.isBlank() && (ownFaction.equals(war.defenderFaction()) || war.supportingDefenders().contains(ownFaction)))
                || (!war.defenderSettlement().isBlank() && settlements.getSettlement(war.defenderSettlement()) != null && PrototypeManager.canManage(player, settlements.getSettlement(war.defenderSettlement())));
        if (!attacker && !defender && !player.createCommandSourceStack().hasPermission(2)) return "You are not authorized to negotiate this war.";
        if (!ownFaction.isBlank() && !canManageDiplomacy(player, ownFaction, 3) && !player.createCommandSourceStack().hasPermission(2))
            return "Your faction rank cannot negotiate peace.";
        String proposerFaction = ownFaction;
        String targetFaction = attacker ? war.defenderFaction() : war.attackerFaction();
        String proposerSettlement = attacker ? war.attackerSettlement() : war.defenderSettlement();
        String targetSettlement = attacker ? war.defenderSettlement() : war.attackerSettlement();
        List<Campaign23Data.PeaceTerm> terms = parsePeaceTerms(encoded);
        UUID proposal = UUID.randomUUID();
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        extra.putPeaceOffer(new Campaign23Data.PeaceOfferRecord(proposal, war.id(), proposerFaction, targetFaction,
                proposerSettlement, targetSettlement, terms, System.currentTimeMillis()));
        String targetLabel = !targetFaction.isBlank() ? factionName(targetFaction) : settlementName(player.serverLevel(), targetSettlement);
        toastPoliticalSide(player.getServer(), targetFaction, targetSettlement, "PEACE OFFER RECEIVED",
                (proposerFaction.isBlank() ? settlementName(player.serverLevel(), proposerSettlement) : factionName(proposerFaction))
                        + " proposed peace with " + terms.size() + " term(s).");
        data.addNews(CampaignData.NewsCategory.DIPLOMACY, CampaignData.NewsImportance.NORMAL, "Peace offer sent",
                (proposerFaction.isBlank() ? settlementName(player.serverLevel(), proposerSettlement) : factionName(proposerFaction)) + " sent peace terms to " + targetLabel + ".",
                day, targetFaction, targetSettlement, CampaignData.NewsVisibility.FACTION);
        return "Peace treaty sent to " + targetLabel + " with " + terms.size() + " term(s).";
    }

    private static List<Campaign23Data.PeaceTerm> parsePeaceTerms(String encoded) {
        List<Campaign23Data.PeaceTerm> terms = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) return terms;
        for (String raw : encoded.split(";")) {
            String[] p = raw.split("~", -1); if (p.length < 5) continue;
            int amount = parseInt(p[2], 0, 1_000_000); int duration = parseInt(p[3], 0, 3650);
            String type = p[0].toUpperCase(Locale.ROOT); String target = PrototypeData.cleanText(p[1], "", 80); String text = PrototypeData.cleanText(p[4], "", 120);
            if (List.of("WEALTH","MANPOWER","REPARATIONS","NAP","CONVERT_FAITH","TRANSFER_SETTLEMENT","TRANSFER_CLAIM","RECOGNIZE_INDEPENDENCE").contains(type))
                terms.add(new Campaign23Data.PeaceTerm(type, target, amount, duration, text));
        }
        return List.copyOf(terms);
    }

    private static String resolvePeaceTerms(ServerPlayer player, CampaignData data, UUID offerId, boolean accept, long day) {
        Campaign23Data extra = Campaign23Data.get(player.serverLevel());
        Campaign23Data.PeaceOfferRecord offer = offerId == null ? null : extra.peaceOffer(offerId);
        if (offer == null) return "That peace offer is no longer available.";
        CampaignData.WarRecord war = offer.warId() == null ? null : data.getWar(offer.warId());
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) { extra.removePeaceOffer(offerId); return "That war has already ended."; }
        String own = factionId(player);
        PrototypeData.SettlementRecord targetSettlement = PrototypeData.get(player.serverLevel()).getSettlement(offer.targetSettlement());
        boolean authorized = (!offer.targetFaction().isBlank() && offer.targetFaction().equals(own) && canManageDiplomacy(player, own, 3))
                || (targetSettlement != null && PrototypeManager.canManage(player, targetSettlement)) || player.createCommandSourceStack().hasPermission(2);
        if (!authorized) return "You are not authorized to answer those peace terms.";
        int score = extra.warScore(war.id());
        boolean proposerAttacker = offer.proposerSettlement().equals(war.attackerSettlement()) || (!offer.proposerFaction().isBlank() && offer.proposerFaction().equals(war.attackerFaction()));
        boolean targetCapitulated = proposerAttacker ? score >= 90 : score <= -90;
        if (!accept && targetCapitulated) return "Your side has capitulated and cannot reject these peace terms.";
        if (!accept) {
            extra.removePeaceOffer(offerId);
            toastPoliticalSide(player.getServer(), offer.proposerFaction(), offer.proposerSettlement(),
                    "PEACE REJECTED", "Your peace terms were rejected.");
            return "Peace terms rejected.";
        }
        String error = validatePeaceTerms(player.serverLevel(), offer, war);
        if (error != null) return "Peace treaty could not be applied: " + error;
        applyPeaceTerms(player.serverLevel(), offer, war, day);
        data.endWar(war.id(), day, targetCapitulated ? "Capitulation peace terms enforced." : "Negotiated peace terms accepted.");
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

    private static String validatePeaceTerms(ServerLevel level, Campaign23Data.PeaceOfferRecord offer, CampaignData.WarRecord war) {
        Campaign23Data extra = Campaign23Data.get(level);
        PrototypeData settlements = PrototypeData.get(level);
        String winnerFaction = offer.proposerFaction();
        String loserFaction = offer.targetFaction();
        PrototypeData.SettlementRecord winnerSettlement = settlements.getSettlement(offer.proposerSettlement());
        PrototypeData.SettlementRecord loserSettlement = settlements.getSettlement(offer.targetSettlement());
        int wealthNeed = 0, manpowerNeed = 0;

        for (Campaign23Data.PeaceTerm term : offer.terms()) {
            switch (term.type()) {
                case "WEALTH" -> wealthNeed += term.amount();
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
                    PrototypeData.SettlementRecord transferred = settlements.getSettlement(term.targetId());
                    if (transferred == null) return "a transferred settlement no longer exists";
                    if (!settlementBelongsToLosingSide(transferred, loserFaction, loserSettlement)) {
                        return transferred.getName() + " does not belong to the side receiving these demands";
                    }
                    if (war.type() == CampaignData.WarType.SETTLEMENT_WAR && loserSettlement != null
                            && !transferred.getId().equals(loserSettlement.getId())) {
                        return "a Settlement War may only transfer its defending/negotiating settlement, not unrelated territory";
                    }
                }
                case "TRANSFER_CLAIM" -> {
                    UUID claimId;
                    try { claimId = UUID.fromString(term.targetId()); }
                    catch (IllegalArgumentException e) { return "an invalid claim was requested"; }
                    PrototypeData.SettlementRecord source = settlementContainingClaim(settlements, claimId);
                    if (source == null) return "the demanded claim is no longer linked to a settlement";
                    if (!settlementBelongsToLosingSide(source, loserFaction, loserSettlement)) {
                        return "the demanded claim is not controlled by the side receiving these demands";
                    }
                    PrototypeData.SettlementRecord destination = settlements.getSettlement(term.text());
                    if (destination == null) return "the destination settlement for a transferred claim no longer exists";
                    if (!settlementBelongsToWinningSide(destination, winnerFaction, winnerSettlement)) {
                        return "the claim destination is not controlled by the side proposing these terms";
                    }
                    if (source.getId().equals(destination.getId())) return "a claim cannot be transferred back into the same settlement";
                }
                case "RECOGNIZE_INDEPENDENCE" -> {
                    PrototypeData.SettlementRecord released = settlements.getSettlement(term.targetId());
                    if (released == null) return "the settlement requested for independence no longer exists";
                    if (loserFaction.isBlank() || !loserFaction.equals(released.getFactionTeam())) {
                        return "the side receiving these demands does not rule the settlement requested for independence";
                    }
                }
                default -> { }
            }
        }

        int availableWealth = !loserFaction.isBlank() ? extra.factionWealth(loserFaction) : loserSettlement == null ? 0 : loserSettlement.getWealth();
        int availableMp = !loserFaction.isBlank() ? settlements.settlementsForFaction(loserFaction).stream().mapToInt(PrototypeData.SettlementRecord::getAvailableManpower).sum()
                : loserSettlement == null ? 0 : loserSettlement.getAvailableManpower();
        if (wealthNeed > availableWealth) return "the losing side no longer has the demanded wealth";
        if (manpowerNeed > availableMp) return "the losing side no longer has the demanded manpower";
        return null;
    }

    private static PrototypeData.SettlementRecord settlementContainingClaim(PrototypeData settlements, UUID claimId) {
        if (claimId == null) return null;
        return settlements.getSettlements().stream()
                .filter(value -> value.getClaimLinks().stream().anyMatch(link -> claimId.equals(link.id())))
                .findFirst().orElse(null);
    }

    private static boolean settlementBelongsToLosingSide(PrototypeData.SettlementRecord settlement, String loserFaction,
                                                          PrototypeData.SettlementRecord loserSettlement) {
        if (settlement == null) return false;
        if (loserFaction != null && !loserFaction.isBlank()) return loserFaction.equals(settlement.getFactionTeam());
        return loserSettlement != null && loserSettlement.getId().equals(settlement.getId());
    }

    private static boolean settlementBelongsToWinningSide(PrototypeData.SettlementRecord settlement, String winnerFaction,
                                                           PrototypeData.SettlementRecord winnerSettlement) {
        if (settlement == null) return false;
        if (winnerFaction != null && !winnerFaction.isBlank()) return winnerFaction.equals(settlement.getFactionTeam());
        return winnerSettlement != null && winnerSettlement.getId().equals(settlement.getId());
    }

    private static void applyPeaceTerms(ServerLevel level, Campaign23Data.PeaceOfferRecord offer, CampaignData.WarRecord war, long day) {
        Campaign23Data extra = Campaign23Data.get(level); PrototypeData settlements = PrototypeData.get(level);
        String winnerFaction = offer.proposerFaction(), loserFaction = offer.targetFaction();
        PrototypeData.SettlementRecord winnerSettlement = settlements.getSettlement(offer.proposerSettlement());
        if (winnerSettlement == null && !winnerFaction.isBlank()) winnerSettlement = settlements.settlementsForFaction(winnerFaction).stream().findFirst().orElse(null);
        PrototypeData.SettlementRecord loserSettlement = settlements.getSettlement(offer.targetSettlement());
        for (Campaign23Data.PeaceTerm term : offer.terms()) {
            switch (term.type()) {
                case "WEALTH" -> {
                    takeWealth(extra, loserFaction, loserSettlement, term.amount());
                    giveWealth(extra, winnerFaction, winnerSettlement, term.amount());
                }
                case "MANPOWER" -> {
                    int moved = takeManpower(settlements, loserFaction, loserSettlement, term.amount());
                    if (winnerSettlement != null && moved > 0) {
                        extra.addForeignManpower(winnerSettlement.getId(), moved);
                        // Foreign manpower is a temporary recruitable pool. It deliberately does NOT increase the
                        // settlement's native maximum manpower, and the social penalty persists until the pool is used.
                        extra.addModifier(winnerSettlement.getId(), Campaign23Manager.standardModifier(level, winnerSettlement.getId(), "foreign_soldiers", 0L));
                    }
                }
                case "REPARATIONS" -> { if (!loserFaction.isBlank() && !winnerFaction.isBlank() && term.amount() > 0 && term.durationDays() > 0) extra.addReparation(new Campaign23Data.ReparationRecord(UUID.randomUUID(), loserFaction, winnerFaction, term.amount(), term.durationDays(), day + 1)); }
                case "NAP" -> { if (!winnerFaction.isBlank() && !loserFaction.isBlank() && term.durationDays() > 0) { dataRelationNap(level, winnerFaction, loserFaction, day); extra.setForcedNap(winnerFaction, loserFaction, day + term.durationDays()); } }
                case "CONVERT_FAITH" -> convertSideFaith(level, loserFaction, loserSettlement, term.text());
                case "TRANSFER_SETTLEMENT" -> transferSettlementByTreaty(level, term.targetId(), winnerFaction);
                case "TRANSFER_CLAIM" -> transferClaimByTreaty(level, term.targetId(), term.text());
                case "RECOGNIZE_INDEPENDENCE" -> { PrototypeData.SettlementRecord s = settlements.getSettlement(term.targetId()); if (s != null) s.setFactionTeam(""); }
            }
        }
    }

    private static void takeWealth(Campaign23Data extra, String faction, PrototypeData.SettlementRecord settlement, int amount) {
        if (amount <= 0) return; if (!faction.isBlank()) extra.changeFactionWealth(faction, -amount); else if (settlement != null) settlement.changeWealth(-amount);
    }
    private static void giveWealth(Campaign23Data extra, String faction, PrototypeData.SettlementRecord settlement, int amount) {
        if (amount <= 0) return; if (!faction.isBlank()) extra.changeFactionWealth(faction, amount); else if (settlement != null) settlement.changeWealth(amount);
    }
    private static int takeManpower(PrototypeData settlements, String faction, PrototypeData.SettlementRecord settlement, int amount) {
        int left = Math.max(0, amount); if (!faction.isBlank()) {
            for (PrototypeData.SettlementRecord s : settlements.settlementsForFaction(faction)) { int take = Math.min(left, s.getAvailableManpower()); if (take > 0) { s.spendAvailableManpower(take); left -= take; } if (left <= 0) break; }
        } else if (settlement != null) { int take = Math.min(left, settlement.getAvailableManpower()); settlement.spendAvailableManpower(take); left -= take; }
        return amount - left;
    }
    private static void dataRelationNap(ServerLevel level, String a, String b, long day) { CampaignData.get(level).relation(a,b).setState(CampaignData.TreatyState.NON_AGGRESSION, day); syncRelation(level,a,b,CampaignData.TreatyState.NON_AGGRESSION); }
    private static void convertSideFaith(ServerLevel level, String faction, PrototypeData.SettlementRecord settlement, String faith) {
        if (!CampaignDefinitions.isValidReligion(faith)) return; UUID ruler = null;
        if (!faction.isBlank() && FactionEvents.recruitsFactionManager != null) { RecruitsFaction f = FactionEvents.recruitsFactionManager.getFactionByStringID(faction); if (f != null) ruler = f.getTeamLeaderUUID(); }
        if (ruler == null && settlement != null) ruler = settlement.getOwnerId();
        PrototypeData.CharacterRecord character = ruler == null ? null : PrototypeData.get(level).getCharacter(ruler); if (character != null) character.setFaith(faith);
    }
    private static void transferSettlementByTreaty(ServerLevel level, String settlementId, String winnerFaction) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(level).getSettlement(settlementId); if (settlement == null) return;
        settlement.setFactionTeam(winnerFaction);
        if (!winnerFaction.isBlank() && FactionEvents.recruitsFactionManager != null && ClaimEvents.recruitsClaimManager != null) {
            RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(winnerFaction);
            if (faction != null) for (PrototypeData.ClaimLink link : settlement.getClaimLinks()) { RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(link.id()); if (claim != null) { claim.setOwnerFaction(faction); ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim); } }
            ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(level);
        }
        settlement.addChronicle("Settlement transferred by peace treaty to " + (winnerFaction.isBlank() ? "independence" : factionName(winnerFaction)) + ".");
    }
    private static void transferClaimByTreaty(ServerLevel level, String claimIdRaw, String destinationSettlementId) {
        UUID claimId; try { claimId = UUID.fromString(claimIdRaw); } catch (IllegalArgumentException e) { return; }
        PrototypeData data = PrototypeData.get(level); PrototypeData.SettlementRecord destination = data.getSettlement(destinationSettlementId); if (destination == null) return;
        PrototypeData.SettlementRecord source = data.getSettlements().stream().filter(s -> s.getClaimLinks().stream().anyMatch(c -> c.id().equals(claimId))).findFirst().orElse(null);
        if (source == null) return; PrototypeData.ClaimLink link = source.getClaimLinks().stream().filter(c -> c.id().equals(claimId)).findFirst().orElse(null); if (link == null) return;
        source.unlinkClaim(link.dimension(), claimId); destination.linkClaim(link.dimension(), claimId, link.name());
        if (ClaimEvents.recruitsClaimManager != null && FactionEvents.recruitsFactionManager != null && !destination.getFactionTeam().isBlank()) {
            RecruitsClaim claim = ClaimEvents.recruitsClaimManager.getClaim(claimId); RecruitsFaction faction = FactionEvents.recruitsFactionManager.getFactionByStringID(destination.getFactionTeam());
            if (claim != null && faction != null) { claim.setOwnerFaction(faction); ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim); ClaimEvents.recruitsClaimManager.broadcastClaimsToAll(level); }
        }
    }
    private static void toastSettlementManagers(MinecraftServer server, String settlementId, String title, String body) {
        PrototypeData.SettlementRecord settlement = PrototypeData.get(server.overworld()).getSettlement(settlementId); if (settlement == null) return;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) if (PrototypeManager.canManage(online, settlement)) CampaignToastSender.send(online, title, body);
    }

    /** Notify whichever political entity actually owns a side of a war. Settlement wars can
     * remain independent of faction participation, so diplomacy feedback must not disappear
     * just because a faction id is blank. */
    private static void toastPoliticalSide(MinecraftServer server, String factionId, String settlementId, String title, String body) {
        if (factionId != null && !factionId.isBlank()) CampaignToastSender.faction(server, factionId, title, body);
        else if (settlementId != null && !settlementId.isBlank()) toastSettlementManagers(server, settlementId, title, body);
    }
    private static String settlementName(ServerLevel level, String id) { PrototypeData.SettlementRecord s = PrototypeData.get(level).getSettlement(id); return s == null ? id : s.getName(); }
    private static int parseInt(String raw, int min, int max) { try { return Math.max(min, Math.min(max, Integer.parseInt(raw))); } catch (Exception ignored) { return min; } }

    private static String offerPeace(ServerPlayer player, CampaignData data, String own, UUID warId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot offer peace.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || !war.involves(own)) return "That war is not active.";
        String target = own.equals(war.attackerFaction()) ? war.defenderFaction() : war.attackerFaction();
        data.createProposal(CampaignData.ProposalType.PEACE, own, target, player.getUUID(), war.id(), day);
        CampaignToastSender.faction(player.getServer(), target, "PEACE OFFER RECEIVED", factionName(own) + " has offered peace.");
        return "Peace offer sent to " + factionName(target) + ".";
    }

    private static String callToWar(ServerPlayer player, CampaignData data, String own, String target, UUID warId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot call allies to war.";
        CampaignData.WarRecord war = warId == null ? null : data.getWar(warId);
        if (war == null || war.status() != CampaignData.WarStatus.ACTIVE || !war.involves(own)) return "Select an active war.";
        if (!validOtherFaction(own, target)) return "Select an allied faction.";
        if (war.involves(target)) return factionName(target) + " is already participating in this war.";
        CampaignData.TreatyState alliance = data.relation(own, target).state();
        if (!alliance.isAlliance()) return "Only an ally can be called to war.";
        boolean ownOnAttackerSide = war.attackerFaction().equals(own) || war.supportingAttackers().contains(own);
        if (alliance == CampaignData.TreatyState.DEFENSIVE_ALLIANCE && ownOnAttackerSide) {
            return "A defensive ally can only be called when your side is defending.";
        }
        data.createProposal(CampaignData.ProposalType.CALL_TO_WAR, own, target, player.getUUID(), war.id(), day);
        CampaignToastSender.faction(player.getServer(), target, "CALL TO WAR", factionName(own) + " requests your aid in " + war.displayConflictType() + ".");
        return "Call to war sent to " + factionName(target) + ".";
    }

    private static String acceptProposal(ServerPlayer player, CampaignData data, String own, UUID proposalId, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot accept treaties.";
        CampaignData.DiplomacyProposal proposal = proposalId == null ? null : data.getProposal(proposalId);
        if (proposal == null || proposal.status() != CampaignData.ProposalStatus.PENDING || !own.equals(proposal.toFaction())) return "That proposal is unavailable.";
        if (proposal.type() == CampaignData.ProposalType.CALL_TO_WAR && proposal.linkedWar() != null) {
            CampaignData.WarRecord war = data.getWar(proposal.linkedWar());
            if (war == null || war.status() != CampaignData.WarStatus.ACTIVE) return "The linked war has already ended.";
            data.resolveProposal(proposal.id(), CampaignData.ProposalStatus.ACCEPTED);
            boolean attackerSide = war.attackerFaction().equals(proposal.fromFaction()) || war.supportingAttackers().contains(proposal.fromFaction());
            war.addSupporter(own, attackerSide);
            String enemy = attackerSide ? war.defenderFaction() : war.attackerFaction();
            CampaignData.DiplomacyRecord relation = data.relation(own, enemy);
            relation.setState(CampaignData.TreatyState.WAR, day);
            relation.changeRelationship(-30);
            syncRelation(player.serverLevel(), own, enemy, CampaignData.TreatyState.WAR);
            data.relation(proposal.fromFaction(), own).changeRelationship(15);
            // Reliability is earned from actual diplomatic conduct. Answering an ally's
            // legitimate call to war is one of the clearest positive signals.
            data.changeFactionReliability(own, 8);
            data.addNews(CampaignData.NewsCategory.WARS, CampaignData.NewsImportance.MAJOR,
                    "Faction joined a war", factionName(own) + " answered " + factionName(proposal.fromFaction()) + "'s call to war.",
                    day, own, war.targetSettlement(), CampaignData.NewsVisibility.PUBLIC);
            CampaignToastSender.faction(player.getServer(), proposal.fromFaction(), "CALL TO WAR ACCEPTED", factionName(own) + " has joined your war.");
            CampaignToastSender.faction(player.getServer(), own, "WAR JOINED", "Your faction joined " + factionName(proposal.fromFaction()) + "'s war.");
            return "Call to war accepted. Your faction has joined the conflict.";
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
            Campaign23Data.get(player.serverLevel()).clearWarInterventionState(proposal.linkedWar());
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
        relation.changeRelationship(state.isAlliance() ? 15 : 8);
        syncRelation(player.serverLevel(), proposal.fromFaction(), proposal.toFaction(), state);
        CampaignToastSender.relationChanged(player.getServer(), proposal.fromFaction(), proposal.toFaction(), state.displayName());
        return pretty(proposal.type().name()) + " accepted.";
    }

    private static String rejectProposal(ServerPlayer player, CampaignData data, String own, UUID proposalId) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot reject treaties.";
        CampaignData.DiplomacyProposal proposal = proposalId == null ? null : data.getProposal(proposalId);
        if (proposal == null || proposal.status() != CampaignData.ProposalStatus.PENDING || !own.equals(proposal.toFaction())) return "That proposal is unavailable.";
        data.resolveProposal(proposal.id(), CampaignData.ProposalStatus.REJECTED);
        data.relation(proposal.fromFaction(), proposal.toFaction()).changeRelationship(-5);
        CampaignToastSender.faction(player.getServer(), proposal.fromFaction(), "PROPOSAL REJECTED", factionName(own) + " rejected your " + pretty(proposal.type().name()) + " proposal.");
        return "Proposal rejected.";
    }

    private static String breakTreaty(ServerPlayer player, CampaignData data, String own, String target, long day) {
        if (!canManageDiplomacy(player, own, 3)) return "Your faction rank cannot break treaties.";
        if (!validOtherFaction(own, target)) return "Select another faction.";
        CampaignData.DiplomacyRecord relation = data.relation(own, target);
        if (relation.state() == CampaignData.TreatyState.WAR || relation.state() == CampaignData.TreatyState.NEUTRAL) return "There is no treaty to break.";
        relation.setState(CampaignData.TreatyState.NEUTRAL, day);
        relation.changeRelationship(-20);
        data.changeFactionReliability(own, -15);
        syncRelation(player.serverLevel(), own, target, CampaignData.TreatyState.NEUTRAL);
        CampaignToastSender.relationChanged(player.getServer(), own, target, "Neutral — treaty broken");
        data.addNews(CampaignData.NewsCategory.DIPLOMACY, CampaignData.NewsImportance.NORMAL,
                "Treaty broken", factionName(own) + " broke its treaty with " + factionName(target) + ".",
                day, own, "", CampaignData.NewsVisibility.PUBLIC);
        return "Treaty with " + factionName(target) + " broken.";
    }

    private static void synchronizeWarPeace(ServerLevel level, CampaignData data, CampaignData.WarRecord war, long day) {
        List<String> attackers = new ArrayList<>(); attackers.add(war.attackerFaction()); attackers.addAll(war.supportingAttackers());
        List<String> defenders = new ArrayList<>(); defenders.add(war.defenderFaction()); defenders.addAll(war.supportingDefenders());
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
            case "MARRY_NPC" -> {
                CampaignData.NpcSpouse spouse = data.getCandidate(player.getUUID(), value);
                if (spouse == null) result = "That generated candidate is no longer available.";
                else {
                    CampaignData.MarriageRecord existing = data.getMarriage(player.getUUID());
                    if (existing == null) {
                        data.marryNpc(player.getUUID(), spouse, day);
                        data.addNews(CampaignData.NewsCategory.CHARACTERS, CampaignData.NewsImportance.NORMAL,
                                "Marriage", PrototypeManager.characterOrAccountName(player) + " married " + spouse.name() + ".",
                                day, factionId(player), "", CampaignData.NewsVisibility.PUBLIC);
                        result = "You married " + spouse.name() + ".";
                    } else {
                        PrototypeData.CharacterRecord character = PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID());
                        boolean permitsAdditionalWives = character != null && character.getFaith().equalsIgnoreCase("Islam");
                        if (!permitsAdditionalWives) result = "Your faith permits only one active spouse.";
                        else if (!spouse.gender().equalsIgnoreCase("Female")) result = "Additional spouses under this faith rule must be female candidates.";
                        else if (data.spouseCount(player.getUUID()) >= 4) result = "You already have the maximum of four spouses.";
                        else if (data.marryAdditionalNpc(player.getUUID(), spouse, day)) {
                            int count = data.spouseCount(player.getUUID());
                            int percent = CampaignData.spouseEffectPercent(count - 1);
                            data.addNews(CampaignData.NewsCategory.CHARACTERS, CampaignData.NewsImportance.NORMAL,
                                    "Marriage", PrototypeManager.characterOrAccountName(player) + " married " + spouse.name() + " as an additional spouse.",
                                    day, factionId(player), "", CampaignData.NewsVisibility.PUBLIC);
                            result = "You married " + spouse.name() + ". As spouse " + count + ", their settlement effects operate at " + percent + "%.";
                        } else result = "That additional marriage cannot be made.";
                    }
                }
            }
            case "REFRESH" -> {
                if (!data.canRefreshCandidates(player.getUUID(), day)) result = "New candidates become available on day " + data.nextCandidateRefreshDay(player.getUUID()) + ".";
                else {
                    Campaign23Data extra = Campaign23Data.get(player.serverLevel());
                    Campaign23Data.CandidateHistory history = extra.candidateHistory(player.getUUID());
                    PrototypeData.CharacterRecord character = PrototypeData.get(player.serverLevel()).getCharacter(player.getUUID());
                    int quality = character == null ? 0 : CampaignDefinitions.totalEffect(character.getTraits(), CampaignDefinitions.EffectKind.SPOUSE_QUALITY);
                    data.refreshCandidates(player.getUUID(), day, history.rejectedIds(), history.seenIds(), quality);
                    result = quality > 0 ? "A new set of spouse candidates has arrived. Your Charisma improved the selection." : "A new set of spouse candidates has arrived.";
                }
            }
            case "SET_GENDER" -> {
                String gender = value == null ? "ANY" : value.toUpperCase(Locale.ROOT);
                if (!gender.equals("ANY") && !gender.equals("MALE") && !gender.equals("FEMALE")) result = "Use Any, Male, or Female.";
                else { Campaign23Data.get(player.serverLevel()).setCandidateGender(player.getUUID(), gender); result = "Marriage candidate filter set to " + gender.toLowerCase(Locale.ROOT) + "."; }
            }
            case "REJECT_CANDIDATE" -> {
                CampaignData.NpcSpouse spouse = data.getCandidate(player.getUUID(), value);
                if (spouse == null) result = "That candidate is no longer available.";
                else { Campaign23Data extra = Campaign23Data.get(player.serverLevel()); extra.rejectCandidate(player.getUUID(), spouse.id()); extra.rejectCandidate(player.getUUID(), spouse.name()); data.rejectCandidate(player.getUUID(), spouse.id()); result = spouse.name() + " was rejected and will not be generated for you again."; }
            }
            case "PROPOSE_PLAYER" -> {
                ServerPlayer target = targetPlayer == null || player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(targetPlayer);
                if (target == null) result = "Select an online player.";
                else {
                    PrototypeData.CharacterRecord targetCharacter = PrototypeData.get(player.serverLevel()).getCharacter(target.getUUID());
                    if (targetCharacter == null || !targetCharacter.isCreated()) result = "That player has not created a character.";
                    else if (data.proposePlayerMarriage(player.getUUID(), target.getUUID())) {
                        result = "Marriage proposal sent.";
                        target.sendSystemMessage(Component.literal(PrototypeManager.characterOrAccountName(player) + " has proposed marriage. Open the Family screen to answer."));
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
                        Campaign23Data.get(player.serverLevel()).addMarriageAlliance(firstFaction, secondFaction);
                        CampaignToastSender.faction(player.getServer(), firstFaction, "MARRIAGE ALLIANCE", "A political marriage now binds your faction to " + factionName(secondFaction) + ".");
                        CampaignToastSender.faction(player.getServer(), secondFaction, "MARRIAGE ALLIANCE", "A political marriage now binds your faction to " + factionName(firstFaction) + ".");
                    }
                    data.addNews(CampaignData.NewsCategory.CHARACTERS, CampaignData.NewsImportance.NORMAL,
                            "Marriage", PrototypeManager.characterOrAccountName(player) + " married " + other + ".",
                            day, factionId(player), "", CampaignData.NewsVisibility.PUBLIC);
                }
                result = accepted ? "Marriage proposal accepted." : "No valid proposal found.";
            }
            case "REJECT" -> { data.rejectPlayerMarriage(player.getUUID()); result = "Marriage proposal rejected."; }
            case "DIVORCE" -> {
                CampaignData.MarriageRecord marriage = data.getMarriage(player.getUUID());
                if (marriage == null) result = "You are not married.";
                else if (day - marriage.marriedDay() < 7L || data.getAdditionalNpcMarriages(player.getUUID()).stream().anyMatch(value -> day - value.marriedDay() < 7L))
                    result = "A voluntary divorce is unavailable until every active marriage has lasted at least 7 Minecraft days.";
                else {
                    UUID spouseId = marriage.spousePlayerId();
                    String firstFaction = factionId(player);
                    String secondFaction = spouseId == null ? "" : factionForPlayerId(player.getServer(), spouseId);
                    data.divorce(player.getUUID());
                    Campaign23Data extra = Campaign23Data.get(player.serverLevel());
                    int divorces = extra.incrementDivorceCount(player.getUUID());
                    PrototypeData.SettlementRecord capital = PrototypeData.get(player.serverLevel()).getOwnedSettlements(player.getUUID()).stream()
                            .filter(s -> s.getRank() == PrototypeData.SettlementRank.CAPITAL || s.getRank() == PrototypeData.SettlementRank.CITY_STATE)
                            .findFirst().orElse(null);
                    if (capital != null) {
                        int penalty = -Math.min(8, 2 * divorces);
                        long expires = extra.nowMillis(capital.getId()) + 3L * 24L * 60L * 60L * 1000L;
                        extra.addModifier(capital.getId(), new Campaign23Data.ModifierRecord("marital_scandal", "Marital Scandal", "minecraft:torn_banner_pattern", "NEGATIVE",
                                "Repeated divorce", "Repeated political marriages and divorces have damaged confidence in the ruler.", penalty, penalty / 2, 0, expires));
                    }
                    if (!firstFaction.isBlank() && !secondFaction.isBlank() && !firstFaction.equals(secondFaction)) {
                        Campaign23Data.get(player.serverLevel()).removeMarriageAlliance(firstFaction, secondFaction);
                        CampaignToastSender.faction(player.getServer(), firstFaction, "MARRIAGE ALLIANCE ENDED", "The marriage alliance with " + factionName(secondFaction) + " has ended.");
                        CampaignToastSender.faction(player.getServer(), secondFaction, "MARRIAGE ALLIANCE ENDED", "The marriage alliance with " + factionName(firstFaction) + " has ended.");
                    }
                    result = "Marriage ended.";
                }
            }
            default -> result = "Unknown family action.";
        }
        openFamily(player, result);
    }


    public static String createFaction(ServerPlayer player, String rawName) {
        return createFaction(player, rawName, ChatFormatting.WHITE, (byte)0);
    }

    private static String createSettlementFromGui(ServerPlayer player, String id, String name) {
        PrototypeData data = PrototypeData.get(player.serverLevel());
        if (id.isBlank()) return "Settlement creation failed: enter a short settlement ID.";
        if (player.getTeam() == null && data.getSettlements().stream()
                .filter(value -> player.getUUID().equals(value.getOwnerId()) && value.getFactionTeam().isBlank()).count() >= 2) {
            return "Independent rulers may own at most two settlements. Create a faction before founding another.";
        }
        PrototypeData.SettlementRecord settlement = data.createSettlement(id, name, player.getUUID());
        if (settlement == null) return "Settlement creation failed: that ID is invalid or already used.";
        settlement.setTownSquare(player.serverLevel().dimension().location().toString(), player.blockPosition());
        Team team = player.getTeam();
        if (team != null) settlement.setFactionTeam(team.getName());
        PrototypeCommands.linkClaimAtPlayer(player, settlement, false);
        data.selectSettlement(player.getUUID(), settlement.getId());
        settlement.addChronicle("Settlement founded by " + characterOrAccountName(player) + ".");
        CampaignData.get(player.serverLevel()).addNews(CampaignData.NewsCategory.SETTLEMENTS, CampaignData.NewsImportance.NORMAL,
                "Settlement founded", settlement.getName() + " was founded as a Village.",
                player.serverLevel().getDayTime() / 24000L, settlement.getFactionTeam(), settlement.getId(), CampaignData.NewsVisibility.PUBLIC);
        broadcastMapMarkers(player.getServer());
        return "Founded " + settlement.getName() + " as a Village. Its Town Square was set to your current position.";
    }

    public static String createFaction(ServerPlayer player, String rawName, ChatFormatting teamColor, byte unitColor) {
        if (player == null) return "No player supplied.";
        if (player.getTeam() != null) return "Leave your current faction before creating another one.";
        if (FactionEvents.recruitsFactionManager == null) return "Recruits faction data is not ready yet.";
        String displayName = PrototypeData.cleanText(rawName, "", 32);
        if (displayName.isBlank()) return "Enter a faction name.";
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BannerItem)) return "Faction creation failed: You are not holding a banner!";
        ItemStack banner = held.copy();
        banner.setCount(1);
        if (RecruitsFactionManager.isBannerBlank(banner)) {
            return "Faction creation failed: Your faction banner must contain at least one pattern.";
        }
        String baseId = PrototypeData.normalizeId(displayName);
        if (baseId.isBlank()) return "That faction name cannot produce a valid internal ID.";
        String factionId = baseId;
        int suffix = 2;
        while (FactionEvents.recruitsFactionManager.getFactionByStringID(factionId) != null
                || player.getServer().getScoreboard().getPlayerTeam(factionId) != null) {
            factionId = baseId + "_" + suffix++;
            if (suffix > 99) return "Too many factions already use that name.";
        }
        try {
            FactionEvents.createTeam(false, player, player.serverLevel(), factionId, displayName,
                    player.getScoreboardName(), banner, teamColor == null ? ChatFormatting.WHITE : teamColor, unitColor);
        } catch (RuntimeException exception) {
            return "Faction creation failed: " + exception.getClass().getSimpleName();
        }
        CampaignData campaign = CampaignData.get(player.serverLevel());
        campaign.factionProfile(factionId);
        Campaign23Data.get(player.serverLevel()).setFactionWealth(factionId, 0);
        long day = player.serverLevel().getDayTime() / 24000L;
        campaign.addNews(CampaignData.NewsCategory.FACTIONS, CampaignData.NewsImportance.MAJOR,
                "Faction founded", displayName + " was founded by " + PrototypeManager.characterOrAccountName(player) + ".",
                day, factionId, "", CampaignData.NewsVisibility.PUBLIC);
        PrototypeManager.broadcastProfiles(player.getServer());
        CampaignToastSender.factionFounded(player, displayName);
        return "Created faction " + displayName + " (" + factionId + ").";
    }

    private static String characterName(ServerLevel level, UUID uuid) {
        PrototypeData.CharacterRecord character = PrototypeData.get(level).getCharacter(uuid);
        return character != null && character.isCreated() ? character.getDisplayName() : uuid.toString();
    }

    public static boolean isFactionLeader(ServerPlayer player, String factionId) {
        if (player.createCommandSourceStack().hasPermission(2)) return true;
        if (factionId == null || factionId.isBlank() || FactionEvents.recruitsFactionManager == null) return false;
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
        if (online != null && online.getTeam() != null) return online.getTeam().getName();
        if (FactionEvents.recruitsFactionManager != null) {
            for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
                if (faction == null) continue;
                if (playerId.equals(faction.getTeamLeaderUUID())) return faction.getStringID();
                if (faction.getMembers().stream().anyMatch(info -> info != null && playerId.equals(info.getUUID()))) return faction.getStringID();
            }
        }
        return "";
    }

    public static String factionId(ServerPlayer player) { return player.getTeam() == null ? "" : player.getTeam().getName(); }

    public static String factionName(String id) {
        if (id == null || id.isBlank()) return "Independent";
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
