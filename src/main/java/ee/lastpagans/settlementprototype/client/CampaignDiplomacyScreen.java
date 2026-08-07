package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CampaignData;
import ee.lastpagans.settlementprototype.DiplomacySnapshot;
import ee.lastpagans.settlementprototype.network.DiplomacyActionPacket;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Settlements 2.3 guided diplomacy screen. Settlement wars are settlement-to-settlement by design. */
public final class CampaignDiplomacyScreen extends ScaledCampaignScreen {
    private static final int W = 560, H = 380;
    private final DiplomacySnapshot snapshot;
    private int left, top;
    private Tab tab = Tab.RELATIONS;
    private int relationIndex;
    private int warTypeIndex = 2;
    private int sourceSettlementIndex;
    private int targetSettlementIndex;
    private int goalIndex;
    private int warAllyIndex;
    private DropdownWidget factionDropdown, typeDropdown, sourceDropdown, targetDropdown, goalDropdown, warAllyDropdown;
    private EditBox customCasusBelli;

    public CampaignDiplomacyScreen(DiplomacySnapshot snapshot) {
        super(Component.literal("Campaign Diplomacy"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        factionDropdown = typeDropdown = sourceDropdown = targetDropdown = goalDropdown = warAllyDropdown = null;
        customCasusBelli = null;
        String[] tabs = {"Relations", "Declare Conflict", "Proposals", "Active Wars"};
        for (int i = 0; i < tabs.length; i++) {
            Tab target = Tab.values()[i];
            addRenderableWidget(Button.builder(Component.literal(tabs[i]), button -> { tab = target; buildWidgets(); })
                    .bounds(left + 12 + i * 134, top + 45, 130, 20).build());
        }
        switch (tab) {
            case RELATIONS -> relationWidgets();
            case DECLARE -> declarationWidgets();
            case PROPOSALS -> proposalWidgets();
            case WARS -> warWidgets();
        }
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + W - 132, top + H - 29, 120, 20).build());
    }

    private void relationWidgets() {
        List<String> factions = snapshot.relations().stream().map(DiplomacySnapshot.RelationEntry::factionName).toList();
        factionDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 88, 235, 20,
                factions.isEmpty() ? List.of("No other factions") : factions, relationIndex,
                value -> relationIndex = Math.max(0, factions.indexOf(value))));
        Button nap = addRenderableWidget(Button.builder(Component.literal("Non-aggression pact"), b -> send("PROPOSE_NAP", null, null, ""))
                .bounds(left + 20, top + 254, 160, 20).build());
        Button defensive = addRenderableWidget(Button.builder(Component.literal("Defensive alliance"), b -> send("PROPOSE_DEFENSIVE", null, null, ""))
                .bounds(left + 186, top + 254, 160, 20).build());
        Button military = addRenderableWidget(Button.builder(Component.literal("Military alliance"), b -> send("PROPOSE_MILITARY", null, null, ""))
                .bounds(left + 352, top + 254, 160, 20).build());
        Button breakTreaty = addRenderableWidget(Button.builder(Component.literal("Break current treaty"), b -> send("BREAK_TREATY", null, null, ""))
                .bounds(left + 20, top + 280, 190, 20).build());
        boolean active = snapshot.canManage() && !snapshot.relations().isEmpty();
        nap.active = defensive.active = military.active = breakTreaty.active = active;
    }

    private void declarationWidgets() {
        List<CampaignData.WarType> types = supportedWarTypes();
        List<String> typeNames = types.stream().map(CampaignData.WarType::displayName).toList();
        CampaignData.WarType type = selectedWarType();
        List<CampaignData.WarGoal> goals = goals();
        List<String> goalNames = goals.stream().map(CampaignData.WarGoal::displayName).toList();
        goalIndex = Math.min(goalIndex, Math.max(0, goalNames.size() - 1));

        goalDropdown = addRenderableWidget(new DropdownWidget(left + 294, top + 143, 246, 20, goalNames, goalIndex, value -> {
            goalIndex = Math.max(0, goalNames.indexOf(value));
            buildWidgets();
        }));
        typeDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 91, 250, 20, typeNames, warTypeIndex, value -> {
            warTypeIndex = Math.max(0, typeNames.indexOf(value));
            sourceSettlementIndex = targetSettlementIndex = goalIndex = 0;
            buildWidgets();
        }));

        if (type == CampaignData.WarType.SETTLEMENT_WAR) {
            List<DiplomacySnapshot.SettlementTarget> ownSettlements = manageableSettlements();
            List<DiplomacySnapshot.SettlementTarget> targets = targetSettlements();
            List<String> ownNames = ownSettlements.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
            List<String> targetNames = targets.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
            sourceSettlementIndex = clampIndex(sourceSettlementIndex, ownNames.size());
            targetSettlementIndex = clampIndex(targetSettlementIndex, targetNames.size());
            sourceDropdown = addRenderableWidget(new DropdownWidget(left + 294, top + 91, 246, 20,
                    ownNames.isEmpty() ? List.of("No manageable settlement") : ownNames, sourceSettlementIndex, value -> {
                        sourceSettlementIndex = Math.max(0, ownNames.indexOf(value)); targetSettlementIndex = 0; buildWidgets();
                    }));
            targetDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 143, 250, 20,
                    targetNames.isEmpty() ? List.of("No valid target settlement") : targetNames, targetSettlementIndex,
                    value -> targetSettlementIndex = Math.max(0, targetNames.indexOf(value))));
        } else {
            List<String> factionNames = snapshot.relations().stream().map(DiplomacySnapshot.RelationEntry::factionName).toList();
            factionDropdown = addRenderableWidget(new DropdownWidget(left + 294, top + 91, 246, 20,
                    factionNames.isEmpty() ? List.of("No other factions") : factionNames, relationIndex,
                    value -> { relationIndex = Math.max(0, factionNames.indexOf(value)); targetSettlementIndex = 0; buildWidgets(); }));
            List<DiplomacySnapshot.SettlementTarget> targets = factionTargetSettlements();
            List<String> targetNames = targets.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
            if (type != CampaignData.WarType.FACTION_WAR) {
                targetSettlementIndex = clampIndex(targetSettlementIndex, targetNames.size());
                targetDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 143, 250, 20,
                        targetNames.isEmpty() ? List.of("No target settlement") : targetNames, targetSettlementIndex,
                        value -> targetSettlementIndex = Math.max(0, targetNames.indexOf(value))));
            }
        }

        if (selectedGoal() == CampaignData.WarGoal.CUSTOM) {
            customCasusBelli = new EditBox(font, left + 20, top + 188, 520, 20, Component.literal("Custom casus belli"));
            customCasusBelli.setMaxLength(100);
            customCasusBelli.setHint(Component.literal("Describe the reason for war (max 100 characters)"));
            addRenderableWidget(customCasusBelli);
        }

        Button declare = addRenderableWidget(Button.builder(Component.literal("Declare " + type.displayName()), b -> declareConflict())
                .bounds(left + 330, top + 290, 210, 24).build());
        declare.active = snapshot.canDeclareWar() && declarationValid();
    }

    private void proposalWidgets() {
        for (int i = 0; i < Math.min(8, snapshot.proposals().size()); i++) {
            DiplomacySnapshot.ProposalEntry proposal = snapshot.proposals().get(i);
            int y = top + 94 + i * 28;
            String acceptAction = proposal.structuredPeace() ? "ACCEPT_PEACE_TERMS" : "ACCEPT_PROPOSAL";
            String rejectAction = proposal.structuredPeace() ? "REJECT_PEACE_TERMS" : "REJECT_PROPOSAL";
            Button accept = addRenderableWidget(Button.builder(Component.literal(proposal.structuredPeace() && !proposal.canReject() ? "Accept (Capitulated)" : "Accept"), b -> send(acceptAction, proposal.id(), null, ""))
                    .bounds(left + 374, y, 92, 20).build());
            Button reject = addRenderableWidget(Button.builder(Component.literal("Reject"), b -> send(rejectAction, proposal.id(), null, ""))
                    .bounds(left + 472, y, 64, 20).build());
            accept.active = snapshot.canManage() || proposal.structuredPeace();
            reject.active = (snapshot.canManage() || proposal.structuredPeace()) && proposal.canReject();
        }
    }

    private void warWidgets() {
        List<DiplomacySnapshot.RelationEntry> allies = alliedRelations();
        List<String> allyNames = allies.stream().map(DiplomacySnapshot.RelationEntry::factionName).toList();
        warAllyIndex = clampIndex(warAllyIndex, allyNames.size());
        warAllyDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 88, 235, 20,
                allyNames.isEmpty() ? List.of("No allied faction") : allyNames, warAllyIndex,
                value -> warAllyIndex = Math.max(0, allyNames.indexOf(value))));

        for (int i = 0; i < Math.min(5, snapshot.wars().size()); i++) {
            DiplomacySnapshot.WarEntry war = snapshot.wars().get(i);
            int y = top + 128 + i * 43;
            boolean settlementWar = "SETTLEMENT_WAR".equals(war.type());
            if (settlementWar && ((war.viewerIsAttackerParent() && !war.attackerFactionJoined()) || (war.viewerIsDefenderParent() && !war.defenderFactionJoined()))) {
                int actionX = left + 300;
                if (war.viewerIsDefenderParent()) {
                    Button ignore = addRenderableWidget(Button.builder(Component.literal(war.viewerIgnoredIntervention() ? "Ignored" : "Ignore"), b -> sendToFaction("IGNORE_SETTLEMENT_WAR", snapshot.ownFactionId(), null, war.id(), ""))
                            .bounds(actionX, y, 58, 20).build());
                    ignore.active = snapshot.canManage() && !war.viewerIgnoredIntervention();
                    actionX += 64;
                }
                Button aid = addRenderableWidget(Button.builder(Component.literal("Send Aid"), b -> minecraft.setScreen(new WarAidScreen(this, war)))
                        .bounds(actionX, y, 72, 20).build());
                Button join = addRenderableWidget(Button.builder(Component.literal("Join War"), b -> sendToFaction("JOIN_SETTLEMENT_WAR", snapshot.ownFactionId(), null, war.id(), ""))
                        .bounds(actionX + 78, y, 78, 20).build());
                aid.active = join.active = snapshot.canManage();
            } else {
                Button call = addRenderableWidget(Button.builder(Component.literal("Call Ally"), b -> sendToFaction("CALL_TO_WAR", selectedWarAllyId(), null, war.id(), ""))
                        .bounds(left + 330, y, 72, 20).build());
                call.active = snapshot.canManage() && !allies.isEmpty();
            }
            Button peace = addRenderableWidget(Button.builder(Component.literal("Peace Terms"), b -> minecraft.setScreen(new PeaceTreatyScreen(this, snapshot, war)))
                    .bounds(left + 492, y, 58, 20).build());
            peace.active = snapshot.canManage();
        }
    }

    private List<DiplomacySnapshot.SettlementTarget> manageableSettlements() {
        return snapshot.settlements().stream().filter(DiplomacySnapshot.SettlementTarget::manageable).toList();
    }

    private List<DiplomacySnapshot.SettlementTarget> targetSettlements() {
        DiplomacySnapshot.SettlementTarget source = selectedSourceSettlement();
        return snapshot.settlements().stream()
                .filter(value -> source == null || !value.settlementId().equals(source.settlementId()))
                .filter(value -> !value.manageable())
                .toList();
    }

    private List<DiplomacySnapshot.SettlementTarget> factionTargetSettlements() {
        return snapshot.settlements().stream().filter(value -> value.factionId().equals(selectedFactionId())).toList();
    }

    private List<CampaignData.WarGoal> goals() {
        CampaignData.WarType selected = selectedWarType();
        return Arrays.stream(CampaignData.WarGoal.values()).filter(value -> value.type() == selected).toList();
    }

    private CampaignData.WarType selectedWarType() {
        List<CampaignData.WarType> values = supportedWarTypes();
        return values.get(clampIndex(warTypeIndex, values.size()));
    }

    private String selectedFactionId() {
        if (snapshot.relations().isEmpty()) return "";
        return snapshot.relations().get(clampIndex(relationIndex, snapshot.relations().size())).factionId();
    }
    private String selectedFactionName() {
        if (snapshot.relations().isEmpty()) return "None";
        return snapshot.relations().get(clampIndex(relationIndex, snapshot.relations().size())).factionName();
    }

    private DiplomacySnapshot.SettlementTarget selectedSourceSettlement() {
        List<DiplomacySnapshot.SettlementTarget> values = manageableSettlements();
        return values.isEmpty() ? null : values.get(clampIndex(sourceSettlementIndex, values.size()));
    }
    private DiplomacySnapshot.SettlementTarget selectedTargetSettlement() {
        List<DiplomacySnapshot.SettlementTarget> values = selectedWarType() == CampaignData.WarType.SETTLEMENT_WAR ? targetSettlements() : factionTargetSettlements();
        return values.isEmpty() ? null : values.get(clampIndex(targetSettlementIndex, values.size()));
    }
    private CampaignData.WarGoal selectedGoal() {
        List<CampaignData.WarGoal> values = goals();
        return values.isEmpty() ? CampaignData.WarGoal.defaultFor(selectedWarType()) : values.get(clampIndex(goalIndex, values.size()));
    }

    private boolean declarationValid() {
        if (selectedWarType() == CampaignData.WarType.SETTLEMENT_WAR)
            return selectedSourceSettlement() != null && selectedTargetSettlement() != null;
        if (snapshot.relations().isEmpty()) return false;
        if (selectedWarType() != CampaignData.WarType.FACTION_WAR && selectedTargetSettlement() == null) return false;
        return selectedGoal() != CampaignData.WarGoal.CUSTOM || (customCasusBelli != null && !customCasusBelli.getValue().trim().isBlank());
    }

    private void declareConflict() {
        CampaignData.WarType type = selectedWarType();
        String custom = customCasusBelli == null ? "" : customCasusBelli.getValue().replace('|', '/').trim();
        if (type == CampaignData.WarType.SETTLEMENT_WAR) {
            DiplomacySnapshot.SettlementTarget source = selectedSourceSettlement();
            DiplomacySnapshot.SettlementTarget target = selectedTargetSettlement();
            if (source == null || target == null) return;
            String extra = "SETTLEMENT_WAR|" + source.settlementId() + "|" + target.settlementId() + "|" + selectedGoal().name() + "|" + custom;
            sendToFaction("DECLARE_WAR", target.factionId(), null, null, extra);
            return;
        }
        DiplomacySnapshot.SettlementTarget target = selectedTargetSettlement();
        String extra = type.name() + "|" + (target == null ? "" : target.settlementId()) + "|" + selectedGoal().name() + "|" + custom;
        send("DECLARE_WAR", null, null, extra);
    }

    private void send(String action, UUID proposal, UUID war, String extra) { sendToFaction(action, selectedFactionId(), proposal, war, extra); }
    private void sendToFaction(String action, String targetFaction, UUID proposal, UUID war, String extra) {
        PrototypeNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket(action, targetFaction == null ? "" : targetFaction, proposal, war, extra));
    }
    private List<DiplomacySnapshot.RelationEntry> alliedRelations() {
        return snapshot.relations().stream().filter(v -> "DEFENSIVE_ALLIANCE".equals(v.state()) || "MILITARY_ALLIANCE".equals(v.state())).toList();
    }
    private String selectedWarAllyId() {
        List<DiplomacySnapshot.RelationEntry> allies = alliedRelations();
        return allies.isEmpty() ? "" : allies.get(clampIndex(warAllyIndex, allies.size())).factionId();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        for (DropdownWidget dropdown : dropdowns()) if (dropdown != null && dropdown.isExpanded() && dropdown.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        for (DropdownWidget dropdown : dropdowns()) if (dropdown != null && dropdown.isExpanded() && dropdown.mouseScrolled(uiX, uiY, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }
    private List<DropdownWidget> dropdowns() {
        List<DropdownWidget> result = new ArrayList<>();
        result.add(factionDropdown); result.add(typeDropdown); result.add(sourceDropdown); result.add(targetDropdown); result.add(goalDropdown); result.add(warAllyDropdown);
        return result;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 39, 0xFF3B3023);
        graphics.fill(left + 12, top + 72, left + W - 12, top + H - 42, 0xFF242424);
        String polity = snapshot.ownFactionName().isBlank() ? "INDEPENDENT" : snapshot.ownFactionName().toUpperCase(Locale.ROOT);
        graphics.drawCenteredString(font, "CAMPAIGN DIPLOMACY — " + polity, left + W / 2, top + 12, 0xFFE7D8B0);
        graphics.drawCenteredString(font, "Authority: [" + snapshot.viewerRank() + "]", left + W / 2, top + 25, snapshot.canManage() ? 0xFFA9D18E : 0xFFFFA080);
        switch (tab) {
            case RELATIONS -> renderRelations(graphics);
            case DECLARE -> renderDeclaration(graphics);
            case PROPOSALS -> renderProposals(graphics);
            case WARS -> renderWars(graphics);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, snapshot.message(), left + W / 2, top + H - 52, 0xFFFFD27A);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderRelations(GuiGraphics graphics) {
        graphics.drawString(font, "Target faction", left + 20, top + 78, 0xFFE7D8B0, false);
        if (snapshot.relations().isEmpty()) { graphics.drawString(font, "No other Recruits factions exist.", left + 20, top + 120, 0xFFAAAAAA, false); return; }
        DiplomacySnapshot.RelationEntry r = snapshot.relations().get(clampIndex(relationIndex, snapshot.relations().size()));
        graphics.drawString(font, "CURRENT RELATIONSHIP", left + 292, top + 84, 0xFFE7D8B0, false);
        stat(graphics, "Diplomatic state", pretty(r.state()), 292, 106);
        stat(graphics, "Political relations", r.relationship(), 292, 125);
        stat(graphics, "Religious compatibility", signed(r.faithModifier()), 292, 144);
        stat(graphics, "Reliability", signed(r.reliability()), 292, 163);
        graphics.drawWordWrap(font, Component.literal("Political relations, religious compatibility and reliability are separate. Formal war controls Recruits battlefield hostility."), left + 20, top + 132, 235, 0xFFAAAAAA);
    }

    private void renderDeclaration(GuiGraphics graphics) {
        CampaignData.WarType type = selectedWarType();
        if (type == CampaignData.WarType.SETTLEMENT_WAR) {
            graphics.drawString(font, "1. Conflict tier", left + 20, top + 79, 0xFFE7D8B0, false);
            graphics.drawString(font, "2. Your settlement", left + 294, top + 79, 0xFFE7D8B0, false);
            graphics.drawString(font, "3. Target settlement", left + 20, top + 131, 0xFFE7D8B0, false);
            graphics.drawString(font, "4. Casus Belli", left + 294, top + 131, 0xFFE7D8B0, false);
        } else {
            graphics.drawString(font, "1. Conflict tier", left + 20, top + 79, 0xFFE7D8B0, false);
            graphics.drawString(font, "2. Target faction", left + 294, top + 79, 0xFFE7D8B0, false);
            if (type != CampaignData.WarType.FACTION_WAR) graphics.drawString(font, "3. Target settlement", left + 20, top + 131, 0xFFE7D8B0, false);
            graphics.drawString(font, type == CampaignData.WarType.FACTION_WAR ? "3. Casus Belli" : "4. Casus Belli", left + 294, top + 131, 0xFFE7D8B0, false);
        }
        if (selectedGoal() == CampaignData.WarGoal.CUSTOM) graphics.drawString(font, "Custom Casus Belli", left + 20, top + 176, 0xFFE7D8B0, false);

        graphics.fill(left + 20, top + 218, left + W - 20, top + 282, 0xFF191919);
        graphics.drawString(font, "DECLARATION SUMMARY", left + 30, top + 227, 0xFFFFD27A, false);
        if (type == CampaignData.WarType.SETTLEMENT_WAR) {
            DiplomacySnapshot.SettlementTarget source = selectedSourceSettlement();
            DiplomacySnapshot.SettlementTarget target = selectedTargetSettlement();
            stat(graphics, "Attacker", source == null ? "None" : source.settlementName(), 30, 246);
            stat(graphics, "Defender", target == null ? "None" : target.settlementName(), 285, 246);
            stat(graphics, "Attacker parent", source == null || source.factionName().isBlank() ? "Independent" : source.factionName(), 30, 264);
            stat(graphics, "Defender parent", target == null || target.factionName().isBlank() ? "Independent" : target.factionName(), 285, 264);
        } else {
            stat(graphics, "Attacker", snapshot.ownFactionName(), 30, 246);
            stat(graphics, "Defender", selectedFactionName(), 285, 246);
            stat(graphics, "Casus Belli", selectedGoal().displayName(), 30, 264);
            stat(graphics, "Conflict tier", type.displayName(), 285, 264);
        }
        graphics.drawString(font, type == CampaignData.WarType.SETTLEMENT_WAR
                ? "Parent factions are observers until they send aid or explicitly join; joining escalates the conflict."
                : "A full faction declaration changes Recruits relations to Enemy only after server-side verification.",
                left + 20, top + 320, 0xFFB8B0A0, false);
    }

    private void renderProposals(GuiGraphics graphics) {
        graphics.drawString(font, "PENDING PROPOSALS", left + 20, top + 82, 0xFFE7D8B0, false);
        if (snapshot.proposals().isEmpty()) { graphics.drawString(font, "No pending diplomatic proposals.", left + 20, top + 108, 0xFFAAAAAA, false); return; }
        for (int i = 0; i < Math.min(8, snapshot.proposals().size()); i++) {
            DiplomacySnapshot.ProposalEntry p = snapshot.proposals().get(i); int y = top + 100 + i * 28;
            graphics.drawString(font, pretty(p.type()) + " from " + p.fromFactionName(), left + 20, y, 0xFFFFFFFF, false);
            if (!p.summary().isBlank()) graphics.drawString(font, font.plainSubstrByWidth(p.summary(), 335), left + 20, y + 11, p.canReject() ? 0xFFAAAAAA : 0xFFFFD27A, false);
            else if (p.linkedWar() != null) graphics.drawString(font, "Linked war: " + p.linkedWar(), left + 20, y + 11, 0xFF888888, false);
        }
    }

    private void renderWars(GuiGraphics graphics) {
        graphics.drawString(font, "Ally to call", left + 20, top + 78, 0xFFAAA084, false);
        graphics.drawString(font, "ACTIVE WARS", left + 292, top + 78, 0xFFE7D8B0, false);
        if (snapshot.wars().isEmpty()) { graphics.drawString(font, "No active wars relevant to you.", left + 20, top + 142, 0xFFAAAAAA, false); return; }
        for (int i = 0; i < Math.min(5, snapshot.wars().size()); i++) {
            DiplomacySnapshot.WarEntry war = snapshot.wars().get(i); int y = top + 125 + i * 43;
            String attackers = war.attackerSettlementName().isBlank() ? war.attackerFactionName() : war.attackerSettlementName();
            String defenders = war.targetSettlementName().isBlank() ? war.defenderFactionName() : war.targetSettlementName();
            graphics.drawString(font, war.typeName() + " — " + attackers + " vs " + defenders, left + 20, y, 0xFFFFB0A0, false);
            graphics.drawString(font, "Casus Belli: " + war.goalName() + " · War score " + signed(war.warScore()), left + 20, y + 12, 0xFFAAAAAA, false);
            graphics.drawString(font, "Escalation: " + war.typeName(), left + 20, y + 23, 0xFF8FA8C8, false);
        }
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) { graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false); }
    private static int clampIndex(int index, int size) { return size <= 0 ? 0 : Math.max(0, Math.min(index, size - 1)); }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String pretty(String raw) { String v = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT); return v.isBlank() ? v : Character.toUpperCase(v.charAt(0)) + v.substring(1); }
    private static List<CampaignData.WarType> supportedWarTypes() { return List.of(CampaignData.WarType.RAID, CampaignData.WarType.SKIRMISH, CampaignData.WarType.SETTLEMENT_WAR, CampaignData.WarType.FACTION_WAR); }
    @Override public boolean isPauseScreen() { return false; }
    private enum Tab { RELATIONS, DECLARE, PROPOSALS, WARS }
}
