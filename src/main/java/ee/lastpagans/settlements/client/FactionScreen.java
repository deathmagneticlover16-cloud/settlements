package ee.lastpagans.settlements.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlements.CampaignData;
import ee.lastpagans.settlements.FactionSnapshot;
import ee.lastpagans.settlements.FactionColorUtil;
import ee.lastpagans.settlements.network.FactionActionPacket;
import ee.lastpagans.settlements.network.FactionCreatePacket;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FactionScreen extends ScaledCampaignScreen {
    private static final int W = 560;
    private static final int H = 440;
    private FactionSnapshot snapshot;
    private int left;
    private int top;
    private Tab tab = Tab.OVERVIEW;
    private BannerRenderer banner;
    private DropdownWidget memberDrop;
    private DropdownWidget rankDrop;
    private DropdownWidget titleRankDrop;
    private DropdownWidget settlementDrop;
    private DropdownWidget availableFactionDrop;
    private DropdownWidget requestDrop;
    private DropdownWidget invitationDrop;
    private DropdownWidget inviteCandidateDrop;
    private EditBox titleEdit;
    private EditBox factionNameEdit;
    private String factionNameDraft = "";
    private DropdownWidget teamColorDrop;
    private DropdownWidget unitColorDrop;
    private DropdownWidget governmentDrop;
    private DropdownWidget capitalDrop;
    private DropdownWidget bannerBaseDrop;
    private DropdownWidget bannerPatternDrop;
    private DropdownWidget bannerPatternColorDrop;
    private BannerRenderer designedBannerRenderer;
    private CreatePage createPage = CreatePage.IDENTITY;
    private int teamColorIndex;
    private int unitColorIndex;
    private int governmentIndex;
    private int capitalIndex;
    private int bannerBaseColorIndex;
    private int bannerPatternIndex;
    private int bannerPatternColorIndex;
    private final List<BannerLayerChoice> bannerLayers = new ArrayList<>();
    private int memberIndex;
    private int rankIndex;
    private int titleRankIndex;
    private int settlementIndex;
    private int availableFactionIndex;
    private int requestIndex;
    private int invitationIndex;
    private int inviteCandidateIndex;

    public FactionScreen(FactionSnapshot snapshot) {
        super(Component.literal("Faction"));
        this.snapshot = snapshot;
        if (!snapshot.factionId().isBlank() && !snapshot.banner().isEmpty()) {
            try {
                banner = new BannerRenderer(new RecruitsFaction(snapshot.factionId(), snapshot.factionName(), snapshot.banner().copy()));
            } catch (RuntimeException ignored) {
                banner = null;
            }
        }
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        rebuildWidgets();
    }

    public boolean updateCreationSnapshot(FactionSnapshot value) {
        if (!snapshot.factionId().isBlank() || !value.factionId().isBlank()) return false;
        snapshot = value;
        rebuildWidgets();
        return true;
    }

    @Override
    protected void rebuildWidgets() {
        if (factionNameEdit != null) factionNameDraft = factionNameEdit.getValue();
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        clearWidgets();
        memberDrop = null;
        rankDrop = null;
        titleRankDrop = null;
        settlementDrop = null;
        availableFactionDrop = null;
        requestDrop = null;
        invitationDrop = null;
        inviteCandidateDrop = null;
        titleEdit = null;
        factionNameEdit = null;
        teamColorDrop = null;
        unitColorDrop = null;
        governmentDrop = null;
        capitalDrop = null;
        bannerBaseDrop = null;
        bannerPatternDrop = null;
        bannerPatternColorDrop = null;

        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + W - 128, top + H - 28, 112, 18).build());

        if (snapshot.factionId().isBlank()) {
            String[] setupTabs = {"Identity", "Banner Editor", "Join / Invitations"};
            addCampaignTabs(setupTabs, createPage.ordinal(), left + 74, top + 76, 128, 20, 14,
                    index -> { createPage = CreatePage.values()[index]; rebuildWidgets(); });

            if (createPage == CreatePage.IDENTITY) {
                factionNameEdit = new EditBox(font, left + 168, top + 126, 280, 22, Component.literal("Faction name"));
                factionNameEdit.setMaxLength(32);
                factionNameEdit.setValue(factionNameDraft);
                factionNameEdit.setHint(Component.literal("Faction name"));
                addRenderableWidget(factionNameEdit);

                List<String> dyeNames = dyeDisplayNames();
                teamColorIndex = clampIndex(teamColorIndex, dyeNames.size());
                unitColorIndex = clampIndex(unitColorIndex, dyeNames.size());
                teamColorDrop = addRenderableWidget(new DropdownWidget(left + 168, top + 164, 132, 20, dyeNames, teamColorIndex,
                        value -> teamColorIndex = Math.max(0, dyeNames.indexOf(value))));
                unitColorDrop = addRenderableWidget(new DropdownWidget(left + 316, top + 164, 132, 20, dyeNames, unitColorIndex,
                        value -> unitColorIndex = Math.max(0, dyeNames.indexOf(value))));

                List<String> governments = java.util.Arrays.stream(CampaignData.GovernmentType.values()).map(CampaignData.GovernmentType::displayName).toList();
                governmentIndex = clampIndex(governmentIndex, governments.size());
                governmentDrop = addRenderableWidget(new DropdownWidget(left + 168, top + 202, 280, 20, governments, governmentIndex,
                        value -> governmentIndex = Math.max(0, governments.indexOf(value))));

                List<String> capitals = snapshot.settlements().stream()
                        .map(value -> value.name() + " — " + value.rank() + " · Pop " + value.population()).toList();
                capitalIndex = clampIndex(capitalIndex, capitals.size());
                if (!capitals.isEmpty()) capitalDrop = addRenderableWidget(new DropdownWidget(left + 168, top + 240, 280, 20, capitals, capitalIndex,
                        value -> capitalIndex = Math.max(0, capitals.indexOf(value))));

                addRenderableWidget(Button.builder(Component.literal("Create Faction"), button -> createFaction())
                        .bounds(left + 196, top + 284, 168, 22).build());
                setInitialFocus(factionNameEdit);
            } else if (createPage == CreatePage.BANNER) {
                List<String> dyes = dyeDisplayNames();
                List<String> patterns = bannerPatternDisplayNames();
                bannerBaseColorIndex = clampIndex(bannerBaseColorIndex, dyes.size());
                bannerPatternColorIndex = clampIndex(bannerPatternColorIndex, dyes.size());
                bannerPatternIndex = clampIndex(bannerPatternIndex, patterns.size());
                bannerBaseDrop = addRenderableWidget(new DropdownWidget(left + 166, top + 132, 154, 20, dyes, bannerBaseColorIndex,
                        value -> { bannerBaseColorIndex = Math.max(0, dyes.indexOf(value)); rebuildDesignedBanner(); }));
                bannerPatternDrop = addRenderableWidget(new DropdownWidget(left + 166, top + 174, 216, 20, patterns, bannerPatternIndex,
                        value -> bannerPatternIndex = Math.max(0, patterns.indexOf(value))));
                bannerPatternColorDrop = addRenderableWidget(new DropdownWidget(left + 390, top + 174, 146, 20, dyes, bannerPatternColorIndex,
                        value -> bannerPatternColorIndex = Math.max(0, dyes.indexOf(value))));
                addRenderableWidget(Button.builder(Component.literal("Add Layer"), button -> addBannerLayer())
                        .bounds(left + 166, top + 210, 104, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Remove Last"), button -> removeBannerLayer())
                        .bounds(left + 276, top + 210, 104, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Clear"), button -> { bannerLayers.clear(); rebuildDesignedBanner(); })
                        .bounds(left + 386, top + 210, 72, 20).build());
                rebuildDesignedBanner();
            }

            if (createPage == CreatePage.JOIN) {
                if (!snapshot.availableFactions().isEmpty()) {
                    List<String> factionChoices = snapshot.availableFactions().stream()
                            .map(value -> value.name() + "  [" + value.players() + "/" + value.maxPlayers() + "]" + (value.pending() ? "  • pending" : ""))
                            .toList();
                    availableFactionIndex = clampIndex(availableFactionIndex, factionChoices.size());
                    availableFactionDrop = addRenderableWidget(new DropdownWidget(left + 86, top + 156, 286, 20, factionChoices, availableFactionIndex,
                            value -> availableFactionIndex = Math.max(0, factionChoices.indexOf(value))));
                    addRenderableWidget(Button.builder(Component.literal("Apply"), button -> factionApplication(false))
                            .bounds(left + 380, top + 156, 72, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("Withdraw"), button -> factionApplication(true))
                            .bounds(left + 458, top + 156, 78, 20).build());
                }
                if (!snapshot.invitations().isEmpty()) {
                    List<String> invitations = snapshot.invitations().stream().map(FactionSnapshot.InvitationEntry::factionName).toList();
                    invitationIndex = clampIndex(invitationIndex, invitations.size());
                    invitationDrop = addRenderableWidget(new DropdownWidget(left + 86, top + 250, 286, 20, invitations, invitationIndex,
                            value -> invitationIndex = Math.max(0, invitations.indexOf(value))));
                    addRenderableWidget(Button.builder(Component.literal("Accept"), button -> factionInvitation(true))
                            .bounds(left + 380, top + 250, 72, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("Decline"), button -> factionInvitation(false))
                            .bounds(left + 458, top + 250, 78, 20).build());
                }
            }
            return;
        }

        String[] labels = {"Overview", "Members", "Settlements", "Ranks"};
        addCampaignTabs(labels, tab.ordinal(), left + 12, top + 68, 102, 20, 6,
                index -> { tab = Tab.values()[index]; rebuildWidgets(); });
        addRenderableWidget(Button.builder(Component.literal("Diplomacy"), button ->
                        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.DIPLOMACY)))
                .bounds(left + 444, top + 68, 104, 20).build());

        if (tab == Tab.MEMBERS) {
            if (snapshot.viewerLeader() && !snapshot.members().isEmpty()) {
                List<FactionSnapshot.MemberEntry> selectable = snapshot.members().stream().filter(member -> !member.leader()).toList();
                List<String> memberNames = selectable.stream()
                        .map(member -> member.characterName() + " (" + member.accountName() + ")")
                        .toList();
                if (!memberNames.isEmpty()) {
                    memberIndex = Math.max(0, Math.min(memberIndex, memberNames.size() - 1));
                    memberDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 316, 160, 20,
                            memberNames, memberIndex, value -> memberIndex = Math.max(0, memberNames.indexOf(value))));
                    List<String> ranks = snapshot.ranks().stream().filter(rank -> rank.tier() < 4)
                            .map(rank -> "Tier " + rank.tier() + " — " + rank.title()).toList();
                    rankIndex = Math.max(0, Math.min(rankIndex, Math.max(0, ranks.size() - 1)));
                    rankDrop = addRenderableWidget(new DropdownWidget(left + 188, top + 316, 120, 20,
                            ranks, rankIndex, value -> rankIndex = Math.max(0, ranks.indexOf(value))));
                    addRenderableWidget(Button.builder(Component.literal("Set Rank"), button -> setRank())
                            .bounds(left + 314, top + 316, 70, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("Remove"), button -> kickMember())
                            .bounds(left + 390, top + 316, 70, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("Transfer"), button -> transferLeadership())
                            .bounds(left + 466, top + 316, 72, 20).build());
                }
            }
            if (snapshot.viewerTier() >= 3 && !snapshot.joinRequests().isEmpty()) {
                List<String> requests = snapshot.joinRequests().stream()
                        .map(value -> value.accountName() + (value.online() ? "  • online" : "  • offline"))
                        .toList();
                requestIndex = Math.max(0, Math.min(requestIndex, requests.size() - 1));
                requestDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 342, 190, 20, requests, requestIndex,
                        value -> requestIndex = Math.max(0, requests.indexOf(value))));
                addRenderableWidget(Button.builder(Component.literal("Accept"), button -> processApplication(true))
                        .bounds(left + 218, top + 342, 88, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Decline"), button -> processApplication(false))
                        .bounds(left + 312, top + 342, 88, 20).build());
            }
            if (snapshot.viewerTier() >= 3 && !snapshot.inviteCandidates().isEmpty()) {
                List<String> candidates = snapshot.inviteCandidates().stream()
                        .map(value -> value.characterName() + " (" + value.accountName() + ")" + (value.alreadyInvited() ? " • invited" : ""))
                        .toList();
                inviteCandidateIndex = Math.max(0, Math.min(inviteCandidateIndex, candidates.size() - 1));
                inviteCandidateDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 368, 278, 20, candidates, inviteCandidateIndex,
                        value -> inviteCandidateIndex = Math.max(0, candidates.indexOf(value))));
                addRenderableWidget(Button.builder(Component.literal("Send Invitation"), button -> sendInvitation())
                        .bounds(left + 306, top + 368, 110, 20).build());
            }
            if (!snapshot.viewerLeader()) {
                addRenderableWidget(Button.builder(Component.literal("Leave Faction"), button ->
                                SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("LEAVE_FACTION", null, 0, "")))
                        .bounds(left + 424, top + 368, 114, 20).build());
            }
        }
        if (tab == Tab.RANKS && snapshot.viewerLeader() && !snapshot.ranks().isEmpty()) {
            List<String> rankOptions = snapshot.ranks().stream()
                    .map(rank -> "Tier " + rank.tier() + " — " + rank.title()).toList();
            titleRankIndex = Math.max(0, Math.min(titleRankIndex, rankOptions.size() - 1));
            titleRankDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 336, 178, 20,
                    rankOptions, titleRankIndex, value -> {
                        titleRankIndex = Math.max(0, rankOptions.indexOf(value));
                        if (titleEdit != null) titleEdit.setValue(snapshot.ranks().get(titleRankIndex).title());
                    }));
            titleEdit = new EditBox(font, left + 206, top + 336, 220, 20, Component.literal("Visible rank title"));
            titleEdit.setMaxLength(24);
            titleEdit.setValue(snapshot.ranks().get(titleRankIndex).title());
            addRenderableWidget(titleEdit);
            addRenderableWidget(Button.builder(Component.literal("Rename"), button -> renameRank())
                    .bounds(left + 432, top + 336, 106, 20).build());
        }
        if (tab == Tab.SETTLEMENTS && snapshot.viewerLeader() && !snapshot.settlements().isEmpty()) {
            List<String> names = snapshot.settlements().stream().map(FactionSnapshot.SettlementEntry::name).toList();
            settlementIndex = Math.max(0, Math.min(settlementIndex, names.size() - 1));
            settlementDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 336, 190, 20, names, settlementIndex,
                    value -> settlementIndex = Math.max(0, names.indexOf(value))));
            addRenderableWidget(Button.builder(Component.literal("Set Capital"), button -> factionSettlementAction("SET_CAPITAL"))
                    .bounds(left + 218, top + 336, 96, 20).build());
        }
    }

    private void createFaction() {
        if (factionNameEdit == null || snapshot.settlements().isEmpty()) return;
        String name = factionNameEdit.getValue().trim();
        if (name.isBlank()) return;
        DyeColor[] dyes = DyeColor.values();
        DyeColor team = dyes[clampIndex(teamColorIndex, dyes.length)];
        DyeColor unit = dyes[clampIndex(unitColorIndex, dyes.length)];
        CampaignData.GovernmentType[] governments = CampaignData.GovernmentType.values();
        CampaignData.GovernmentType government = governments[clampIndex(governmentIndex, governments.length)];
        FactionSnapshot.SettlementEntry capital = snapshot.settlements().get(clampIndex(capitalIndex, snapshot.settlements().size()));
        ItemStack bannerItem = buildDesignedBanner();
        SettlementNetwork.CHANNEL.sendToServer(new FactionCreatePacket(name, team.getName(), unit.getName(), government.name(), capital.id(), bannerItem));
    }

    private void factionSettlementAction(String action) {
        if (snapshot.settlements().isEmpty()) return;
        FactionSnapshot.SettlementEntry settlement = snapshot.settlements().get(Math.min(settlementIndex, snapshot.settlements().size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket(action, null, 100, settlement.id()));
    }

    private void factionInvitation(boolean accept) {
        if (snapshot.invitations().isEmpty()) return;
        FactionSnapshot.InvitationEntry invitation = snapshot.invitations().get(Math.min(invitationIndex, snapshot.invitations().size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket(accept ? "ACCEPT_FACTION_INVITE" : "DECLINE_FACTION_INVITE", null, 0, invitation.factionId()));
    }

    private void sendInvitation() {
        if (snapshot.inviteCandidates().isEmpty()) return;
        FactionSnapshot.PlayerChoice candidate = snapshot.inviteCandidates().get(Math.min(inviteCandidateIndex, snapshot.inviteCandidates().size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("SEND_FACTION_INVITE", candidate.playerId(), 0, ""));
    }

    private void factionApplication(boolean withdraw) {
        if (snapshot.availableFactions().isEmpty()) return;
        FactionSnapshot.FactionChoice faction = snapshot.availableFactions().get(Math.min(availableFactionIndex, snapshot.availableFactions().size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket(withdraw ? "CANCEL_JOIN_REQUEST" : "REQUEST_JOIN", null, 0, faction.id()));
    }

    private void processApplication(boolean accept) {
        if (snapshot.joinRequests().isEmpty()) return;
        FactionSnapshot.RequestEntry request = snapshot.joinRequests().get(Math.min(requestIndex, snapshot.joinRequests().size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket(accept ? "ACCEPT_JOIN_REQUEST" : "REJECT_JOIN_REQUEST", null, 0, request.accountName()));
    }

    private void transferLeadership() {
        List<FactionSnapshot.MemberEntry> selectable = snapshot.members().stream().filter(member -> !member.leader()).toList();
        if (selectable.isEmpty()) return;
        FactionSnapshot.MemberEntry member = selectable.get(Math.min(memberIndex, selectable.size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("TRANSFER_LEADERSHIP", member.playerId(), 0, ""));
    }

    private void kickMember() {
        List<FactionSnapshot.MemberEntry> selectable = snapshot.members().stream().filter(member -> !member.leader()).toList();
        if (selectable.isEmpty()) return;
        FactionSnapshot.MemberEntry member = selectable.get(Math.min(memberIndex, selectable.size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("KICK_MEMBER", member.playerId(), 0, ""));
    }

    private void renameRank() {
        if (snapshot.ranks().isEmpty() || titleEdit == null) return;
        FactionSnapshot.RankEntry rank = snapshot.ranks().get(Math.min(titleRankIndex, snapshot.ranks().size() - 1));
        String title = titleEdit.getValue().trim();
        if (title.isBlank()) return;
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("SET_RANK_TITLE", null, rank.tier(), title));
    }

    private void setRank() {
        List<FactionSnapshot.MemberEntry> selectable = snapshot.members().stream().filter(member -> !member.leader()).toList();
        List<FactionSnapshot.RankEntry> ranks = snapshot.ranks().stream().filter(rank -> rank.tier() < 4).toList();
        if (selectable.isEmpty() || ranks.isEmpty()) return;
        FactionSnapshot.MemberEntry member = selectable.get(Math.min(memberIndex, selectable.size() - 1));
        FactionSnapshot.RankEntry rank = ranks.get(Math.min(rankIndex, ranks.size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("SET_MEMBER_RANK", member.playerId(), rank.tier(), ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 62);
        drawCampaignPanel(graphics, left + 12, top + 96, left + W - 12, top + H - 38);

        if (snapshot.factionId().isBlank()) {
            graphics.drawCenteredString(font, "CREATE A FACTION", left + W / 2, top + 18, 0xFFE7D8B0);
            graphics.drawCenteredString(font, "An independent polity can be proclaimed as a formal faction once it meets the requirements.", left + W / 2, top + 42, 0xFFCCCCCC);
            if (createPage == CreatePage.IDENTITY) {
                graphics.drawString(font, "Faction name", left + 92, top + 132, 0xFFE7D8B0, false);
                graphics.drawString(font, "Faction color", left + 92, top + 170, 0xFFE7D8B0, false);
                graphics.drawString(font, "Unit color", left + 316, top + 152, 0xFFE7D8B0, false);
                graphics.drawString(font, "Government", left + 92, top + 208, 0xFFE7D8B0, false);
                graphics.drawString(font, "Capital", left + 92, top + 246, 0xFFE7D8B0, false);
                graphics.drawCenteredString(font, bannerLayers.isEmpty() ? "Banner: no pattern layers yet — use Banner Editor." : "Banner: " + bannerLayers.size() + " pattern layer(s) ready.",
                        left + W / 2, top + 318, bannerLayers.isEmpty() ? 0xFFFFA69B : 0xFF9FE39F);
            } else if (createPage == CreatePage.BANNER) {
                graphics.drawString(font, "Base dye", left + 92, top + 138, 0xFFE7D8B0, false);
                graphics.drawString(font, "Pattern", left + 92, top + 180, 0xFFE7D8B0, false);
                graphics.drawString(font, "Pattern dye", left + 390, top + 162, 0xFFE7D8B0, false);
                graphics.drawString(font, "Layers " + bannerLayers.size() + "/6", left + 166, top + 244, 0xFFCCCCCC, false);
                for (int i = 0; i < bannerLayers.size(); i++) {
                    BannerLayerChoice layer = bannerLayers.get(i);
                    graphics.drawString(font, (i + 1) + ". " + layer.displayName() + " · " + displayDye(layer.dye()), left + 166, top + 260 + i * 13, 0xFFAAAAAA, false);
                }
                if (designedBannerRenderer != null) designedBannerRenderer.renderBanner(graphics, left + 466, top + 116, 28, 56, CAMPAIGN_BANNER_SCALE);
            }
            if (createPage == CreatePage.JOIN) {
                graphics.drawCenteredString(font, "JOIN AN EXISTING FACTION", left + W / 2, top + 122, 0xFFE7D8B0);
                if (snapshot.availableFactions().isEmpty())
                    graphics.drawCenteredString(font, "No public factions are currently available.", left + W / 2, top + 160, 0xFF999999);
                graphics.drawCenteredString(font, "FACTION INVITATIONS", left + W / 2, top + 216, 0xFFE7D8B0);
                if (snapshot.invitations().isEmpty())
                    graphics.drawCenteredString(font, "No pending invitations.", left + W / 2, top + 254, 0xFF999999);
            }
            if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fitText(snapshot.message(), W - 50), left + W / 2, top + 398, CAMPAIGN_MESSAGE);
            super.render(graphics, uiMouseX, uiMouseY, partialTick);
            endCampaignRender(graphics);
            return;
        }

        if (banner != null) banner.renderBanner(graphics, left + 18, top + 7, 20, 40, CAMPAIGN_BANNER_SCALE);
        graphics.drawString(font, fitText(snapshot.factionName().toUpperCase(), 340), left + 72, top + 13, CAMPAIGN_TEXT, false);
        graphics.drawString(font, "Leader: " + fitText(snapshot.leaderName(), 270), left + 72, top + 31, CAMPAIGN_TITLE, false);
        graphics.drawString(font, "Your rank: [" + snapshot.viewerRank() + "]", left + 72, top + 47, 0xFFCCCCCC, false);

        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case MEMBERS -> renderMembers(graphics);
            case SETTLEMENTS -> renderSettlements(graphics);
            case RANKS -> renderRanks(graphics);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fitText(snapshot.message(), W - 40), left + W / 2, top + 398, CAMPAIGN_MESSAGE);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderOverview(GuiGraphics graphics) {
        stat(graphics, "Members", snapshot.members().size(), 24, 110);
        stat(graphics, "Settlements", snapshot.settlements().size(), 24, 130);
        stat(graphics, "Realm population", snapshot.realmPopulation(), 24, 150);
        stat(graphics, "Daily tax income", snapshot.dailyTaxIncome(), 24, 170);
        stat(graphics, "State Treasury", snapshot.stateTreasury(), 24, 190);
        stat(graphics, "Military roster", fitText(snapshot.rosterName(), 150), 24, 210);
        stat(graphics, "Government", snapshot.government(), 24, 230);
        stat(graphics, "Active wars", snapshot.activeWars(), 24, 250);
        graphics.drawString(font, "Faction dye: " + prettify(snapshot.teamDye()), left + 24, top + 274,
                0xFF000000 | FactionColorUtil.rgb(FactionColorUtil.dye(snapshot.teamDye())), false);
        graphics.drawString(font, "Unit dye: " + prettify(snapshot.unitDye()), left + 24, top + 292,
                0xFF000000 | FactionColorUtil.rgb(FactionColorUtil.dye(snapshot.unitDye())), false);
        graphics.drawString(font, "Recruits uses the nearest supported colors.", left + 24, top + 314, 0xFFAAAAAA, false);
        graphics.drawString(font, "Allies", left + 300, top + 110, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal(snapshot.allies().isEmpty() ? "None" : String.join(", ", snapshot.allies())),
                left + 300, top + 128, 226, 0xFFCCCCCC);
        graphics.drawString(font, "Enemies", left + 300, top + 184, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal(snapshot.enemies().isEmpty() ? "None" : String.join(", ", snapshot.enemies())),
                left + 300, top + 202, 226, 0xFFFFA69B);
    }

    private void renderMembers(GuiGraphics graphics) {
        graphics.drawString(font, "FACTION MEMBERS", left + 24, top + 108, 0xFFE7D8B0, false);
        graphics.drawString(font, "MEMBERSHIP APPLICATIONS", left + 322, top + 108, 0xFFE7D8B0, false);
        for (int i = 0; i < Math.min(13, snapshot.members().size()); i++) {
            FactionSnapshot.MemberEntry member = snapshot.members().get(i);
            int y = top + 128 + i * 13;
            String prefix = "[" + member.rankTitle() + "] ";
            String suffix = member.online() ? " •" : "";
            graphics.drawString(font, fitText(prefix + member.characterName() + suffix, 270),
                    left + 24, y, member.online() ? 0xFFFFFFFF : 0xFF999999, false);
        }
        if (snapshot.joinRequests().isEmpty()) {
            graphics.drawString(font, snapshot.viewerTier() >= 3 ? "No pending applications." : "Tier 3+ officers manage applications.",
                    left + 322, top + 130, 0xFF999999, false);
        } else {
            for (int i = 0; i < Math.min(10, snapshot.joinRequests().size()); i++) {
                FactionSnapshot.RequestEntry request = snapshot.joinRequests().get(i);
                int y = top + 128 + i * 14;
                graphics.drawString(font, fitText(request.accountName() + (request.online() ? " • online" : " • offline"), 210),
                        left + 322, y, request.online() ? 0xFFFFFFFF : 0xFF999999, false);
            }
        }
        if (snapshot.viewerLeader()) {
            graphics.drawString(font, "Member rank / removal", left + 24, top + 302, 0xFFAAAAAA, false);
        }
        if (snapshot.viewerTier() >= 3) {
            graphics.drawString(font, "Application review", left + 24, top + 330, 0xFFAAAAAA, false);
            graphics.drawString(font, "Invite an online independent player", left + 24, top + 356, 0xFFAAAAAA, false);
        }
    }

    private void renderSettlements(GuiGraphics graphics) {
        graphics.drawString(font, "OWNED SETTLEMENTS", left + 24, top + 108, 0xFFE7D8B0, false);
        for (int i = 0; i < Math.min(9, snapshot.settlements().size()); i++) {
            FactionSnapshot.SettlementEntry settlement = snapshot.settlements().get(i);
            int y = top + 128 + i * 16;
            String capital = settlement.capital() ? " ◆ CAPITAL" : "";
            graphics.drawString(font, fitText(settlement.name() + " — " + settlement.rank() + capital, 250), left + 24, y, CAMPAIGN_TEXT, false);
            graphics.drawString(font, "Pop " + settlement.population() + " · Stability " + settlement.stability()
                    + "% · Tax income " + settlement.taxIncome() + "/day", left + 300, y, 0xFFAAAAAA, false);
        }
        if (snapshot.settlements().isEmpty()) graphics.drawString(font, "This faction owns no registered settlements.", left + 24, top + 132, 0xFFAAAAAA, false);
    }

    private void renderRanks(GuiGraphics graphics) {
        graphics.drawString(font, "FACTION RANKS", left + 24, top + 108, 0xFFE7D8B0, false);
        for (int i = 0; i < snapshot.ranks().size(); i++) {
            FactionSnapshot.RankEntry rank = snapshot.ranks().get(i);
            int y = top + 130 + i * 25;
            graphics.drawString(font, "Tier " + rank.tier() + " — [" + rank.title() + "]", left + 24, y, 0xFFFFFFFF, false);
            String permission = switch (rank.tier()) {
                case 4 -> "Faction leader: complete authority";
                case 3 -> "Senior administration and diplomacy";
                case 2 -> "Officer and military administration";
                case 1 -> "Full faction member";
                default -> "Common member";
            };
            graphics.drawString(font, permission, left + 214, y, 0xFFAAAAAA, false);
        }
        graphics.drawString(font, snapshot.viewerLeader()
                        ? "Rename the selected visible title below."
                        : "Visible titles do not change Minecraft usernames or UUIDs.",
                left + 24, top + 318, 0xFFAA9D7F, false);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private static int clampIndex(int value, int size) { return size <= 0 ? 0 : Math.max(0, Math.min(value, size - 1)); }

    private static List<String> dyeDisplayNames() {
        return java.util.Arrays.stream(DyeColor.values()).map(FactionScreen::displayDye).toList();
    }

    private static String displayDye(DyeColor color) {
        String raw = color.getName().replace('_', ' ');
        return java.util.Arrays.stream(raw.split(" ")).map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static List<BannerPatternChoice> bannerPatterns() {
        List<BannerPatternChoice> values = new ArrayList<>();
        BuiltInRegistries.BANNER_PATTERN.entrySet().forEach(entry -> {
            BannerPattern pattern = entry.getValue();
            String path = entry.getKey().location().getPath();
            if (!"base".equals(path)) values.add(new BannerPatternChoice(pattern.getHashname(), prettify(path)));
        });
        values.sort(java.util.Comparator.comparing(BannerPatternChoice::displayName, String.CASE_INSENSITIVE_ORDER));
        return values;
    }

    private static List<String> bannerPatternDisplayNames() { return bannerPatterns().stream().map(BannerPatternChoice::displayName).toList(); }

    private static String prettify(String value) {
        return java.util.Arrays.stream(value.replace('_', ' ').split(" ")).map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void addBannerLayer() {
        if (bannerLayers.size() >= 6) return;
        List<BannerPatternChoice> patterns = bannerPatterns();
        if (patterns.isEmpty()) return;
        DyeColor[] dyes = DyeColor.values();
        BannerPatternChoice pattern = patterns.get(clampIndex(bannerPatternIndex, patterns.size()));
        DyeColor dye = dyes[clampIndex(bannerPatternColorIndex, dyes.length)];
        bannerLayers.add(new BannerLayerChoice(pattern.hash(), pattern.displayName(), dye));
        rebuildDesignedBanner();
    }

    private void removeBannerLayer() {
        if (!bannerLayers.isEmpty()) bannerLayers.remove(bannerLayers.size() - 1);
        rebuildDesignedBanner();
    }

    private ItemStack buildDesignedBanner() {
        DyeColor[] dyes = DyeColor.values();
        DyeColor base = dyes[clampIndex(bannerBaseColorIndex, dyes.length)];
        ItemStack stack = new ItemStack(BannerBlock.byColor(base).asItem());
        ListTag patterns = new ListTag();
        for (BannerLayerChoice layer : bannerLayers) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Pattern", layer.hash());
            tag.putInt("Color", layer.dye().getId());
            patterns.add(tag);
        }
        if (!patterns.isEmpty()) {
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.put("Patterns", patterns);
            stack.addTagElement("BlockEntityTag", blockEntity);
        }
        return stack;
    }

    private void rebuildDesignedBanner() {
        ItemStack stack = buildDesignedBanner();
        try {
            if (designedBannerRenderer == null) designedBannerRenderer = new BannerRenderer(new RecruitsFaction("settlements_preview", "Preview", new CompoundTag()));
            designedBannerRenderer.setBannerItem(stack);
        } catch (RuntimeException ignored) { designedBannerRenderer = null; }
    }

    @Override public boolean isPauseScreen() { return false; }
    private enum Tab { OVERVIEW, MEMBERS, SETTLEMENTS, RANKS }
    private enum CreatePage { IDENTITY, BANNER, JOIN }
    private record BannerPatternChoice(String hash, String displayName) {}
    private record BannerLayerChoice(String hash, String displayName, DyeColor dye) {}
}
