package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.DiplomacySnapshot;
import ee.lastpagans.settlementprototype.network.DiplomacyActionPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One-time or daily material support for a parent faction's settlement war without joining it. */
public final class WarAidScreen extends Screen {
    private final Screen parent;
    private final DiplomacySnapshot.WarEntry war;
    private EditBox manpower, wealth, food;
    private boolean recurring;
    private int left, top;

    public WarAidScreen(Screen parent, DiplomacySnapshot.WarEntry war) {
        super(Component.literal("Send War Aid")); this.parent = parent; this.war = war;
    }
    @Override protected void init() {
        left = (width - 390) / 2; top = (height - 250) / 2;
        manpower = numberField(left + 180, top + 78, "0");
        wealth = numberField(left + 180, top + 108, "0");
        food = numberField(left + 180, top + 138, "0");
        addRenderableWidget(Button.builder(Component.literal(recurring ? "Frequency: Daily" : "Frequency: One-time"), b -> { recurring = !recurring; rebuildWidgets(); })
                .bounds(left + 30, top + 175, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Send Aid"), b -> send()).bounds(left + 200, top + 175, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(left + 286, top + 175, 70, 20).build());
    }
    @Override protected void rebuildWidgets() { clearWidgets(); init(); }
    private EditBox numberField(int x, int y, String value) {
        EditBox box = new EditBox(font, x, y, 170, 20, Component.empty()); box.setValue(value); box.setFilter(v -> v.isEmpty() || v.matches("[0-9]{0,7}")); addRenderableWidget(box); return box;
    }
    private int parse(EditBox box) { try { return Math.max(0, Integer.parseInt(box.getValue().isBlank() ? "0" : box.getValue())); } catch (NumberFormatException ignored) { return 0; } }
    private void send() {
        String extra = parse(manpower) + "|" + parse(wealth) + "|" + parse(food) + "|" + recurring;
        PrototypeNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("SEND_AID", "", null, war.id(), extra));
        minecraft.setScreen(parent);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g); g.fill(left, top, left + 390, top + 230, 0xF0181818); g.fill(left + 3, top + 3, left + 387, top + 40, 0xFF3B3023);
        g.drawCenteredString(font, "SEND AID", left + 195, top + 14, 0xFFE7D8B0);
        g.drawCenteredString(font, war.attackerSettlementName() + " vs " + war.targetSettlementName(), left + 195, top + 49, 0xFFBBBBBB);
        g.drawString(font, "Manpower", left + 30, top + 84, 0xFFFFFFFF, false); g.drawString(font, "Wealth", left + 30, top + 114, 0xFFFFFFFF, false); g.drawString(font, "Food", left + 30, top + 144, 0xFFFFFFFF, false);
        g.drawCenteredString(font, "Sending aid does not make your faction a belligerent.", left + 195, top + 207, 0xFFFFD27A);
        super.render(g, mx, my, pt);
    }
    @Override public boolean isPauseScreen() { return false; }
}
