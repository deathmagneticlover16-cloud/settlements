package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignSnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** One reusable navigation screen for the two branches of the campaign tree. */
public final class CampaignBranchScreen extends ScaledCampaignScreen {
    public enum Branch { SETTLEMENT, REALM }

    private static final int W = 500, H = 350;
    private final CampaignSnapshot snapshot;
    private final Branch branch;
    private int left, top;

    public CampaignBranchScreen(CampaignSnapshot snapshot, Branch branch) {
        super(Component.literal(branch == Branch.SETTLEMENT ? "Settlement Branch" : "Realm Branch"));
        this.snapshot = snapshot;
        this.branch = branch;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        if (branch == Branch.SETTLEMENT) buildSettlementLeaves();
        else buildRealmLeaves();
        addRenderableWidget(Button.builder(Component.literal("Campaign Tree"), button -> request(OpenCampaignRequestPacket.ScreenTarget.MAIN))
                .bounds(left + 370, top + 318, 112, 20).build());
    }

    private void buildSettlementLeaves() {
        leaf("SETTLEMENT OVERVIEW", 30, 100, OpenCampaignRequestPacket.ScreenTarget.SETTLEMENT);
        addRenderableWidget(Button.builder(Component.literal("FOUND SETTLEMENT"), button -> minecraft.setScreen(new SettlementCreationScreen()))
                .bounds(left + 30, top + 152, 210, 34).build());
        leaf("RESEARCH", 30, 204, OpenCampaignRequestPacket.ScreenTarget.RESEARCH);
    }

    private void buildRealmLeaves() {
        leaf("CHARACTER", 30, 100, OpenCampaignRequestPacket.ScreenTarget.CHARACTER);
        leaf("FAMILY", 260, 100, OpenCampaignRequestPacket.ScreenTarget.FAMILY);
        leaf("FACTION", 30, 152, OpenCampaignRequestPacket.ScreenTarget.FACTION);
        leaf("MILITARY", 260, 152, OpenCampaignRequestPacket.ScreenTarget.MILITARY);
        leaf("DIPLOMACY", 30, 204, OpenCampaignRequestPacket.ScreenTarget.DIPLOMACY);
        leaf("WORLD NEWS", 260, 204, OpenCampaignRequestPacket.ScreenTarget.WORLD_NEWS);
    }

    private void leaf(String label, int x, int y, OpenCampaignRequestPacket.ScreenTarget target) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> request(target))
                .bounds(left + x, top + y, 210, 34).build());
    }

    private void request(OpenCampaignRequestPacket.ScreenTarget target) {
        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX), uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 58);
        drawCampaignPanel(graphics, left + 14, top + 70, left + W - 14, top + 302);
        String heading = branch == Branch.SETTLEMENT ? "SETTLEMENT BRANCH" : "REALM BRANCH";
        String description = branch == Branch.SETTLEMENT
                ? "Local land, population, treasury and development"
                : "Character, dynasty, faction, armed forces and foreign affairs";
        graphics.drawCenteredString(font, heading, left + W / 2, top + 14, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, description, left + W / 2, top + 34, CAMPAIGN_MUTED);
        if (branch == Branch.SETTLEMENT) {
            graphics.drawString(font, "Selected: " + snapshot.selectedSettlementName(), left + 30, top + 82, 0xFFB8B0A0, false);
            graphics.drawString(font, "Manage", left + 250, top + 112, CAMPAIGN_MUTED, false);
            graphics.drawString(font, "Create", left + 250, top + 164, CAMPAIGN_MUTED, false);
            graphics.drawString(font, "Develop", left + 250, top + 216, CAMPAIGN_MUTED, false);
            stat(graphics, "Settlements", snapshot.settlementCount(), 30, 260);
            stat(graphics, "Population", snapshot.totalPopulation(), 180, 260);
            stat(graphics, "Treasury", snapshot.totalTreasury(), 340, 260);
            stat(graphics, "Stability", snapshot.averageStability() + "%", 30, 280);
            stat(graphics, "Food", snapshot.totalFood(), 180, 280);
        } else {
            graphics.drawString(font, snapshot.factionName() + " · " + snapshot.activeWars() + " active war(s)", left + 30, top + 82, 0xFFB8B0A0, false);
            stat(graphics, "Manpower", snapshot.availableManpower() + "/" + snapshot.maximumManpower(), 30, 260);
            stat(graphics, "Treaties", snapshot.pendingDiplomacy(), 210, 260);
            stat(graphics, "Marriage", snapshot.pendingMarriage(), 350, 260);
            stat(graphics, "Active wars", snapshot.activeWars(), 30, 280);
        }
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, CAMPAIGN_TEXT, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
