package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CampaignDefinitions;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.SubmitCharacterPacket;
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

/** Three rounds of three server-verifiable trait cards, with a single reroll for the whole character. */
public final class TraitSelectionScreen extends ScaledCampaignScreen {
    private static final int W = 570, H = 330;
    private final CharacterCreationScreen.CharacterDraft draft;
    private final List<String> selected;
    private int round;
    private boolean rerollUsed;
    private int rerollRound;
    private List<String> choices = List.of();
    private int left, top;

    public TraitSelectionScreen(CharacterCreationScreen.CharacterDraft draft) {
        this(draft, new ArrayList<>(), 0, false, -1);
    }

    private TraitSelectionScreen(CharacterCreationScreen.CharacterDraft draft, List<String> selected, int round,
                                 boolean rerollUsed, int rerollRound) {
        super(Component.literal("Pick Your Traits"));
        this.draft = draft;
        this.selected = new ArrayList<>(selected);
        this.round = round;
        this.rerollUsed = rerollUsed;
        this.rerollRound = rerollRound;
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        rebuildChoices();
        rebuildButtons();
    }

    private void rebuildChoices() {
        if (minecraft == null || minecraft.player == null) return;
        choices = CampaignDefinitions.traitChoices(minecraft.player.getUUID(), draft.displayName(), round,
                rerollRound == round, selected);
    }

    private void rebuildButtons() {
        clearWidgets();
        for (int i = 0; i < choices.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("Choose"), b -> choose(index))
                    .bounds(left + 22 + i * 180, top + 257, 166, 22).build());
        }
        if (!rerollUsed) {
            addRenderableWidget(Button.builder(Component.literal("Reroll these three (1 left)"), b -> reroll())
                    .bounds(left + 183, top + 292, 204, 20).build());
        }
    }

    private void reroll() {
        if (rerollUsed) return;
        rerollUsed = true;
        rerollRound = round;
        rebuildChoices();
        rebuildButtons();
    }

    private void choose(int index) {
        if (index < 0 || index >= choices.size()) return;
        selected.add(choices.get(index));
        if (selected.size() >= 3) {
            PrototypeNetwork.CHANNEL.sendToServer(new SubmitCharacterPacket(
                    draft.first(), draft.middle(), draft.last(), draft.faith(), draft.address(), draft.title(),
                    selected, rerollRound));
            return;
        }
        round++;
        rebuildChoices();
        rebuildButtons();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 48, 0xFF3B3023);
        graphics.drawCenteredString(font, "PICK YOUR TRAITS", left + W / 2, top + 12, 0xFFE7D8B0);
        graphics.drawCenteredString(font, "Choice " + (round + 1) + " of 3 · choose one of the three cards", left + W / 2, top + 29, 0xFFC9B989);

        for (int i = 0; i < choices.size(); i++) renderCard(graphics, choices.get(i), left + 22 + i * 180, top + 62, uiMouseX, uiMouseY);
        if (!selected.isEmpty()) {
            String names = selected.stream().map(CampaignDefinitions::trait).filter(java.util.Objects::nonNull)
                    .map(CampaignDefinitions.TraitDefinition::name).reduce((a,b) -> a + " · " + b).orElse("");
            graphics.drawCenteredString(font, "Chosen: " + names, left + W / 2, top + 284, 0xFFAAAAAA);
        }
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderCard(GuiGraphics graphics, String id, int x, int y, int mouseX, int mouseY) {
        CampaignDefinitions.TraitDefinition trait = CampaignDefinitions.trait(id);
        if (trait == null) return;
        graphics.fill(x, y, x + 166, y + 184, 0xFF292929);
        graphics.fill(x + 2, y + 2, x + 164, y + 31, 0xFF413729);
        ResourceLocation iconId = ResourceLocation.tryParse(trait.iconItem());
        Item item = iconId == null ? Items.BOOK : BuiltInRegistries.ITEM.get(iconId);
        if (item == Items.AIR) item = Items.BOOK;
        graphics.renderItem(new ItemStack(item), x + 10, y + 8);
        graphics.drawString(font, trait.name(), x + 32, y + 12, tone(trait.tone()), false);
        graphics.drawWordWrap(font, Component.literal(trait.description()), x + 10, y + 43, 146, 0xFFCCCCCC);
        graphics.drawString(font, "Effects", x + 10, y + 112, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal(trait.effectsText()), x + 10, y + 127, 146, 0xFFAAAAAA);
        if (mouseX >= x && mouseX <= x + 166 && mouseY >= y && mouseY <= y + 184)
            graphics.renderTooltip(font, Component.literal(trait.effectsText()), mouseX, mouseY);
    }

    private int tone(CampaignDefinitions.TraitTone tone) {
        return switch (tone) {
            case POSITIVE -> 0xFFA9D18E;
            case NEGATIVE -> 0xFFFF9B8E;
            case MIXED -> 0xFFFFD27A;
            default -> 0xFFFFFFFF;
        };
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
