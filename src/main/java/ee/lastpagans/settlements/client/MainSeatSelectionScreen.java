package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CharacterSnapshot;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.SetMainSeatPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MainSeatSelectionScreen extends ScaledCampaignScreen {
    private static final int W = 470;
    private static final int H = 310;
    private static final int PAGE_SIZE = 7;
    private final CharacterSnapshot snapshot;
    private final boolean returnToMain;
    private int page;
    private int left;
    private int top;

    public MainSeatSelectionScreen(CharacterSnapshot snapshot, boolean returnToMain) {
        super(Component.literal("Choose Main Seat"));
        this.snapshot = snapshot;
        this.returnToMain = returnToMain;
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        List<CharacterSnapshot.HoldingEntry> holdings = snapshot.holdings();
        int pages = Math.max(1, (holdings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        int end = Math.min(holdings.size(), start + PAGE_SIZE);

        for (int i = start; i < end; i++) {
            CharacterSnapshot.HoldingEntry holding = holdings.get(i);
            String suffix = holding.settlementName().equals(snapshot.primarySeat()) ? "  [CURRENT]" : "";
            addRenderableWidget(Button.builder(Component.literal(holding.display() + suffix), button ->
                    SettlementNetwork.CHANNEL.sendToServer(new SetMainSeatPacket(holding.settlementId(), returnToMain)))
                    .bounds(left + 40, top + 79 + (i - start) * 27, W - 80, 20).build());
        }

        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀ Previous"), button -> { page--; rebuildButtons(); })
                    .bounds(left + 40, top + 270, 105, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Next ▶"), button -> { page++; rebuildButtons(); })
                    .bounds(left + 153, top + 270, 105, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), button ->
                minecraft.setScreen(new CharacterSheetScreen(snapshot, returnToMain)))
                .bounds(left + W - 145, top + 270, 105, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 45);
        graphics.drawCenteredString(font, "CHOOSE MAIN SEAT", left + W / 2, top + 14, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, "Current: " + (snapshot.primarySeat().isBlank() ? "None" : snapshot.primarySeat()), left + W / 2, top + 53, 0xFFC8C0B0);
        if (snapshot.holdings().isEmpty()) {
            graphics.drawCenteredString(font, "You do not own a settlement that can become your Main Seat.", left + W / 2, top + 118, 0xFFAAAAAA);
        } else {
            graphics.drawCenteredString(font, "Only settlements you personally own are eligible.", left + W / 2, top + 65, 0xFF888888);
        }
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean isPauseScreen() { return false; }
}
