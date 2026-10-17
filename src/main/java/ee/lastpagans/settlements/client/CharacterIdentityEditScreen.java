package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CharacterSnapshot;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.UpdateCharacterIdentityPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class CharacterIdentityEditScreen extends ScaledCampaignScreen {
    private static final int W = 440;
    private static final int H = 270;
    private final CharacterSnapshot snapshot;
    private final boolean returnToMain;
    private int left;
    private int top;
    private EditBox firstName;
    private EditBox middleName;
    private EditBox lastName;
    private EditBox house;

    public CharacterIdentityEditScreen(CharacterSnapshot snapshot, boolean returnToMain) {
        super(Component.literal("Edit Character Identity"));
        this.snapshot = snapshot;
        this.returnToMain = returnToMain;
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);

        firstName = nameBox(top + 72, "First name", snapshot.firstName());
        middleName = nameBox(top + 98, "Middle name", snapshot.middleName());
        lastName = nameBox(top + 124, "Last name", snapshot.lastName());

        house = new EditBox(font, left + 152, top + 178, 252, 20, Component.literal("Main house"));
        house.setMaxLength(32);
        house.setHint(Component.literal("Main house (optional)"));
        house.setValue(snapshot.house());
        addRenderableWidget(house);

        addRenderableWidget(Button.builder(Component.literal("Back"), button ->
                minecraft.setScreen(new CharacterSheetScreen(snapshot, returnToMain)))
                .bounds(left + 152, top + 232, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save Identity"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new UpdateCharacterIdentityPacket(
                        firstName.getValue(), middleName.getValue(), lastName.getValue(), house.getValue(), returnToMain)))
                .bounds(left + 260, top + 232, 144, 20).build());
        setInitialFocus(firstName);
    }

    private EditBox nameBox(int y, String hint, String value) {
        EditBox box = new EditBox(font, left + 152, y, 252, 20, Component.literal(hint));
        box.setMaxLength(24);
        box.setHint(Component.literal(hint));
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 43);
        graphics.drawCenteredString(font, "CHARACTER IDENTITY", left + W / 2, top + 14, CAMPAIGN_TITLE);

        graphics.drawWordWrap(font, Component.literal(
                "Your personal name can be changed, but doing so has a social and administrative cost. Main House changes remain free."
        ), left + 22, top + 49, W - 44, 0xFFB8B0A0);

        graphics.drawString(font, "First name", left + 28, top + 78, 0xFFDDDDDD, false);
        graphics.drawString(font, "Middle name", left + 28, top + 104, 0xFFDDDDDD, false);
        graphics.drawString(font, "Last name", left + 28, top + 130, 0xFFDDDDDD, false);
        graphics.drawWordWrap(font, Component.literal(
                        "Changing the name costs " + snapshot.renameCurrencyCost() + " item(s) from #settlements:currency."),
                left + 28, top + 151, W - 56, 0xFFFFC873);

        graphics.drawString(font, "Main house", left + 28, top + 184, 0xFFDDDDDD, false);
        graphics.drawString(font, "House edits are free and may be left blank.", left + 28, top + 207, 0xFF888888, false);

        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean isPauseScreen() { return false; }
}
