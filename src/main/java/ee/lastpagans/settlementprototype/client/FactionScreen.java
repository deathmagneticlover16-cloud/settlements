package ee.lastpagans.settlementprototype.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlementprototype.FactionSnapshot;
import ee.lastpagans.settlementprototype.network.FactionActionPacket;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FactionScreen extends ScaledCampaignScreen {
    private static final int W = 560;
    private static final int H = 370;
    private final FactionSnapshot snapshot;
    private int left;
    private int top;
    private Tab tab = Tab.OVERVIEW;
    private BannerRenderer banner;
    private DropdownWidget memberDrop;
    private DropdownWidget rankDrop;
    private DropdownWidget titleRankDrop;
    private DropdownWidget settlementDrop;
    private EditBox titleEdit;
    private EditBox factionNameEdit;
    private DropdownWidget teamColorDrop;
    private DropdownWidget unitColorDrop;
    private int teamColorIndex;
    private int unitColorIndex;
    private int memberIndex;
    private int rankIndex;
    private int titleRankIndex;
    private int settlementIndex;

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

    @Override
    protected void rebuildWidgets() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        clearWidgets();
        memberDrop = null;
        rankDrop = null;
        titleRankDrop = null;
        settlementDrop = null;
        titleEdit = null;
        factionNameEdit = null;
        teamColorDrop = null;
        unitColorDrop = null;

        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + W - 128, top + H - 28, 112, 18).build());

        if (snapshot.factionId().isBlank()) {
            factionNameEdit = new EditBox(font, left + 132, top + 116, 296, 22, Component.literal("Faction name"));
            factionNameEdit.setMaxLength(32);
            factionNameEdit.setHint(Component.literal("Faction name"));
            addRenderableWidget(factionNameEdit);
            List<String> teamColors = List.of("White","Red","Dark Red","Gold","Yellow","Green","Dark Green","Aqua","Dark Aqua","Blue","Dark Blue","Light Purple","Dark Purple","Gray","Dark Gray","Black");
            List<String> unitColors = List.of("White","Orange","Magenta","Light Blue","Yellow","Lime","Pink","Gray","Light Gray","Cyan","Purple","Blue","Brown","Green","Red","Black");
            teamColorIndex = Math.max(0, Math.min(teamColorIndex, teamColors.size() - 1));
            unitColorIndex = Math.max(0, Math.min(unitColorIndex, unitColors.size() - 1));
            teamColorDrop = addRenderableWidget(new DropdownWidget(left + 132, top + 150, 142, 20, teamColors, teamColorIndex, value -> teamColorIndex = teamColors.indexOf(value)));
            unitColorDrop = addRenderableWidget(new DropdownWidget(left + 286, top + 150, 142, 20, unitColors, unitColorIndex, value -> unitColorIndex = unitColors.indexOf(value)));
            addRenderableWidget(Button.builder(Component.literal("Create Faction"), button -> createFaction())
                    .bounds(left + 196, top + 202, 168, 22).build());
            setInitialFocus(factionNameEdit);
            return;
        }

        String[] labels = {"Overview", "Members", "Settlements", "Ranks"};
        for (int i = 0; i < labels.length; i++) {
            Tab target = Tab.values()[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> {
                tab = target;
                rebuildWidgets();
            }).bounds(left + 12 + i * 108, top + 68, 102, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Diplomacy"), button ->
                        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.DIPLOMACY)))
                .bounds(left + 444, top + 68, 104, 20).build());

        if (tab == Tab.MEMBERS && snapshot.viewerLeader() && !snapshot.members().isEmpty()) {
            List<FactionSnapshot.MemberEntry> selectable = snapshot.members().stream().filter(member -> !member.leader()).toList();
            List<String> memberNames = selectable.stream()
                    .map(member -> member.characterName() + " (" + member.accountName() + ")")
                    .toList();
            if (!memberNames.isEmpty()) {
                memberIndex = Math.max(0, Math.min(memberIndex, memberNames.size() - 1));
                memberDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 276, 246, 20,
                        memberNames, memberIndex, value -> memberIndex = Math.max(0, memberNames.indexOf(value))));
                List<String> ranks = snapshot.ranks().stream().filter(rank -> rank.tier() < 4)
                        .map(rank -> "Tier " + rank.tier() + " — " + rank.title()).toList();
                rankIndex = Math.max(0, Math.min(rankIndex, Math.max(0, ranks.size() - 1)));
                rankDrop = addRenderableWidget(new DropdownWidget(left + 274, top + 276, 170, 20,
                        ranks, rankIndex, value -> rankIndex = Math.max(0, ranks.indexOf(value))));
                addRenderableWidget(Button.builder(Component.literal("Set rank"), button -> setRank())
                        .bounds(left + 450, top + 276, 88, 20).build());
            }
        }
        if (tab == Tab.RANKS && snapshot.viewerLeader() && !snapshot.ranks().isEmpty()) {
            List<String> rankOptions = snapshot.ranks().stream()
                    .map(rank -> "Tier " + rank.tier() + " — " + rank.title()).toList();
            titleRankIndex = Math.max(0, Math.min(titleRankIndex, rankOptions.size() - 1));
            titleRankDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 276, 178, 20,
                    rankOptions, titleRankIndex, value -> {
                        titleRankIndex = Math.max(0, rankOptions.indexOf(value));
                        if (titleEdit != null) titleEdit.setValue(snapshot.ranks().get(titleRankIndex).title());
                    }));
            titleEdit = new EditBox(font, left + 206, top + 276, 220, 20, Component.literal("Visible rank title"));
            titleEdit.setMaxLength(24);
            titleEdit.setValue(snapshot.ranks().get(titleRankIndex).title());
            addRenderableWidget(titleEdit);
            addRenderableWidget(Button.builder(Component.literal("Rename"), button -> renameRank())
                    .bounds(left + 432, top + 276, 106, 20).build());
        }
        if (tab == Tab.SETTLEMENTS && snapshot.viewerLeader() && !snapshot.settlements().isEmpty()) {
            List<String> names = snapshot.settlements().stream().map(FactionSnapshot.SettlementEntry::name).toList();
            settlementIndex = Math.max(0, Math.min(settlementIndex, names.size() - 1));
            settlementDrop = addRenderableWidget(new DropdownWidget(left + 22, top + 276, 190, 20, names, settlementIndex,
                    value -> settlementIndex = Math.max(0, names.indexOf(value))));
            addRenderableWidget(Button.builder(Component.literal("Set Capital"), button -> factionSettlementAction("SET_CAPITAL"))
                    .bounds(left + 218, top + 276, 96, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+100 Treasury"), button -> factionSettlementAction("TREASURY_DEPOSIT"))
                    .bounds(left + 320, top + 276, 104, 20).build());
            addRenderableWidget(Button.builder(Component.literal("-100 Treasury"), button -> factionSettlementAction("TREASURY_WITHDRAW"))
                    .bounds(left + 430, top + 276, 108, 20).build());
        }
    }

    private void createFaction() {
        if (factionNameEdit == null) return;
        String name = factionNameEdit.getValue().trim();
        if (name.isBlank()) return;
        String[] formatting = {"WHITE","RED","DARK_RED","GOLD","YELLOW","GREEN","DARK_GREEN","AQUA","DARK_AQUA","BLUE","DARK_BLUE","LIGHT_PURPLE","DARK_PURPLE","GRAY","DARK_GRAY","BLACK"};
        String teamColor = formatting[Math.max(0, Math.min(teamColorIndex, formatting.length - 1))];
        PrototypeNetwork.CHANNEL.sendToServer(new FactionActionPacket("CREATE_FACTION", null, Math.max(0, Math.min(15, unitColorIndex)), teamColor + "|" + name));
    }

    private void factionSettlementAction(String action) {
        if (snapshot.settlements().isEmpty()) return;
        FactionSnapshot.SettlementEntry settlement = snapshot.settlements().get(Math.min(settlementIndex, snapshot.settlements().size() - 1));
        PrototypeNetwork.CHANNEL.sendToServer(new FactionActionPacket(action, null, 100, settlement.id()));
    }

    private void renameRank() {
        if (snapshot.ranks().isEmpty() || titleEdit == null) return;
        FactionSnapshot.RankEntry rank = snapshot.ranks().get(Math.min(titleRankIndex, snapshot.ranks().size() - 1));
        String title = titleEdit.getValue().trim();
        if (title.isBlank()) return;
        PrototypeNetwork.CHANNEL.sendToServer(new FactionActionPacket("SET_RANK_TITLE", null, rank.tier(), title));
    }

    private void setRank() {
        List<FactionSnapshot.MemberEntry> selectable = snapshot.members().stream().filter(member -> !member.leader()).toList();
        List<FactionSnapshot.RankEntry> ranks = snapshot.ranks().stream().filter(rank -> rank.tier() < 4).toList();
        if (selectable.isEmpty() || ranks.isEmpty()) return;
        FactionSnapshot.MemberEntry member = selectable.get(Math.min(memberIndex, selectable.size() - 1));
        FactionSnapshot.RankEntry rank = ranks.get(Math.min(rankIndex, ranks.size() - 1));
        PrototypeNetwork.CHANNEL.sendToServer(new FactionActionPacket("SET_MEMBER_RANK", member.playerId(), rank.tier(), ""));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (teamColorDrop != null && teamColorDrop.isExpanded() && teamColorDrop.mouseClicked(uiX, uiY, button)) return true;
        if (unitColorDrop != null && unitColorDrop.isExpanded() && unitColorDrop.mouseClicked(uiX, uiY, button)) return true;
        if (memberDrop != null && memberDrop.isExpanded() && memberDrop.mouseClicked(uiX, uiY, button)) return true;
        if (rankDrop != null && rankDrop.isExpanded() && rankDrop.mouseClicked(uiX, uiY, button)) return true;
        if (titleRankDrop != null && titleRankDrop.isExpanded() && titleRankDrop.mouseClicked(uiX, uiY, button)) return true;
        if (settlementDrop != null && settlementDrop.isExpanded() && settlementDrop.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (teamColorDrop != null && teamColorDrop.isExpanded() && teamColorDrop.mouseScrolled(uiX, uiY, delta)) return true;
        if (unitColorDrop != null && unitColorDrop.isExpanded() && unitColorDrop.mouseScrolled(uiX, uiY, delta)) return true;
        if (memberDrop != null && memberDrop.isExpanded() && memberDrop.mouseScrolled(uiX, uiY, delta)) return true;
        if (rankDrop != null && rankDrop.isExpanded() && rankDrop.mouseScrolled(uiX, uiY, delta)) return true;
        if (titleRankDrop != null && titleRankDrop.isExpanded() && titleRankDrop.mouseScrolled(uiX, uiY, delta)) return true;
        if (settlementDrop != null && settlementDrop.isExpanded() && settlementDrop.mouseScrolled(uiX, uiY, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0171717);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 62, 0xFF3B3023);
        graphics.fill(left + 12, top + 96, left + W - 12, top + H - 38, 0xFF242424);

        if (snapshot.factionId().isBlank()) {
            graphics.drawCenteredString(font, "CREATE A FACTION", left + W / 2, top + 18, 0xFFE7D8B0);
            graphics.drawCenteredString(font, "You are currently independent.", left + W / 2, top + 42, 0xFFCCCCCC);
            graphics.drawCenteredString(font, "A patterned banner is mandatory. Hold it in your main hand before creating the faction.",
                    left + W / 2, top + 88, 0xFFAA9D7F);
            graphics.drawString(font, "Faction name", left + 132, top + 104, 0xFFE7D8B0, false);
            graphics.drawString(font, "Team color", left + 132, top + 140, 0xFFE7D8B0, false);
            graphics.drawString(font, "Unit color", left + 286, top + 140, 0xFFE7D8B0, false);
            if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fit(snapshot.message(), W - 50), left + W / 2, top + 238, 0xFFFFD27A);
            super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
            return;
        }

        if (banner != null) banner.renderBanner(graphics, left + 18, top + 7, 20, 40, CAMPAIGN_BANNER_SCALE);
        graphics.drawString(font, fit(snapshot.factionName().toUpperCase(), 340), left + 72, top + 13, 0xFFFFFFFF, false);
        graphics.drawString(font, "Leader: " + fit(snapshot.leaderName(), 270), left + 72, top + 31, 0xFFE7D8B0, false);
        graphics.drawString(font, "Your rank: [" + snapshot.viewerRank() + "]", left + 72, top + 47, 0xFFCCCCCC, false);

        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case MEMBERS -> renderMembers(graphics);
            case SETTLEMENTS -> renderSettlements(graphics);
            case RANKS -> renderRanks(graphics);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fit(snapshot.message(), W - 40), left + W / 2, top + 326, 0xFFFFD27A);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderOverview(GuiGraphics graphics) {
        stat(graphics, "Members", snapshot.members().size(), 24, 110);
        stat(graphics, "Settlements", snapshot.settlements().size(), 24, 130);
        stat(graphics, "Realm population", snapshot.realmPopulation(), 24, 150);
        stat(graphics, "Settlement wealth", snapshot.realmWealth(), 24, 170);
        stat(graphics, "Faction treasury", snapshot.factionTreasury(), 24, 190);
        stat(graphics, "Military roster", fit(snapshot.rosterName(), 150), 24, 210);
        stat(graphics, "Active wars", snapshot.activeWars(), 24, 230);
        graphics.drawString(font, "Allies", left + 300, top + 110, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal(snapshot.allies().isEmpty() ? "None" : String.join(", ", snapshot.allies())),
                left + 300, top + 128, 226, 0xFFCCCCCC);
        graphics.drawString(font, "Enemies", left + 300, top + 184, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal(snapshot.enemies().isEmpty() ? "None" : String.join(", ", snapshot.enemies())),
                left + 300, top + 202, 226, 0xFFFFA69B);
    }

    private void renderMembers(GuiGraphics graphics) {
        graphics.drawString(font, "FACTION MEMBERS", left + 24, top + 108, 0xFFE7D8B0, false);
        for (int i = 0; i < Math.min(10, snapshot.members().size()); i++) {
            FactionSnapshot.MemberEntry member = snapshot.members().get(i);
            int y = top + 128 + i * 13;
            String prefix = "[" + member.rankTitle() + "] ";
            String suffix = member.online() ? "  • online" : "";
            graphics.drawString(font, fit(prefix + member.characterName() + " (" + member.accountName() + ")" + suffix, W - 58),
                    left + 24, y, member.online() ? 0xFFFFFFFF : 0xFF999999, false);
        }
        if (snapshot.viewerLeader()) {
            graphics.drawString(font, "Select a member and permission tier below.", left + 24, top + 262, 0xFFAAAAAA, false);
        }
    }

    private void renderSettlements(GuiGraphics graphics) {
        graphics.drawString(font, "OWNED SETTLEMENTS", left + 24, top + 108, 0xFFE7D8B0, false);
        for (int i = 0; i < Math.min(9, snapshot.settlements().size()); i++) {
            FactionSnapshot.SettlementEntry settlement = snapshot.settlements().get(i);
            int y = top + 128 + i * 16;
            String capital = settlement.capital() ? " ◆ CAPITAL" : "";
            graphics.drawString(font, fit(settlement.name() + " — " + settlement.rank() + capital, 250), left + 24, y, 0xFFFFFFFF, false);
            graphics.drawString(font, "Pop " + settlement.population() + " · Stability " + settlement.stability()
                    + "% · Wealth " + settlement.wealth(), left + 300, y, 0xFFAAAAAA, false);
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
                left + 24, top + 258, 0xFFAA9D7F, false);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private String fit(String value, int pixelWidth) {
        return scrollingText(value, pixelWidth);
    }

    @Override public boolean isPauseScreen() { return false; }
    private enum Tab { OVERVIEW, MEMBERS, SETTLEMENTS, RANKS }
}
