package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignDefinitions;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.character.CharacterGender;
import ee.lastpagans.settlements.client.wildfire.ClientCharacterBodyState;
import ee.lastpagans.settlements.client.wildfire.SettlementsWildfirePreviewSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CharacterCreationScreen extends ScaledCampaignScreen implements CharacterDraftScreen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 380;
    private static final List<String> GENDERS = List.of("Male", "Female", "Other");

    private int left;
    private int top;
    private EditBox first;
    private EditBox middle;
    private EditBox last;
    private DropdownWidget faith;
    private DropdownWidget title;
    private DropdownWidget gender;
    private Button breastsNo;
    private Button breastsYes;
    private Button breastsEdit;
    private String warning = "";
    private final CharacterCreationDraftSession session;

    public CharacterCreationScreen() { this(ClientCharacterBodyState.draftSession()); }
    public CharacterCreationScreen(CharacterCreationDraftSession session) {
        super(Component.literal("Create Your Character"));
        this.session = session;
    }

    @Override public CharacterCreationDraftSession draftSession() { return session; }

    @Override
    protected void init() {
        setupCampaignScale(PANEL_WIDTH, PANEL_HEIGHT);
        left = campaignLeft(PANEL_WIDTH);
        top = campaignTop(PANEL_HEIGHT);
        CharacterCreationDraftSession.CharacterDraft draft = session.draft();

        first = field(left + 170, top + 55, 112, "First name");
        middle = field(left + 288, top + 55, 132, "Middle (optional)");
        last = field(left + 170, top + 83, 250, "Last name");

        first.setValue(draft.first());
        middle.setValue(draft.middle());
        last.setValue(draft.last());

        addRenderableWidget(Button.builder(Component.literal("Preview Character"), button -> preview())
                .bounds(left + 260, top + 346, 160, 20).build());

        breastsNo = addRenderableWidget(Button.builder(Component.literal("No"), button -> setBreasts(false))
                .bounds(left + 250, top + 274, 52, 20).build());
        breastsYes = addRenderableWidget(Button.builder(Component.literal("Yes"), button -> setBreasts(true))
                .bounds(left + 306, top + 274, 52, 20).build());
        breastsEdit = addRenderableWidget(Button.builder(Component.literal("Edit…"), button -> editBreasts())
                .bounds(left + 362, top + 274, 58, 20).build());

        // Lower lists are added first so expanded upper lists render above them.
        gender = addRenderableWidget(new DropdownWidget(left + 250, top + 246, 170, 20, GENDERS,
                index(GENDERS, body().gender().displayName()), this::setGender));
        faith = addRenderableWidget(new DropdownWidget(left + 170, top + 162, 250, 20,
                CampaignDefinitions.RELIGIONS,
                index(CampaignDefinitions.RELIGIONS, draft.faith()), value -> saveIdentity()));
        title = addRenderableWidget(new DropdownWidget(left + 170, top + 190, 250, 20,
                CampaignDefinitions.PERSONAL_TITLES,
                index(CampaignDefinitions.PERSONAL_TITLES, draft.title()), value -> saveIdentity()));
        first.setResponder(value -> saveIdentity());
        middle.setResponder(value -> saveIdentity());
        last.setResponder(value -> saveIdentity());
        warning = session.error();
        refreshBodyButtons();
        setInitialFocus(first);
    }

    private int index(List<String> values, String value) {
        int index = values.indexOf(value);
        return index < 0 ? 0 : index;
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
        saveIdentity();
        minecraft.setScreen(new CharacterPreviewScreen(session));
    }

    private void saveIdentity() {
        session.identity(first.getValue(), middle.getValue(), last.getValue(), faith.value(), body().gender().displayName(), title.value());
    }

    private CharacterBodyData body() { return session.draft().body(); }

    private void setGender(String value) {
        CharacterGender selected = CharacterGender.parse(value);
        if (selected != null) session.gender(selected);
        refreshBodyButtons();
    }

    private void setBreasts(boolean enabled) {
        session.breasts(enabled);
        refreshBodyButtons();
    }

    private void editBreasts() {
        saveIdentity();
        SettlementsWildfirePreviewSession.begin(this, session);
    }

    private void refreshBodyButtons() {
        if (breastsNo == null) return;
        boolean supported = body().gender() != CharacterGender.MALE;
        boolean enabled = supported && body().breasts().enabled();
        breastsNo.setMessage(Component.literal(enabled ? "No" : "No ✓"));
        breastsYes.setMessage(Component.literal(enabled ? "Yes ✓" : "Yes"));
        breastsYes.active = supported;
        breastsEdit.active = supported && enabled;
        breastsEdit.visible = enabled;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (gender.isExpanded() && gender.mouseClicked(uiX, uiY, button)) return true;
        if (faith.isExpanded() && faith.mouseClicked(uiX, uiY, button)) return true;
        if (title.isExpanded() && title.mouseClicked(uiX, uiY, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        double uiX = campaignMouseX(x), uiY = campaignMouseY(y);
        if (gender.isExpanded() && gender.mouseScrolled(uiX, uiY, delta)) return true;
        if (faith.isExpanded() && faith.mouseScrolled(uiX, uiY, delta)) return true;
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
        graphics.fill(left + 14, top + 50, left + 154, top + 303, 0xFF242424);
        graphics.drawCenteredString(font, "CREATE YOUR CHARACTER", left + PANEL_WIDTH / 2, top + 13, 0xFFE7D8B0);
        graphics.drawCenteredString(font, "Your account, UUID and Recruits faction remain unchanged.", left + PANEL_WIDTH / 2, top + 28, 0xFFAA9D7F);
        graphics.drawCenteredString(font, "Character", left + 84, top + 57, 0xFFE7D8B0);
        if (minecraft != null && minecraft.player != null) {
            SettlementsWildfirePreviewSession.renderDraft(session, () ->
                    PortraitRenderer.render(graphics, minecraft.player, left + 23, top + 70, 122, 221, uiMouseX, uiMouseY));
        }
        graphics.drawString(font, "Name", left + 170, top + 44, 0xFFDDDDDD, false);
        graphics.drawString(font, "Identity", left + 170, top + 132, 0xFFDDDDDD, false);
        graphics.drawString(font, "Faith", left + 170, top + 151, 0xFF888888, false);
        graphics.drawString(font, "Title", left + 170, top + 179, 0xFF888888, false);
        graphics.drawString(font, "Body", left + 170, top + 232, 0xFFDDDDDD, false);
        graphics.drawString(font, "Gender", left + 170, top + 252, 0xFFAAAAAA, false);
        graphics.drawString(font, "Boobs?", left + 170, top + 280, 0xFFAAAAAA, false);
        if (body().gender() == CharacterGender.MALE && uiMouseX >= left + 306 && uiMouseX <= left + 420
                && uiMouseY >= top + 274 && uiMouseY <= top + 294)
            graphics.renderTooltip(font, Component.literal("Wildfire's Male body type does not support breasts."), uiMouseX, uiMouseY);
        if (!warning.isBlank()) graphics.drawWordWrap(font, Component.literal(warning), left + 170, top + 303, 250, 0xFFFFC86B);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

}
