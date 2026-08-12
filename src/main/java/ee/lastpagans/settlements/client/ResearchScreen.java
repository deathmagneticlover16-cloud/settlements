package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.ResearchSnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.ResearchActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ResearchScreen extends ScaledCampaignScreen {
    private static final int W = 540, H = 330, ROWS = 8;
    private ResearchSnapshot snapshot;
    private int left, top, scroll, selected;
    private Button startButton, cancelButton;

    public ResearchScreen(ResearchSnapshot snapshot) {
        super(Component.literal("Research"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W); top = campaignTop(H);
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), b ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + W - 118, top + H - 26, 104, 18).build());
        startButton = addRenderableWidget(Button.builder(Component.literal("Begin Research"), b -> startSelected())
                .bounds(left + 314, top + 250, 124, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.literal("Cancel Current"), b ->
                SettlementNetwork.CHANNEL.sendToServer(new ResearchActionPacket("CANCEL", "")))
                .bounds(left + 314, top + 275, 124, 20).build());
        refreshButtons();
    }

    public void updateSnapshot(ResearchSnapshot value) { snapshot = value; scroll = Math.min(scroll, Math.max(0, value.technologies().size() - ROWS)); refreshButtons(); }

    private void startSelected() {
        if (selected < 0 || selected >= snapshot.technologies().size()) return;
        SettlementNetwork.CHANNEL.sendToServer(new ResearchActionPacket("START", snapshot.technologies().get(selected).id()));
    }

    private void refreshButtons() {
        if (startButton == null) return;
        ResearchSnapshot.TechnologyEntry entry = selected >= 0 && selected < snapshot.technologies().size() ? snapshot.technologies().get(selected) : null;
        startButton.active = snapshot.canManage() && snapshot.activeStageId().isBlank() && entry != null && entry.available() && !entry.completed();
        cancelButton.active = snapshot.canManage() && !snapshot.activeStageId().isBlank();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 4, top + 4, left + W - 4, top + 56, 0xFF3B3023);
        graphics.drawCenteredString(font, "RESEARCH — " + snapshot.polityName(), left + W / 2, top + 14, 0xFFFFE2A3);
        graphics.drawCenteredString(font, snapshot.historyStagesInstalled() ? "History Stages integration active" : "History Stages not installed", left + W / 2, top + 33,
                snapshot.historyStagesInstalled() ? 0xFFA8E39D : 0xFFFFA27A);
        graphics.fill(left + 12, top + 66, left + 286, top + 302, 0xFF242424);
        graphics.fill(left + 296, top + 66, left + 528, top + 302, 0xFF242424);
        graphics.drawString(font, "Technologies", left + 22, top + 75, 0xFFFFFFFF, false);
        List<ResearchSnapshot.TechnologyEntry> list = snapshot.technologies();
        int max = Math.min(list.size(), scroll + ROWS);
        for (int index = scroll; index < max; index++) {
            ResearchSnapshot.TechnologyEntry entry = list.get(index);
            int row = index - scroll;
            int y = top + 96 + row * 23;
            boolean chosen = index == selected;
            graphics.fill(left + 20, y - 2, left + 278, y + 18, chosen ? 0xFF5A4B35 : 0xFF333333);
            int color = entry.completed() ? 0xFF8FD694 : entry.available() ? 0xFFFFFFFF : 0xFF999999;
            String prefix = entry.completed() ? "✓ " : (!entry.available() ? "× " : "");
            graphics.renderItem(icon(entry.icon()), left + 24, y);
            graphics.drawString(font, prefix + trim(entry.name(), 31), left + 44, y + 4, color, false);
        }
        renderDetails(graphics);
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, snapshot.message(), left + W / 2, top + 309, 0xFFFFD27A);
        super.render(graphics, ux, uy, partialTick);
        endCampaignRender(graphics);
    }

    private void renderDetails(GuiGraphics graphics) {
        graphics.drawString(font, "Current project", left + 306, top + 75, 0xFFFFFFFF, false);
        if (snapshot.activeStageId().isBlank()) graphics.drawString(font, "None", left + 306, top + 92, 0xFFAAAAAA, false);
        else {
            graphics.drawString(font, trim(snapshot.activeStageName(), 28), left + 306, top + 92, 0xFFFFE2A3, false);
            int x = left + 306, y = top + 111, width = 202;
            graphics.fill(x, y, x + width, y + 9, 0xFF111111);
            int fill = (int)Math.round(width * Math.max(0, Math.min(1000, snapshot.progressPermille())) / 1000.0D);
            graphics.fill(x, y, x + fill, y + 9, 0xFF8F7A45);
            graphics.drawString(font, (snapshot.progressPermille() / 10.0D) + "% · " + duration(snapshot.remainingSeconds()) + " remaining", x, y + 15, 0xFFCCCCCC, false);
            graphics.drawString(font, "Research speed: " + snapshot.speedPercent() + "%", x, y + 31, 0xFFCCCCCC, false);
        }
        ResearchSnapshot.TechnologyEntry selectedEntry = selected >= 0 && selected < snapshot.technologies().size() ? snapshot.technologies().get(selected) : null;
        graphics.drawString(font, "Selected technology", left + 306, top + 166, 0xFFFFFFFF, false);
        if (selectedEntry != null) {
            graphics.renderItem(icon(selectedEntry.icon()), left + 306, top + 181);
            graphics.drawString(font, trim(selectedEntry.name(), 24), left + 326, top + 184, selectedEntry.completed() ? 0xFF8FD694 : 0xFFFFE2A3, false);
            graphics.drawString(font, "Base time: " + duration(selectedEntry.durationSeconds()), left + 306, top + 201, 0xFFCCCCCC, false);
            graphics.drawString(font, selectedEntry.completed() ? "Completed" : selectedEntry.available() ? "Available" : "Locked", left + 306, top + 217,
                    selectedEntry.completed() ? 0xFF8FD694 : selectedEntry.available() ? 0xFFFFFFFF : 0xFFFF8A8A, false);
            graphics.drawString(font, "Requires: " + trim(selectedEntry.dependencies(), 26), left + 306, top + 233, 0xFFAAAAAA, false);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        if (button == 0 && ux >= left + 20 && ux <= left + 278 && uy >= top + 94 && uy <= top + 94 + ROWS * 23) {
            int row = (int)((uy - (top + 94)) / 23);
            int index = scroll + row;
            if (index >= 0 && index < snapshot.technologies().size()) { selected = index; refreshButtons(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, snapshot.technologies().size() - ROWS);
        if (delta < 0) scroll = Math.min(max, scroll + 1); else if (delta > 0) scroll = Math.max(0, scroll - 1);
        return true;
    }

    private ItemStack icon(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw == null ? "" : raw);
        Item item = id == null ? Items.BOOK : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) item = Items.BOOK;
        return new ItemStack(item);
    }

    private static String duration(long seconds) {
        long h = seconds / 3600L, m = (seconds % 3600L) / 60L;
        if (h > 0) return h + "h " + m + "m";
        return Math.max(1L, m) + "m";
    }
    private static String trim(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…"; }
    @Override public boolean isPauseScreen() { return false; }
}
