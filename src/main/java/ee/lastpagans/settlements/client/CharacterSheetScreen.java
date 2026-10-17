package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CharacterSnapshot;
import ee.lastpagans.settlements.network.FamilyActionPacket;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CharacterSheetScreen extends ScaledCampaignScreen {
    private static final int W = 580;
    private static final int H = 410;
    private final CharacterSnapshot snapshot;
    private final boolean returnToMain;
    private int left;
    private int top;

    public CharacterSheetScreen(CharacterSnapshot snapshot, boolean returnToMain) {
        super(Component.literal("Character Sheet"));
        this.snapshot = snapshot;
        this.returnToMain = returnToMain;
    }

    @Override
    protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);

        addRenderableWidget(Button.builder(Component.literal("Donate to Faith"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new FamilyActionPacket("DONATE_FAITH", "", null)))
                .bounds(left + 18, top + 376, 135, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Identity"), button ->
                minecraft.setScreen(new CharacterIdentityEditScreen(snapshot, returnToMain)))
                .bounds(left + 159, top + 376, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Main Seat"), button ->
                minecraft.setScreen(new MainSeatSelectionScreen(snapshot, returnToMain)))
                .bounds(left + 270, top + 376, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Family"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.FAMILY)))
                .bounds(left + 386, top + 376, 72, 20).build());
        String text = returnToMain ? "Return to Main" : "Close";
        addRenderableWidget(Button.builder(Component.literal(text), button -> {
            if (returnToMain) SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN));
            else onClose();
        }).bounds(left + 464, top + 376, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 47);
        drawCampaignPanel(graphics, left + 14, top + 57, left + 164, top + 330);
        drawCampaignPanel(graphics, left + 174, top + 57, left + 566, top + 198);
        drawCampaignPanel(graphics, left + 174, top + 208, left + 566, top + 330);

        graphics.drawCenteredString(font, snapshot.displayName().toUpperCase(), left + W / 2, top + 11, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, snapshot.titleLine(), left + W / 2, top + 29, 0xFFC9B989);
        if (minecraft != null && minecraft.player != null) {
            PortraitRenderer.render(graphics, minecraft.player, left + 23, top + 67, 132, 253, uiMouseX, uiMouseY);
        }

        int x = left + 186;
        int y = top + 68;
        line(graphics, "Faith", snapshot.faith(), x, y);
        line(graphics, "Devotion", snapshot.devotion() + "/100 — " + snapshot.devotionTier(), x, y + 16);
        String reliability = snapshot.reliabilityTracked()
                ? signed(snapshot.reliability()) + " — " + snapshot.reliabilityTier()
                : "Independent";
        line(graphics, "Reliability", reliability, x, y + 32);
        line(graphics, "Faction", snapshot.faction(), x, y + 48);
        line(graphics, "Faction rank", snapshot.factionRank(), x, y + 64);
        line(graphics, "Main house", blank(snapshot.house()), x, y + 80);
        line(graphics, "Main seat", blank(snapshot.primarySeat()), x, y + 96);
        line(graphics, "Spouse", blank(snapshot.spouse()), x, y + 112);

        graphics.drawString(font, "CHARACTER TRAITS", x, top + 218, 0xFFE7D8B0, false);
        CharacterSnapshot.TraitEntry hovered = null;
        for (int i = 0; i < Math.min(4, snapshot.traits().size()); i++) {
            CharacterSnapshot.TraitEntry trait = snapshot.traits().get(i);
            int column = i % 2;
            int row = i / 2;
            int cardX = x + column * 189;
            int cardY = top + 238 + row * 40;
            graphics.fill(cardX - 4, cardY - 6, cardX + 180, cardY + 29, 0xFF1D1D1D);
            graphics.renderItem(icon(trait.iconItem()), cardX, cardY - 3);
            int toneColor = switch (trait.tone()) {
                case "POSITIVE" -> 0xFF92D18B;
                case "NEGATIVE" -> 0xFFFF8A80;
                case "MIXED" -> 0xFFFFD27A;
                default -> 0xFFCCCCCC;
            };
            graphics.drawString(font, ellipsize(trait.name(), 150), cardX + 22, cardY, toneColor, false);
            String effect = trait.effects().isEmpty() ? "No direct modifier" : String.join("; ", trait.effects());
            graphics.drawString(font, ellipsize(effect, 150), cardX + 22, cardY + 12, CAMPAIGN_MUTED, false);
            if (uiMouseX >= cardX - 4 && uiMouseX <= cardX + 180 && uiMouseY >= cardY - 6 && uiMouseY <= cardY + 29) hovered = trait;
        }
        if (snapshot.traits().size() > 4) {
            graphics.drawString(font, "+" + (snapshot.traits().size() - 4) + " more traits", x, top + 316, 0xFF888888, false);
        }

        String holdings = snapshot.holdings().isEmpty()
                ? "None"
                : snapshot.holdings().stream().map(CharacterSnapshot.HoldingEntry::display).collect(Collectors.joining(", "));
        graphics.drawString(font, "Holdings: " + scrollingText(holdings, 490), left + 18, top + 340, 0xFF999999, false);
        graphics.drawString(font, "Account: " + snapshot.accountName(), left + 18, top + 357, 0xFF666666, false);

        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hovered.name()));
            tooltip.add(Component.literal(hovered.description()));
            hovered.effects().forEach(effect -> tooltip.add(Component.literal(effect)));
            graphics.renderTooltip(font, tooltip, Optional.empty(), uiMouseX, uiMouseY);
        }
    }

    private ItemStack icon(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return new ItemStack(Items.BOOK);
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? new ItemStack(Items.BOOK) : new ItemStack(item);
    }

    private void line(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(font, label + ":", x, y, 0xFF999999, false);
        graphics.drawString(font, ellipsize(value, 280), x + 94, y, CAMPAIGN_TEXT, false);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String blank(String value) { return value == null || value.isBlank() ? "None" : value; }
    @Override public boolean isPauseScreen() { return false; }
}
