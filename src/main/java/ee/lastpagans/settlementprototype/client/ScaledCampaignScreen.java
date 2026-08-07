package ee.lastpagans.settlementprototype.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared scaling support for the large campaign screens.
 *
 * Minecraft's GUI scale can leave less than 500 logical pixels of width on a 1080p display.
 * The campaign screens are designed on a fixed virtual canvas, then uniformly scaled down only
 * when that canvas would otherwise overflow. Mouse coordinates are translated back into the
 * virtual canvas so buttons/dropdowns remain clickable at every vanilla GUI scale.
 */
public abstract class ScaledCampaignScreen extends Screen {
    private float campaignScale = 1.0F;
    private int campaignVirtualWidth;
    private int campaignVirtualHeight;

    protected ScaledCampaignScreen(Component title) {
        super(title);
    }

    protected final void setupCampaignScale(int designWidth, int designHeight) {
        float horizontal = Math.max(0.01F, (width - 8.0F) / Math.max(1.0F, designWidth));
        float vertical = Math.max(0.01F, (height - 8.0F) / Math.max(1.0F, designHeight));
        campaignScale = Math.min(1.0F, Math.min(horizontal, vertical));
        // Do not impose an artificial minimum: fitting the whole menu is preferable to clipping it.
        campaignVirtualWidth = Math.max(designWidth, (int)Math.floor(width / campaignScale));
        campaignVirtualHeight = Math.max(designHeight, (int)Math.floor(height / campaignScale));
    }

    protected final int campaignLeft(int designWidth) {
        return (campaignVirtualWidth - designWidth) / 2;
    }

    protected final int campaignTop(int designHeight) {
        return (campaignVirtualHeight - designHeight) / 2;
    }

    protected final int campaignMouseX(int rawMouseX) {
        return (int)Math.floor(rawMouseX / campaignScale);
    }

    protected final int campaignMouseY(int rawMouseY) {
        return (int)Math.floor(rawMouseY / campaignScale);
    }

    protected final double campaignMouseX(double rawMouseX) {
        return rawMouseX / campaignScale;
    }

    protected final double campaignMouseY(double rawMouseY) {
        return rawMouseY / campaignScale;
    }

    protected final float campaignScale() {
        return campaignScale;
    }

    protected final void beginCampaignRender(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(campaignScale, campaignScale, 1.0F);
    }

    protected final void endCampaignRender(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(campaignMouseX(mouseX), campaignMouseY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(campaignMouseX(mouseX), campaignMouseY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(campaignMouseX(mouseX), campaignMouseY(mouseY), button,
                dragX / campaignScale, dragY / campaignScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(campaignMouseX(mouseX), campaignMouseY(mouseY), delta);
    }
}
