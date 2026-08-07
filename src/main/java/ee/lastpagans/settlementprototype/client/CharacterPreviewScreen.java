package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.SubmitCharacterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CharacterPreviewScreen extends Screen {
    private static final int W = 390;
    private static final int H = 250;
    private final CharacterCreationScreen.CharacterDraft draft;
    private int left;
    private int top;

    public CharacterPreviewScreen(CharacterCreationScreen.CharacterDraft draft) {
        super(Component.literal("Character Preview"));
        this.draft = draft;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.literal("Go Back"), button -> minecraft.setScreen(new CharacterCreationScreen(draft)))
                .bounds(left + 16, top + 218, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Confirm Character"), button -> confirm())
                .bounds(left + 238, top + 218, 136, 20).build());
    }

    private void confirm() {
        PrototypeNetwork.CHANNEL.sendToServer(new SubmitCharacterPacket(
                draft.first(), draft.middle(), draft.last(), draft.faith(), draft.address(), draft.title(), draft.dynasty()
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 44, 0xFF3B3023);
        graphics.fill(left + 14, top + 54, left + 154, top + 207, 0xFF242424);
        graphics.drawCenteredString(font, draft.displayName().toUpperCase(), left + W / 2, top + 10, 0xFFE7D8B0);
        graphics.drawCenteredString(font, draft.title(), left + W / 2, top + 27, 0xFFC9B989);
        if (minecraft != null && minecraft.player != null) {
            PortraitRenderer.render(graphics, minecraft.player, left + 23, top + 63, 122, 135, mouseX, mouseY);
        }
        int x = left + 172;
        int y = top + 60;
        line(graphics, "Faith", draft.faith(), x, y);
        line(graphics, "Form of address", draft.address(), x, y + 25);
        line(graphics, "Dynasty", draft.dynasty().isBlank() ? "None" : draft.dynasty(), x, y + 50);
        graphics.drawWordWrap(font, Component.literal(
                "Two starting character traits will be generated when you confirm. They will be shown clearly on your character sheet."
        ), x, y + 83, 195, 0xFFAAAAAA);
        graphics.drawWordWrap(font, Component.literal(
                "This is the one-time correction screen. Use Go Back before confirming."
        ), x, y + 133, 195, 0xFFAA9D7F);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(font, label + ":", x, y, 0xFF999999, false);
        graphics.drawString(font, value, x + 86, y, 0xFFFFFFFF, false);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
