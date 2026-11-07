package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.DiplomacySnapshot;
import ee.lastpagans.settlements.network.DiplomacyActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Direct one-time support for a parent faction's settlement without formally joining its war. */
public final class WarAidScreen extends ScaledCampaignScreen {
    private final Screen parent;
    private final DiplomacySnapshot snapshot;
    private final DiplomacySnapshot.WarEntry war;
    private EditBox manpower;
    private EditBox treasury;
    private EditBox food;
    private int left;
    private int top;

    public WarAidScreen(Screen parent, DiplomacySnapshot snapshot, DiplomacySnapshot.WarEntry war) {
        super(Component.literal("Send War Aid"));
        this.parent = parent;
        this.snapshot = snapshot;
        this.war = war;
    }

    @Override
    protected void init() {
        setupCampaignScale(420, 250);
        left = campaignLeft(420);
        top = campaignTop(250);
        boolean maySend = canSendAid();
        manpower = numberField(left + 150, top + 78);
        treasury = numberField(left + 150, top + 108);
        food = numberField(left + 150, top + 138);
        manpower.setEditable(maySend);
        treasury.setEditable(maySend);
        food.setEditable(maySend);
        Button send = addRenderableWidget(Button.builder(Component.literal("Send One-time Aid"), b -> send())
                .bounds(left + 150, top + 176, 130, 20).build());
        send.active = maySend;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent))
                .bounds(left + 290, top + 176, 90, 20).build());
    }

    private EditBox numberField(int x, int y) {
        EditBox box = new EditBox(font, x, y, 130, 20, Component.empty());
        box.setValue("0");
        box.setFilter(v -> v.isEmpty() || v.matches("[0-9]{0,7}"));
        addRenderableWidget(box);
        return box;
    }

    private int parse(EditBox box) {
        try { return Math.max(0, Integer.parseInt(box.getValue().isBlank() ? "0" : box.getValue())); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void send() {
        String extra = parse(manpower) + "|" + parse(treasury) + "|" + parse(food);
        SettlementNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("SEND_AID", "", null, war.id(), extra));
        minecraft.setScreen(parent);
    }

    private boolean canSendAid() {
        String own = snapshot.ownFactionId();
        return snapshot.canManage() && !own.isBlank()
                && (own.equals(war.attackerFactionId()) || own.equals(war.defenderFactionId()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, 420, 250, 40);
        graphics.drawCenteredString(font, "SEND ONE-TIME WAR AID", left + 210, top + 14, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, war.attackerSettlementName() + " vs " + war.targetSettlementName(), left + 210, top + 49, 0xFFBBBBBB);
        graphics.drawString(font, "Manpower", left + 34, top + 84, 0xFFFFFFFF, false);
        graphics.drawString(font, "Treasury", left + 34, top + 114, 0xFFFFFFFF, false);
        graphics.drawString(font, "Food", left + 34, top + 144, 0xFFFFFFFF, false);
        if (!canSendAid()) graphics.drawString(font, "Only a parent faction may authorize aid.", left + 34, top + 205, 0xFFAAAAAA, false);
        graphics.drawCenteredString(font, "Aid does not make your faction a belligerent.", left + 210, top + 225, 0xFFFFD27A);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean isPauseScreen() { return false; }
}
