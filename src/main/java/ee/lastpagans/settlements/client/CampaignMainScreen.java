package ee.lastpagans.settlements.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlements.CampaignSnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Responsive realm dashboard and sole top-level campaign navigation. */
public final class CampaignMainScreen extends ScaledCampaignScreen {
    private static final int W = 520, H = 300;
    private final CampaignSnapshot snapshot;
    private int left, top;
    private BannerRenderer banner;

    public CampaignMainScreen(CampaignSnapshot snapshot) {
        super(Component.translatable("screen.settlements.realm_overview"));
        this.snapshot = snapshot;
        if (!snapshot.factionBanner().isEmpty() && !snapshot.factionId().isBlank()) {
            try { banner = new BannerRenderer(new RecruitsFaction(snapshot.factionId(), snapshot.factionName(), snapshot.factionBanner().copy())); }
            catch (RuntimeException ignored) { banner = null; }
        }
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W); top = campaignTop(H);
        nav("Realm Overview", 54, null);
        nav("Settlements", 80, OpenCampaignRequestPacket.ScreenTarget.SETTLEMENT);
        nav("Realm / Faction", 106, OpenCampaignRequestPacket.ScreenTarget.FACTION);
        nav("Military", 132, OpenCampaignRequestPacket.ScreenTarget.MILITARY);
        nav("Diplomacy", 158, OpenCampaignRequestPacket.ScreenTarget.DIPLOMACY);
        nav("Research", 184, OpenCampaignRequestPacket.ScreenTarget.RESEARCH);
        nav("Chronicle", 210, OpenCampaignRequestPacket.ScreenTarget.WORLD_NEWS);
        nav("Character", 236, OpenCampaignRequestPacket.ScreenTarget.CHARACTER);
        addRenderableWidget(Button.builder(Component.literal("Found Settlement"), ignored -> minecraft.setScreen(new SettlementCreationScreen()))
                .bounds(left + 166, top + 247, 128, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(left + W - 70, top + 12, 52, 18).build());
    }

    private void nav(String label, int y, OpenCampaignRequestPacket.ScreenTarget target) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
            if (target != null) SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target));
        }).bounds(left + 14, top + y, 132, 22).build());
        button.active = target != null;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiX = campaignMouseX(mouseX), uiY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 42);
        graphics.drawString(font, "SETTLEMENTS", left + 16, top + 17, CampaignTheme.LINEN, false);
        graphics.drawString(font, "REALM OVERVIEW", left + 166, top + 17, CampaignTheme.BRASS, false);
        graphics.fill(left + 154, top + 48, left + 155, top + H - 14, CampaignTheme.BRASS_DARK);

        drawCampaignPanel(graphics, left + 166, top + 54, left + W - 18, top + 123);
        if (minecraft != null && minecraft.player != null)
            PortraitRenderer.render(graphics, minecraft.player, left + 176, top + 60, 48, 54, uiX, uiY);
        if (banner != null) banner.renderBanner(graphics, left + W - 66, top + 58, 20, 40, CAMPAIGN_BANNER_SCALE);
        graphics.drawString(font, ellipsize(snapshot.characterName(), 205), left + 234, top + 65, CampaignTheme.LINEN, false);
        graphics.drawString(font, ellipsize(snapshot.titleLine(), 205), left + 234, top + 82, CampaignTheme.BRASS, false);
        graphics.drawString(font, "Faith  " + snapshot.faith(), left + 234, top + 99, CampaignTheme.MUTED, false);

        drawCampaignPanel(graphics, left + 166, top + 132, left + W - 18, top + 232);
        graphics.drawString(font, "REALM AT A GLANCE", left + 178, top + 143, CampaignTheme.BRASS, false);
        metric(graphics, "Settlements", snapshot.settlementCount(), 178, 164);
        metric(graphics, "Population", snapshot.totalPopulation(), 332, 164);
        metric(graphics, "Treasury", snapshot.totalTreasury(), 178, 184);
        metric(graphics, "Stability", snapshot.averageStability() + "%", 332, 184);
        metric(graphics, "Manpower", snapshot.availableManpower() + " / " + snapshot.maximumManpower(), 178, 204);
        metric(graphics, "Food", snapshot.totalFood(), 332, 204);
        graphics.drawString(font, "Selected  " + ellipsize(snapshot.selectedSettlementName(), 250), left + 178, top + 219, CampaignTheme.MUTED, false);

        int notices = snapshot.pendingDiplomacy() + snapshot.activeWars();
        String notice = notices == 0 ? "No urgent diplomatic decisions." : snapshot.pendingDiplomacy() + " proposal(s)  ·  " + snapshot.activeWars() + " active war(s)";
        graphics.drawString(font, notice, left + 306, top + 254, notices == 0 ? CampaignTheme.MUTED : CampaignTheme.WARN, false);
        super.render(graphics, uiX, uiY, partialTick);
        endCampaignRender(graphics);
    }

    private void metric(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label, left + x, top + y, CampaignTheme.MUTED, false);
        graphics.drawString(font, String.valueOf(value), left + x + 76, top + y, CampaignTheme.LINEN, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
