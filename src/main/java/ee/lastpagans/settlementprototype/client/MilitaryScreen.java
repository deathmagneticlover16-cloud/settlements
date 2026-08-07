package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.PrototypeManager;
import ee.lastpagans.settlementprototype.PrototypeSnapshot;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.SettlementActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strategic recruitment and upkeep screen. This deliberately is not an RTS unit overview. */
public final class MilitaryScreen extends Screen {
    private static final int W = 540, H = 330;
    private PrototypeSnapshot snapshot;
    private int left, top;
    private DropdownWidget unitDropdown;
    private int selectedUnit;

    public MilitaryScreen(PrototypeSnapshot snapshot) {
        super(Component.literal("Military"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        rebuildWidgets();
    }

    public void updateSnapshot(PrototypeSnapshot value) {
        snapshot = value;
        selectedUnit = Math.min(selectedUnit, Math.max(0, professionalTemplates().size() - 1));
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        List<PrototypeSnapshot.UnitTemplateEntry> templates = professionalTemplates();
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
        addRenderableWidget(Button.builder(Component.literal("Raise Levy"), button -> send(PrototypeManager.SettlementAction.RAISE_LEVY))
                .bounds(left + 24, top + 250, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return Levy"), button -> send(PrototypeManager.SettlementAction.RETURN_LEVY))
                .bounds(left + 136, top + 250, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("To Barracks"), button -> send(PrototypeManager.SettlementAction.RETURN_TO_BARRACKS))
                .bounds(left + 248, top + 250, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recover MP"), button -> send(PrototypeManager.SettlementAction.RUN_CHECKER))
                .bounds(left + 358, top + 250, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("◀ Previous"), button -> switchSettlement(false))
                .bounds(left + 14, top + 301, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Next ▶"), button -> switchSettlement(true))
                .bounds(left + 112, top + 301, 78, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Settlement"), button ->
                PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.SETTLEMENT)))
                .bounds(left + 322, top + 301, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 420, top + 301, 106, 18).build());
    }

    private List<PrototypeSnapshot.UnitTemplateEntry> professionalTemplates() {
        List<PrototypeSnapshot.UnitTemplateEntry> values = new ArrayList<>();
        for (PrototypeSnapshot.UnitTemplateEntry template : snapshot.unitTemplates()) {
            if (!"LEVY".equals(template.type())) values.add(template);
        }
        return values;
    }

    private void recruit(int amount) {
        List<PrototypeSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        if (templates.isEmpty()) return;
        String type = templates.get(Math.min(selectedUnit, templates.size() - 1)).type();
        PrototypeNetwork.CHANNEL.sendToServer(new SettlementActionPacket(
                snapshot.settlementId(), PrototypeManager.SettlementAction.RECRUIT_UNIT, type, amount));
    }

    private void send(PrototypeManager.SettlementAction action) {
        PrototypeNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(), action));
    }

    private void switchSettlement(boolean next) {
        PrototypeNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(),
                next ? PrototypeManager.SettlementAction.SELECT_NEXT : PrototypeManager.SettlementAction.SELECT_PREVIOUS,
                "MILITARY", 0));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (unitDropdown != null && unitDropdown.isExpanded() && unitDropdown.mouseClicked(x, y, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (unitDropdown != null && unitDropdown.isExpanded() && unitDropdown.mouseScrolled(x, y, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0171717);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 58, 0xFF3B3023);
        graphics.fill(left + 14, top + 68, left + W - 14, top + 286, 0xFF242424);
        graphics.drawCenteredString(font, "MILITARY — " + fit(snapshot.settlementName().toUpperCase(Locale.ROOT), 420), left + W / 2, top + 13, 0xFFE7D8B0);
        graphics.drawCenteredString(font, fit(snapshot.factionName() + " · " + snapshot.rosterName(), 440), left + W / 2, top + 31, 0xFFB8B0A0);

        stat(graphics, "Native manpower", snapshot.availableManpower() + " / " + snapshot.maximumManpower(), 26, 78);
        stat(graphics, "Foreign manpower", snapshot.foreignManpower(), 26, 98);
        stat(graphics, "Professional soldiers", snapshot.professionals(), 26, 118);
        stat(graphics, "Levymen", snapshot.levymen(), 286, 118);
        stat(graphics, "Recruit morale", snapshot.averageMorale() == 0 ? "No loaded troops" : snapshot.averageMorale() + "%", 286, 78);
        stat(graphics, "Mercenaries", snapshot.mercenaries(), 286, 98);

        graphics.drawString(font, "UNIT TEMPLATE", left + 24, top + 132, 0xFFE7D8B0, false);
        List<PrototypeSnapshot.UnitTemplateEntry> templates = professionalTemplates();
        if (!templates.isEmpty()) {
            PrototypeSnapshot.UnitTemplateEntry selected = templates.get(Math.min(selectedUnit, templates.size() - 1));
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

        graphics.drawString(font, "Faction/settlement wage obligation: " + snapshot.mercenaryWages() + " wealth / cycle", left + 286, top + 214, 0xFFB8B0A0, false);
        status(graphics, "Town Square", snapshot.townSquareSet(), 24, 230);
        status(graphics, "Muster Ground", snapshot.musterGroundSet(), 190, 230);
        status(graphics, "Barracks", snapshot.barracksSet(), 374, 230);
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fit(snapshot.message(), W - 40), left + W / 2, top + 281, 0xFFFFD27A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private void status(GuiGraphics graphics, String label, boolean set, int x, int y) {
        graphics.drawString(font, (set ? "✓ " : "! ") + label + (set ? " set" : " missing"), left + x, top + y,
                set ? 0xFFA9D18E : 0xFFFFA080, false);
    }

    private String fit(String value, int pixelWidth) {
        if (value == null) return "";
        return font.width(value) <= pixelWidth ? value : font.plainSubstrByWidth(value, Math.max(0, pixelWidth - font.width("…"))) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
