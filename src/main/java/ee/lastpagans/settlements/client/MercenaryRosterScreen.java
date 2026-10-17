package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.MercenaryRosterSnapshot;
import ee.lastpagans.settlements.network.MercenaryRosterActionPacket;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
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
import java.util.Locale;

/** Campaign-market view for named mercenary companies. */
public final class MercenaryRosterScreen extends ScaledCampaignScreen {
    private static final int W = 560, H = 370;
    private static final int ROW_H = 35, VISIBLE_ROWS = 6;
    private MercenaryRosterSnapshot snapshot;
    private int left, top, selected, scroll;

    public MercenaryRosterScreen(MercenaryRosterSnapshot snapshot) {
        super(Component.literal("Mercenary Rosters"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H); rebuildWidgets();
    }

    public void updateSnapshot(MercenaryRosterSnapshot value) {
        snapshot = value;
        selected = Math.min(selected, Math.max(0, snapshot.entries().size() - 1));
        scroll = Math.min(scroll, Math.max(0, snapshot.entries().size() - VISIBLE_ROWS));
        rebuildWidgets();
    }

    @Override protected void rebuildWidgets() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H); clearWidgets();
        MercenaryRosterSnapshot.Entry entry = selectedEntry();
        if (entry != null) {
            String action = entry.yours() ? "RELEASE" : "HIRE";
            Button actionButton = addRenderableWidget(Button.builder(Component.literal(entry.actionLabel()), button ->
                    SettlementNetwork.CHANNEL.sendToServer(new MercenaryRosterActionPacket(entry.id(), action)))
                    .bounds(left + 370, top + 286, 160, 22).build());
            actionButton.active = entry.yours() || entry.canHire();
        }
        addRenderableWidget(Button.builder(Component.literal("Military"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MILITARY)))
                .bounds(left + 350, top + 340, 86, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 442, top + 340, 104, 18).build());
    }

    private MercenaryRosterSnapshot.Entry selectedEntry() {
        return snapshot.entries().isEmpty() ? null : snapshot.entries().get(Math.max(0, Math.min(selected, snapshot.entries().size() - 1)));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (button == 0 && uiX >= left + 18 && uiX <= left + 302 && uiY >= top + 82 && uiY < top + 82 + VISIBLE_ROWS * ROW_H) {
            int row = (int)((uiY - (top + 82)) / ROW_H);
            int index = scroll + row;
            if (index >= 0 && index < snapshot.entries().size()) { selected = index; rebuildWidgets(); return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (uiX >= left + 18 && uiX <= left + 302 && uiY >= top + 82 && uiY < top + 82 + VISIBLE_ROWS * ROW_H) {
            int max = Math.max(0, snapshot.entries().size() - VISIBLE_ROWS);
            scroll = Math.max(0, Math.min(max, scroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX), uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 62);
        graphics.drawCenteredString(font, "MERCENARY ROSTERS", left + W / 2, top + 13, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, snapshot.settlementName() + " · " + snapshot.factionName(), left + W / 2, top + 31, 0xFFB8B0A0);
        graphics.drawCenteredString(font, "Available Wealth: " + snapshot.availableWealth() + " · Reliability: " + snapshot.reliability(), left + W / 2, top + 46, 0xFFCFBE98);

        drawCampaignPanel(graphics, left + 14, top + 72, left + 310, top + 300);
        drawCampaignPanel(graphics, left + 320, top + 72, left + 546, top + 326);
        graphics.drawString(font, "AVAILABLE COMPANIES", left + 22, top + 74, 0xFFE7D8B0, false);
        List<MercenaryRosterSnapshot.Entry> entries = snapshot.entries();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row; if (index >= entries.size()) break;
            MercenaryRosterSnapshot.Entry entry = entries.get(index);
            int y = top + 82 + row * ROW_H;
            graphics.fill(left + 18, y, left + 302, y + 31, index == selected ? 0xFF51442F : 0xFF303030);
            ItemStack icon = icon(entry.iconItem()); graphics.renderItem(icon, left + 24, y + 7);
            graphics.drawString(font, fitText(entry.name(), 165), left + 46, y + 5, CAMPAIGN_TEXT, false);
            String line = entry.size() + " troops · " + entry.upkeep() + "/" + entry.intervalDays() + "d · " + entry.status();
            graphics.drawString(font, fitText(line, 235), left + 46, y + 18, "Paid".equals(entry.status()) || "Available".equals(entry.status()) ? 0xFFB8B0A0 : 0xFFFFB080, false);
        }
        if (entries.size() > VISIBLE_ROWS) graphics.drawString(font, "Scroll for more", left + 216, top + 287, 0xFF8F887C, false);

        MercenaryRosterSnapshot.Entry entry = selectedEntry();
        if (entry != null) {
            graphics.drawString(font, entry.name().toUpperCase(Locale.ROOT), left + 330, top + 82, 0xFFE7D8B0, false);
            graphics.renderItem(icon(entry.iconItem()), left + 510, top + 78);
            detail(graphics, "Status", entry.status(), 100);
            detail(graphics, "Employer", entry.employer(), 116);
            detail(graphics, "Loyalty", entry.loyalty() + "/100", 132);
            detail(graphics, "Base hire", entry.hireCost() + " Wealth", 148);
            detail(graphics, entry.yours() ? "Contract" : "Current price", entry.yours() ? "Active" : entry.effectiveHireCost() + " Wealth", 164);
            detail(graphics, "Upkeep", entry.upkeep() + " every " + entry.intervalDays() + " day(s)", 180);
            graphics.drawString(font, "COMPOSITION", left + 330, top + 204, 0xFFE7D8B0, false);
            graphics.drawWordWrap(font, Component.literal(entry.composition()), left + 330, top + 218, 202, 0xFFDDDDDD);
            if (!entry.reason().isBlank()) graphics.drawWordWrap(font, Component.literal(entry.reason()), left + 330, top + 258, 202, 0xFFFFA080);
        } else {
            graphics.drawCenteredString(font, "No mercenary companies configured.", left + 433, top + 150, 0xFFFFA080);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fitText(snapshot.message(), W - 40), left + W / 2, top + 316, CAMPAIGN_MESSAGE);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void detail(GuiGraphics graphics, String label, String value, int y) {
        graphics.drawString(font, label + ":", left + 330, top + y, 0xFFAAA294, false);
        graphics.drawString(font, fitText(value, 130), left + 400, top + y, CAMPAIGN_TEXT, false);
    }

    private ItemStack icon(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw == null ? "" : raw);
        Item item = id == null ? Items.IRON_SWORD : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) item = Items.IRON_SWORD;
        return new ItemStack(item);
    }

    @Override public boolean isPauseScreen() { return false; }
}
