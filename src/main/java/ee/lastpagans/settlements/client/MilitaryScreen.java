package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.SettlementSnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strategic recruitment and upkeep screen. This deliberately is not an RTS unit overview. */
public final class MilitaryScreen extends ScaledCampaignScreen {
    private static final int W = 540, H = 360;
    private static final int PAGE_FORCES = 0, PAGE_LEVIES = 1, PAGE_MERCENARIES = 2;

    private SettlementSnapshot snapshot;
    private int left, top;
    private DropdownWidget unitDropdown;
    private int selectedUnit;
    private int page = PAGE_FORCES;
    private long snapshotReceivedAt = System.currentTimeMillis();

    public MilitaryScreen(SettlementSnapshot snapshot) {
        super(Component.literal("Military"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        rebuildWidgets();
    }

    public void updateSnapshot(SettlementSnapshot value) {
        snapshot = value;
        snapshotReceivedAt = System.currentTimeMillis();
        selectedUnit = Math.min(selectedUnit, Math.max(0, professionalTemplates().size() - 1));
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        clearWidgets();
        unitDropdown = null;

        addCampaignTabs(new String[]{"Forces", "Levies", "Mercenaries"}, page, left + 24, top + 68, 160, 20, 6,
                index -> { page = index; rebuildWidgets(); });

        if (page == PAGE_FORCES) buildForcesWidgets();
        else if (page == PAGE_LEVIES) buildLevyWidgets();
        else buildMercenaryWidgets();

        addRenderableWidget(Button.builder(Component.literal("◀ Previous"), button -> switchSettlement(false))
                .bounds(left + 14, top + 331, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Next ▶"), button -> switchSettlement(true))
                .bounds(left + 112, top + 331, 78, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Realm Branch"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.REALM_BRANCH)))
                .bounds(left + 420, top + 331, 106, 18).build());
    }

    private void buildForcesWidgets() {
        List<SettlementSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        List<String> names = templates.stream().map(value -> value.displayName() + (value.configured() ? "" : " — fallback")).toList();
        if (!names.isEmpty()) {
            selectedUnit = Math.max(0, Math.min(selectedUnit, names.size() - 1));
            unitDropdown = addRenderableWidget(new DropdownWidget(left + 24, top + 178, 220, 20, names, selectedUnit, value -> {
                int found = names.indexOf(value);
                if (found >= 0) selectedUnit = found;
            }));
        }

        int[] amounts = {1, 5, 10, 25};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            addRenderableWidget(Button.builder(Component.literal("Recruit " + amount), button -> recruit(amount))
                    .bounds(left + 254 + i * 66, top + 178, 62, 20).build());
        }
    }

    private void buildLevyWidgets() {
        addRenderableWidget(Button.builder(Component.literal("Raise Levy"), button -> send(SettlementManager.SettlementAction.RAISE_LEVY))
                .bounds(left + 24, top + 274, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return Levy"), button -> send(SettlementManager.SettlementAction.RETURN_LEVY))
                .bounds(left + 142, top + 274, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("To Barracks"), button -> send(SettlementManager.SettlementAction.RETURN_TO_BARRACKS))
                .bounds(left + 260, top + 274, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recover MP"), button -> send(SettlementManager.SettlementAction.RUN_CHECKER))
                .bounds(left + 378, top + 274, 112, 20).build());
    }

    private void buildMercenaryWidgets() {
        addRenderableWidget(Button.builder(Component.literal("Mercenary Rosters"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MERCENARIES)))
                .bounds(left + 40, top + 196, 180, 20).build());

    }

    private List<SettlementSnapshot.UnitTemplateEntry> professionalTemplates() {
        List<SettlementSnapshot.UnitTemplateEntry> values = new ArrayList<>();
        for (SettlementSnapshot.UnitTemplateEntry template : snapshot.unitTemplates()) {
            if (!"LEVY".equals(template.type())) values.add(template);
        }
        return values;
    }

    private void recruit(int amount) {
        List<SettlementSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        if (templates.isEmpty()) return;
        String type = templates.get(Math.min(selectedUnit, templates.size() - 1)).type();
        SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(
                snapshot.settlementId(), SettlementManager.SettlementAction.RECRUIT_UNIT, type, amount));
    }

    private void send(SettlementManager.SettlementAction action) {
        SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(), action));
    }

    private void switchSettlement(boolean next) {
        SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(),
                next ? SettlementManager.SettlementAction.SELECT_NEXT : SettlementManager.SettlementAction.SELECT_PREVIOUS,
                "MILITARY", 0));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 58);
        drawCampaignPanel(graphics, left + 14, top + 96, left + W - 14, top + 316);
        graphics.drawCenteredString(font, "MILITARY — " + fitText(snapshot.settlementName().toUpperCase(Locale.ROOT), 420), left + W / 2, top + 13, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, fitText(snapshot.factionName() + " · " + snapshot.rosterName(), 440), left + W / 2, top + 31, 0xFFB8B0A0);

        if (page == PAGE_FORCES) renderForces(graphics);
        else if (page == PAGE_LEVIES) renderLevies(graphics);
        else renderMercenaries(graphics);

        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fitText(snapshot.message(), W - 40), left + W / 2, top + 306, CAMPAIGN_MESSAGE);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderForces(GuiGraphics graphics) {
        graphics.drawString(font, "FORCES", left + 24, top + 104, 0xFFE7D8B0, false);
        stat(graphics, "Native manpower", snapshot.availableManpower() + " / " + snapshot.maximumManpower(), 26, 122);
        stat(graphics, "Foreign manpower", snapshot.foreignManpower(), 26, 140);
        stat(graphics, "Professional soldiers", snapshot.professionals(), 26, 158);
        stat(graphics, "Recruit morale", snapshot.averageMorale() == 0 ? "No loaded troops" : snapshot.averageMorale() + "%", 286, 122);
        stat(graphics, "Mercenaries", snapshot.mercenaries(), 286, 140);
        stat(graphics, "Levymen", snapshot.levymen(), 286, 158);

        graphics.drawString(font, "UNIT TEMPLATE", left + 24, top + 168, 0xFFE7D8B0, false);
        List<SettlementSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        if (templates.isEmpty()) {
            graphics.drawString(font, "No professional unit types are available.", left + 24, top + 210, 0xFFFFA080, false);
            return;
        }

        SettlementSnapshot.UnitTemplateEntry selected = templates.get(Math.min(selectedUnit, templates.size() - 1));
        graphics.drawString(font, "Selected: " + selected.displayName(), left + 24, top + 212, 0xFFFFFFFF, false);
        graphics.drawString(font, "Source: " + (selected.configured() ? "Configured recruit template" : "Recruits fallback"), left + 24, top + 230, 0xFFB8B0A0, false);
        if (selected.mercenary()) {
            graphics.drawString(font, "Contract: " + selected.wage() + " treasury every " + selected.paymentIntervalDays() + " days", left + 24, top + 248, 0xFFFFC27A, false);
            graphics.drawWordWrap(font, Component.literal("Unpaid mercenaries lose morale and can eventually desert."), left + 24, top + 266, 470, 0xFFFFB0A0);
        } else {
            graphics.drawString(font, "Contract: no recurring wage", left + 24, top + 248, 0xFFB8B0A0, false);
        }
    }

    private void renderLevies(GuiGraphics graphics) {
        SettlementSnapshot.LevyStatusEntry levy = snapshot.levyStatus();
        graphics.drawString(font, "LEVY MOBILISATION", left + 24, top + 104, 0xFFE7D8B0, false);
        String factionLabel = snapshot.factionId().isBlank() ? "Independent" : "Faction";
        graphics.drawString(font, "Capacity: you " + levy.playerDeployed() + "/" + levy.playerCap()
                + " · " + factionLabel + " " + levy.factionDeployed() + "/" + levy.factionCap(), left + 24, top + 126, 0xFFFFFFFF, false);

        long elapsed = Math.max(0L, System.currentTimeMillis() - snapshotReceivedAt);
        long musterRemaining = Math.max(0L, levy.mobilizationRemainingMillis() - elapsed);
        long cooldownRemaining = Math.max(0L, levy.cooldownRemainingMillis() - elapsed);
        String levyState = levy.mobilizingTotal() > 0 && musterRemaining > 0
                ? "Mustering " + levy.mobilizingSpawned() + "/" + levy.mobilizingTotal() + " · " + duration(musterRemaining) + " remaining"
                : cooldownRemaining > 0 ? "Cooldown · " + duration(cooldownRemaining) + " remaining"
                : "Ready to mobilise";
        graphics.drawString(font, "Status: " + levyState, left + 24, top + 148, 0xFFB8B0A0, false);
        graphics.drawString(font, "Service state: " + levy.serviceState(), left + 24, top + 166, 0xFFB8B0A0, false);

        graphics.drawString(font, "REQUIREMENTS", left + 24, top + 196, 0xFFE7D8B0, false);
        status(graphics, "Town Square", snapshot.townSquareSet(), 24, 216);
        status(graphics, "Muster Ground", snapshot.musterGroundSet(), 190, 216);
        status(graphics, "Barracks", snapshot.barracksSet(), 374, 216);
        graphics.drawWordWrap(font, Component.literal("Raise Levy mobilises temporary troops. Return them when the campaign is over to avoid prolonged-service penalties and begin recovery."),
                left + 24, top + 240, 470, 0xFFAAAAAA);
    }

    private void renderMercenaries(GuiGraphics graphics) {
        graphics.drawString(font, "MERCENARIES", left + 24, top + 104, 0xFFE7D8B0, false);
        graphics.drawString(font, "Active mercenaries: " + snapshot.mercenaries(), left + 24, top + 126, 0xFFFFFFFF, false);
        graphics.drawString(font, "Obligation: " + snapshot.mercenaryWages() + " treasury / cycle", left + 24, top + 144, 0xFFB8B0A0, false);
        String timing = snapshot.mercenaries() <= 0 ? "No active contracts"
                : snapshot.mercenaryStatus() + " · next payment day " + snapshot.mercenaryNextPaymentDay();
        graphics.drawString(font, "Status: " + timing, left + 24, top + 162,
                "Paid".equals(snapshot.mercenaryStatus()) ? 0xFFA9D18E : snapshot.mercenaries() <= 0 ? 0xFFAAAAAA : 0xFFFFA080, false);



        graphics.fill(left + 270, top + 104, left + 271, top + 286, 0xFF3E3E3E);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private void status(GuiGraphics graphics, String label, boolean set, int x, int y) {
        graphics.drawString(font, (set ? "✓ " : "! ") + label + (set ? " set" : " missing"), left + x, top + y,
                set ? 0xFFA9D18E : 0xFFFFA080, false);
    }

    private static String duration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds / 3600L) % 24L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    @Override public boolean isPauseScreen() { return false; }
}
