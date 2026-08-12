package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.SettlementSnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.SettlementActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strategic recruitment and upkeep screen. This deliberately is not an RTS unit overview. */
public final class MilitaryScreen extends ScaledCampaignScreen {
    private static final int W = 540, H = 360;
    private SettlementSnapshot snapshot;
    private int left, top;
    private DropdownWidget unitDropdown;
    private int selectedUnit;
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
        List<SettlementSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        List<String> names = templates.stream().map(value -> value.displayName() + (value.configured() ? "" : " — fallback")).toList();
        if (!names.isEmpty()) {
            selectedUnit = Math.max(0, Math.min(selectedUnit, names.size() - 1));
            unitDropdown = addRenderableWidget(new DropdownWidget(left + 24, top + 140, 220, 20, names, selectedUnit, value -> {
                int found = names.indexOf(value);
                if (found >= 0) selectedUnit = found;
            }));
        } else unitDropdown = null;

        int[] amounts = {1, 5, 10, 25};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            addRenderableWidget(Button.builder(Component.literal("Recruit " + amount), button -> recruit(amount))
                    .bounds(left + 254 + i * 66, top + 140, 62, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Mercenary Rosters"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MERCENARIES)))
                .bounds(left + 24, top + 206, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Raise Levy"), button -> send(SettlementManager.SettlementAction.RAISE_LEVY))
                .bounds(left + 24, top + 274, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return Levy"), button -> send(SettlementManager.SettlementAction.RETURN_LEVY))
                .bounds(left + 136, top + 274, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("To Barracks"), button -> send(SettlementManager.SettlementAction.RETURN_TO_BARRACKS))
                .bounds(left + 248, top + 274, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recover MP"), button -> send(SettlementManager.SettlementAction.RUN_CHECKER))
                .bounds(left + 358, top + 274, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("◀ Previous"), button -> switchSettlement(false))
                .bounds(left + 14, top + 331, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Next ▶"), button -> switchSettlement(true))
                .bounds(left + 112, top + 331, 78, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Settlement"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.SETTLEMENT)))
                .bounds(left + 322, top + 331, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 420, top + 331, 106, 18).build());
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

    @Override public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (unitDropdown != null && unitDropdown.isExpanded() && unitDropdown.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (unitDropdown != null && unitDropdown.isExpanded() && unitDropdown.mouseScrolled(uiX, uiY, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0171717);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 58, 0xFF3B3023);
        graphics.fill(left + 14, top + 68, left + W - 14, top + 316, 0xFF242424);
        graphics.drawCenteredString(font, "MILITARY — " + fit(snapshot.settlementName().toUpperCase(Locale.ROOT), 420), left + W / 2, top + 13, 0xFFE7D8B0);
        graphics.drawCenteredString(font, fit(snapshot.factionName() + " · " + snapshot.rosterName(), 440), left + W / 2, top + 31, 0xFFB8B0A0);

        stat(graphics, "Native manpower", snapshot.availableManpower() + " / " + snapshot.maximumManpower(), 26, 78);
        stat(graphics, "Foreign manpower", snapshot.foreignManpower(), 26, 98);
        stat(graphics, "Professional soldiers", snapshot.professionals(), 26, 118);
        stat(graphics, "Levymen", snapshot.levymen(), 286, 118);
        stat(graphics, "Recruit morale", snapshot.averageMorale() == 0 ? "No loaded troops" : snapshot.averageMorale() + "%", 286, 78);
        stat(graphics, "Mercenaries", snapshot.mercenaries(), 286, 98);

        graphics.drawString(font, "UNIT TEMPLATE", left + 24, top + 132, 0xFFE7D8B0, false);
        List<SettlementSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        if (!templates.isEmpty()) {
            SettlementSnapshot.UnitTemplateEntry selected = templates.get(Math.min(selectedUnit, templates.size() - 1));
            int y = top + 174;
            stat(graphics, "Template", selected.configured() ? "Configured egg" : "Recruits fallback", 26, y);
            stat(graphics, "Type", selected.displayName(), 26, y + 20);
            if (selected.mercenary()) {
                stat(graphics, "Contract", selected.wage() + " wealth every " + selected.paymentIntervalDays() + " days", 286, y);
                graphics.drawWordWrap(font, Component.literal("Unpaid mercenaries lose morale and desert after three missed payments."),
                        left + 286, y + 20, 222, 0xFFFFB0A0);
            } else {
                stat(graphics, "Contract", "No recurring wage", 286, y);
            }
        } else {
            graphics.drawString(font, "No professional unit types are available.", left + 24, top + 174, 0xFFFFA080, false);
        }

        graphics.drawString(font, "Mercenary obligation: " + snapshot.mercenaryWages() + " wealth / cycle", left + 286, top + 214, 0xFFB8B0A0, false);
        String mercenaryTiming = snapshot.mercenaries() <= 0 ? "No active contracts"
                : snapshot.mercenaryStatus() + " · next payment day " + snapshot.mercenaryNextPaymentDay();
        graphics.drawString(font, "Mercenary status: " + mercenaryTiming, left + 286, top + 226,
                "Paid".equals(snapshot.mercenaryStatus()) ? 0xFFA9D18E : snapshot.mercenaries() <= 0 ? 0xFFAAAAAA : 0xFFFFA080, false);
        SettlementSnapshot.LevyStatusEntry levy = snapshot.levyStatus();
        String factionLabel = snapshot.factionId().isBlank() ? "Independent" : "Faction";
        graphics.drawString(font, "Levy capacity: you " + levy.playerDeployed() + "/" + levy.playerCap()
                + " · " + factionLabel + " " + levy.factionDeployed() + "/" + levy.factionCap(), left + 24, top + 230, 0xFFB8B0A0, false);
        long elapsed = Math.max(0L, System.currentTimeMillis() - snapshotReceivedAt);
        long musterRemaining = Math.max(0L, levy.mobilizationRemainingMillis() - elapsed);
        long cooldownRemaining = Math.max(0L, levy.cooldownRemainingMillis() - elapsed);
        String levyState = levy.mobilizingTotal() > 0 && musterRemaining > 0
                ? "Mustering " + levy.mobilizingSpawned() + "/" + levy.mobilizingTotal() + " · " + duration(musterRemaining) + " remaining"
                : cooldownRemaining > 0 ? "Cooldown " + duration(cooldownRemaining)
                : "Ready to mobilise";
        graphics.drawString(font, "Levy status: " + levyState + " · " + levy.serviceState(), left + 24, top + 244, 0xFFB8B0A0, false);
        status(graphics, "Town Square", snapshot.townSquareSet(), 24, 260);
        status(graphics, "Muster Ground", snapshot.musterGroundSet(), 190, 260);
        status(graphics, "Barracks", snapshot.barracksSet(), 374, 260);
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fit(snapshot.message(), W - 40), left + W / 2, top + 305, 0xFFFFD27A);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
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

    private String fit(String value, int pixelWidth) {
        return scrollingText(value, pixelWidth);
    }

    @Override public boolean isPauseScreen() { return false; }
}
