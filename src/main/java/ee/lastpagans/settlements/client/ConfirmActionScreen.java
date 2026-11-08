package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared high-risk action confirmation with an explicit consequence summary. */
public final class ConfirmActionScreen extends ScaledCampaignScreen {
    private static final int W = 400, H = 170;
    private final Screen parent;
    private final Component heading;
    private final Component detail;
    private final Runnable action;
    private int left, top;

    public ConfirmActionScreen(Screen parent, Component heading, Component detail, Runnable action) {
        super(heading);
        this.parent = parent; this.heading = heading; this.detail = detail; this.action = action;
    }

    @Override protected void init() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> minecraft.setScreen(parent))
                .bounds(left + 80, top + 128, 100, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Confirm"), ignored -> {
            action.run(); minecraft.setScreen(parent);
        }).bounds(left + 220, top + 128, 100, 22).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); int ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        beginCampaignRender(graphics); drawCampaignFrame(graphics, left, top, W, H, 42);
        graphics.drawCenteredString(font, heading, left + W / 2, top + 17, CampaignTheme.DANGER);
        graphics.drawWordWrap(font, detail, left + 28, top + 62, W - 56, CampaignTheme.LINEN);
        super.render(graphics, ux, uy, partialTick); endCampaignRender(graphics);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
