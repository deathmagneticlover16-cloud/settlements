package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CharacterSnapshot;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.UpdateCharacterIdentityPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CharacterIdentityEditScreen extends Screen {
    private static final int W = 420;
    private static final int H = 210;
    private final CharacterSnapshot snapshot;
    private final boolean returnToMain;
    private int left;
    private int top;
    private EditBox dynasty;
    private EditBox house;

    public CharacterIdentityEditScreen(CharacterSnapshot snapshot, boolean returnToMain) {
        super(Component.literal("Edit Character Identity"));
        this.snapshot = snapshot;
        this.returnToMain = returnToMain;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        dynasty = new EditBox(font, left + 150, top + 72, 242, 20, Component.literal("Dynasty"));
        dynasty.setMaxLength(32);
        dynasty.setHint(Component.literal("Dynasty name (optional)"));
        dynasty.setValue(snapshot.dynasty());
        addRenderableWidget(dynasty);

        house = new EditBox(font, left + 150, top + 108, 242, 20, Component.literal("Main house"));
        house.setMaxLength(32);
        house.setHint(Component.literal("Main house (optional)"));
        house.setValue(snapshot.house());
        addRenderableWidget(house);

        addRenderableWidget(Button.builder(Component.literal("Back"), button ->
                minecraft.setScreen(new CharacterSheetScreen(snapshot, returnToMain)))
                .bounds(left + 150, top + 166, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button ->
                PrototypeNetwork.CHANNEL.sendToServer(new UpdateCharacterIdentityPacket(
                        dynasty.getValue(), house.getValue(), returnToMain)))
                .bounds(left + 250, top + 166, 142, 20).build());
        setInitialFocus(dynasty);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 43, 0xFF3B3023);
        graphics.drawCenteredString(font, "EDIT DYNASTY AND MAIN HOUSE", left + W / 2, top + 14, 0xFFE7D8B0);
        graphics.drawWordWrap(font, Component.literal(
                "These fields can be updated later as your character gains a dynasty or establishes a principal house."
        ), left + 22, top + 49, W - 44, 0xFFB8B0A0);
        graphics.drawString(font, "Dynasty", left + 26, top + 78, 0xFFDDDDDD, false);
        graphics.drawString(font, "Main house", left + 26, top + 114, 0xFFDDDDDD, false);
        graphics.drawString(font, "Leave either field blank until it exists in roleplay.", left + 26, top + 143, 0xFF888888, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
