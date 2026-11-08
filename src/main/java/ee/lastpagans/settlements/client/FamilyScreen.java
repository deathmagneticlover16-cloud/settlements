package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.FamilySnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Read-only Character > Marriage detail. Marriage state is changed only by administrators. */
public final class FamilyScreen extends ScaledCampaignScreen {
    private static final int W = 520, H = 300;
    private final FamilySnapshot snapshot;
    private int left, top;

    public FamilyScreen(FamilySnapshot snapshot) {
        super(Component.translatable("gui.settlements.marriage"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W); top = campaignTop(H);
        addRenderableWidget(Button.builder(Component.translatable("gui.settlements.back_character"), button ->
                        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.CHARACTER)))
                .bounds(left + W - 142, top + H - 30, 122, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiX = campaignMouseX(mouseX), uiY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 48);
        drawCampaignPanel(graphics, left + 18, top + 60, left + W - 18, top + H - 44);
        graphics.drawString(font, Component.translatable("gui.settlements.character_marriage", snapshot.characterName()),
                left + 22, top + 19, CAMPAIGN_TITLE, false);
        if (snapshot.married()) {
            graphics.drawString(font, Component.translatable("gui.settlements.spouse", snapshot.spouseName()), left + 34, top + 82, CAMPAIGN_TEXT, false);
            graphics.drawString(font, Component.translatable("gui.settlements.faith", snapshot.spouseFaith()), left + 34, top + 104, CAMPAIGN_MUTED, false);
            graphics.drawString(font, Component.translatable("gui.settlements.marriage_duration", snapshot.marriageDurationDays()), left + 34, top + 126, CAMPAIGN_MUTED, false);
            graphics.drawString(font, Component.translatable("gui.settlements.traits"), left + 34, top + 154, CAMPAIGN_TITLE, false);
            graphics.drawWordWrap(font, Component.literal(snapshot.spouseTraits()), left + 34, top + 170, W - 68, CAMPAIGN_TEXT);
            graphics.drawString(font, Component.translatable("gui.settlements.active_effects"), left + 34, top + 205, CAMPAIGN_TITLE, false);
            graphics.drawWordWrap(font, Component.literal(snapshot.spouseEffect()), left + 34, top + 221, W - 68, CAMPAIGN_TEXT);
        } else {
            graphics.drawString(font, Component.translatable("gui.settlements.unmarried"), left + 34, top + 88, CAMPAIGN_TITLE, false);
            graphics.drawWordWrap(font, Component.translatable("gui.settlements.marriage_admin_hint"), left + 34, top + 112, W - 68, CAMPAIGN_MUTED);
        }
        if (!snapshot.message().isBlank()) graphics.drawWordWrap(font, Component.literal(snapshot.message()), left + 34, top + 254, W - 190, CAMPAIGN_MESSAGE);
        super.render(graphics, uiX, uiY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean isPauseScreen() { return false; }
}
