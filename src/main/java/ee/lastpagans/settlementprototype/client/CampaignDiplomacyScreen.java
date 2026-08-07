package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CampaignData;
import ee.lastpagans.settlementprototype.DiplomacySnapshot;
import ee.lastpagans.settlementprototype.network.DiplomacyActionPacket;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Guided diplomacy screen: target, conflict tier, settlement, goal, participants, then explicit confirmation. */
public final class CampaignDiplomacyScreen extends Screen {
    private static final int W = 520, H = 356;
    private final DiplomacySnapshot snapshot;
    private int left, top;
    private Tab tab = Tab.RELATIONS;
    private int relationIndex;
    private int warTypeIndex = 3;
    private int settlementIndex;
    private int goalIndex;
    private int warAllyIndex;
    private DropdownWidget factionDropdown, typeDropdown, settlementDropdown, goalDropdown, warAllyDropdown;

    public CampaignDiplomacyScreen(DiplomacySnapshot snapshot) {
        super(Component.literal("Campaign Diplomacy"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        factionDropdown = typeDropdown = settlementDropdown = goalDropdown = warAllyDropdown = null;
        String[] tabs = {"Relations", "Declare Conflict", "Proposals", "Active Wars"};
        for (int i = 0; i < tabs.length; i++) {
            Tab target = Tab.values()[i];
            addRenderableWidget(Button.builder(Component.literal(tabs[i]), button -> {
                tab = target;
                buildWidgets();
            }).bounds(left + 12 + i * 124, top + 45, 120, 20).build());
        }
        switch (tab) {
            case RELATIONS -> relationWidgets();
            case DECLARE -> declarationWidgets();
            case PROPOSALS -> proposalWidgets();
            case WARS -> warWidgets();
        }
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 390, top + 326, 116, 20).build());
    }

    private void relationWidgets() {
        List<String> factions = snapshot.relations().stream().map(DiplomacySnapshot.RelationEntry::factionName).toList();
        factionDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 86, 220, 20, factions, relationIndex, value -> {
            relationIndex = Math.max(0, factions.indexOf(value));
        }));
        Button nap = addRenderableWidget(Button.builder(Component.literal("Non-aggression pact"), button -> send("PROPOSE_NAP", null, null, ""))
                .bounds(left + 20, top + 229, 145, 20).build());
        Button defensive = addRenderableWidget(Button.builder(Component.literal("Defensive alliance"), button -> send("PROPOSE_DEFENSIVE", null, null, ""))
                .bounds(left + 171, top + 229, 145, 20).build());
        Button military = addRenderableWidget(Button.builder(Component.literal("Military alliance"), button -> send("PROPOSE_MILITARY", null, null, ""))
                .bounds(left + 322, top + 229, 145, 20).build());
        Button breakTreaty = addRenderableWidget(Button.builder(Component.literal("Break current treaty"), button -> send("BREAK_TREATY", null, null, ""))
                .bounds(left + 20, top + 255, 176, 20).build());
        nap.active = defensive.active = military.active = breakTreaty.active = snapshot.canManage() && !snapshot.relations().isEmpty();
    }

    private void declarationWidgets() {
        List<String> factionNames = snapshot.relations().stream().map(DiplomacySnapshot.RelationEntry::factionName).toList();
        List<String> typeNames = supportedWarTypes().stream().map(CampaignData.WarType::displayName).toList();
        List<DiplomacySnapshot.SettlementTarget> settlements = filteredSettlements();
        List<String> settlementNames = settlements.stream().map(DiplomacySnapshot.SettlementTarget::display).toList();
        List<CampaignData.WarGoal> goals = goals();
        List<String> goalNames = goals.stream().map(CampaignData.WarGoal::displayName).toList();
        settlementIndex = Math.min(settlementIndex, Math.max(0, settlementNames.size() - 1));
        goalIndex = Math.min(goalIndex, Math.max(0, goalNames.size() - 1));

        // Add from lowest to highest so expanded upper dropdowns remain visible above later fields.
        goalDropdown = addRenderableWidget(new DropdownWidget(left + 278, top + 137, 222, 20, goalNames, goalIndex, value -> goalIndex = Math.max(0, goalNames.indexOf(value))));
        settlementDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 137, 244, 20,
                settlementNames.isEmpty() ? List.of("No settlement required") : settlementNames, settlementIndex,
                value -> settlementIndex = Math.max(0, settlementNames.indexOf(value))));
        typeDropdown = addRenderableWidget(new DropdownWidget(left + 278, top + 91, 222, 20, typeNames, warTypeIndex, value -> {
            warTypeIndex = Math.max(0, typeNames.indexOf(value));
            settlementIndex = 0;
            goalIndex = 0;
            buildWidgets();
        }));
        factionDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 91, 244, 20, factionNames, relationIndex, value -> {
            relationIndex = Math.max(0, factionNames.indexOf(value));
            settlementIndex = 0;
            buildWidgets();
        }));

        Button declare = addRenderableWidget(Button.builder(Component.literal(declareButtonText()), button -> declareConflict())
                .bounds(left + 278, top + 259, 222, 24).build());
        declare.active = snapshot.canDeclareWar() && !snapshot.relations().isEmpty() && declarationValid();
    }

    private void proposalWidgets() {
        for (int i = 0; i < Math.min(7, snapshot.proposals().size()); i++) {
            DiplomacySnapshot.ProposalEntry proposal = snapshot.proposals().get(i);
            int y = top + 94 + i * 27;
            Button accept = addRenderableWidget(Button.builder(Component.literal("Accept"), button -> send("ACCEPT_PROPOSAL", proposal.id(), null, ""))
                    .bounds(left + 350, y, 68, 20).build());
            Button reject = addRenderableWidget(Button.builder(Component.literal("Reject"), button -> send("REJECT_PROPOSAL", proposal.id(), null, ""))
                    .bounds(left + 424, y, 68, 20).build());
            accept.active = reject.active = snapshot.canManage();
        }
    }

    private void warWidgets() {
        List<DiplomacySnapshot.RelationEntry> allies = alliedRelations();
        List<String> allyNames = allies.stream().map(DiplomacySnapshot.RelationEntry::factionName).toList();
        warAllyIndex = Math.min(warAllyIndex, Math.max(0, allyNames.size() - 1));
        warAllyDropdown = addRenderableWidget(new DropdownWidget(left + 20, top + 91, 224, 20,
                allyNames.isEmpty() ? List.of("No allied faction available") : allyNames, warAllyIndex,
                value -> warAllyIndex = Math.max(0, allyNames.indexOf(value))));

        for (int i = 0; i < Math.min(5, snapshot.wars().size()); i++) {
            DiplomacySnapshot.WarEntry war = snapshot.wars().get(i);
            int y = top + 128 + i * 34;
            Button call = addRenderableWidget(Button.builder(Component.literal("Call Ally"),
                            button -> sendToFaction("CALL_TO_WAR", selectedWarAllyId(), null, war.id(), ""))
                    .bounds(left + 300, y, 88, 20).build());
            Button peace = addRenderableWidget(Button.builder(Component.literal("Offer Peace"),
                            button -> send("OFFER_PEACE", null, war.id(), ""))
                    .bounds(left + 394, y, 98, 20).build());
            call.active = snapshot.canManage() && !allies.isEmpty();
            peace.active = snapshot.canManage();
        }
    }

    private List<DiplomacySnapshot.SettlementTarget> filteredSettlements() {
        CampaignData.WarType type = selectedWarType();
        if (type == CampaignData.WarType.FACTION_WAR) return List.of();
        return snapshot.settlements().stream().filter(value -> value.factionId().equals(selectedFactionId())).toList();
    }

    private List<CampaignData.WarGoal> goals() {
        CampaignData.WarType selected = selectedWarType();
        return Arrays.stream(CampaignData.WarGoal.values()).filter(value -> value.type() == selected).toList();
    }

    private CampaignData.WarType selectedWarType() {
        List<CampaignData.WarType> values = supportedWarTypes();
        return values.get(Math.max(0, Math.min(warTypeIndex, values.size() - 1)));
    }

    private String selectedFactionId() {
        if (snapshot.relations().isEmpty()) return "";
        return snapshot.relations().get(Math.max(0, Math.min(relationIndex, snapshot.relations().size() - 1))).factionId();
    }

    private String selectedFactionName() {
        if (snapshot.relations().isEmpty()) return "None";
        return snapshot.relations().get(Math.max(0, Math.min(relationIndex, snapshot.relations().size() - 1))).factionName();
    }

    private DiplomacySnapshot.SettlementTarget selectedSettlement() {
        List<DiplomacySnapshot.SettlementTarget> values = filteredSettlements();
        return values.isEmpty() ? null : values.get(Math.max(0, Math.min(settlementIndex, values.size() - 1)));
    }

    private CampaignData.WarGoal selectedGoal() {
        List<CampaignData.WarGoal> values = goals();
        return values.isEmpty() ? CampaignData.WarGoal.defaultFor(selectedWarType())
                : values.get(Math.max(0, Math.min(goalIndex, values.size() - 1)));
    }

    private boolean declarationValid() {
        return selectedWarType() == CampaignData.WarType.FACTION_WAR || selectedSettlement() != null;
    }

    private String declareButtonText() {
        return "Declare " + selectedWarType().displayName();
    }

    private void declareConflict() {
        DiplomacySnapshot.SettlementTarget settlement = selectedSettlement();
        String extra = selectedWarType().name() + "|" + (settlement == null ? "" : settlement.settlementId()) + "|" + selectedGoal().name();
        send("DECLARE_WAR", null, null, extra);
    }

    private void send(String action, UUID proposal, UUID war, String extra) {
        sendToFaction(action, selectedFactionId(), proposal, war, extra);
    }

    private void sendToFaction(String action, String targetFaction, UUID proposal, UUID war, String extra) {
        PrototypeNetwork.CHANNEL.sendToServer(new DiplomacyActionPacket(action, targetFaction, proposal, war, extra));
    }

    private List<DiplomacySnapshot.RelationEntry> alliedRelations() {
        return snapshot.relations().stream()
                .filter(value -> "DEFENSIVE_ALLIANCE".equals(value.state()) || "MILITARY_ALLIANCE".equals(value.state()))
                .toList();
    }

    private String selectedWarAllyId() {
        List<DiplomacySnapshot.RelationEntry> allies = alliedRelations();
        return allies.isEmpty() ? "" : allies.get(Math.max(0, Math.min(warAllyIndex, allies.size() - 1))).factionId();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (DropdownWidget dropdown : dropdowns()) {
            if (dropdown != null && dropdown.isExpanded() && dropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (DropdownWidget dropdown : dropdowns()) {
            if (dropdown != null && dropdown.isExpanded() && dropdown.mouseScrolled(mouseX, mouseY, delta)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private List<DropdownWidget> dropdowns() {
        List<DropdownWidget> values = new ArrayList<>();
        values.add(factionDropdown); values.add(typeDropdown); values.add(settlementDropdown); values.add(goalDropdown); values.add(warAllyDropdown);
        return values;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 39, 0xFF3B3023);
        graphics.fill(left + 12, top + 72, left + W - 12, top + 306, 0xFF242424);
        graphics.drawCenteredString(font, "CAMPAIGN DIPLOMACY — " + snapshot.ownFactionName().toUpperCase(Locale.ROOT), left + W / 2, top + 12, 0xFFE7D8B0);
        graphics.drawCenteredString(font, "Authority: [" + snapshot.viewerRank() + "]", left + W / 2, top + 25, snapshot.canManage() ? 0xFFA9D18E : 0xFFFFA080);

        switch (tab) {
            case RELATIONS -> renderRelations(graphics);
            case DECLARE -> renderDeclaration(graphics);
            case PROPOSALS -> renderProposals(graphics);
            case WARS -> renderWars(graphics);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, snapshot.message(), left + W / 2, top + 312, 0xFFFFD27A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRelations(GuiGraphics graphics) {
        graphics.drawString(font, "Target faction", left + 20, top + 76, 0xFFE7D8B0, false);
        if (snapshot.relations().isEmpty()) {
            graphics.drawString(font, "No other Recruits factions exist.", left + 20, top + 116, 0xFFAAAAAA, false);
            return;
        }
        DiplomacySnapshot.RelationEntry relation = snapshot.relations().get(Math.min(relationIndex, snapshot.relations().size() - 1));
        graphics.drawString(font, "CURRENT RELATIONSHIP", left + 270, top + 83, 0xFFE7D8B0, false);
        stat(graphics, "Diplomatic state", pretty(relation.state()), 270, 104);
        stat(graphics, "Relations", relation.relationship(), 270, 123);
        stat(graphics, "Faith compatibility", signed(relation.faithModifier()), 270, 142);
        stat(graphics, "Faction reliability", signed(relation.reliability()), 270, 161);
        graphics.drawWordWrap(font, Component.literal(
                "Treaties affect political relations. Only a formal conflict declaration changes Recruits' battlefield ally/enemy state."
        ), left + 20, top + 132, 220, 0xFFAAAAAA);
        graphics.drawString(font, "Select Declare Conflict to choose the exact war tier, target settlement and war goal.", left + 20, top + 201, 0xFFFFD27A, false);
    }

    private void renderDeclaration(GuiGraphics graphics) {
        graphics.drawString(font, "1. Target faction", left + 20, top + 79, 0xFFE7D8B0, false);
        graphics.drawString(font, "2. Conflict tier", left + 278, top + 79, 0xFFE7D8B0, false);
        graphics.drawString(font, "3. Target settlement", left + 20, top + 125, 0xFFE7D8B0, false);
        graphics.drawString(font, "4. War goal", left + 278, top + 125, 0xFFE7D8B0, false);

        CampaignData.WarType type = selectedWarType();
        DiplomacySnapshot.SettlementTarget settlement = selectedSettlement();
        graphics.fill(left + 20, top + 172, left + 500, top + 248, 0xFF191919);
        graphics.drawString(font, "FINAL DECLARATION SUMMARY", left + 30, top + 181, 0xFFFFD27A, false);
        stat(graphics, "Attacker", snapshot.ownFactionName(), 30, 199);
        stat(graphics, "Defender", selectedFactionName(), 270, 199);
        stat(graphics, "Conflict tier", type.displayName(), 30, 217);
        stat(graphics, "War goal", selectedGoal().displayName(), 270, 217);
        String target = settlement == null ? "Entire faction" : settlement.settlementName() + " — " + settlement.rank();
        stat(graphics, "Exact target", target, 30, 235);

        String consequence = switch (type) {
            case RAID -> "Limited raid against the selected settlement; no automatic territorial transfer.";
            case SKIRMISH -> "Restricted local clash focused on the selected settlement.";
            case SETTLEMENT_WAR -> "The named settlement is the direct target; its parent faction is the formal defender.";
            case FACTION_WAR -> "Every settlement and soldier of both factions enters a full faction war.";
            case INDEPENDENCE_WAR -> "The selected settlement attempts to break from its current parent faction.";
        };
        Component consequenceText = Component.literal(snapshot.canDeclareWar()
                ? consequence
                : "Leader authority required. " + consequence);
        graphics.drawWordWrap(font, consequenceText, left + 20, top + 251, 242,
                snapshot.canDeclareWar() ? 0xFFB8B0A0 : 0xFFFFA080);
    }

    private void renderProposals(GuiGraphics graphics) {
        graphics.drawString(font, "PENDING PROPOSALS", left + 20, top + 82, 0xFFE7D8B0, false);
        if (snapshot.proposals().isEmpty()) {
            graphics.drawString(font, "No pending treaties, peace offers or calls to war.", left + 20, top + 106, 0xFFAAAAAA, false);
            return;
        }
        for (int i = 0; i < Math.min(7, snapshot.proposals().size()); i++) {
            DiplomacySnapshot.ProposalEntry proposal = snapshot.proposals().get(i);
            int y = top + 100 + i * 27;
            graphics.drawString(font, pretty(proposal.type()) + " from " + proposal.fromFactionName(), left + 20, y, 0xFFFFFFFF, false);
            if (proposal.linkedWar() != null) graphics.drawString(font, "Linked war: " + proposal.linkedWar(), left + 20, y + 11, 0xFF888888, false);
        }
    }

    private void renderWars(GuiGraphics graphics) {
        graphics.drawString(font, "Ally to call", left + 20, top + 77, 0xFFAAA084, false);
        graphics.drawString(font, "ACTIVE WARS", left + 278, top + 77, 0xFFE7D8B0, false);
        if (snapshot.wars().isEmpty()) {
            graphics.drawString(font, "Your faction is not involved in an active war.", left + 20, top + 142, 0xFFAAAAAA, false);
            return;
        }
        for (int i = 0; i < Math.min(5, snapshot.wars().size()); i++) {
            DiplomacySnapshot.WarEntry war = snapshot.wars().get(i);
            int y = top + 125 + i * 34;
            graphics.drawString(font, war.typeName() + " — " + war.attackerFactionName() + " vs " + war.defenderFactionName(), left + 20, y, 0xFFFFB0A0, false);
            String target = war.targetSettlementName().isBlank() ? "Entire faction" : war.targetSettlementName();
            graphics.drawString(font, "Goal: " + war.goalName() + " · Target: " + target + " · Day " + war.startedDay(), left + 20, y + 12, 0xFFAAAAAA, false);
        }
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return value.isBlank() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }


    private static List<CampaignData.WarType> supportedWarTypes() {
        return List.of(CampaignData.WarType.RAID, CampaignData.WarType.SKIRMISH,
                CampaignData.WarType.SETTLEMENT_WAR, CampaignData.WarType.FACTION_WAR);
    }

    @Override public boolean isPauseScreen() { return false; }
    private enum Tab { RELATIONS, DECLARE, PROPOSALS, WARS }
}
