package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.FamilySnapshot;
import ee.lastpagans.settlementprototype.network.FamilyActionPacket;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FamilyScreen extends Screen {
    private static final int W = 500;
    private static final int H = 320;
    private final FamilySnapshot snapshot;
    private int left;
    private int top;
    private DropdownWidget candidateDrop;
    private DropdownWidget genderDrop;
    private int candidateIndex;

    public FamilyScreen(FamilySnapshot snapshot) {
        super(Component.literal("Family and Marriage"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        if (!snapshot.married()) {
            List<String> genders = List.of("Any", "Female", "Male");
            int genderIndex = snapshot.preferredGender().equalsIgnoreCase("FEMALE") ? 1 : snapshot.preferredGender().equalsIgnoreCase("MALE") ? 2 : 0;
            genderDrop = addRenderableWidget(new DropdownWidget(left + 244, top + 59, 92, 20, genders, genderIndex, value ->
                    PrototypeNetwork.CHANNEL.sendToServer(new FamilyActionPacket("SET_GENDER", value, null))));
            List<String> names = snapshot.candidates().stream().map(FamilySnapshot.Candidate::name).toList();
            candidateIndex = Math.max(0, Math.min(candidateIndex, Math.max(0, names.size() - 1)));
            if (!names.isEmpty()) candidateDrop = addRenderableWidget(new DropdownWidget(left + 342, top + 59, 140, 20, names, candidateIndex,
                    value -> candidateIndex = Math.max(0, names.indexOf(value))));
            addRenderableWidget(Button.builder(Component.literal("Marry selected candidate"), button -> marrySelected())
                    .bounds(left + 244, top + 236, 168, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Reject"), button -> rejectSelected())
                    .bounds(left + 418, top + 236, 64, 20).build());
            Button refresh = addRenderableWidget(Button.builder(Component.literal("Generate new candidates"), button -> send("REFRESH"))
                    .bounds(left + 318, top + 292, 164, 18).build());
            refresh.active = snapshot.canRefresh();
            if (snapshot.pendingProposer() != null) {
                addRenderableWidget(Button.builder(Component.literal("Accept player proposal"), button -> send("ACCEPT"))
                        .bounds(left + 18, top + 226, 150, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Reject"), button -> send("REJECT"))
                        .bounds(left + 174, top + 226, 60, 20).build());
            }
        } else {
            Button divorce = addRenderableWidget(Button.builder(Component.literal("End marriage"), button -> send("DIVORCE"))
                    .bounds(left + 18, top + 226, 120, 20).build());
            divorce.active = snapshot.canDivorce();
        }
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + 18, top + 292, 120, 18).build());
    }

    private void marrySelected() {
        if (snapshot.candidates().isEmpty()) return;
        FamilySnapshot.Candidate candidate = snapshot.candidates().get(Math.min(candidateIndex, snapshot.candidates().size() - 1));
        PrototypeNetwork.CHANNEL.sendToServer(new FamilyActionPacket("MARRY_NPC", candidate.id(), null));
    }

    private void rejectSelected() {
        if (snapshot.candidates().isEmpty()) return;
        FamilySnapshot.Candidate candidate = snapshot.candidates().get(Math.min(candidateIndex, snapshot.candidates().size() - 1));
        PrototypeNetwork.CHANNEL.sendToServer(new FamilyActionPacket("REJECT_CANDIDATE", candidate.id(), null));
    }

    private void send(String action) {
        PrototypeNetwork.CHANNEL.sendToServer(new FamilyActionPacket(action, "", null));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (genderDrop != null && genderDrop.isExpanded() && genderDrop.mouseClicked(x, y, button)) return true;
        if (candidateDrop != null && candidateDrop.isExpanded() && candidateDrop.mouseClicked(x, y, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (genderDrop != null && genderDrop.isExpanded() && genderDrop.mouseScrolled(x, y, delta)) return true;
        if (candidateDrop != null && candidateDrop.isExpanded() && candidateDrop.mouseScrolled(x, y, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 40, 0xFF3B3023);
        graphics.fill(left + 12, top + 48, left + 236, top + 216, 0xFF242424);
        graphics.fill(left + 242, top + 48, left + 488, top + 228, 0xFF242424);
        graphics.drawCenteredString(font, "FAMILY — " + snapshot.characterName(), left + W / 2, top + 14, 0xFFE7D8B0);

        if (snapshot.married()) {
            graphics.drawString(font, "SPOUSE", left + 22, top + 60, 0xFFE7D8B0, false);
            line(graphics, "Name", snapshot.spouseName(), left + 22, top + 82);
            line(graphics, "Faith", snapshot.spouseFaith(), left + 22, top + 100);
            line(graphics, "Culture", snapshot.spouseCulture(), left + 22, top + 118);
            graphics.drawString(font, "Traits", left + 22, top + 141, 0xFF999999, false);
            graphics.drawWordWrap(font, Component.literal(snapshot.spouseTraits()), left + 72, top + 141, 150, 0xFFFFFFFF);
            graphics.drawString(font, "Effects", left + 22, top + 177, 0xFF999999, false);
            graphics.drawWordWrap(font, Component.literal(snapshot.spouseEffect()), left + 72, top + 177, 150, 0xFFCCCCCC);
            if (!snapshot.canDivorce()) graphics.drawString(font, "Divorce unlocks after 7 days", left + 22, top + 211, 0xFFFFC080, false);
        } else {
            graphics.drawString(font, "No spouse", left + 22, top + 64, 0xFFCCCCCC, false);
            if (snapshot.pendingProposer() != null) {
                graphics.drawString(font, "Player proposal", left + 22, top + 92, 0xFFE7D8B0, false);
                graphics.drawWordWrap(font, Component.literal(snapshot.pendingProposerName() + " has proposed marriage."),
                        left + 22, top + 110, 198, 0xFFFFD27A);
            } else {
                graphics.drawWordWrap(font, Component.literal(
                        "AI spouse candidates are generated from cultural first-name and family-name pools. Candidate sets persist for seven Minecraft days."
                ), left + 22, top + 92, 198, 0xFFAAAAAA);
            }
        }

        graphics.drawString(font, "AI SPOUSE CANDIDATES", left + 252, top + 42, 0xFFE7D8B0, false);
        if (!snapshot.married() && !snapshot.candidates().isEmpty()) {
            FamilySnapshot.Candidate candidate = snapshot.candidates().get(Math.min(candidateIndex, snapshot.candidates().size() - 1));
            int x = left + 252;
            int y = top + 91;
            line(graphics, "Gender", candidate.gender(), x, y);
            line(graphics, "Faith", candidate.faith(), x, y + 18);
            line(graphics, "Culture", candidate.culture(), x, y + 36);
            graphics.drawString(font, "Traits", x, y + 58, 0xFF999999, false);
            graphics.drawWordWrap(font, Component.literal(candidate.traits()), x + 54, y + 58, 166, 0xFFFFFFFF);
            graphics.drawString(font, "Capital effects", x, y + 97, 0xFF999999, false);
            graphics.drawWordWrap(font, Component.literal(candidate.effect()), x, y + 113, 218, 0xFFCCCCCC);
            graphics.drawString(font, "Candidate refresh: day " + snapshot.nextRefreshDay(), x, top + 211,
                    snapshot.canRefresh() ? 0xFF92D18B : 0xFF888888, false);
        } else if (snapshot.married()) {
            graphics.drawWordWrap(font, Component.literal("You are already married. Candidate generation is paused until the marriage ends."),
                    left + 252, top + 76, 220, 0xFFAAAAAA);
        }

        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, snapshot.message(), left + W / 2, top + 272, 0xFFFFD27A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(font, label + ":", x, y, 0xFF999999, false);
        graphics.drawString(font, value, x + 58, y, 0xFFFFFFFF, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
