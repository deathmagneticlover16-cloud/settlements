package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.network.FactionActionPacket;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small GUI replacement for the most common settlement creation command. */
public final class SettlementCreationScreen extends ScaledCampaignScreen {
    private static final int W = 360, H = 190;
    private int left, top;
    private EditBox nameBox;

    public SettlementCreationScreen() { super(Component.literal("Found Settlement")); }

    @Override protected void init() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H);
        nameBox = new EditBox(font, left + 118, top + 76, 214, 20, Component.translatable("gui.settlements.settlement_name"));
        nameBox.setMaxLength(64); addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Found Settlement"), b -> create()).bounds(left + 118, top + 128, 128, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> back()).bounds(left + 252, top + 128, 80, 22).build());
        setInitialFocus(nameBox);
    }

    private void create() {
        String name = nameBox.getValue().trim();
        if (name.isBlank()) return;
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("CREATE_SETTLEMENT", null, 0, name.replace('|','/')));
    }
    private void back() { SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)); }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int uiMx = campaignMouseX(mx);
        int uiMy = campaignMouseY(my);
        beginCampaignRender(g);
        drawCampaignFrame(g, left, top, W, H, 40);
        g.drawCenteredString(font, "FOUND A SETTLEMENT", left + W / 2, top + 14, CAMPAIGN_TITLE);
        g.drawString(font, Component.translatable("gui.settlements.settlement_name"), left + 24, top + 82, 0xFFCCCCCC, false);
        g.drawCenteredString(font, Component.translatable("gui.settlements.founding_hint"), left + W / 2, top + 160, 0xFF999999);
        super.render(g, uiMx, uiMy, pt);
        endCampaignRender(g);
    }
    @Override public boolean isPauseScreen() { return false; }
}
