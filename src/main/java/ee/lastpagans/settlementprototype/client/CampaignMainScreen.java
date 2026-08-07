package ee.lastpagans.settlementprototype.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlementprototype.CampaignSnapshot;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CampaignMainScreen extends Screen {
    private static final int W = 480, H = 310;
    private final CampaignSnapshot snapshot;
    private int left, top;
    private BannerRenderer banner;

    public CampaignMainScreen(CampaignSnapshot snapshot) {
        super(Component.literal("Campaign Overview"));
        this.snapshot = snapshot;
        if (!snapshot.factionBanner().isEmpty() && !snapshot.factionId().isBlank()) {
            try { banner = new BannerRenderer(new RecruitsFaction(snapshot.factionId(), snapshot.factionName(), snapshot.factionBanner().copy())); }
            catch (RuntimeException ignored) { banner = null; }
        }
    }

    @Override protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addButton("CHARACTER", 18, 92, OpenCampaignRequestPacket.ScreenTarget.CHARACTER);
        addButton("SETTLEMENT", 132, 92, OpenCampaignRequestPacket.ScreenTarget.SETTLEMENT);
        addButton("FACTION", 246, 92, OpenCampaignRequestPacket.ScreenTarget.FACTION);
        addButton("MILITARY", 360, 92, OpenCampaignRequestPacket.ScreenTarget.MILITARY);
        addButton("DIPLOMACY", 18, 134, OpenCampaignRequestPacket.ScreenTarget.DIPLOMACY);
        addButton("FAMILY", 132, 134, OpenCampaignRequestPacket.ScreenTarget.FAMILY);
        addRenderableWidget(Button.builder(Component.literal("WORLD NEWS"), button -> open(OpenCampaignRequestPacket.ScreenTarget.WORLD_NEWS))
                .bounds(left + 246, top + 134, 108, 34).build());
        addRenderableWidget(Button.builder(Component.literal("NEW SETTLEMENT"), button -> minecraft.setScreen(new SettlementCreationScreen()))
                .bounds(left + 360, top + 134, 108, 34).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose()).bounds(left + 404, top + 282, 60, 18).build());
    }

    private void addButton(String label, int x, int y, OpenCampaignRequestPacket.ScreenTarget target) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> open(target)).bounds(left + x, top + y, 108, 34).build());
    }

    private void open(OpenCampaignRequestPacket.ScreenTarget target) {
        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0181818);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 82, 0xFF3B3023);
        graphics.fill(left + 10, top + 86, left + W - 10, top + 176, 0xFF242424);
        graphics.fill(left + 10, top + 184, left + W - 10, top + 245, 0xFF242424);
        if (minecraft != null && minecraft.player != null) PortraitRenderer.render(graphics, minecraft.player, left + 16, top + 9, 64, 66, mouseX, mouseY);
        if (banner != null) banner.renderBanner(graphics, left + W - 58, top + 7, 20, 40, 22);

        graphics.drawString(font, snapshot.characterName(), left + 94, top + 13, 0xFFFFFFFF, false);
        graphics.drawString(font, snapshot.titleLine(), left + 94, top + 29, 0xFFE7D8B0, false);
        graphics.drawString(font, "Faith: " + snapshot.faith(), left + 94, top + 47, 0xFFCCCCCC, false);
        graphics.drawString(font, "Faction: " + snapshot.factionName(), left + 94, top + 63, 0xFFCCCCCC, false);

        stat(graphics, "Settlements", snapshot.settlementCount(), 22, 193);
        stat(graphics, "Population", snapshot.totalPopulation(), 170, 193);
        stat(graphics, "Wealth", snapshot.totalWealth(), 330, 193);
        stat(graphics, "Manpower", snapshot.availableManpower() + "/" + snapshot.maximumManpower(), 22, 215);
        stat(graphics, "Stability", snapshot.averageStability() + "%", 170, 215);
        stat(graphics, "Food", snapshot.totalFood(), 330, 215);
        graphics.drawString(font, "Selected settlement: " + snapshot.selectedSettlementName(), left + 22, top + 235, 0xFFB8B0A0, false);

        if (snapshot.pendingDiplomacy() + snapshot.pendingMarriage() + snapshot.activeWars() > 0) {
            graphics.drawString(font, "Notices: treaties " + snapshot.pendingDiplomacy() + " · marriage " + snapshot.pendingMarriage()
                    + " · wars " + snapshot.activeWars(), left + 16, top + 263, 0xFFFFD27A, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void stat(GuiGraphics graphics, String name, Object value, int x, int y) {
        graphics.drawString(font, name + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
