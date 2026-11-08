package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.FamilySnapshot;
import ee.lastpagans.settlements.network.FamilyActionPacket;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FamilyScreen extends ScaledCampaignScreen {
    private static final int W = 440;
    private static final int H = 270;
    private final FamilySnapshot snapshot;
    private int left;
    private int top;
    private int playerIndex;

    public FamilyScreen(FamilySnapshot snapshot) {
        super(Component.literal("Family and Marriage"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        if (!snapshot.married()) {
            if (snapshot.pendingProposer() == null && !snapshot.playerCandidates().isEmpty()) {
                List<String> players = snapshot.playerCandidates().stream().map(FamilySnapshot.PlayerCandidate::name).toList();
                playerIndex = Math.max(0, Math.min(playerIndex, players.size() - 1));
                addRenderableWidget(new DropdownWidget(left + 226, top + 90, 128, 20, players, playerIndex,
                        value -> playerIndex = Math.max(0, players.indexOf(value))));
                addRenderableWidget(Button.builder(Component.literal("Propose"), button -> proposePlayer())
                        .bounds(left + 360, top + 90, 62, 20).build());
            }
            if (snapshot.pendingProposer() != null) {
                addRenderableWidget(Button.builder(Component.literal("Accept"), button -> send("ACCEPT"))
                        .bounds(left + 226, top + 142, 92, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Reject"), button -> send("REJECT"))
                        .bounds(left + 326, top + 142, 92, 20).build());
            }
        } else {
            Button divorce = addRenderableWidget(Button.builder(Component.literal("End marriage"), button -> send("DIVORCE"))
                    .bounds(left + 24, top + 218, 112, 20).build());
            divorce.active = snapshot.canDivorce();
        }
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 298, top + 238, 120, 18).build());
    }

    private void proposePlayer() {
        if (snapshot.playerCandidates().isEmpty()) return;
        FamilySnapshot.PlayerCandidate target = snapshot.playerCandidates().get(Math.min(playerIndex, snapshot.playerCandidates().size() - 1));
        SettlementNetwork.CHANNEL.sendToServer(new FamilyActionPacket("PROPOSE_PLAYER", "", target.id()));
    }

    private void send(String action) {
        SettlementNetwork.CHANNEL.sendToServer(new FamilyActionPacket(action, "", null));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 40);
        drawCampaignPanel(graphics, left + 12, top + 48, left + 212, top + 244);
        drawCampaignPanel(graphics, left + 218, top + 48, left + 428, top + 220);
        graphics.drawCenteredString(font, "FAMILY — " + snapshot.characterName(), left + W / 2, top + 14, CAMPAIGN_TITLE);

        if (snapshot.married()) {
            graphics.drawString(font, "SPOUSE", left + 24, top + 62, 0xFFE7D8B0, false);
            line(graphics, "Name", snapshot.spouseName(), left + 24, top + 86);
            line(graphics, "Faith", snapshot.spouseFaith(), left + 24, top + 106);
            graphics.drawString(font, "Traits", left + 24, top + 132, 0xFF999999, false);
            graphics.drawWordWrap(font, Component.literal(snapshot.spouseTraits()), left + 24, top + 146, 174, 0xFFFFFFFF);
            graphics.drawString(font, "Effects", left + 24, top + 178, 0xFF999999, false);
            graphics.drawWordWrap(font, Component.literal(snapshot.spouseEffect()), left + 24, top + 192, 174, 0xFFCCCCCC);
            if (!snapshot.canDivorce()) graphics.drawString(font, "Divorce unlocks after 7 days", left + 24, top + 231, 0xFFFFC080, false);
        } else {
            graphics.drawString(font, "No spouse", left + 24, top + 66, 0xFFCCCCCC, false);
            graphics.drawWordWrap(font, Component.literal("Marriage is between player characters. Both characters must be online and unmarried."),
                    left + 24, top + 92, 174, 0xFFAAAAAA);
        }

        graphics.drawString(font, "PLAYER MARRIAGE", left + 228, top + 62, 0xFFE7D8B0, false);
        if (snapshot.married()) {
            graphics.drawWordWrap(font, Component.literal("Your marriage may create a political marriage alliance between your factions."),
                    left + 228, top + 88, 190, 0xFFAAAAAA);
        } else if (snapshot.pendingProposer() != null) {
            graphics.drawWordWrap(font, Component.literal(snapshot.pendingProposerName() + " has proposed marriage."),
                    left + 228, top + 92, 188, 0xFFFFD27A);
        } else if (snapshot.playerCandidates().isEmpty()) {
            graphics.drawWordWrap(font, Component.literal("No eligible player characters are currently online."),
                    left + 228, top + 92, 188, 0xFFAAAAAA);
        } else {
            graphics.drawString(font, "Choose an eligible character", left + 228, top + 78, 0xFFAAAAAA, false);
        }

        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, snapshot.message(), left + W / 2, top + 225, 0xFFFFD27A);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void line(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(font, label + ":", x, y, 0xFF999999, false);
        graphics.drawString(font, value, x + 48, y, 0xFFFFFFFF, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
