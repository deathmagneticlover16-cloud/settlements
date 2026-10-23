package ee.lastpagans.settlements.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlements.CampaignSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class CampaignMainScreen extends ScaledCampaignScreen {
    private static final int W = 480, H = 350;
    private final CampaignSnapshot snapshot;
    private int left, top;
    private BannerRenderer banner;

    public CampaignMainScreen(CampaignSnapshot snapshot) {
        super(Component.literal("Campaign Overview"));
        this.snapshot = snapshot;
        if (!snapshot.factionBanner().isEmpty() && !snapshot.factionId().isBlank()) {
            try { banner = new BannerRenderer(new RecruitsFaction(snapshot.factionId(), snapshot.factionName(), snapshot.factionBanner().copy())); }
            catch (RuntimeException ignored) { banner = null; }
        }
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        addRenderableWidget(Button.builder(Component.literal("SETTLEMENT BRANCH"), button ->
                        minecraft.setScreen(new CampaignBranchScreen(snapshot, CampaignBranchScreen.Branch.SETTLEMENT)))
                .bounds(left + 26, top + 126, 204, 46).build());
        addRenderableWidget(Button.builder(Component.literal("REALM BRANCH"), button ->
                        minecraft.setScreen(new CampaignBranchScreen(snapshot, CampaignBranchScreen.Branch.REALM)))
                .bounds(left + 250, top + 126, 204, 46).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose()).bounds(left + 404, top + 322, 60, 18).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 82);
        drawCampaignPanel(graphics, left + 10, top + 86, left + W - 10, top + 202);
        if (minecraft != null && minecraft.player != null) PortraitRenderer.render(graphics, minecraft.player, left + 16, top + 9, 64, 66, uiMouseX, uiMouseY);
        if (banner != null) banner.renderBanner(graphics, left + W - 58, top + 7, 20, 40, CAMPAIGN_BANNER_SCALE);

        graphics.drawString(font, snapshot.characterName(), left + 94, top + 13, 0xFFFFFFFF, false);
        graphics.drawString(font, snapshot.titleLine(), left + 94, top + 29, 0xFFE7D8B0, false);
        graphics.drawString(font, "Faith: " + snapshot.faith(), left + 94, top + 47, 0xFFCCCCCC, false);
        graphics.drawString(font, "Faction: " + snapshot.factionName(), left + 94, top + 63, 0xFFCCCCCC, false);

        graphics.drawCenteredString(font, "CAMPAIGN TREE", left + W / 2, top + 96, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, "Local administration", left + 128, top + 110, CAMPAIGN_MUTED);
        graphics.drawCenteredString(font, "People, politics and war", left + 352, top + 110, CAMPAIGN_MUTED);
        graphics.drawCenteredString(font, "Overview · Found · Research", left + 128, top + 181, 0xFF8F887A);
        graphics.drawCenteredString(font, "Character · Faction · Military · Diplomacy", left + 352, top + 181, 0xFF8F887A);

        graphics.drawCenteredString(font, "Choose a branch to continue.", left + W / 2, top + 238, CAMPAIGN_MUTED);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean isPauseScreen() { return false; }
}
