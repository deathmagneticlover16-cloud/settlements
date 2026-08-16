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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Total-War-inspired technology tree backed by History Stages individual stages. */
public final class ResearchScreen extends ScaledCampaignScreen {
    private static final int W = 660, H = 400, COLUMNS = 4, ROWS = 7;
    private static final int TREE_X = 14, TREE_Y = 112, TREE_W = 430, TREE_H = 244;
    private ResearchSnapshot snapshot;
    private int left, top, scroll, selected = -1, refreshTicks;
    private Button startButton, cancelButton;

    public ResearchScreen(ResearchSnapshot snapshot) {
        super(Component.literal("Research"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W); top = campaignTop(H);
        startButton = addRenderableWidget(Button.builder(Component.literal("Begin Research"), b -> startSelected())
                .bounds(left + 466, top + 304, 166, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.literal("Cancel Current"), b ->
                SettlementNetwork.CHANNEL.sendToServer(new ResearchActionPacket("CANCEL", "")))
                .bounds(left + 466, top + 330, 166, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), b ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 528, top + 370, 104, 18).build());
        refreshButtons();
    }

    @Override public void tick() {
        super.tick();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            SettlementNetwork.CHANNEL.sendToServer(new ResearchActionPacket("REFRESH", ""));
        }
    }

    public void updateSnapshot(ResearchSnapshot value) {
        String selectedId = selected >= 0 && selected < snapshot.technologies().size() ? snapshot.technologies().get(selected).id() : "";
        snapshot = value;
        if (!selectedId.isBlank()) {
            selected = -1;
            for (int i = 0; i < value.technologies().size(); i++) if (selectedId.equals(value.technologies().get(i).id())) { selected = i; break; }
        }
        scroll = Math.min(scroll, maxScroll());
        refreshButtons();
    }

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
        graphics.fill(left, top, left + W, top + H, 0xF0161616);
        graphics.fill(left + 4, top + 4, left + W - 4, top + 56, 0xFF3B3023);
        graphics.drawCenteredString(font, "RESEARCH — " + snapshot.polityName(), left + W / 2, top + 13, 0xFFFFE2A3);
        graphics.drawCenteredString(font, snapshot.historyStagesInstalled()
                        ? "History Stages · Settlement research tier " + roman(snapshot.researchTier()) + " · " + snapshot.speedPercent() + "% speed"
                        : "History Stages not installed — research unavailable",
                left + W / 2, top + 33, snapshot.historyStagesInstalled() ? 0xFFA8E39D : 0xFFFFA27A);

        renderActiveProject(graphics);
        graphics.fill(left + TREE_X, top + TREE_Y, left + TREE_X + TREE_W, top + TREE_Y + TREE_H, 0xFF222222);
        graphics.fill(left + 452, top + TREE_Y, left + 646, top + TREE_Y + TREE_H, 0xFF222222);
        renderTree(graphics);
        renderDetails(graphics);
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, trim(snapshot.message(), 80), left + W / 2, top + 382, 0xFFFFD27A);
        super.render(graphics, ux, uy, partialTick);
        endCampaignRender(graphics);
    }

    private void renderActiveProject(GuiGraphics graphics) {
        int x = left + 14, y = top + 64, width = W - 28;
        graphics.fill(x, y, x + width, y + 38, 0xFF25211B);
        graphics.drawString(font, "ACTIVE RESEARCH", x + 8, y + 6, 0xFFE7D8B0, false);
        if (snapshot.activeStageId().isBlank()) {
            graphics.drawString(font, "No project selected", x + 112, y + 6, 0xFFAAAAAA, false);
            return;
        }
        graphics.drawString(font, trim(snapshot.activeStageName(), 40), x + 112, y + 6, 0xFFFFE2A3, false);
        int barX = x + 8, barY = y + 22, barW = width - 190;
        graphics.fill(barX, barY, barX + barW, barY + 8, 0xFF0E0E0E);
        int fill = (int)Math.round(barW * Math.max(0, Math.min(1000, snapshot.progressPermille())) / 1000.0D);
        graphics.fill(barX, barY, barX + fill, barY + 8, 0xFF8F7A45);
        graphics.drawString(font, String.format(java.util.Locale.ROOT, "%.1f%%", snapshot.progressPermille() / 10.0D), barX + barW + 8, barY, 0xFFFFFFFF, false);
        graphics.drawString(font, duration(snapshot.remainingSeconds()) + " remaining", x + width - 112, y + 6, 0xFFCCCCCC, false);
    }

    private void renderTree(GuiGraphics graphics) {
        String[] headers = {"I", "II", "III", "IV+"};
        int colW = TREE_W / COLUMNS;
        for (int col = 0; col < COLUMNS; col++) {
            int x = left + TREE_X + col * colW;
            if (col > 0) graphics.fill(x, top + TREE_Y + 4, x + 1, top + TREE_Y + TREE_H - 4, 0xFF3A3A3A);
            graphics.drawCenteredString(font, headers[col], x + colW / 2, top + TREE_Y + 8, 0xFFC9B98E);
        }

        List<Node> nodes = visibleNodes();
        Map<String, Node> byId = new HashMap<>();
        for (Node node : nodes) byId.put(snapshot.technologies().get(node.index()).id(), node);
        renderDependencyEdges(graphics, byId);

        for (Node node : nodes) {
            ResearchSnapshot.TechnologyEntry entry = snapshot.technologies().get(node.index());
            boolean chosen = node.index() == selected;
            int frame = chosen ? 0xFFB89652 : entry.completed() ? 0xFF557755 : entry.available() ? 0xFF756747 : 0xFF3B3B3B;
            int fill = chosen ? 0xFF51442F : entry.completed() ? 0xFF2E4230 : entry.available() ? 0xFF35322B : 0xFF282828;
            graphics.fill(node.x(), node.y(), node.x() + node.w(), node.y() + node.h(), frame);
            graphics.fill(node.x() + 1, node.y() + 1, node.x() + node.w() - 1, node.y() + node.h() - 1, fill);
            graphics.renderItem(icon(entry.icon()), node.x() + 4, node.y() + 4);
            int color = entry.completed() ? 0xFFA9D18E : entry.available() ? 0xFFFFE2A3 : 0xFF888888;
            graphics.drawString(font, trim(entry.name(), 12), node.x() + 24, node.y() + 5, color, false);
            String state = entry.completed() ? "DONE" : entry.available() ? "READY" : "LOCKED";
            graphics.drawString(font, state, node.x() + 24, node.y() + 15, color, false);
        }
        if (maxScroll() > 0) graphics.drawString(font, "Mouse wheel to scroll technology rows", left + TREE_X + 8, top + TREE_Y + TREE_H - 13, 0xFF888888, false);
    }

    private void renderDependencyEdges(GuiGraphics graphics, Map<String, Node> byId) {
        for (Node target : nodes) {
            ResearchSnapshot.TechnologyEntry targetEntry = snapshot.technologies().get(target.index());
            for (String dependencyId : targetEntry.dependencyIds()) {
                Node source = byId.get(dependencyId);
                if (source == null) continue;
                ResearchSnapshot.TechnologyEntry sourceEntry = snapshot.technologies().get(source.index());
                int color = sourceEntry.completed() ? 0xFF678A67 : 0xFF5B5548;
                drawDependencyEdge(graphics, source, target, color);
            }
        }
    }

    private void drawDependencyEdge(GuiGraphics graphics, Node source, Node target, int color) {
        int sx = source.x() + source.w();
        int sy = source.y() + source.h() / 2;
        int tx = target.x();
        int ty = target.y() + target.h() / 2;
        if (tx <= sx) return;
        int middle = sx + Math.max(4, (tx - sx) / 2);
        graphics.fill(sx, sy - 1, middle + 1, sy + 1, color);
        graphics.fill(middle, Math.min(sy, ty), middle + 2, Math.max(sy, ty) + 1, color);
        graphics.fill(middle, ty - 1, tx, ty + 1, color);
        graphics.fill(tx - 4, ty - 3, tx, ty + 4, color);
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = left + 464, y = top + TREE_Y + 10;
        graphics.drawString(font, "TECHNOLOGY", x, y, 0xFFE7D8B0, false);
        ResearchSnapshot.TechnologyEntry entry = selected >= 0 && selected < snapshot.technologies().size() ? snapshot.technologies().get(selected) : null;
        if (entry == null) {
            graphics.drawWordWrap(font, Component.literal("Select a technology node. Arrows show the prerequisite path: research the node on the left before its descendants become available."), x, y + 22, 166, 0xFFAAAAAA);
            return;
        }
        graphics.renderItem(icon(entry.icon()), x, y + 22);
        graphics.drawString(font, trim(entry.name(), 21), x + 22, y + 25, entry.completed() ? 0xFFA9D18E : 0xFFFFE2A3, false);
        graphics.drawString(font, entry.completed() ? "Completed" : entry.available() ? "Available" : "Locked", x, y + 48,
                entry.completed() ? 0xFFA9D18E : entry.available() ? 0xFFFFE2A3 : 0xFFFF8A8A, false);
        graphics.drawString(font, "Base time: " + duration(entry.durationSeconds()), x, y + 64, 0xFFCCCCCC, false);
        String tier = "Settlement tier gate: " + roman(entry.requiredTier()) + ("EXACT".equalsIgnoreCase(entry.tierMode()) ? " exactly" : "+");
        boolean tierAllowed = "EXACT".equalsIgnoreCase(entry.tierMode())
                ? snapshot.researchTier() == entry.requiredTier()
                : snapshot.researchTier() >= entry.requiredTier();
        graphics.drawString(font, tier, x, y + 80, tierAllowed ? 0xFFCCCCCC : 0xFFFF8A8A, false);
        graphics.drawString(font, "Prerequisites", x, y + 104, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal(entry.dependencies()), x, y + 118, 166, 0xFFAAAAAA);
    }

    private List<Node> visibleNodes() {
        int colW = TREE_W / COLUMNS;
        int[] rowCounts = new int[COLUMNS];
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < snapshot.technologies().size(); i++) {
            ResearchSnapshot.TechnologyEntry entry = snapshot.technologies().get(i);
            int col = Math.min(COLUMNS - 1, Math.max(0, entry.depth()));
            int row = rowCounts[col]++;
            if (row < scroll || row >= scroll + ROWS) continue;
            int x = left + TREE_X + col * colW + 5;
            int y = top + TREE_Y + 26 + (row - scroll) * 29;
            nodes.add(new Node(i, x, y, colW - 10, 25));
        }
        return nodes;
    }

    private int maxScroll() {
        int[] counts = new int[COLUMNS];
        for (ResearchSnapshot.TechnologyEntry entry : snapshot.technologies()) counts[Math.min(COLUMNS - 1, Math.max(0, entry.depth()))]++;
        int maximum = 0;
        for (int count : counts) maximum = Math.max(maximum, count);
        return Math.max(0, maximum - ROWS);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        if (button == 0) for (Node node : visibleNodes()) {
            if (ux >= node.x() && ux <= node.x() + node.w() && uy >= node.y() && uy <= node.y() + node.h()) {
                selected = node.index(); refreshButtons(); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        if (ux >= left + TREE_X && ux <= left + TREE_X + TREE_W && uy >= top + TREE_Y && uy <= top + TREE_Y + TREE_H) {
            if (delta < 0) scroll = Math.min(maxScroll(), scroll + 1); else if (delta > 0) scroll = Math.max(0, scroll - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private ItemStack icon(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw == null ? "" : raw);
        Item item = id == null ? Items.BOOK : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) item = Items.BOOK;
        return new ItemStack(item);
    }

    private static String duration(long seconds) {
        long h = seconds / 3600L, m = (seconds % 3600L) / 60L, s = seconds % 60L;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static String roman(int value) {
        return switch (Math.max(1, Math.min(4, value))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    private static String trim(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…"; }
    private record Node(int index, int x, int y, int w, int h) {}
    @Override public boolean isPauseScreen() { return false; }
}
