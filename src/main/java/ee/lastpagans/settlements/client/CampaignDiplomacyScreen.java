package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignData;
import ee.lastpagans.settlements.CultureDefinition;
import ee.lastpagans.settlements.DiplomacySnapshot;
import ee.lastpagans.settlements.network.DiplomacyActionPacket;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Map-centred, contextual diplomacy workspace backed exclusively by server snapshots. */
public final class CampaignDiplomacyScreen extends ScaledCampaignScreen {
    private static final int W = 840, H = 460;
    private final DiplomacySnapshot snapshot;
    private int left, top, selectedIndex = -1, scroll;
    private Overlay overlay = Overlay.NONE;
    private CampaignData.ProposalType draftTerm;
    private CampaignData.WarType warType = CampaignData.WarType.WAR;
    private int goalIndex, settlementIndex;
    private EditBox customReason;
    private Button declareButton;
    private final Set<String> selectedAllies = new LinkedHashSet<>();

    public CampaignDiplomacyScreen(DiplomacySnapshot snapshot) {
        super(Component.literal("Last Pagans · Diplomacy"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H); rebuild();
    }

    private void rebuild() {
        clearWidgets(); customReason = null; declareButton = null;
        addRenderableWidget(Button.builder(Component.literal("Correspondence (" + snapshot.proposals().size() + ")"), b -> open(Overlay.CORRESPONDENCE))
                .bounds(left + W - 260, top + 14, 142, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return"), b -> SettlementNetwork.CHANNEL.sendToServer(
                        new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + W - 108, top + 14, 88, 20).build());
        for (int i = 0; i < Math.min(13, snapshot.relations().size()); i++) {
            int index = i; DiplomacySnapshot.RelationEntry relation = snapshot.relations().get(i);
            Button row = addRenderableWidget(Button.builder(Component.literal(relation.factionName() + "  " + signed(relation.relationship())), b -> select(index))
                    .bounds(left + 18, top + 166 + i * 21, 204, 19).build());
            row.active = overlay == Overlay.NONE;
        }
        int mapRight = selected() == null ? left + W - 14 : left + W - 294;
        for (DiplomacySnapshot.SettlementTarget holding : snapshot.settlements().stream().limit(40).toList()) {
            int relation = relationIndex(holding.factionId());
            int[] point = mapPoint(holding, left + 248, top + 82, mapRight - 92, top + H - 64);
            Button marker = addRenderableWidget(Button.builder(Component.literal(holding.settlementName()), b -> {
                if (relation < 0) { selectedIndex = -1; rebuild(); } else select(relation);
            }).bounds(point[0], point[1], 82, 18).build());
            marker.active = overlay == Overlay.NONE;
        }
        addRenderableWidget(Button.builder(Component.literal("ⓘ"), b -> { scroll = 0; open(Overlay.OWN_CULTURE); })
                .bounds(left + 190, top + 124, 24, 18).build());
        DiplomacySnapshot.RelationEntry target = selected();
        if (target != null) {
            addRenderableWidget(Button.builder(Component.literal("ⓘ"), b -> { scroll = 0; open(Overlay.TARGET_CULTURE); })
                    .bounds(left + W - 45, top + 134, 22, 18).build());
            addRenderableWidget(Button.builder(Component.literal("NEGOTIATE"), b -> beginNegotiation())
                    .bounds(left + W - 273, top + 202, 250, 22).build());
            addRenderableWidget(Button.builder(Component.literal("AGREEMENTS & OBLIGATIONS"), b -> open(Overlay.AGREEMENTS))
                    .bounds(left + W - 273, top + 229, 250, 22).build());
            addRenderableWidget(Button.builder(Component.literal("FULL RELATIONSHIP BREAKDOWN"), b -> { scroll = 0; open(Overlay.RELATIONSHIP); })
                    .bounds(left + W - 273, top + H - 56, 250, 20).build());
        }
        if (overlay != Overlay.NONE) overlayWidgets();
    }

    private void overlayWidgets() {
        addRenderableWidget(Button.builder(Component.literal("✕ CLOSE"), b -> closeOverlay())
                .bounds(left + W - 190, top + 66, 110, 20).build());
        switch (overlay) {
            case NEGOTIATION -> {
                addRenderableWidget(Button.builder(Component.literal("+ ADD OFFER / DEMAND"), b -> open(Overlay.TERM_PICKER))
                        .bounds(left + 244, top + H - 100, 184, 22).build());
                addRenderableWidget(Button.builder(Component.literal("WAR / RAID"), b -> { overlay = Overlay.WAR; rebuild(); })
                        .bounds(left + 434, top + H - 100, 118, 22).build());
                DiplomacySnapshot.WarEntry joinable = joinableWar();
                if (joinable != null) addRenderableWidget(Button.builder(Component.literal("REQUEST TO JOIN WAR"), b -> send("REQUEST_JOIN_WAR", null, joinable.id(), ""))
                        .bounds(left + 244, top + H - 126, 184, 20).build());
                Button send = addRenderableWidget(Button.builder(Component.literal("SEND PROPOSAL"), b -> sendDraft())
                        .bounds(left + 558, top + H - 100, 150, 22).build());
                send.active = draftTerm != null && selected() != null;
            }
            case TERM_PICKER -> termPickerWidgets();
            case WAR -> warWidgets();
            case AGREEMENTS -> agreementWidgets();
            case CORRESPONDENCE -> correspondenceWidgets();
            default -> { }
        }
    }

    private void termPickerWidgets() {
        DiplomacySnapshot.RelationEntry target = selected(); if (target == null) return;
        int y = top + 140;
        DiplomacySnapshot.WarEntry activeWar = warWith(target.factionId());
        if ("WAR".equals(target.state())) {
            if (activeWar != null && "WAR".equals(activeWar.type())) termButton("Peace", CampaignData.ProposalType.PEACE, y);
            return;
        }
        termButton("Non-aggression pact", CampaignData.ProposalType.NON_AGGRESSION, y);
        termButton("Defensive alliance", CampaignData.ProposalType.DEFENSIVE_ALLIANCE, y + 28);
        termButton("Military alliance", CampaignData.ProposalType.MILITARY_ALLIANCE, y + 56);
        if (target.tributeRate() == 0) termButton("Vassalage", CampaignData.ProposalType.VASSALAGE, y + 84);
    }

    private void termButton(String label, CampaignData.ProposalType type, int y) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> { draftTerm = type; overlay = Overlay.NEGOTIATION; rebuild(); })
                .bounds(left + 290, y, 260, 22).build());
    }

    private void warWidgets() {
        if (selected() == null) return;
        addRenderableWidget(Button.builder(Component.literal("WAR"), b -> { warType = CampaignData.WarType.WAR; goalIndex = settlementIndex = 0; rebuild(); })
                .bounds(left + 240, top + 124, 130, 22).build());
        addRenderableWidget(Button.builder(Component.literal("RAID"), b -> { warType = CampaignData.WarType.RAID; goalIndex = settlementIndex = 0; rebuild(); })
                .bounds(left + 376, top + 124, 130, 22).build());
        List<CampaignData.WarGoal> goals = goals(); List<String> names = goals.stream().map(CampaignData.WarGoal::displayName).toList();
        goalIndex = clamp(goalIndex, names.size());
        addRenderableWidget(new DropdownWidget(left + 240, top + 174, 266, 20, names, goalIndex, value -> {
            goalIndex = Math.max(0, names.indexOf(value)); rebuild();
        }));
        List<DiplomacySnapshot.SettlementTarget> settlements = targetSettlements();
        if (selectedGoal().requiresSettlement()) {
            List<String> settlementNames = settlements.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
            settlementIndex = clamp(settlementIndex, settlementNames.size());
            addRenderableWidget(new DropdownWidget(left + 240, top + 224, 266, 20,
                    settlementNames.isEmpty() ? List.of("No eligible holding") : settlementNames, settlementIndex,
                    value -> settlementIndex = Math.max(0, settlementNames.indexOf(value))));
        }
        if (selectedGoal().custom()) {
            customReason = new EditBox(font, left + 240, top + 274, 360, 20, Component.literal("Casus Belli"));
            customReason.setMaxLength(100); customReason.setHint(Component.literal("Required written reason"));
            customReason.setResponder(value -> updateDeclareButton()); addRenderableWidget(customReason);
        }
        if (warType == CampaignData.WarType.WAR) {
            int allyY = top + 306;
            for (DiplomacySnapshot.RelationEntry ally : offensiveAllies()) {
                boolean chosen = selectedAllies.contains(ally.factionId());
                addRenderableWidget(Button.builder(Component.literal((chosen ? "☑ " : "☐ ") + ally.factionName()), b -> {
                    if (!selectedAllies.add(ally.factionId())) selectedAllies.remove(ally.factionId()); rebuild();
                }).bounds(left + 240, allyY, 266, 18).build());
                allyY += 20;
            }
        }
        declareButton = addRenderableWidget(Button.builder(Component.literal("DECLARE " + warType.displayName().toUpperCase(Locale.ROOT)), b -> declareConflict())
                .bounds(left + 508, top + H - 100, 200, 22).build());
        updateDeclareButton();
    }

    private void agreementWidgets() {
        DiplomacySnapshot.RelationEntry target = selected(); if (target == null) return;
        if (!"NEUTRAL".equals(target.state()) && !"WAR".equals(target.state()))
            addRenderableWidget(Button.builder(Component.literal("END " + pretty(target.state()).toUpperCase(Locale.ROOT)), b -> send("BREAK_TREATY", null, null, ""))
                    .bounds(left + 510, top + 180, 198, 22).build());
        if (snapshot.ownFactionId().equals(target.overlordId()))
            addRenderableWidget(Button.builder(Component.literal("RELEASE VASSAL"), b -> send("RELEASE_VASSAL", null, null, ""))
                    .bounds(left + 510, top + 214, 198, 22).build());
        if (snapshot.ownFactionId().equals(target.vassalId()) && target.factionId().equals(target.overlordId())) {
            addRenderableWidget(Button.builder(Component.literal("REQUEST INDEPENDENCE"), b -> send("REQUEST_INDEPENDENCE", null, null, ""))
                    .bounds(left + 510, top + 214, 198, 22).build());
            int lower = nextLowerTribute(target.tributeRate());
            if (lower > 0) addRenderableWidget(Button.builder(Component.literal("REQUEST " + lower + "% TRIBUTE"), b -> send("REQUEST_LOWER_TRIBUTE", null, null, Integer.toString(lower)))
                    .bounds(left + 510, top + 248, 198, 22).build());
        }
        DiplomacySnapshot.WarEntry war = warWith(target.factionId());
        if (war != null) addRenderableWidget(Button.builder(Component.literal(war.type().equals("RAID") ? "END RAID" : "NEGOTIATE PEACE"), b -> {
            if ("RAID".equals(war.type())) send("END_RAID", null, war.id(), "");
            else { draftTerm = CampaignData.ProposalType.PEACE; overlay = Overlay.NEGOTIATION; rebuild(); }
        }).bounds(left + 510, top + 282, 198, 22).build());
        if (war != null && "WAR".equals(war.type())) {
            boolean defending = snapshot.ownFactionId().equals(war.defenderFactionId()) || war.supportingDefenders().contains(snapshot.ownFactionName());
            int y = top + 316;
            for (DiplomacySnapshot.RelationEntry ally : snapshot.relations().stream()
                    .filter(v -> "MILITARY_ALLIANCE".equals(v.state()) || defending && "DEFENSIVE_ALLIANCE".equals(v.state()))
                    .filter(v -> !war.attackerFactionId().equals(v.factionId()) && !war.defenderFactionId().equals(v.factionId())).toList()) {
                addRenderableWidget(Button.builder(Component.literal("CALL " + ally.factionName()), b -> sendToFaction("CALL_TO_WAR", ally.factionId(), null, war.id(), ""))
                        .bounds(left + 510, y, 198, 18).build());
                y += 20;
            }
        }
    }

    private void correspondenceWidgets() {
        for (int i = 0; i < Math.min(9, snapshot.proposals().size()); i++) {
            DiplomacySnapshot.ProposalEntry proposal = snapshot.proposals().get(i); int y = top + 142 + i * 30;
            if (proposal.incoming() && "PENDING".equals(proposal.status())) {
                addRenderableWidget(Button.builder(Component.literal("ACCEPT"), b -> sendToFaction(proposal.structuredPeace() ? "ACCEPT_PEACE_TERMS" : "ACCEPT_PROPOSAL", proposal.fromFactionId(), proposal.id(), proposal.linkedWar(), ""))
                        .bounds(left + 550, y, 72, 20).build());
                Button reject = addRenderableWidget(Button.builder(Component.literal("DECLINE"), b -> sendToFaction(proposal.structuredPeace() ? "REJECT_PEACE_TERMS" : "REJECT_PROPOSAL", proposal.fromFactionId(), proposal.id(), proposal.linkedWar(), ""))
                        .bounds(left + 628, y, 80, 20).build());
                reject.active = proposal.canReject();
            } else if (!proposal.incoming() && "PENDING".equals(proposal.status()) && !proposal.structuredPeace()) {
                addRenderableWidget(Button.builder(Component.literal("WITHDRAW"), b -> sendToFaction("WITHDRAW_PROPOSAL", proposal.toFactionId(), proposal.id(), proposal.linkedWar(), ""))
                        .bounds(left + 628, y, 80, 20).build());
            }
        }
    }

    private void beginNegotiation() {
        DiplomacySnapshot.WarEntry conflict = selected() == null ? null : warWith(selected().factionId());
        draftTerm = conflict != null && "WAR".equals(conflict.type()) ? CampaignData.ProposalType.PEACE : null;
        overlay = Overlay.NEGOTIATION; rebuild();
    }

    private void sendDraft() {
        if (draftTerm == null) return;
        if (draftTerm == CampaignData.ProposalType.PEACE) {
            DiplomacySnapshot.WarEntry peaceWar = selected() == null ? null : warWith(selected().factionId());
            if (peaceWar != null && minecraft != null) minecraft.setScreen(new PeaceTreatyScreen(this, snapshot, peaceWar));
            return;
        }
        String action = switch (draftTerm) {
            case NON_AGGRESSION -> "PROPOSE_NAP";
            case DEFENSIVE_ALLIANCE -> "PROPOSE_DEFENSIVE";
            case MILITARY_ALLIANCE -> "PROPOSE_MILITARY";
            case VASSALAGE -> "PROPOSE_VASSALAGE";
            case REQUEST_INDEPENDENCE -> "REQUEST_INDEPENDENCE";
            case REQUEST_TRIBUTE -> "REQUEST_LOWER_TRIBUTE";
            case PEACE -> "OFFER_PEACE";
            case CALL_TO_WAR -> "CALL_TO_WAR";
            case REQUEST_JOIN_WAR -> "REQUEST_JOIN_WAR";
        };
        DiplomacySnapshot.WarEntry war = selected() == null ? null : warWith(selected().factionId());
        send(action, null, war == null ? null : war.id(), "");
    }

    private void declareConflict() {
        DiplomacySnapshot.SettlementTarget settlement = selectedGoal().requiresSettlement() && !targetSettlements().isEmpty()
                ? targetSettlements().get(clamp(settlementIndex, targetSettlements().size())) : null;
        String reason = customReason == null ? "" : customReason.getValue().replace('|', '/').trim();
        send("DECLARE_WAR", null, null, warType.name() + "|" + (settlement == null ? "" : settlement.settlementId()) + "|" + selectedGoal().name() + "|" + reason + "|" + String.join(",", selectedAllies));
    }

    private void updateDeclareButton() {
        if (declareButton == null) return;
        declareButton.active = snapshot.canDeclareWar() && selected() != null
                && (!selectedGoal().requiresSettlement() || !targetSettlements().isEmpty())
                && (!selectedGoal().custom() || customReason != null && !customReason.getValue().trim().isBlank());
    }

    private void select(int index) { if (overlay != Overlay.NONE) return; selectedIndex = selectedIndex == index ? -1 : index; draftTerm = null; scroll = 0; rebuild(); }
    private void open(Overlay value) { overlay = value; rebuild(); }
    private void closeOverlay() { overlay = overlay == Overlay.TERM_PICKER || overlay == Overlay.WAR ? Overlay.NEGOTIATION : Overlay.NONE; rebuild(); }
    private DiplomacySnapshot.RelationEntry selected() { return selectedIndex < 0 || selectedIndex >= snapshot.relations().size() ? null : snapshot.relations().get(selectedIndex); }
    private List<CampaignData.WarGoal> goals() { return Arrays.stream(CampaignData.WarGoal.values()).filter(v -> v.appliesTo(warType)).filter(this::eligibleGoal).toList(); }
    private boolean eligibleGoal(CampaignData.WarGoal goal) {
        DiplomacySnapshot.RelationEntry target = selected();
        if (goal == CampaignData.WarGoal.INDEPENDENCE_WAR) return target != null && snapshot.ownFactionId().equals(target.vassalId()) && target.factionId().equals(target.overlordId());
        if (goal == CampaignData.WarGoal.PUNITIVE_RAID || goal == CampaignData.WarGoal.BORDER_CLASH
                || goal == CampaignData.WarGoal.RECOVER_TERRITORY || goal == CampaignData.WarGoal.LIBERATE_SETTLEMENT
                || goal == CampaignData.WarGoal.FORCE_WITHDRAWAL || goal == CampaignData.WarGoal.PUNITIVE_WAR) return false;
        return true;
    }
    private CampaignData.WarGoal selectedGoal() { List<CampaignData.WarGoal> values = goals(); return values.isEmpty() ? CampaignData.WarGoal.defaultFor(warType) : values.get(clamp(goalIndex, values.size())); }
    private List<DiplomacySnapshot.SettlementTarget> targetSettlements() { DiplomacySnapshot.RelationEntry target = selected(); return target == null ? List.of() : snapshot.settlements().stream().filter(v -> target.factionId().equals(v.factionId())).toList(); }
    private DiplomacySnapshot.WarEntry warWith(String faction) { return snapshot.wars().stream().filter(w -> faction.equals(w.attackerFactionId()) || faction.equals(w.defenderFactionId())).findFirst().orElse(null); }
    private DiplomacySnapshot.WarEntry joinableWar() { DiplomacySnapshot.RelationEntry target = selected(); if (target == null) return null; return snapshot.wars().stream().filter(w -> "WAR".equals(w.type())).filter(w -> target.factionId().equals(w.attackerFactionId()) || target.factionId().equals(w.defenderFactionId())).filter(w -> !snapshot.ownFactionId().equals(w.attackerFactionId()) && !snapshot.ownFactionId().equals(w.defenderFactionId())).findFirst().orElse(null); }
    private List<DiplomacySnapshot.RelationEntry> offensiveAllies() { return snapshot.relations().stream().filter(v -> "MILITARY_ALLIANCE".equals(v.state())).filter(v -> selected() == null || !v.factionId().equals(selected().factionId())).toList(); }
    private void send(String action, UUID proposal, UUID war, String extra) { sendToFaction(action, selected() == null ? "" : selected().factionId(), proposal, war, extra); }
    private void sendToFaction(String action, String target, UUID proposal, UUID war, String extra) { SettlementNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket(action, target == null ? "" : target, proposal, war, extra)); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); int ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY); beginCampaignRender(g);
        drawCampaignFrame(g, left, top, W, H, 40); g.drawString(font, "LAST PAGANS · DIPLOMACY", left + 20, top + 19, 0xFFD8BA83, false);
        renderLeft(g); renderPoliticalMap(g); if (selected() != null) renderDossier(g, selected()); if (overlay != Overlay.NONE) renderOverlay(g);
        if (!snapshot.message().isBlank()) g.drawCenteredString(font, fitText(snapshot.message(), W - 60), left + W / 2, top + H - 18, CAMPAIGN_MESSAGE);
        super.render(g, ux, uy, partialTick); endCampaignRender(g);
    }

    private void renderLeft(GuiGraphics g) {
        drawCampaignPanel(g, left + 12, top + 48, left + 228, top + H - 32); DiplomacySnapshot.PolityDetails d = snapshot.ownDetails();
        g.drawString(font, "YOUR FACTION", left + 22, top + 60, 0xFFD8BA83, false); g.drawString(font, snapshot.ownFactionName(), left + 22, top + 78, 0xFFF1E5D0, false);
        compact(g, "Ruler", d.ruler(), 22, 94); compact(g, "Capital", d.capital(), 22, 106); compact(g, "Faith", d.faith(), 22, 118); compact(g, "Culture", d.culture(), 22, 130);
        compact(g, "Authority", snapshot.viewerRank(), 22, 142); g.drawString(font, "KNOWN FACTIONS", left + 22, top + 154, 0xFFD8BA83, false);
    }

    private void renderPoliticalMap(GuiGraphics g) {
        int right = selected() == null ? left + W - 14 : left + W - 294; drawCampaignPanel(g, left + 236, top + 48, right, top + H - 32);
        g.drawCenteredString(font, "POLITICAL MAP", (left + 236 + right) / 2, top + 60, 0xFFD8BA83);
    }

    private void renderDossier(GuiGraphics g, DiplomacySnapshot.RelationEntry r) {
        int x = left + W - 286; drawCampaignPanel(g, x, top + 48, left + W - 12, top + H - 32); DiplomacySnapshot.PolityDetails d = r.details();
        g.drawString(font, "FACTION DETAILS", x + 12, top + 60, 0xFFD8BA83, false); g.drawString(font, r.factionName(), x + 12, top + 78, 0xFFF1E5D0, false);
        compactAt(g, "Ruler", d.ruler(), x + 12, top + 96); compactAt(g, "Government", d.government(), x + 12, top + 108); compactAt(g, "Capital", d.capital(), x + 12, top + 120);
        compactAt(g, "Culture", d.culture(), x + 12, top + 138); compactAt(g, "Faith", d.faith(), x + 12, top + 156); compactAt(g, "Holdings", d.holdings(), x + 12, top + 168); compactAt(g, "Reliability", signed(r.reliability()), x + 12, top + 180);
        g.drawString(font, "RELATIONSHIP FACTORS  " + signed(r.relationship()), x + 12, top + 263, 0xFFD8BA83, false); int y = top + 282;
        for (DiplomacySnapshot.RelationshipFactor f : r.factors()) {
            int rowTop = y;
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(f.label()), 190)) {
                if (y >= top + H - 62) break;
                g.drawString(font, line, x + 12, y, 0xFFF1E5D0, false); y += 10;
            }
            g.drawString(font, signed(f.value()), left + W - 48, rowTop, f.value() < 0 ? 0xFFC77765 : 0xFF8DB16F, false);
            y += 4;
            if (y >= top + H - 62) break;
        }
    }

    private void renderOverlay(GuiGraphics g) {
        g.fill(left + 4, top + 42, left + W - 4, top + H - 28, 0xCC15120F); int x1 = left + 74, y1 = top + 54, x2 = left + W - 74, y2 = top + H - 42;
        g.fill(x1, y1, x2, y2, 0xFFE4D2AD); g.renderOutline(x1, y1, x2 - x1, y2 - y1, 0xFF79664C);
        switch (overlay) {
            case OWN_CULTURE -> renderCulture(g, snapshot.ownDetails().culture(), snapshot.ownFactionName());
            case TARGET_CULTURE -> renderCulture(g, selected() == null ? "" : selected().details().culture(), selected() == null ? "" : selected().factionName());
            case RELATIONSHIP -> renderRelationship(g); case AGREEMENTS -> renderAgreements(g); case NEGOTIATION -> renderNegotiation(g);
            case TERM_PICKER -> {
                title(g, "ADD DIPLOMATIC TERM"); g.drawString(font, "Available terms", left + 290, top + 116, 0xFF493B2B, false);
            }
            case WAR -> renderWar(g); case CORRESPONDENCE -> renderCorrespondence(g); default -> { }
        }
    }

    private void renderCulture(GuiGraphics g, String cultureName, String faction) {
        title(g, "CULTURAL DOSSIER · " + cultureName.toUpperCase(Locale.ROOT)); CultureDefinition definition = CultureDefinition.get(cultureName);
        if (definition == null) { g.drawString(font, "No culture definition", left + 100, top + 120, 0xFF493B2B, false); return; }
        g.drawString(font, faction, left + 100, top + 96, 0xFF493B2B, false); g.drawString(font, "Primary faith: " + definition.primaryFaith(), left + 100, top + 116, 0xFF493B2B, false);
        g.drawString(font, "Secondary faith(s): " + (definition.secondaryFaiths().isEmpty() ? "None" : String.join(", ", definition.secondaryFaiths())), left + 100, top + 130, 0xFF493B2B, false);
        g.drawString(font, "ADVANTAGES", left + 100, top + 154, 0xFF493B2B, false); g.drawString(font, "DRAWBACKS", left + 430, top + 154, 0xFF493B2B, false);
        renderTraitList(g, definition.advantages(), left + 100, top + 172, 300); renderTraitList(g, definition.drawbacks(), left + 430, top + 172, 300);
    }
    private void renderTraitList(GuiGraphics g, List<String> values, int x, int y, int width) {
        int cursor = y;
        for (int i = scroll; i < values.size() && cursor < top + H - 72; i++) {
            String source = values.get(i);
            String status = cultureTraitActive(source) ? " · Active" : " · Planned / not currently applied";
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal("• " + source + status), width)) {
                if (cursor >= top + H - 72) break;
                g.drawString(font, line, x, cursor, 0xFF493B2B, false); cursor += 11;
            }
            cursor += 5;
        }
    }
    private void renderRelationship(GuiGraphics g) {
        title(g, "FULL RELATIONSHIP BREAKDOWN"); DiplomacySnapshot.RelationEntry target = selected(); if (target == null) return; int y = top + 112;
        for (int i = scroll; i < target.factors().size() && y < top + H - 92; i++) {
            DiplomacySnapshot.RelationshipFactor f = target.factors().get(i);
            g.drawString(font, f.label(), left + 105, y, 0xFF493B2B, false);
            g.drawString(font, signed(f.value()), left + W - 120, y, f.value() < 0 ? 0xFF9A4F43 : 0xFF55763D, false);
            y += 12;
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(f.explanation()), W - 250)) {
                if (y >= top + H - 92) break;
                g.drawString(font, line, left + 105, y, 0xFF79664C, false);
                y += 10;
            }
            y += 8;
        }
        g.drawString(font, "TOTAL  " + signed(target.relationship()), left + W - 200, top + H - 78, 0xFF493B2B, false);
    }
    private void renderAgreements(GuiGraphics g) {
        title(g, "AGREEMENTS & OBLIGATIONS"); DiplomacySnapshot.RelationEntry target = selected(); if (target == null) return;
        g.drawString(font, snapshot.ownFactionName() + " ↔ " + target.factionName(), left + 105, top + 96, 0xFF493B2B, false); g.drawString(font, "ACTIVE CONTRACTS & CONFLICTS", left + 105, top + 128, 0xFF493B2B, false);
        String state = pretty(target.state()); g.drawString(font, "• " + ("Neutral".equals(state) ? "No active bilateral treaty" : state), left + 105, top + 150, 0xFF493B2B, false);
        if (target.tributeRate() > 0) g.drawString(font, "• Vassalage · " + target.tributeRate() + "% tribute" + (target.vassalSuspended() ? " · SUSPENDED" : " · projected " + target.projectedTribute() + " Silver"), left + 105, top + 166, 0xFF493B2B, false);
        DiplomacySnapshot.WarEntry war = warWith(target.factionId()); if (war != null) g.drawString(font, "• " + war.typeName() + " · " + war.goalName() + " · score " + signed(war.warScore()), left + 105, top + 182, 0xFF493B2B, false);
        g.drawString(font, "MANAGE DIPLOMATIC OBLIGATIONS", left + 500, top + 128, 0xFF493B2B, false);
    }
    private void renderNegotiation(GuiGraphics g) {
        title(g, "NEGOTIATION · " + (selected() == null ? "" : selected().factionName())); g.drawString(font, "YOUR DEMANDS", left + 118, top + 120, 0xFF493B2B, false); g.drawString(font, "YOUR OFFERS", left + 444, top + 120, 0xFF493B2B, false);
        if (draftTerm != null) { String term = pretty(draftTerm.name()); g.drawString(font, mutual(draftTerm) ? term + " (Mutual)" : term, left + 118, top + 150, 0xFF493B2B, false); if (mutual(draftTerm)) g.drawString(font, term + " (Mutual)", left + 444, top + 150, 0xFF493B2B, false); }
    }
    private void renderWar(GuiGraphics g) {
        title(g, "DECLARE " + warType.displayName().toUpperCase(Locale.ROOT)); g.drawString(font, "Conflict type", left + 240, top + 110, 0xFF493B2B, false); g.drawString(font, "Casus Belli", left + 240, top + 160, 0xFF493B2B, false);
        if (selectedGoal().requiresSettlement()) g.drawString(font, "Target holding", left + 240, top + 210, 0xFF493B2B, false); if (selectedGoal().custom()) g.drawString(font, "Written reason", left + 240, top + 260, 0xFF493B2B, false);
        if (warType == CampaignData.WarType.WAR) g.drawString(font, "CALL MILITARY ALLIES", left + 240, top + 296, 0xFF493B2B, false);
        else g.drawString(font, "Limited economic conflict; territorial terms are unavailable.", left + 240, top + 316, 0xFF79664C, false);
    }
    private void renderCorrespondence(GuiGraphics g) {
        title(g, "CORRESPONDENCE · RECEIVED / SENT / HISTORY"); if (snapshot.proposals().isEmpty()) { g.drawString(font, "No diplomatic correspondence", left + 105, top + 120, 0xFF493B2B, false); return; }
        for (int i = 0; i < Math.min(9, snapshot.proposals().size()); i++) { DiplomacySnapshot.ProposalEntry p = snapshot.proposals().get(i); int y = top + 144 + i * 30; String direction = p.incoming() ? "Received from " + p.fromFactionName() : "Sent to " + p.toFactionName(); g.drawString(font, pretty(p.type()) + " · " + direction + " · " + pretty(p.status()), left + 105, y, 0xFF493B2B, false); if (!p.summary().isBlank()) g.drawString(font, fitText(p.summary(), 390), left + 105, y + 11, 0xFF79664C, false); }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) { if (overlay == Overlay.OWN_CULTURE || overlay == Overlay.TARGET_CULTURE || overlay == Overlay.RELATIONSHIP) { scroll = Math.max(0, scroll + (delta < 0 ? 1 : -1)); return true; } return super.mouseScrolled(mouseX, mouseY, delta); }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (overlay != Overlay.NONE) return false;
        double ux = campaignMouseX((int)mouseX), uy = campaignMouseY((int)mouseY);
        int right = selected() == null ? left + W - 14 : left + W - 294;
        if (ux >= left + 236 && ux <= right && uy >= top + 48 && uy <= top + H - 32) {
            selectedIndex = -1; draftTerm = null; rebuild(); return true;
        }
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) { if (key == 256 && overlay != Overlay.NONE) { closeOverlay(); return true; } if (key == 256 && selectedIndex >= 0) { selectedIndex = -1; rebuild(); return true; } return super.keyPressed(key, scan, mods); }
    private void title(GuiGraphics g, String value) { g.drawString(font, value, left + 100, top + 72, 0xFF493B2B, false); }
    private void compact(GuiGraphics g, String label, Object value, int x, int y) { compactAt(g, label, value, left + x, top + y); }
    private void compactAt(GuiGraphics g, String label, Object value, int x, int y) { g.drawString(font, label + ": " + fitText(String.valueOf(value), 170), x, y, 0xFFF1E5D0, false); }
    private static boolean mutual(CampaignData.ProposalType type) { return type == CampaignData.ProposalType.NON_AGGRESSION || type == CampaignData.ProposalType.DEFENSIVE_ALLIANCE || type == CampaignData.ProposalType.MILITARY_ALLIANCE || type == CampaignData.ProposalType.PEACE; }
    private static boolean cultureTraitActive(String value) { String lower = value.toLowerCase(Locale.ROOT); return lower.contains("diplomatic penalty") || lower.contains("religious incompatibility penalties"); }
    private static int nextLowerTribute(int current) { return current > 25 ? 25 : current > 10 ? 10 : current > 5 ? 5 : 0; }
    private int relationIndex(String factionId) { for (int i = 0; i < snapshot.relations().size(); i++) if (snapshot.relations().get(i).factionId().equals(factionId)) return i; return -1; }
    private int[] mapPoint(DiplomacySnapshot.SettlementTarget value, int minX, int minY, int maxX, int maxY) {
        int worldMinX = snapshot.settlements().stream().mapToInt(DiplomacySnapshot.SettlementTarget::worldX).min().orElse(0);
        int worldMaxX = snapshot.settlements().stream().mapToInt(DiplomacySnapshot.SettlementTarget::worldX).max().orElse(worldMinX + 1);
        int worldMinZ = snapshot.settlements().stream().mapToInt(DiplomacySnapshot.SettlementTarget::worldZ).min().orElse(0);
        int worldMaxZ = snapshot.settlements().stream().mapToInt(DiplomacySnapshot.SettlementTarget::worldZ).max().orElse(worldMinZ + 1);
        double nx = worldMaxX == worldMinX ? 0.5D : (value.worldX() - worldMinX) / (double)(worldMaxX - worldMinX);
        double nz = worldMaxZ == worldMinZ ? 0.5D : (value.worldZ() - worldMinZ) / (double)(worldMaxZ - worldMinZ);
        return new int[]{minX + (int)Math.round(nx * Math.max(0, maxX - minX)), minY + (int)Math.round(nz * Math.max(0, maxY - minY))};
    }
    private static int clamp(int index, int size) { return size <= 0 ? 0 : Math.max(0, Math.min(index, size - 1)); }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String pretty(String raw) { String v = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT); return v.isBlank() ? v : Character.toUpperCase(v.charAt(0)) + v.substring(1); }
    @Override public boolean isPauseScreen() { return false; }
    private enum Overlay { NONE, OWN_CULTURE, TARGET_CULTURE, RELATIONSHIP, AGREEMENTS, NEGOTIATION, TERM_PICKER, WAR, CORRESPONDENCE }
}
