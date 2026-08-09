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
    private EditBox idBox, nameBox;

    public SettlementCreationScreen() { super(Component.literal("Found Settlement")); }

    @Override protected void init() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H);
        idBox = new EditBox(font, left + 118, top + 62, 214, 20, Component.literal("Settlement ID"));
        idBox.setMaxLength(32); idBox.setFilter(value -> value.matches("[A-Za-z0-9_\\-]*")); addRenderableWidget(idBox);
        nameBox = new EditBox(font, left + 118, top + 90, 214, 20, Component.literal("Settlement Name"));
        nameBox.setMaxLength(64); addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Found Settlement"), b -> create()).bounds(left + 118, top + 128, 128, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> back()).bounds(left + 252, top + 128, 80, 22).build());
        setInitialFocus(idBox);
    }

    private void create() {
        String id = idBox.getValue().trim(); String name = nameBox.getValue().trim();
        if (id.isBlank()) return; if (name.isBlank()) name = id;
        SettlementNetwork.CHANNEL.sendToServer(new FactionActionPacket("CREATE_SETTLEMENT", null, 0, id + "|" + name.replace('|','/')));
    }
    private void back() { SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)); }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int uiMx = campaignMouseX(mx);
        int uiMy = campaignMouseY(my);
        beginCampaignRender(g); g.fill(left, top, left + W, top + H, 0xF0181818); g.fill(left + 3, top + 3, left + W - 3, top + 40, 0xFF3B3023);
        g.drawCenteredString(font, "FOUND A SETTLEMENT", left + W / 2, top + 14, 0xFFE7D8B0);
        g.drawString(font, "Short ID", left + 24, top + 68, 0xFFCCCCCC, false); g.drawString(font, "Display name", left + 24, top + 96, 0xFFCCCCCC, false);
        g.drawCenteredString(font, "Town Square is set at your current position; the current Recruits claim is linked when possible.", left + W / 2, top + 160, 0xFF999999);
        super.render(g, uiMx, uiMy, pt);
        endCampaignRender(g);
    }
    @Override public boolean isPauseScreen() { return false; }
}
