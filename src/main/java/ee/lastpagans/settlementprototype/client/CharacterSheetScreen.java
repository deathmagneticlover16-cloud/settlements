package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CharacterSnapshot;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CharacterSheetScreen extends Screen {
    private static final int W = 560;
    private static final int H = 380;
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
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.literal("Edit Dynasty / House"), button ->
                minecraft.setScreen(new CharacterIdentityEditScreen(snapshot, returnToMain)))
                .bounds(left + 176, top + 346, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Family"), button ->
                PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.FAMILY)))
                .bounds(left + 334, top + 346, 82, 20).build());
        String text = returnToMain ? "Return to Main" : "Close";
        addRenderableWidget(Button.builder(Component.literal(text), button -> {
            if (returnToMain) PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN));
            else onClose();
        }).bounds(left + 424, top + 346, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 47, 0xFF3B3023);
        graphics.fill(left + 14, top + 57, left + 164, top + 302, 0xFF242424);
        graphics.fill(left + 174, top + 57, left + 546, top + 171, 0xFF242424);
        graphics.fill(left + 174, top + 181, left + 546, top + 302, 0xFF242424);

        graphics.drawCenteredString(font, snapshot.displayName().toUpperCase(), left + W / 2, top + 11, 0xFFE7D8B0);
        graphics.drawCenteredString(font, snapshot.titleLine(), left + W / 2, top + 29, 0xFFC9B989);
        if (minecraft != null && minecraft.player != null) {
            PortraitRenderer.render(graphics, minecraft.player, left + 23, top + 67, 132, 225, mouseX, mouseY);
        }

        int x = left + 186;
        int y = top + 68;
        line(graphics, "Faith", snapshot.faith(), x, y);
        line(graphics, "Faction", snapshot.faction(), x, y + 16);
        line(graphics, "Faction rank", snapshot.factionRank(), x, y + 32);
        line(graphics, "Dynasty", blank(snapshot.dynasty()), x, y + 48);
        line(graphics, "Main house", blank(snapshot.house()), x, y + 64);
        line(graphics, "Primary seat", blank(snapshot.primarySeat()), x, y + 80);
        line(graphics, "Spouse", blank(snapshot.spouse()), x, y + 96);

        graphics.drawString(font, "CHARACTER TRAITS", x, top + 191, 0xFFE7D8B0, false);
        CharacterSnapshot.TraitEntry hovered = null;
        for (int i = 0; i < Math.min(4, snapshot.traits().size()); i++) {
            CharacterSnapshot.TraitEntry trait = snapshot.traits().get(i);
            int column = i % 2;
            int row = i / 2;
            int cardX = x + column * 179;
            int cardY = top + 211 + row * 40;
            graphics.fill(cardX - 4, cardY - 6, cardX + 170, cardY + 29, 0xFF1D1D1D);
            graphics.renderItem(icon(trait.iconItem()), cardX, cardY - 3);
            int toneColor = switch (trait.tone()) {
                case "POSITIVE" -> 0xFF92D18B;
                case "NEGATIVE" -> 0xFFFF8A80;
                case "MIXED" -> 0xFFFFD27A;
                default -> 0xFFCCCCCC;
            };
            graphics.drawString(font, shorten(trait.name(), 19), cardX + 22, cardY, toneColor, false);
            String effect = trait.effects().isEmpty() ? "No direct modifier" : String.join("; ", trait.effects());
            graphics.drawString(font, shorten(effect, 25), cardX + 22, cardY + 12, 0xFFAAAAAA, false);
            if (mouseX >= cardX - 4 && mouseX <= cardX + 170 && mouseY >= cardY - 6 && mouseY <= cardY + 29) hovered = trait;
        }
        if (snapshot.traits().size() > 4) {
            graphics.drawString(font, "+" + (snapshot.traits().size() - 4) + " more traits", x, top + 289, 0xFF888888, false);
        }

        String holdings = snapshot.holdings().isEmpty() ? "None" : String.join(", ", snapshot.holdings());
        graphics.drawString(font, "Holdings: " + shorten(holdings, 61), left + 18, top + 307, 0xFF999999, false);
        graphics.drawString(font, "Account: " + snapshot.accountName(), left + 18, top + 324, 0xFF666666, false);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hovered.name()));
            tooltip.add(Component.literal(hovered.description()));
            hovered.effects().forEach(effect -> tooltip.add(Component.literal(effect)));
            graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
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
        graphics.drawString(font, shorten(value, 37), x + 88, y, 0xFFFFFFFF, false);
    }

    private static String blank(String value) { return value == null || value.isBlank() ? "None" : value; }
    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
