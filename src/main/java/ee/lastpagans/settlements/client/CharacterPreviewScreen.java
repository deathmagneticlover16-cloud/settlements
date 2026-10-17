package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class CharacterPreviewScreen extends ScaledCampaignScreen implements CharacterDraftScreen {
    private static final int W = 390;
    private static final int H = 270;
    private final CharacterCreationDraftSession session;
    private int left;
    private int top;

    public CharacterPreviewScreen(CharacterCreationDraftSession session) {
        super(Component.literal("Character Preview"));
        this.session = session;
    }

    @Override public CharacterCreationDraftSession draftSession() { return session; }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        addRenderableWidget(Button.builder(Component.literal("Go Back"), button -> minecraft.setScreen(new CharacterCreationScreen(session)))
                .bounds(left + 16, top + 238, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Confirm Character"), button -> confirm())
                .bounds(left + 238, top + 238, 136, 20).build());
    }

    private void confirm() {
        minecraft.setScreen(new TraitSelectionScreen(session));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CharacterCreationDraftSession.CharacterDraft draft = session.draft();
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 44);
        drawCampaignPanel(graphics, left + 14, top + 54, left + 154, top + 227);
        graphics.drawCenteredString(font, draft.displayName().toUpperCase(), left + W / 2, top + 10, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, draft.title(), left + W / 2, top + 27, 0xFFC9B989);
        if (minecraft != null && minecraft.player != null) {
            ee.lastpagans.settlements.client.wildfire.SettlementsWildfirePreviewSession.renderDraft(session, () ->
                    PortraitRenderer.render(graphics, minecraft.player, left + 23, top + 63, 122, 155, uiMouseX, uiMouseY));
        }
        int x = left + 172;
        int y = top + 60;
        line(graphics, "Faith", draft.faith(), x, y);
        line(graphics, "Form of address", draft.address(), x, y + 25);
        line(graphics, "Title", draft.title(), x, y + 50);
        line(graphics, "Gender", draft.body().gender().displayName(), x, y + 75);
        line(graphics, "Breasts", draft.body().breasts().enabled() ? "Yes" : "No", x, y + 100);
        graphics.drawWordWrap(font, Component.literal(
                "After this preview you will choose three starting traits from three random cards per round. Main House and Main Seat can be set later."
        ), x, y + 128, 195, 0xFFAAAAAA);
        graphics.drawWordWrap(font, Component.literal(
                "This is the one-time correction screen. Use Go Back before confirming."
        ), x, y + 178, 195, 0xFFAA9D7F);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void line(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(font, label + ":", x, y, 0xFF999999, false);
        graphics.drawString(font, value, x + 86, y, 0xFFFFFFFF, false);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
