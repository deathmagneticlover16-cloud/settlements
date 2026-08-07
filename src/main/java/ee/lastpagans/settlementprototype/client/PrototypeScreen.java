package ee.lastpagans.settlementprototype.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlementprototype.PrototypeManager;
import ee.lastpagans.settlementprototype.PrototypeSnapshot;
import ee.lastpagans.settlementprototype.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import ee.lastpagans.settlementprototype.network.SettlementActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class PrototypeScreen extends Screen {
    private static final int W = 540, H = 330;
    private PrototypeSnapshot snapshot;
    private int left, top;
    private Tab tab = Tab.OVERVIEW;
    private BannerRenderer factionBanner, settlementBanner;

    public PrototypeScreen(PrototypeSnapshot snapshot) {
        super(Component.literal("Settlement"));
        this.snapshot = snapshot;
        rebuildBanners();
    }

    @Override protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        buildWidgets();
    }

    private void buildWidgets() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        String[] labels = {"Overview", "Residents", "Treasury", "Effects", "Manage"};
        for (int i = 0; i < labels.length; i++) {
            Tab target = Tab.values()[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> {
                tab = target;
                buildWidgets();
            }).bounds(left + 12 + i * 103, top + 68, 98, 20).build());
        }
        switch (tab) {
            case OVERVIEW -> overviewButtons();
            case RESIDENTS -> residentButtons();
            case TREASURY -> treasuryButtons();
            case EFFECTS -> effectButtons();
            case MANAGEMENT -> managementButtons();
        }

        addRenderableWidget(Button.builder(Component.literal("◀ Previous"), button -> switchSettlement(false))
                .bounds(left + 14, top + 301, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Next ▶"), button -> switchSettlement(true))
                .bounds(left + 112, top + 301, 78, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Military"), button -> open(OpenCampaignRequestPacket.ScreenTarget.MILITARY))
                .bounds(left + 322, top + 301, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button -> open(OpenCampaignRequestPacket.ScreenTarget.MAIN))
                .bounds(left + 420, top + 301, 106, 18).build());
    }

    private void overviewButtons() {
        addRenderableWidget(Button.builder(Component.literal("Character"), button -> send(PrototypeManager.SettlementAction.OPEN_CHARACTER))
                .bounds(left + 20, top + 260, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> send(PrototypeManager.SettlementAction.REFRESH))
                .bounds(left + 126, top + 260, 80, 20).build());
    }

    private void residentButtons() {
        addRenderableWidget(Button.builder(Component.literal("Claim Settler Contract"), button -> send(PrototypeManager.SettlementAction.CLAIM_CONTRACT))
                .bounds(left + 20, top + 260, 170, 20).build());
    }

    private void treasuryButtons() {
        addRenderableWidget(Button.builder(Component.literal("Deposit Held Food"), button -> send(PrototypeManager.SettlementAction.DEPOSIT_FOOD))
                .bounds(left + 20, top + 232, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Deposit Held Silver"), button -> send(PrototypeManager.SettlementAction.DEPOSIT_SILVER))
                .bounds(left + 180, top + 232, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Withdraw 1 Silver"), button -> send(PrototypeManager.SettlementAction.WITHDRAW_SILVER_ONE))
                .bounds(left + 20, top + 260, 154, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Withdraw 10 Silver"), button -> send(PrototypeManager.SettlementAction.WITHDRAW_SILVER_TEN))
                .bounds(left + 180, top + 260, 154, 20).build());
    }

    private void effectButtons() {
        addRenderableWidget(Button.builder(Component.literal("Refresh effects"), button -> send(PrototypeManager.SettlementAction.REFRESH))
                .bounds(left + 20, top + 260, 116, 20).build());
    }

    private void managementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Set held banner"), button -> send(PrototypeManager.SettlementAction.SET_CUSTOM_BANNER))
                .bounds(left + 20, top + 244, 132, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Use faction banner"), button -> send(PrototypeManager.SettlementAction.USE_FACTION_BANNER))
                .bounds(left + 158, top + 244, 142, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No banner"), button -> send(PrototypeManager.SettlementAction.CLEAR_BANNER))
                .bounds(left + 306, top + 244, 110, 20).build());
    }

    private void open(OpenCampaignRequestPacket.ScreenTarget target) {
        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target));
    }

    private void send(PrototypeManager.SettlementAction action) {
        PrototypeNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(), action));
    }

    private void switchSettlement(boolean next) {
        PrototypeNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(),
                next ? PrototypeManager.SettlementAction.SELECT_NEXT : PrototypeManager.SettlementAction.SELECT_PREVIOUS,
                "SETTLEMENT", 0));
    }

    public void updateSnapshot(PrototypeSnapshot value) {
        snapshot = value;
        rebuildBanners();
        buildWidgets();
    }

    private void rebuildBanners() {
        factionBanner = null;
        settlementBanner = null;
        try {
            if (!snapshot.factionId().isBlank() && !snapshot.factionBanner().isEmpty()) {
                factionBanner = new BannerRenderer(new RecruitsFaction(snapshot.factionId(), snapshot.factionName(), snapshot.factionBanner().copy()));
            }
            if (!snapshot.settlementBannerStack().isEmpty()) {
                ItemStack stack = ItemStack.of(snapshot.settlementBannerStack());
                if (!stack.isEmpty()) {
                    settlementBanner = new BannerRenderer(new RecruitsFaction("settlement", "Settlement", new net.minecraft.nbt.CompoundTag()));
                    settlementBanner.setBannerItem(stack);
                }
            }
        } catch (RuntimeException ignored) {
            factionBanner = null;
            settlementBanner = null;
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + W, top + H, 0xF0171717);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 62, 0xFF3B3023);
        graphics.fill(left + 12, top + 96, left + W - 12, top + 290, 0xFF242424);

        BannerRenderer large = "CUSTOM".equals(snapshot.bannerMode()) && settlementBanner != null
                ? settlementBanner : "FACTION".equals(snapshot.bannerMode()) ? factionBanner : null;
        if (large != null) large.renderBanner(graphics, left + 17, top + 7, 20, 40, 22);
        if (settlementBanner != null && factionBanner != null && large == settlementBanner) {
            factionBanner.renderBanner(graphics, left + 56, top + 17, 20, 40, 10);
        }

        graphics.drawString(font, fit(snapshot.settlementName().toUpperCase(Locale.ROOT), 360), left + 86, top + 13, 0xFFFFFFFF, false);
        graphics.drawString(font, snapshot.rank() + " · " + snapshot.rankSubtitle(), left + 86, top + 31, 0xFFE7D8B0, false);
        graphics.drawString(font, fit(snapshot.factionName() + " · " + pretty(snapshot.role()), 360), left + 86, top + 47, 0xFFB8B0A0, false);

        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case RESIDENTS -> renderResidents(graphics);
            case TREASURY -> renderTreasury(graphics);
            case EFFECTS -> renderEffects(graphics, mouseX, mouseY);
            case MANAGEMENT -> renderManagement(graphics);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fit(snapshot.message(), W - 36), left + W / 2, top + 284, 0xFFFFD27A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverview(GuiGraphics graphics) {
        stat(graphics, "Population", snapshot.totalPopulation(), 22, 108);
        stat(graphics, "Registered villagers", snapshot.villagerCount(), 22, 128);
        stat(graphics, "Stability", snapshot.stability() + "%", 22, 148);
        stat(graphics, "Food", snapshot.food(), 22, 168);
        stat(graphics, "Claims", snapshot.claimLinked() ? snapshot.claimCount() + " linked" : "none", 22, 188);
        if (snapshot.claimLinked()) graphics.drawString(font, fit(snapshot.claimName(), 220), left + 22, top + 206, 0xFFAAAAAA, false);

        stat(graphics, "Wealth", snapshot.wealth(), 292, 108);
        stat(graphics, "Housing", snapshot.villagerCount() + " / " + snapshot.housingCapacity(), 292, 128);
        status(graphics, "Town Square", snapshot.townSquareSet(), 292, 148);
        status(graphics, "Muster Ground", snapshot.musterGroundSet(), 292, 168);
        status(graphics, "Barracks", snapshot.barracksSet(), 292, 188);
        graphics.drawWordWrap(font, Component.literal(snapshot.notice().isBlank() ? "No settlement notice." : snapshot.notice()),
                left + 292, top + 210, 220, 0xFFAAAAAA);
    }

    private void renderResidents(GuiGraphics graphics) {
        stat(graphics, "Registered villagers", snapshot.villagerCount(), 22, 110);
        stat(graphics, "Population multiplier", String.format(Locale.ROOT, "×%.2f", snapshot.populationMultiplier()), 22, 132);
        stat(graphics, "Civilian population", snapshot.civilianPopulation(), 22, 154);
        stat(graphics, "Military population", snapshot.militaryPopulation(), 22, 176);
        stat(graphics, "Free housing", snapshot.freeHousing(), 290, 110);
        stat(graphics, "Contracts ready", snapshot.pendingContracts(), 290, 132);
        graphics.drawWordWrap(font, Component.literal(
                "Every villager UUID can contribute only once. Contract settlers use the same resident registry, so registering them again cannot increase population."),
                left + 290, top + 158, 220, 0xFFAAAAAA);
    }

    private void renderTreasury(GuiGraphics graphics) {
        stat(graphics, "Food stockpile", snapshot.food(), 22, 110);
        stat(graphics, "Settlement wealth", snapshot.wealth(), 22, 132);
        stat(graphics, "Mercenary wage cycle", snapshot.mercenaryWages(), 22, 154);
        graphics.drawString(font, "RECENT CONTRIBUTIONS", left + 274, top + 110, 0xFFE7D8B0, false);
        for (int i = 0; i < Math.min(8, snapshot.contributions().size()); i++) {
            graphics.drawString(font, fit(snapshot.contributions().get(i), 238), left + 274, top + 130 + i * 15, 0xFFBBBBBB, false);
        }
    }

    private void renderEffects(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "ACTIVE BONUSES AND PENALTIES", left + 22, top + 108, 0xFFE7D8B0, false);
        int shown = Math.min(7, snapshot.effects().size());
        for (int i = 0; i < shown; i++) {
            PrototypeSnapshot.EffectEntry effect = snapshot.effects().get(i);
            int y = top + 130 + i * 21;
            int tone = switch (effect.tone()) {
                case "POSITIVE" -> 0xFFA9D18E;
                case "NEGATIVE" -> 0xFFFF9B8E;
                case "MIXED" -> 0xFFFFD27A;
                default -> 0xFFFFFFFF;
            };
            ResourceLocation iconId = ResourceLocation.tryParse(effect.iconItem());
            Item item = iconId == null ? Items.BOOK : BuiltInRegistries.ITEM.get(iconId);
            if (item == Items.AIR) item = Items.BOOK;
            graphics.renderItem(new ItemStack(item), left + 22, y - 5);
            graphics.drawString(font, fit(effect.name(), 190), left + 44, y, tone, false);
            graphics.drawString(font, fit(effect.source() + " · " + effect.duration(), 250), left + 264, y, 0xFF999999, false);
            if (mouseX >= left + 18 && mouseX <= left + W - 18 && mouseY >= y - 6 && mouseY <= y + 14) {
                graphics.renderTooltip(font, Component.literal(effect.detail()), mouseX, mouseY);
            }
        }
        if (snapshot.effects().isEmpty()) graphics.drawString(font, "No active settlement modifiers.", left + 22, top + 134, 0xFFAAAAAA, false);
        graphics.drawString(font, "Hover an entry for the exact mechanical effect.", left + 22, top + 252, 0xFFAA9D7F, false);
    }

    private void renderManagement(GuiGraphics graphics) {
        stat(graphics, "Banner mode", pretty(snapshot.bannerMode()), 22, 110);
        stat(graphics, "Faction banner", snapshot.factionId().isBlank() ? "none" : fit(snapshot.factionName(), 180), 22, 132);
        stat(graphics, "Military roster", fit(snapshot.rosterName(), 180), 22, 154);
        stat(graphics, "Linked claims", snapshot.claimCount(), 22, 176);
        stat(graphics, "Barracks area", snapshot.barracksSet() ? snapshot.barracksRadius() + " block radius" : "not set", 22, 198);
        stat(graphics, "Barracks capacity", snapshot.barracksSet() ? snapshot.barracksCapacity() : "—", 22, 220);
        graphics.drawWordWrap(font, Component.literal(
                "Town Square is for civilians. The Muster Ground is the military spawn area. Barracks provide a home, rest point and optional upkeep chest. Multiple Recruits claims may be linked to one settlement."),
                left + 286, top + 112, 226, 0xFFAAAAAA);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private void status(GuiGraphics graphics, String label, boolean set, int x, int y) {
        graphics.drawString(font, label + ": " + (set ? "set" : "missing"), left + x, top + y,
                set ? 0xFFA9D18E : 0xFFFFA080, false);
    }

    private String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return value.isBlank() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String fit(String value, int pixelWidth) {
        if (value == null) return "";
        return font.width(value) <= pixelWidth ? value : font.plainSubstrByWidth(value, Math.max(0, pixelWidth - font.width("…"))) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
    private enum Tab { OVERVIEW, RESIDENTS, TREASURY, EFFECTS, MANAGEMENT }
}
