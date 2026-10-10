package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.AreaSelection;
import ee.lastpagans.settlements.HousingAreasSnapshot;
import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.network.HousingAreaActionPacket;
import ee.lastpagans.settlements.network.SettlementActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class HousingAreasScreen extends ScaledCampaignScreen {
    private static final int W = 500, H = 330, ROWS = 7;
    private HousingAreasSnapshot snapshot;
    private int left, top, selected = -1, scroll;
    private Button addButton, editButton, deleteButton, viewButton;

    public HousingAreasScreen(HousingAreasSnapshot snapshot) {
        super(Component.literal("Housing Areas"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W); top = campaignTop(H);
        addButton = addRenderableWidget(Button.builder(Component.literal("Add Area"), b ->
                AreaSelectionEditor.begin(snapshot.settlementId(), AreaSelection.Type.HOUSING))
                .bounds(left + 20, top + 286, 88, 20).build());
        editButton = addRenderableWidget(Button.builder(Component.literal("Edit Selected"), b -> editSelected())
                .bounds(left + 114, top + 286, 104, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete Selected"), b -> deleteSelected())
                .bounds(left + 224, top + 286, 112, 20).build());
        viewButton = addRenderableWidget(Button.builder(Component.literal("View In World"), b -> HousingAreaOverlay.begin(snapshot))
                .bounds(left + 342, top + 286, 118, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> SettlementNetwork.CHANNEL.sendToServer(
                        new SettlementActionPacket(snapshot.settlementId(), SettlementManager.SettlementAction.REFRESH)))
                .bounds(left + 400, top + 310, 60, 16).build());
        refreshButtons();
    }

    public void updateSnapshot(HousingAreasSnapshot value) {
        int selectedArea = selectedEntry() == null ? -1 : selectedEntry().index();
        snapshot = value;
        selected = -1;
        for (int i = 0; i < snapshot.areas().size(); i++) if (snapshot.areas().get(i).index() == selectedArea) selected = i;
        scroll = Math.min(scroll, maxScroll());
        refreshButtons();
    }

    private void editSelected() {
        HousingAreasSnapshot.Entry entry = selectedEntry();
        if (entry != null) AreaSelectionEditor.begin(snapshot.settlementId(), AreaSelection.Type.HOUSING, entry.index());
    }

    private void deleteSelected() {
        HousingAreasSnapshot.Entry entry = selectedEntry();
        if (entry == null) return;
        SettlementNetwork.CHANNEL.sendToServer(new HousingAreaActionPacket(snapshot.settlementId(),
                HousingAreaActionPacket.Action.DELETE, entry.index()));
    }

    private HousingAreasSnapshot.Entry selectedEntry() {
        return selected >= 0 && selected < snapshot.areas().size() ? snapshot.areas().get(selected) : null;
    }

    private void refreshButtons() {
        boolean hasSelection = selectedEntry() != null && snapshot.canManage();
        if (addButton != null) addButton.active = snapshot.canManage();
        if (editButton != null) editButton.active = hasSelection;
        if (deleteButton != null) deleteButton.active = hasSelection;
        if (viewButton != null) viewButton.active = !snapshot.areas().isEmpty();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 44, 0xFF3B3023);
        graphics.drawCenteredString(font, "HOUSING AREAS — " + snapshot.settlementName(), left + W / 2, top + 13, 0xFFE7D8B0);
        graphics.drawCenteredString(font, snapshot.areas().size() + " area(s) · box selection only", left + W / 2, top + 29, 0xFFB9AD8E);
        graphics.fill(left + 20, top + 58, left + 480, top + 274, 0xFF222222);
        List<HousingAreasSnapshot.Entry> areas = snapshot.areas();
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= areas.size()) break;
            HousingAreasSnapshot.Entry area = areas.get(index);
            int y = top + 66 + row * 28;
            int frame = index == selected ? 0xFFB89652 : 0xFF444444;
            graphics.fill(left + 28, y, left + 472, y + 23, frame);
            graphics.fill(left + 29, y + 1, left + 471, y + 22, index == selected ? 0xFF51442F : 0xFF2B2B2B);
            graphics.drawString(font, "Area " + (area.index() + 1), left + 38, y + 7, 0xFFFFE2A3, false);
            graphics.drawString(font, area.width() + "×" + area.height() + "×" + area.depth(), left + 118, y + 7, 0xFFDDDDDD, false);
            graphics.drawString(font, shortDimension(area.dimension()), left + 210, y + 7, 0xFFAAAAAA, false);
            graphics.drawString(font, area.min().toShortString() + " → " + area.max().toShortString(), left + 300, y + 7, 0xFFAAAAAA, false);
        }
        if (areas.isEmpty()) graphics.drawCenteredString(font, "No housing areas. Add one to begin counting beds.", left + W / 2, top + 155, 0xFFAAAAAA);
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, trim(snapshot.message(), 72), left + W / 2, top + 276, 0xFFFFC86B);
        super.render(graphics, ux, uy, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        if (button == 0 && ux >= left + 28 && ux <= left + 472 && uy >= top + 66 && uy < top + 262) {
            int row = (int)(uy - (top + 66)) / 28;
            int index = scroll + row;
            if (index >= 0 && index < snapshot.areas().size()) { selected = index; refreshButtons(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta < 0) scroll = Math.min(maxScroll(), scroll + 1); else if (delta > 0) scroll = Math.max(0, scroll - 1);
        return true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_DELETE && selectedEntry() != null && snapshot.canManage()) { deleteSelected(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private int maxScroll() { return Math.max(0, snapshot.areas().size() - ROWS); }
    private static String shortDimension(String value) { int colon = value.indexOf(':'); return colon >= 0 ? value.substring(colon + 1) : value; }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    @Override public boolean isPauseScreen() { return false; }
}
