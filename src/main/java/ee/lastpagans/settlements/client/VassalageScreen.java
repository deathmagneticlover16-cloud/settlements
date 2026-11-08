package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignManager;
import ee.lastpagans.settlements.DiplomacySnapshot;
import ee.lastpagans.settlements.network.DiplomacyActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Authoritative tribute administration for an existing overlord/vassal relationship. */
public final class VassalageScreen extends ScaledCampaignScreen {
    private static final int W = 520, H = 330;
    private final CampaignDiplomacyScreen parent;
    private final DiplomacySnapshot snapshot;
    private final DiplomacySnapshot.RelationEntry relation;
    private int left, top, selectedRate;
    private boolean canImpose;

    public VassalageScreen(CampaignDiplomacyScreen parent, DiplomacySnapshot snapshot, DiplomacySnapshot.RelationEntry relation) {
        super(Component.literal("Vassalage / Tribute"));
        this.parent = parent;
        this.snapshot = snapshot;
        this.relation = relation;
        this.selectedRate = relation.tributeRate();
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W); top = campaignTop(H);
        canImpose = snapshot.canManage() && snapshot.ownFactionId().equals(relation.overlordId());
        TributeRateSlider slider = addRenderableWidget(new TributeRateSlider(left + 34, top + 112, W - 68, 22,
                selectedRate, rate -> selectedRate = rate));
        slider.active = canImpose;
        Button apply = addRenderableWidget(Button.builder(Component.literal("Apply Tribute"), ignored -> send("SET_TRIBUTE", relation.vassalId(), Integer.toString(selectedRate)))
                .bounds(left + W - 174, top + H - 52, 140, 22).build());
        apply.active = canImpose;
        if (canImpose) addRenderableWidget(Button.builder(Component.literal("Release Vassal"), ignored -> send("RELEASE_VASSAL", relation.vassalId(), ""))
                .bounds(left + 34, top + H - 52, 116, 22).build());
        else if (snapshot.canManage() && snapshot.ownFactionId().equals(relation.vassalId()))
            addRenderableWidget(Button.builder(Component.literal("Seek Independence"), ignored -> send("SEEK_INDEPENDENCE", "", ""))
                    .bounds(left + 34, top + H - 52, 130, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> minecraft.setScreen(parent))
                .bounds(left + W - 82, top + 14, 48, 18).build());
    }

    private void send(String action, String target, String extra) {
        SettlementNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket(action, target, null, null, extra));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiX = campaignMouseX(mouseX), uiY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 48);
        drawCampaignPanel(graphics, left + 18, top + 58, left + W - 18, top + H - 66);
        graphics.drawString(font, "VASSALAGE / TRIBUTE", left + 24, top + 18, CAMPAIGN_TITLE, false);
        String overlord = snapshot.ownFactionId().equals(relation.overlordId()) ? snapshot.ownFactionName() : relation.factionName();
        String vassal = snapshot.ownFactionId().equals(relation.vassalId()) ? snapshot.ownFactionName() : relation.factionName();
        graphics.drawCenteredString(font, overlord + "  - overlord of -  " + vassal, left + W / 2, top + 72, CAMPAIGN_TEXT);
        graphics.drawCenteredString(font, canImpose ? "Choose one of four legal tribute tiers." : "Only the overlord ruler may alter the rate.",
                left + W / 2, top + 91, CAMPAIGN_MUTED);
        int oldPenalty = CampaignManager.tributePenalty(relation.tributeRate());
        int newPenalty = CampaignManager.tributePenalty(selectedRate);
        int ceiling = CampaignManager.tributeRelationshipCeiling(selectedRate);
        int resulting = Math.max(-100, Math.min(ceiling, relation.relationship() + newPenalty - oldPenalty));
        int projectedTribute = (int)((long)relation.projectedGrossTax() * selectedRate / 100L);
        int retained = relation.projectedGrossTax() - projectedTribute;
        drawCampaignInset(graphics, left + 34, top + 150, left + W - 34, top + 238);
        line(graphics, "Projected actual tax", relation.projectedGrossTax(), 164);
        line(graphics, "Overlord receives", projectedTribute, 182);
        line(graphics, "Vassal retains", retained, 200);
        line(graphics, "One relationship score", relation.relationship() + "  →  " + resulting, 218);
        String warning = selectedRate == 50 ? "Oppressive: relationship capped at -50; severe local unrest."
                : selectedRate == 25 ? "Heavy: relationship capped at +20; substantial local unrest."
                : selectedRate == 10 ? "Standard obligation; moderate political pressure." : "Light obligation; limited unrest.";
        graphics.drawCenteredString(font, warning, left + W / 2, top + 250, selectedRate >= 25 ? 0xFFFF9C78 : CAMPAIGN_MUTED);
        super.render(graphics, uiX, uiY, partialTick);
        endCampaignRender(graphics);
    }

    private void line(GuiGraphics graphics, String label, Object value, int y) {
        graphics.drawString(font, label, left + 50, top + y, CAMPAIGN_MUTED, false);
        graphics.drawString(font, String.valueOf(value), left + 330, top + y, CAMPAIGN_TEXT, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
