package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CharacterSnapshot;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.UpdateCharacterIdentityPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class CharacterIdentityEditScreen extends ScaledCampaignScreen {
    private static final int W = 420;
    private static final int H = 176;
    private final CharacterSnapshot snapshot;
    private final boolean returnToMain;
    private int left;
    private int top;
    private EditBox house;

    public CharacterIdentityEditScreen(CharacterSnapshot snapshot, boolean returnToMain) {
        super(Component.literal("Edit Main House"));
        this.snapshot = snapshot;
        this.returnToMain = returnToMain;
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);

        house = new EditBox(font, left + 150, top + 76, 242, 20, Component.literal("Main house"));
        house.setMaxLength(32);
        house.setHint(Component.literal("Main house (optional)"));
        house.setValue(snapshot.house());
        addRenderableWidget(house);

        addRenderableWidget(Button.builder(Component.literal("Back"), button ->
                minecraft.setScreen(new CharacterSheetScreen(snapshot, returnToMain)))
                .bounds(left + 150, top + 136, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new UpdateCharacterIdentityPacket(
                        house.getValue(), returnToMain)))
                .bounds(left + 250, top + 136, 142, 20).build());
        setInitialFocus(house);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 43, 0xFF3B3023);
        graphics.drawCenteredString(font, "EDIT MAIN HOUSE", left + W / 2, top + 14, 0xFFE7D8B0);
        graphics.drawWordWrap(font, Component.literal(
                "Main House is a roleplay identity field and can be changed later as your character's political situation develops."
        ), left + 22, top + 49, W - 44, 0xFFB8B0A0);
        graphics.drawString(font, "Main house", left + 26, top + 82, 0xFFDDDDDD, false);
        graphics.drawString(font, "Leave it blank if your character has no established house.", left + 26, top + 108, 0xFF888888, false);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean isPauseScreen() { return false; }
}
