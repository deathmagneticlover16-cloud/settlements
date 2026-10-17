package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.DiplomacySnapshot;
import ee.lastpagans.settlements.network.DiplomacyActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
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
    private boolean manpowerDaily, wealthDaily, foodDaily;
    private int aidScroll;
    private int left, top;

    public WarAidScreen(Screen parent, DiplomacySnapshot snapshot, DiplomacySnapshot.WarEntry war) {
        super(Component.literal("Send War Aid")); this.parent = parent; this.snapshot = snapshot; this.war = war;
    }
    @Override protected void init() {
        setupCampaignScale(460, 370); left = campaignLeft(460); top = campaignTop(370);
        boolean maySend = canSendAid();
        manpower = numberField(left + 150, top + 78, 120, manpowerValue);
        wealth = numberField(left + 150, top + 108, 120, wealthValue);
        food = numberField(left + 150, top + 138, 120, foodValue);
        manpower.setEditable(maySend); wealth.setEditable(maySend); food.setEditable(maySend);

        Button manpowerFrequency = addRenderableWidget(Button.builder(Component.literal(manpowerDaily ? "Daily" : "One-time"), b -> { preserveValues(); manpowerDaily = !manpowerDaily; rebuildWidgets(); })
                .bounds(left + 285, top + 78, 125, 20).build());
        Button wealthFrequency = addRenderableWidget(Button.builder(Component.literal(wealthDaily ? "Daily" : "One-time"), b -> { preserveValues(); wealthDaily = !wealthDaily; rebuildWidgets(); })
                .bounds(left + 285, top + 108, 125, 20).build());
        Button foodFrequency = addRenderableWidget(Button.builder(Component.literal(foodDaily ? "Daily" : "One-time"), b -> { preserveValues(); foodDaily = !foodDaily; rebuildWidgets(); })
                .bounds(left + 285, top + 138, 125, 20).build());
        Button send = addRenderableWidget(Button.builder(Component.literal("Send Aid"), b -> send()).bounds(left + 210, top + 175, 100, 20).build());
        manpowerFrequency.active = maySend; wealthFrequency.active = maySend; foodFrequency.active = maySend; send.active = maySend;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(left + 320, top + 175, 100, 20).build());

        java.util.List<DiplomacySnapshot.AidEntry> commitments = commitments();
        aidScroll = Math.max(0, Math.min(aidScroll, Math.max(0, commitments.size() - 4)));
        for (int i = 0; i < Math.min(4, commitments.size() - aidScroll); i++) {
            DiplomacySnapshot.AidEntry aid = commitments.get(aidScroll + i);
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
    private EditBox numberField(int x, int y, int width, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.empty()); box.setValue(value); box.setFilter(v -> v.isEmpty() || v.matches("[0-9]{0,7}")); addRenderableWidget(box); return box;
    }
    private int parse(EditBox box) { try { return Math.max(0, Integer.parseInt(box.getValue().isBlank() ? "0" : box.getValue())); } catch (NumberFormatException ignored) { return 0; } }
    private void send() {
        preserveValues();
        String extra = parse(manpower) + "|" + parse(wealth) + "|" + parse(food) + "|"
                + manpowerDaily + "|" + wealthDaily + "|" + foodDaily;
        SettlementNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("SEND_AID", "", null, war.id(), extra));
        minecraft.setScreen(parent);
    }
    private boolean canSendAid() {
        String own = snapshot.ownFactionId();
        return snapshot.canManage() && !own.isBlank()
                && (own.equals(war.attackerFactionId()) || own.equals(war.defenderFactionId()));
    }
    private void cancel(DiplomacySnapshot.AidEntry aid) {
        SettlementNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("CANCEL_AID", "", aid.id(), war.id(), ""));
        minecraft.setScreen(parent);
    }
    private java.util.List<DiplomacySnapshot.AidEntry> commitments() {
        return snapshot.aid().stream().filter(value -> war.id().equals(value.warId()) && value.recurring()).toList();
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        java.util.List<DiplomacySnapshot.AidEntry> values = commitments();
        if (values.size() > 4) {
            preserveValues();
            aidScroll = Math.max(0, Math.min(values.size() - 4, aidScroll + (delta < 0 ? 1 : -1)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int uiMx = campaignMouseX(mx);
        int uiMy = campaignMouseY(my);
        beginCampaignRender(g);
        drawCampaignFrame(g, left, top, 460, 360, 40);
        g.drawCenteredString(font, canSendAid() ? "SEND AID / AID LEDGER" : "WAR AID LEDGER", left + 230, top + 14, CAMPAIGN_TITLE);
        g.drawCenteredString(font, war.attackerSettlementName() + " vs " + war.targetSettlementName(), left + 230, top + 49, 0xFFBBBBBB);
        g.drawString(font, "Manpower", left + 30, top + 84, 0xFFFFFFFF, false); g.drawString(font, "Wealth", left + 30, top + 114, 0xFFFFFFFF, false); g.drawString(font, "Food", left + 30, top + 144, 0xFFFFFFFF, false);
        g.drawString(font, "Each resource can be one-time or daily.", left + 30, top + 160, 0xFFAAAAAA, false);
        if (!canSendAid()) g.drawString(font, "Read-only: only a parent faction may authorize aid.", left + 30, top + 184, 0xFFAAAAAA, false);
        g.drawString(font, "ACTIVE DAILY AID", left + 30, top + 208, 0xFFE7D8B0, false);
        java.util.List<DiplomacySnapshot.AidEntry> commitments = commitments();
        if (commitments.isEmpty()) {
            g.drawString(font, "No recurring aid commitments are active for this war.", left + 30, top + 225, 0xFFAAAAAA, false);
        } else {
            for (int i = 0; i < Math.min(4, commitments.size() - aidScroll); i++) {
                DiplomacySnapshot.AidEntry aid = commitments.get(aidScroll + i);
                int y = top + 220 + i * 25;
                String line = aid.fromFactionName() + " → " + aid.targetSettlementName() + ": " + aid.summary();
                g.drawString(font, font.plainSubstrByWidth(line, 315), left + 30, y, 0xFFFFFFFF, false);
            }
            if (commitments.size() > 4) g.drawString(font, "Scroll aid ledger · " + (aidScroll + 1) + "-"
                    + Math.min(commitments.size(), aidScroll + 4) + " / " + commitments.size(), left + 30, top + 320, 0xFFAAAAAA, false);
        }
        g.drawCenteredString(font, "Sending aid does not make your faction a belligerent.", left + 230, top + 345, 0xFFFFD27A);
        super.render(g, uiMx, uiMy, pt);
        endCampaignRender(g);
    }
    @Override public boolean isPauseScreen() { return false; }
}
