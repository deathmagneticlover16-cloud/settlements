package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Shared scaling support for the large campaign screens.
 *
 * Minecraft's GUI scale can leave less than 500 logical pixels of width on a 1080p display.
 * The campaign screens are designed on a fixed virtual canvas, then uniformly scaled down only
 * when that canvas would otherwise overflow. Mouse coordinates are translated back into the
 * virtual canvas so buttons/dropdowns remain clickable at every vanilla GUI scale.
 */
public abstract class ScaledCampaignScreen extends Screen {
    /** Recruits BannerRenderer ignores its width/height parameters and uses only the final scale. */
    protected static final int CAMPAIGN_BANNER_SCALE = 12;
    protected static final int CAMPAIGN_SMALL_BANNER_SCALE = 7;
    protected static final int CAMPAIGN_TITLE = 0xFFE7D8B0;
    protected static final int CAMPAIGN_TEXT = 0xFFFFFFFF;
    protected static final int CAMPAIGN_MUTED = 0xFFAAAAAA;
    protected static final int CAMPAIGN_MESSAGE = 0xFFFFD27A;
    private static final int CAMPAIGN_FRAME = 0xF0181818;
    private static final int CAMPAIGN_HEADER = 0xFF3B3023;
    private static final int CAMPAIGN_PANEL = 0xFF242424;
    private static final int CAMPAIGN_INSET = 0xFF191919;
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

    /**
     * Returns a single-line window that automatically travels to the end of long text and back.
     * Short labels are returned untouched. The end positions pause briefly so names/status messages
     * remain readable instead of constantly sliding.
     */
    protected final String scrollingText(String value, int pixelWidth) {
        String text = value == null ? "" : value;
        if (text.isEmpty() || pixelWidth <= 0 || font.width(text) <= pixelWidth) return text;
        String visibleSuffix = font.plainSubstrByWidth(text, pixelWidth, true);
        int lastStart = Math.max(0, text.length() - visibleSuffix.length());
        if (lastStart <= 0) lastStart = Math.max(1, text.length() - 1);
        final int pauseSteps = 5;
        int travel = Math.max(1, lastStart);
        int period = pauseSteps + travel + pauseSteps + travel;
        int step = (int)((net.minecraft.Util.getMillis() / 260L) % period);
        int start;
        if (step < pauseSteps) start = 0;
        else if (step < pauseSteps + travel) start = step - pauseSteps;
        else if (step < pauseSteps + travel + pauseSteps) start = travel;
        else start = travel - (step - (pauseSteps + travel + pauseSteps));
        start = Math.max(0, Math.min(lastStart, start));
        return font.plainSubstrByWidth(text.substring(start), pixelWidth);
    }

    /** Draws the common campaign window and header used by every full-screen menu. */
    protected final void drawCampaignFrame(GuiGraphics graphics, int left, int top, int width, int height,
                                           int headerBottom) {
        graphics.fill(left, top, left + width, top + height, CAMPAIGN_FRAME);
        graphics.fill(left + 3, top + 3, left + width - 3, top + headerBottom, CAMPAIGN_HEADER);
    }

    /** Draws a normal content panel using absolute bounds in the scaled campaign canvas. */
    protected final void drawCampaignPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, CAMPAIGN_PANEL);
    }

    /** Draws a darker nested panel for summaries and secondary content. */
    protected final void drawCampaignInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, CAMPAIGN_INSET);
    }

    /** Pixel-aware animated fit for status lines and names. */
    protected final String fitText(String value, int pixelWidth) {
        return scrollingText(value == null ? "" : value, pixelWidth);
    }

    /** Pixel-aware static truncation for dense rows and cards. */
    protected final String ellipsize(String value, int pixelWidth) {
        String text = value == null ? "" : value;
        if (pixelWidth <= 0 || font.width(text) <= pixelWidth) return text;
        String suffix = "…";
        int available = Math.max(0, pixelWidth - font.width(suffix));
        return font.plainSubstrByWidth(text, available) + suffix;
    }

    /** Adds a consistently spaced tab strip and disables the active tab as its selected state. */
    protected final void addCampaignTabs(String[] labels, int selected, int x, int y, int width, int height,
                                         int spacing, IntConsumer onSelected) {
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            Button button = Button.builder(Component.literal(labels[i]), ignored -> onSelected.accept(index))
                    .bounds(x + i * (width + spacing), y, width, height).build();
            button.active = i != selected;
            addRenderableWidget(button);
        }
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
        double uiX = campaignMouseX(mouseX);
        double uiY = campaignMouseY(mouseY);
        // Expanded dropdowns own the pointer before every other widget. Without this, a button
        // underneath a visually expanded menu can consume the same click.
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof DropdownWidget dropdown && dropdown.isExpanded()) {
                if (dropdown.mouseClicked(uiX, uiY, button)) return true;
                dropdown.close();
            }
        }
        return super.mouseClicked(uiX, uiY, button);
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
        double uiX = campaignMouseX(mouseX);
        double uiY = campaignMouseY(mouseY);
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof DropdownWidget dropdown && dropdown.isExpanded() && dropdown.mouseScrolled(uiX, uiY, delta)) return true;
        }
        return super.mouseScrolled(uiX, uiY, delta);
    }
}
