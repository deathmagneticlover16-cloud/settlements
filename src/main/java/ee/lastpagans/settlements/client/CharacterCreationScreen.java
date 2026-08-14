package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignDefinitions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CharacterCreationScreen extends ScaledCampaignScreen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 286;
    private static final String[] ROMAN_MALE_FIRST = {"Marcus", "Lucius", "Gaius", "Titus", "Publius", "Quintus", "Aulus", "Sextus", "Decimus"};
    private static final String[] ROMAN_FEMALE_FIRST = {"Julia", "Aurelia", "Claudia", "Flavia", "Valeria", "Cornelia", "Aemilia", "Livia", "Marcella"};
    private static final String[] ROMAN_MALE_FAMILY = {"Valerius", "Cassius", "Flavius", "Aemilius", "Aurelius", "Claudius", "Cornelius", "Julius"};

    private int left;
    private int top;
    private EditBox first;
    private EditBox middle;
    private EditBox last;
    private DropdownWidget faith;
    private DropdownWidget address;
    private DropdownWidget title;
    private String warning = "";
    private final CharacterDraft initial;

    public CharacterCreationScreen() { this(null); }
    public CharacterCreationScreen(CharacterDraft initial) {
        super(Component.literal("Create Your Character"));
        this.initial = initial;
    }

    @Override
    protected void init() {
        setupCampaignScale(PANEL_WIDTH, PANEL_HEIGHT);
        left = campaignLeft(PANEL_WIDTH);
        top = campaignTop(PANEL_HEIGHT);

        first = field(left + 170, top + 55, 112, "First name");
        middle = field(left + 288, top + 55, 132, "Middle (optional)");
        last = field(left + 170, top + 83, 250, "Last name");

        if (initial != null) {
            first.setValue(initial.first());
            middle.setValue(initial.middle());
            last.setValue(initial.last());
        }

        addRenderableWidget(Button.builder(Component.literal("Preview Character"), button -> preview())
                .bounds(left + 260, top + 252, 160, 20).build());

        // Lower lists are added first so expanded upper lists render above them.
        title = addRenderableWidget(new DropdownWidget(left + 170, top + 204, 250, 20,
                CampaignDefinitions.PERSONAL_TITLES,
                index(CampaignDefinitions.PERSONAL_TITLES, initial == null ? "" : initial.title()), value -> {}));
        address = addRenderableWidget(new DropdownWidget(left + 170, top + 176, 250, 20,
                CampaignDefinitions.ADDRESSES,
                index(CampaignDefinitions.ADDRESSES, initial == null ? "" : initial.address()), value -> {}));
        faith = addRenderableWidget(new DropdownWidget(left + 170, top + 148, 250, 20,
                CampaignDefinitions.RELIGIONS,
                index(CampaignDefinitions.RELIGIONS, initial == null ? "" : initial.faith()), value -> {}));
        addRenderableWidget(Button.builder(Component.literal("Random Roman Name"), button -> randomRomanName())
                .bounds(left + 170, top + 109, 132, 18).build());
        setInitialFocus(first);
    }

    private int index(List<String> values, String value) {
        int index = values.indexOf(value);
        return index < 0 ? 0 : index;
    }

    private void randomRomanName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean female = "Female".equalsIgnoreCase(address == null ? "" : address.value());
        if (!female && "Neutral".equalsIgnoreCase(address == null ? "" : address.value())) female = random.nextBoolean();
        String family = ROMAN_MALE_FAMILY[random.nextInt(ROMAN_MALE_FAMILY.length)];
        first.setValue(female ? ROMAN_FEMALE_FIRST[random.nextInt(ROMAN_FEMALE_FIRST.length)]
                : ROMAN_MALE_FIRST[random.nextInt(ROMAN_MALE_FIRST.length)]);
        middle.setValue("");
        last.setValue(female && family.endsWith("us") ? family.substring(0, family.length() - 2) + "a" : family);
        warning = "Roman name generated. You can edit it freely.";
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(24);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    private void preview() {
        if (first.getValue().trim().isBlank() || last.getValue().trim().isBlank()) {
            warning = "A first and last name are required.";
            return;
        }
        minecraft.setScreen(new CharacterPreviewScreen(new CharacterDraft(
                first.getValue(), middle.getValue(), last.getValue(), faith.value(), address.value(), title.value()
        )));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (faith.isExpanded() && faith.mouseClicked(uiX, uiY, button)) return true;
        if (address.isExpanded() && address.mouseClicked(uiX, uiY, button)) return true;
        if (title.isExpanded() && title.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (faith.isExpanded() && faith.mouseScrolled(uiX, uiY, delta)) return true;
        if (address.isExpanded() && address.mouseScrolled(uiX, uiY, delta)) return true;
        if (title.isExpanded() && title.mouseScrolled(uiX, uiY, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + 40, 0xFF3B3023);
        graphics.fill(left + 14, top + 50, left + 154, top + 239, 0xFF242424);
        graphics.drawCenteredString(font, "CREATE YOUR CHARACTER", left + PANEL_WIDTH / 2, top + 13, 0xFFE7D8B0);
        graphics.drawCenteredString(font, "Your account, UUID and Recruits faction remain unchanged.", left + PANEL_WIDTH / 2, top + 28, 0xFFAA9D7F);
        graphics.drawCenteredString(font, "Character", left + 84, top + 57, 0xFFE7D8B0);
        if (minecraft != null && minecraft.player != null) {
            PortraitRenderer.render(graphics, minecraft.player, left + 23, top + 70, 122, 157, uiMouseX, uiMouseY);
        }
        graphics.drawString(font, "Name", left + 170, top + 44, 0xFFDDDDDD, false);
        graphics.drawString(font, "Identity", left + 170, top + 132, 0xFFDDDDDD, false);
        if (!warning.isBlank()) graphics.drawCenteredString(font, warning, left + PANEL_WIDTH / 2, top + PANEL_HEIGHT + 5, 0xFFFFC86B);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

    public record CharacterDraft(String first, String middle, String last, String faith, String address, String title) {
        public String displayName() {
            return (first + " " + middle + " " + last).replaceAll("\\s+", " ").trim();
        }
    }
}
