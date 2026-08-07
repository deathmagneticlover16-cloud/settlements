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
public final class WarAidScreen extends ScaledCampaignScreen {
    private final Screen parent;
    private final DiplomacySnapshot snapshot;
    private final DiplomacySnapshot.WarEntry war;
    private EditBox manpower, wealth, food;
    private String manpowerValue = "0", wealthValue = "0", foodValue = "0";
    private boolean recurring;
    private int left, top;

    public WarAidScreen(Screen parent, DiplomacySnapshot snapshot, DiplomacySnapshot.WarEntry war) {
        super(Component.literal("Send War Aid")); this.parent = parent; this.snapshot = snapshot; this.war = war;
    }
    @Override protected void init() {
        setupCampaignScale(460, 370); left = campaignLeft(460); top = campaignTop(370);
        manpower = numberField(left + 200, top + 78, manpowerValue);
        wealth = numberField(left + 200, top + 108, wealthValue);
        food = numberField(left + 200, top + 138, foodValue);
        addRenderableWidget(Button.builder(Component.literal(recurring ? "Frequency: Daily" : "Frequency: One-time"), b -> { preserveValues(); recurring = !recurring; rebuildWidgets(); })
                .bounds(left + 30, top + 175, 170, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Send Aid"), b -> send()).bounds(left + 210, top + 175, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(left + 320, top + 175, 100, 20).build());

        java.util.List<DiplomacySnapshot.AidEntry> commitments = commitments();
        for (int i = 0; i < Math.min(4, commitments.size()); i++) {
            DiplomacySnapshot.AidEntry aid = commitments.get(i);
            if (snapshot.canManage() && snapshot.ownFactionId().equals(aid.fromFactionId())) {
                int y = top + 220 + i * 25;
                addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> cancel(aid))
                        .bounds(left + 355, y - 5, 65, 20).build());
            }
        }
    }
    @Override protected void rebuildWidgets() { clearWidgets(); init(); }
    private void preserveValues() {
        if (manpower != null) manpowerValue = manpower.getValue();
        if (wealth != null) wealthValue = wealth.getValue();
        if (food != null) foodValue = food.getValue();
    }
    private EditBox numberField(int x, int y, String value) {
        EditBox box = new EditBox(font, x, y, 170, 20, Component.empty()); box.setValue(value); box.setFilter(v -> v.isEmpty() || v.matches("[0-9]{0,7}")); addRenderableWidget(box); return box;
    }
    private int parse(EditBox box) { try { return Math.max(0, Integer.parseInt(box.getValue().isBlank() ? "0" : box.getValue())); } catch (NumberFormatException ignored) { return 0; } }
    private void send() {
        preserveValues();
        String extra = parse(manpower) + "|" + parse(wealth) + "|" + parse(food) + "|" + recurring;
        PrototypeNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("SEND_AID", "", null, war.id(), extra));
        minecraft.setScreen(parent);
    }
    private void cancel(DiplomacySnapshot.AidEntry aid) {
        PrototypeNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("CANCEL_AID", "", aid.id(), war.id(), ""));
        minecraft.setScreen(parent);
    }
    private java.util.List<DiplomacySnapshot.AidEntry> commitments() {
        return snapshot.aid().stream().filter(value -> war.id().equals(value.warId()) && value.recurring()).toList();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int uiMx = campaignMouseX(mx);
        int uiMy = campaignMouseY(my);
        beginCampaignRender(g); g.fill(left, top, left + 460, top + 360, 0xF0181818); g.fill(left + 3, top + 3, left + 457, top + 40, 0xFF3B3023);
        g.drawCenteredString(font, "SEND AID", left + 230, top + 14, 0xFFE7D8B0);
        g.drawCenteredString(font, war.attackerSettlementName() + " vs " + war.targetSettlementName(), left + 230, top + 49, 0xFFBBBBBB);
        g.drawString(font, "Manpower", left + 30, top + 84, 0xFFFFFFFF, false); g.drawString(font, "Wealth", left + 30, top + 114, 0xFFFFFFFF, false); g.drawString(font, "Food", left + 30, top + 144, 0xFFFFFFFF, false);
        g.drawString(font, "ACTIVE DAILY AID", left + 30, top + 208, 0xFFE7D8B0, false);
        java.util.List<DiplomacySnapshot.AidEntry> commitments = commitments();
        if (commitments.isEmpty()) {
            g.drawString(font, "No recurring aid commitments are active for this war.", left + 30, top + 225, 0xFFAAAAAA, false);
        } else {
            for (int i = 0; i < Math.min(4, commitments.size()); i++) {
                DiplomacySnapshot.AidEntry aid = commitments.get(i);
                int y = top + 220 + i * 25;
                String line = aid.fromFactionName() + " → " + aid.targetSettlementName() + ": " + aid.summary();
                g.drawString(font, font.plainSubstrByWidth(line, 315), left + 30, y, 0xFFFFFFFF, false);
            }
            if (commitments.size() > 4) g.drawString(font, "+ " + (commitments.size() - 4) + " more commitments", left + 30, top + 320, 0xFFAAAAAA, false);
        }
        g.drawCenteredString(font, "Sending aid does not make your faction a belligerent.", left + 230, top + 345, 0xFFFFD27A);
        super.render(g, uiMx, uiMy, pt);
        endCampaignRender(g);
    }
    @Override public boolean isPauseScreen() { return false; }
}
