package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.WorldNewsSnapshot;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorldNewsScreen extends ScaledCampaignScreen {
    private static final int W = 520;
    private static final int H = 310;
    private static final List<String> FILTERS = List.of(
            "All news", "Wars", "Diplomacy", "Settlements", "Factions", "Characters", "Economy", "Religion", "Military"
    );
    private final WorldNewsSnapshot snapshot;
    private int left;
    private int top;
    private int scroll;
    private String filter = "All news";
    private DropdownWidget filterDrop;

    public WorldNewsScreen(WorldNewsSnapshot snapshot) {
        super(Component.literal("World News"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        filterDrop = addRenderableWidget(new DropdownWidget(left + 18, top + 48, 170, 20, FILTERS, 0, value -> {
            filter = value;
            scroll = 0;
        }));
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 392, top + 282, 116, 18).build());
    }

    private List<WorldNewsSnapshot.Entry> filtered() {
        if ("All news".equals(filter)) return snapshot.entries();
        String category = filter.toUpperCase(Locale.ROOT);
        return snapshot.entries().stream().filter(entry -> entry.category().equals(category)).toList();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (filterDrop != null && filterDrop.isExpanded() && filterDrop.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (filterDrop != null && filterDrop.isExpanded() && filterDrop.mouseScrolled(uiX, uiY, delta)) return true;
        List<WorldNewsSnapshot.Entry> values = filtered();
        scroll = Math.max(0, Math.min(Math.max(0, values.size() - 5), scroll + (delta < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0171717);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 40, 0xFF3B3023);
        graphics.fill(left + 12, top + 76, left + W - 12, top + 274, 0xFF242424);
        graphics.drawCenteredString(font, "WORLD NEWS — DAY " + snapshot.currentDay(), left + W / 2, top + 14, 0xFFE7D8B0);
        graphics.drawString(font, "Filter", left + 18, top + 40, 0xFFCCCCCC, false);
        graphics.drawString(font, snapshot.entries().size() + " visible records", left + 205, top + 55, 0xFF888888, false);

        List<WorldNewsSnapshot.Entry> values = filtered();
        if (values.isEmpty()) {
            graphics.drawCenteredString(font, "No world news matches this filter.", left + W / 2, top + 152, 0xFFAAAAAA);
        } else {
            for (int i = 0; i < 5 && scroll + i < values.size(); i++) {
                WorldNewsSnapshot.Entry entry = values.get(scroll + i);
                renderEntry(graphics, entry, left + 20, top + 84 + i * 37);
            }
        }
        graphics.drawString(font, "Use the mouse wheel to browse older reports.", left + 20, top + 262, 0xFF777777, false);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderEntry(GuiGraphics graphics, WorldNewsSnapshot.Entry entry, int x, int y) {
        int border = switch (entry.importance()) {
            case "MAJOR" -> 0xFF8D4B3F;
            case "NORMAL" -> 0xFF6C6252;
            default -> 0xFF444444;
        };
        graphics.fill(x, y, x + 480, y + 33, 0xDD181818);
        graphics.fill(x, y, x + 3, y + 33, border);
        graphics.drawString(font, icon(entry.category()) + " " + entry.headline(), x + 9, y + 4, 0xFFFFFFFF, false);
        graphics.drawString(font, shorten(entry.body(), 67), x + 9, y + 16, 0xFFBBBBBB, false);
        String location = !entry.settlementName().isBlank() ? entry.settlementName()
                : !entry.factionName().isBlank() ? entry.factionName() : "World";
        graphics.drawString(font, "Day " + entry.day() + " · " + location + " · " + pretty(entry.visibility()),
                x + 310, y + 4, 0xFF8E8577, false);
    }

    private static String icon(String category) {
        return switch (category) {
            case "WARS" -> "⚔";
            case "DIPLOMACY" -> "☞";
            case "SETTLEMENTS" -> "⌂";
            case "FACTIONS" -> "⚑";
            case "CHARACTERS" -> "♙";
            case "ECONOMY" -> "◆";
            case "RELIGION" -> "✦";
            case "MILITARY" -> "♜";
            default -> "•";
        };
    }

    private static String pretty(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
