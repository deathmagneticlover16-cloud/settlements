package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignDefinitions;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.SubmitCharacterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Three rounds of three server-verifiable trait cards, with a single reroll for the whole character. */
public final class TraitSelectionScreen extends ScaledCampaignScreen implements CharacterDraftScreen {
    private static final int W = 570, H = 330;
    private final CharacterCreationDraftSession session;
    private List<String> choices = List.of();
    private int left, top;

    public TraitSelectionScreen(CharacterCreationDraftSession session) {
        super(Component.literal("Pick Your Traits"));
        this.session = session;
    }

    @Override public CharacterCreationDraftSession draftSession() { return session; }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        rebuildChoices();
        rebuildButtons();
    }

    private void rebuildChoices() {
        if (minecraft == null || minecraft.player == null) return;
        choices = session.choices();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (session.state() == CharacterCreationDraftSession.State.SUBMITTING) return;
        addRenderableWidget(Button.builder(Component.literal("Go Back"), b -> minecraft.setScreen(new CharacterCreationScreen(session)))
                .bounds(left + 22, top + 292, 100, 20).build());
        if (session.completeTraits()) {
            addRenderableWidget(Button.builder(Component.literal("Submit Character"), b -> submit())
                    .bounds(left + 370, top + 292, 178, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Choose traits again"), b -> {
                session.restartTraits(); rebuildChoices(); rebuildButtons();
            }).bounds(left + 183, top + 292, 174, 20).build());
            return;
        }
        for (int i = 0; i < choices.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("Choose"), b -> choose(index))
                    .bounds(left + 22 + i * 180, top + 257, 166, 22).build());
        }
        if (!session.rerollUsed()) {
            addRenderableWidget(Button.builder(Component.literal("Reroll these three (1 left)"), b -> reroll())
                    .bounds(left + 183, top + 292, 204, 20).build());
        }
    }

    private void reroll() {
        if (!session.reroll()) return;
        rebuildChoices();
        rebuildButtons();
    }

    private void choose(int index) {
        if (index < 0 || index >= choices.size()) return;
        if (!session.choose(choices.get(index))) return;
        if (session.completeTraits()) {
            submit();
            return;
        }
        rebuildChoices();
        rebuildButtons();
    }

    private void submit() {
        if (!session.submit()) return;
        CharacterCreationDraftSession.CharacterDraft draft = session.draft();
        SettlementNetwork.CHANNEL.sendToServer(new SubmitCharacterPacket(session.id(),
                draft.first(), draft.middle(), draft.last(), draft.faith(), draft.address(), draft.title(),
                draft.body(), session.selectedTraits(), session.rerollRound()));
        rebuildChoices();
        rebuildButtons();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 48);
        graphics.drawCenteredString(font, "PICK YOUR TRAITS", left + W / 2, top + 12, CAMPAIGN_TITLE);
        graphics.drawCenteredString(font, session.state() == CharacterCreationDraftSession.State.SUBMITTING
                ? "Waiting for server confirmation…" : session.completeTraits() ? "Three traits selected"
                : "Choice " + (session.round() + 1) + " of 3 · choose one of the three cards", left + W / 2, top + 29, 0xFFC9B989);

        for (int i = 0; i < choices.size(); i++) renderCard(graphics, choices.get(i), left + 22 + i * 180, top + 62, uiMouseX, uiMouseY);
        if (!session.selectedTraits().isEmpty()) {
            String names = session.selectedTraits().stream().map(CampaignDefinitions::trait).filter(java.util.Objects::nonNull)
                    .map(CampaignDefinitions.TraitDefinition::name).reduce((a,b) -> a + " · " + b).orElse("");
            graphics.drawCenteredString(font, "Chosen: " + names, left + W / 2, top + 284, 0xFFAAAAAA);
        }
        if (!session.error().isBlank())
            graphics.drawWordWrap(font, Component.literal(session.error()), left + 22, top + 62, W - 44, 0xFFFFC86B);
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
