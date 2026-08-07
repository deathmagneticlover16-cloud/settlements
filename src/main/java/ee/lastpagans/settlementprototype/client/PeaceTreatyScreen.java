package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CampaignDefinitions;
import ee.lastpagans.settlementprototype.DiplomacySnapshot;
import ee.lastpagans.settlementprototype.network.DiplomacyActionPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Negotiated Settlements 2.3 peace treaty builder. */
public final class PeaceTreatyScreen extends ScaledCampaignScreen {
    private static final int W = 520, H = 410;
    private static final int MAX_SELECTED_CLAIMS = 40;
    private final Screen parent;
    private final DiplomacySnapshot snapshot;
    private final DiplomacySnapshot.WarEntry war;
    private int left, top;
    private EditBox wealth, manpower, reparations, reparationsDays, napDays;
    private String wealthValue = "0", manpowerValue = "0", reparationsValue = "0", reparationsDaysValue = "0", napDaysValue = "0";
    private DropdownWidget faith, settlement, claimSettlement, claim, destination;
    private int faithIndex, settlementIndex, claimSettlementIndex, claimIndex, destinationIndex;
    private final Set<UUID> selectedClaimIds = new LinkedHashSet<>();
    private boolean convertFaith, transferSettlement, transferClaim, recognizeIndependence;

    public PeaceTreatyScreen(Screen parent, DiplomacySnapshot snapshot, DiplomacySnapshot.WarEntry war) {
        super(Component.literal("Peace Treaty")); this.parent = parent; this.snapshot = snapshot; this.war = war;
    }

    @Override protected void init() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H);
        wealth = number(left + 142, top + 68, 104, wealthValue); manpower = number(left + 142, top + 96, 104, manpowerValue);
        reparations = number(left + 142, top + 124, 104, reparationsValue); reparationsDays = number(left + 142, top + 152, 104, reparationsDaysValue);
        napDays = number(left + 142, top + 180, 104, napDaysValue);

        addRenderableWidget(Button.builder(Component.literal(convertFaith ? "☑ Force faith conversion" : "☐ Force faith conversion"), b -> { preserveValues(); convertFaith = !convertFaith; rebuildWidgets(); })
                .bounds(left + 272, top + 68, 220, 20).build());
        faithIndex = clamp(faithIndex, CampaignDefinitions.RELIGIONS.size());
        if (convertFaith) faith = addRenderableWidget(new DropdownWidget(left + 272, top + 92, 220, 20, CampaignDefinitions.RELIGIONS, faithIndex, v -> faithIndex = Math.max(0, CampaignDefinitions.RELIGIONS.indexOf(v))));

        List<DiplomacySnapshot.SettlementTarget> enemySettlements = enemySettlements();
        List<String> enemyNames = enemySettlements.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
        settlementIndex = clamp(settlementIndex, enemyNames.size());
        addRenderableWidget(Button.builder(Component.literal(transferSettlement ? "☑ Transfer settlement" : "☐ Transfer settlement"), b -> { preserveValues(); transferSettlement = !transferSettlement; rebuildWidgets(); })
                .bounds(left + 272, top + 120, 220, 20).build());
        if (transferSettlement) settlement = addRenderableWidget(new DropdownWidget(left + 272, top + 144, 220, 20,
                enemyNames.isEmpty() ? List.of("No enemy settlement") : enemyNames, settlementIndex,
                v -> { preserveValues(); settlementIndex = Math.max(0, enemyNames.indexOf(v)); rebuildWidgets(); }));

        addRenderableWidget(Button.builder(Component.literal(transferClaim
                        ? "☑ Transfer linked claims (" + selectedClaimIds.size() + " selected)"
                        : "☐ Transfer linked claims"), b -> { preserveValues(); transferClaim = !transferClaim; rebuildWidgets(); })
                .bounds(left + 272, top + 172, 220, 20).build());
        if (transferClaim) {
            List<String> claimSettlementNames = enemySettlements.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
            claimSettlementIndex = clamp(claimSettlementIndex, claimSettlementNames.size());
            claimSettlement = addRenderableWidget(new DropdownWidget(left + 272, top + 196, 220, 20,
                    claimSettlementNames.isEmpty() ? List.of("No enemy settlement") : claimSettlementNames, claimSettlementIndex, v -> {
                        preserveValues(); claimSettlementIndex = Math.max(0, claimSettlementNames.indexOf(v)); claimIndex = 0; rebuildWidgets();
                    }));
            List<DiplomacySnapshot.ClaimTarget> claims = selectedClaimSettlement() == null ? List.of() : selectedClaimSettlement().claims();
            List<String> claimNames = claims.stream().map(DiplomacySnapshot.ClaimTarget::display).toList();
            claimIndex = clamp(claimIndex, claimNames.size());
            claim = addRenderableWidget(new DropdownWidget(left + 272, top + 220, 150, 20,
                    claimNames.isEmpty() ? List.of("No linked claim") : claimNames, claimIndex, v -> { preserveValues(); claimIndex = Math.max(0, claimNames.indexOf(v)); rebuildWidgets(); }));
            DiplomacySnapshot.ClaimTarget currentClaim = selectedClaim();
            Button toggleClaim = addRenderableWidget(Button.builder(Component.literal(currentClaim != null && selectedClaimIds.contains(currentClaim.id()) ? "Remove" : "Add"), b -> toggleSelectedClaim())
                    .bounds(left + 428, top + 220, 64, 20).build());
            toggleClaim.active = currentClaim != null && (selectedClaimIds.contains(currentClaim.id()) || selectedClaimIds.size() < MAX_SELECTED_CLAIMS);
            List<DiplomacySnapshot.SettlementTarget> own = ownSettlements();
            List<String> ownNames = own.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
            destinationIndex = clamp(destinationIndex, ownNames.size());
            destination = addRenderableWidget(new DropdownWidget(left + 272, top + 244, 220, 20,
                    ownNames.isEmpty() ? List.of("No receiving settlement") : ownNames, destinationIndex, v -> destinationIndex = Math.max(0, ownNames.indexOf(v))));
        }

        addRenderableWidget(Button.builder(Component.literal(recognizeIndependence ? "☑ Recognize independence" : "☐ Recognize independence"), b -> { preserveValues(); recognizeIndependence = !recognizeIndependence; rebuildWidgets(); })
                .bounds(left + 272, top + 276, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Send Treaty"), b -> send()).bounds(left + 300, top + 355, 112, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(left + 418, top + 355, 74, 22).build());
    }

    @Override protected void rebuildWidgets() { clearWidgets(); init(); }
    private void preserveValues() {
        if (wealth != null) wealthValue = wealth.getValue();
        if (manpower != null) manpowerValue = manpower.getValue();
        if (reparations != null) reparationsValue = reparations.getValue();
        if (reparationsDays != null) reparationsDaysValue = reparationsDays.getValue();
        if (napDays != null) napDaysValue = napDays.getValue();
        if (faith != null) faithIndex = Math.max(0, CampaignDefinitions.RELIGIONS.indexOf(faith.value()));
    }
    private EditBox number(int x, int y, int w, String value) { EditBox box = new EditBox(font, x, y, w, 20, Component.empty()); box.setValue(value == null || value.isBlank() ? "0" : value); box.setFilter(v -> v.isEmpty() || v.matches("[0-9]{0,7}")); addRenderableWidget(box); return box; }
    private int value(EditBox box) { try { return Math.max(0, Integer.parseInt(box.getValue().isBlank() ? "0" : box.getValue())); } catch (NumberFormatException ignored) { return 0; } }

    private List<DiplomacySnapshot.SettlementTarget> ownSettlements() {
        boolean viewerAttacker = isViewerAttackerSide();
        boolean settlementWar = "SETTLEMENT_WAR".equals(war.type());
        boolean parentJoined = viewerAttacker ? war.attackerFactionJoined() : war.defenderFactionJoined();
        String sideSettlement = viewerAttacker ? war.attackerSettlementId() : war.targetSettlementId();
        String sideFaction = viewerAttacker ? war.attackerFactionId() : war.defenderFactionId();
        boolean viewerActsAsFaction = snapshot.canManage() && !sideFaction.isBlank() && sideFaction.equals(snapshot.ownFactionId())
                && (!settlementWar || parentJoined);
        return snapshot.settlements().stream()
                .filter(s -> viewerActsAsFaction ? sideFaction.equals(s.factionId())
                        : s.manageable() && s.settlementId().equals(sideSettlement))
                .toList();
    }
    private List<DiplomacySnapshot.SettlementTarget> enemySettlements() {
        boolean viewerAttacker = isViewerAttackerSide();
        boolean settlementWar = "SETTLEMENT_WAR".equals(war.type());
        boolean enemyParentJoined = viewerAttacker ? war.defenderFactionJoined() : war.attackerFactionJoined();
        String enemyFaction = viewerAttacker ? war.defenderFactionId() : war.attackerFactionId();
        String enemySettlement = viewerAttacker ? war.targetSettlementId() : war.attackerSettlementId();
        return snapshot.settlements().stream()
                .filter(s -> !settlementWar || enemyParentJoined
                        ? (!enemyFaction.isBlank() && enemyFaction.equals(s.factionId())) || s.settlementId().equals(enemySettlement)
                        : s.settlementId().equals(enemySettlement))
                .toList();
    }
    private boolean isViewerAttackerSide() {
        String ownFaction = snapshot.ownFactionId();
        if (!ownFaction.isBlank()) {
            if (ownFaction.equals(war.attackerFactionId()) || war.viewerIsAttackerParent()) return true;
            if (ownFaction.equals(war.defenderFactionId()) || war.viewerIsDefenderParent()) return false;
        }
        return snapshot.settlements().stream()
                .anyMatch(s -> s.manageable() && s.settlementId().equals(war.attackerSettlementId()));
    }
    private DiplomacySnapshot.SettlementTarget selectedEnemySettlement() { List<DiplomacySnapshot.SettlementTarget> v = enemySettlements(); return v.isEmpty() ? null : v.get(clamp(settlementIndex, v.size())); }
    private DiplomacySnapshot.SettlementTarget selectedClaimSettlement() { List<DiplomacySnapshot.SettlementTarget> v = enemySettlements(); return v.isEmpty() ? null : v.get(clamp(claimSettlementIndex, v.size())); }
    private DiplomacySnapshot.SettlementTarget selectedDestination() { List<DiplomacySnapshot.SettlementTarget> v = ownSettlements(); return v.isEmpty() ? null : v.get(clamp(destinationIndex, v.size())); }
    private DiplomacySnapshot.ClaimTarget selectedClaim() { DiplomacySnapshot.SettlementTarget s = selectedClaimSettlement(); if (s == null || s.claims().isEmpty()) return null; return s.claims().get(clamp(claimIndex, s.claims().size())); }
    private void toggleSelectedClaim() {
        preserveValues();
        DiplomacySnapshot.ClaimTarget selected = selectedClaim();
        if (selected == null) return;
        if (!selectedClaimIds.remove(selected.id()) && selectedClaimIds.size() < MAX_SELECTED_CLAIMS) selectedClaimIds.add(selected.id());
        rebuildWidgets();
    }

    private void send() {
        preserveValues();
        List<String> terms = new ArrayList<>();
        if (value(wealth) > 0) terms.add("WEALTH~~" + value(wealth) + "~0~");
        if (value(manpower) > 0) terms.add("MANPOWER~~" + value(manpower) + "~0~");
        if (value(reparations) > 0 && value(reparationsDays) > 0) terms.add("REPARATIONS~~" + value(reparations) + "~" + value(reparationsDays) + "~");
        if (value(napDays) > 0) terms.add("NAP~~0~" + value(napDays) + "~");
        if (convertFaith && faith != null) terms.add("CONVERT_FAITH~~0~0~" + clean(faith.value()));
        DiplomacySnapshot.SettlementTarget enemy = selectedEnemySettlement();
        if (transferSettlement && enemy != null) terms.add("TRANSFER_SETTLEMENT~" + clean(enemy.settlementId()) + "~0~0~");
        DiplomacySnapshot.SettlementTarget destinationTarget = selectedDestination();
        if (transferClaim && destinationTarget != null) {
            Set<UUID> validEnemyClaims = new LinkedHashSet<>();
            for (DiplomacySnapshot.SettlementTarget source : enemySettlements())
                for (DiplomacySnapshot.ClaimTarget claimTarget : source.claims()) validEnemyClaims.add(claimTarget.id());
            for (UUID claimId : selectedClaimIds) if (validEnemyClaims.contains(claimId))
                terms.add("TRANSFER_CLAIM~" + claimId + "~0~0~" + clean(destinationTarget.settlementId()));
        }
        if (recognizeIndependence && enemy != null) terms.add("RECOGNIZE_INDEPENDENCE~" + clean(enemy.settlementId()) + "~0~0~");
        PrototypeNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket("OFFER_PEACE_TERMS", "", null, war.id(), String.join(";", terms)));
        minecraft.setScreen(parent);
    }
    private static String clean(String s) { return s == null ? "" : s.replace('~','/').replace(';','/').replace('|','/'); }
    private static int clamp(int i, int size) { return size <= 0 ? 0 : Math.max(0, Math.min(i, size - 1)); }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        for (DropdownWidget d : new DropdownWidget[]{faith, settlement, claimSettlement, claim, destination}) if (d != null && d.isExpanded() && d.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        for (DropdownWidget d : new DropdownWidget[]{faith, settlement, claimSettlement, claim, destination}) if (d != null && d.isExpanded() && d.mouseScrolled(uiX, uiY, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int uiMx = campaignMouseX(mx);
        int uiMy = campaignMouseY(my);
        beginCampaignRender(g); g.fill(left, top, left + W, top + H, 0xF0181818); g.fill(left + 3, top + 3, left + W - 3, top + 40, 0xFF3B3023);
        g.drawCenteredString(font, "NEGOTIATE PEACE", left + W / 2, top + 13, 0xFFE7D8B0);
        g.drawCenteredString(font, war.typeName() + " · War score " + (war.warScore() > 0 ? "+" : "") + war.warScore(), left + W / 2, top + 29, 0xFFAAAAAA);
        g.drawString(font, "Lump wealth", left + 25, top + 74, 0xFFFFFFFF, false); g.drawString(font, "Manpower", left + 25, top + 102, 0xFFFFFFFF, false);
        g.drawString(font, "Reparations/day", left + 25, top + 130, 0xFFFFFFFF, false); g.drawString(font, "Reparation days", left + 25, top + 158, 0xFFFFFFFF, false); g.drawString(font, "Forced NAP days", left + 25, top + 186, 0xFFFFFFFF, false);
        g.drawWordWrap(font, Component.literal("Terms are demands on the opposing side. A side at ±90 war score is considered capitulated and cannot reject terms from the dominant side."), left + 25, top + 224, 220, 0xFFFFD27A);
        if (transferClaim) g.drawString(font, "Selected territorial claims: " + selectedClaimIds.size() + "/" + MAX_SELECTED_CLAIMS, left + 272, top + 302, 0xFFAAAAAA, false);
        super.render(g, uiMx, uiMy, pt);
        endCampaignRender(g);
    }
    @Override public boolean isPauseScreen() { return false; }
}
