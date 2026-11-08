package ee.lastpagans.settlements.client;

import com.talhanation.recruits.client.gui.component.BannerRenderer;
import com.talhanation.recruits.world.RecruitsFaction;
import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.SettlementSnapshot;
import ee.lastpagans.settlements.AreaSelection;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import ee.lastpagans.settlements.network.SettlementActionPacket;
import ee.lastpagans.settlements.network.HousingAreaActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

public final class SettlementScreen extends ScaledCampaignScreen {
    private static final int W = 540, H = 330;
    private static Tab lastTab = Tab.OVERVIEW;
    private static ManagePage lastManagePage = ManagePage.LOCATIONS;
    private SettlementSnapshot snapshot;
    private int left, top;
    private Tab tab = lastTab;
    private BannerRenderer factionBanner, settlementBanner;
    private long clearClaimsConfirmUntil;
    private ManagePage managePage = lastManagePage;
    private int effectScroll;
    private EditBox renameBox, transferBox;
    private DropdownWidget memberCandidateDropdown;
    private TaxRateSlider taxSlider;
    private final java.util.Map<String, String> candidateIdsByLabel = new java.util.LinkedHashMap<>();
    private String transientMessage = "";
    private long transientMessageUntil;

    public SettlementScreen(SettlementSnapshot snapshot) {
        super(Component.literal("Settlement"));
        this.snapshot = snapshot;
        setTransientMessage(snapshot.message());
        rebuildBanners();
    }

    @Override protected void init() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        buildWidgets();
    }

    private void buildWidgets() {
        setupCampaignScale(W, H);
        left = campaignLeft(W);
        top = campaignTop(H);
        clearWidgets();
        String[] labels = {"Overview", "Residents", "Treasury", "Effects", "Manage"};
        addCampaignTabs(labels, tab.ordinal(), left + 12, top + 68, 98, 20, 5,
                index -> { tab = Tab.values()[index]; lastTab = tab; buildWidgets(); });
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
        if (!isLimitedView()) {
            addRenderableWidget(Button.builder(Component.literal("Military"), button -> open(OpenCampaignRequestPacket.ScreenTarget.MILITARY))
                    .bounds(left + 322, top + 301, 92, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button -> open(OpenCampaignRequestPacket.ScreenTarget.MAIN))
                .bounds(left + 420, top + 301, 106, 18).build());
    }

    private void overviewButtons() {
        addRenderableWidget(Button.builder(Component.literal("Character"), button -> send(SettlementManager.SettlementAction.OPEN_CHARACTER))
                .bounds(left + 20, top + 260, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> send(SettlementManager.SettlementAction.REFRESH))
                .bounds(left + 126, top + 260, 80, 20).build());
    }

    private void residentButtons() {
        if (snapshot.canManage()) {
            addRenderableWidget(Button.builder(Component.literal(snapshot.immigrationEnabled() ? "Immigration: ON" : "Immigration: OFF"),
                            button -> send(SettlementManager.SettlementAction.TOGGLE_IMMIGRATION))
                    .bounds(left + 20, top + 260, 170, 20).build());
        }
    }

    private void treasuryButtons() {
        taxSlider = null;
        if (snapshot.canManage()) {
            taxSlider = addRenderableWidget(new TaxRateSlider(left + 20, top + 224, 140, 18, snapshot.taxRatePercent(),
                    rate -> send(SettlementManager.SettlementAction.SET_TAX_POLICY, Integer.toString(rate))));
        }
        if (snapshot.canManage()) {
            if (snapshot.upkeepChestSet()) addRenderableWidget(Button.builder(Component.literal("Clear Upkeep Chest"), button -> send(SettlementManager.SettlementAction.CLEAR_UPKEEP_CHEST))
                    .bounds(left + 356, top + 260, 154, 18).build());
        }
    }

    private void effectButtons() {
        addRenderableWidget(Button.builder(Component.literal("Refresh effects"), button -> send(SettlementManager.SettlementAction.REFRESH))
                .bounds(left + 20, top + 260, 116, 20).build());
    }

    private void managementButtons() {
        if (!snapshot.canManage()) return;
        String[] labels = {"Locations", "Identity", "Claims", "Managers"};
        addCampaignTabs(labels, managePage.ordinal(), left + 20, top + 102, 112, 18, 6, index -> {
            managePage = ManagePage.values()[index];
            lastManagePage = managePage;
            clearClaimsConfirmUntil = 0L;
            buildWidgets();
        });
        switch (managePage) {
            case LOCATIONS -> locationManagementButtons();
            case IDENTITY -> identityManagementButtons();
            case CLAIMS -> claimManagementButtons();
            case MEMBERS -> memberManagementButtons();
        }
    }

    private void locationManagementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Relocate Town Square"), b -> TownSquareRelocationEditor.begin(snapshot.settlementId(), snapshot.rank()))
                .bounds(left + 30, top + 146, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Set Upkeep Chest"), b -> send(SettlementManager.SettlementAction.SET_UPKEEP_CHEST))
                .bounds(left + 195, top + 146, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Edit Barracks Area"), b -> beginArea(AreaSelection.Type.BARRACKS))
                .bounds(left + 360, top + 146, 150, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Manage Housing Areas"), b -> SettlementNetwork.CHANNEL.sendToServer(
                        new HousingAreaActionPacket(snapshot.settlementId(), HousingAreaActionPacket.Action.OPEN, -1)))
                .bounds(left + 30, top + 200, 214, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Recalculate"), b -> send(SettlementManager.SettlementAction.RECALCULATE_HOUSING))
                .bounds(left + 250, top + 200, 92, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Edit Muster Ground"), b -> beginArea(AreaSelection.Type.MUSTER))
                .bounds(left + 30, top + 244, 214, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Edit Prayer Site"), b -> beginArea(AreaSelection.Type.PRAYER))
                .bounds(left + 300, top + 244, 214, 18).build());
    }

    private void identityManagementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Set Held Banner"), button -> send(SettlementManager.SettlementAction.SET_CUSTOM_BANNER))
                .bounds(left + 30, top + 154, 142, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use Faction Banner"), button -> send(SettlementManager.SettlementAction.USE_FACTION_BANNER))
                .bounds(left + 178, top + 154, 152, 18).build());

        renameBox = new EditBox(font, left + 30, top + 208, 224, 20, Component.literal("New settlement name"));
        renameBox.setMaxLength(64);
        addRenderableWidget(renameBox);
        addRenderableWidget(Button.builder(Component.literal("Rename: 5,000 Silver"), b -> send(SettlementManager.SettlementAction.RENAME_SETTLEMENT, renameBox.getValue()))
                .bounds(left + 260, top + 208, 142, 20).build());

        if ("OWNER".equals(snapshot.role())) {
            transferBox = new EditBox(font, left + 30, top + 252, 224, 20, Component.literal("Online player"));
            transferBox.setMaxLength(32);
            addRenderableWidget(transferBox);
            addRenderableWidget(Button.builder(Component.literal("Transfer Ownership"), b -> send(SettlementManager.SettlementAction.TRANSFER_SETTLEMENT, transferBox.getValue()))
                    .bounds(left + 260, top + 252, 128, 20).build());

        }
    }

    private void claimManagementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Link Claim Here"), button -> send(SettlementManager.SettlementAction.LINK_CURRENT_CLAIM))
                .bounds(left + 30, top + 164, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unlink Current Claim"), button -> send(SettlementManager.SettlementAction.UNLINK_CURRENT_CLAIM))
                .bounds(left + 30, top + 206, 150, 20).build());
        boolean armed = clearClaimsConfirmUntil > System.currentTimeMillis();
        Component label = Component.literal(armed ? "CONFIRM UNLINK ALL" : "UNLINK ALL CLAIMS");
        addRenderableWidget(new DangerButton(left + 30, top + 248, 150, 20, label, () -> {
                    long now = System.currentTimeMillis();
                    if (clearClaimsConfirmUntil > now) {
                        send(SettlementManager.SettlementAction.CLEAR_CLAIMS);
                        clearClaimsConfirmUntil = 0L;
                    } else {
                        clearClaimsConfirmUntil = now + 5_000L;
                    }
                    buildWidgets();
                }));
    }

    private void open(OpenCampaignRequestPacket.ScreenTarget target) {
        SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target));
    }

    private void beginArea(AreaSelection.Type type) {
        AreaSelectionEditor.begin(snapshot.settlementId(), type);
    }

    private void memberManagementButtons() {
        candidateIdsByLabel.clear();
        for (SettlementSnapshot.PlayerChoice candidate : snapshot.memberCandidates()) {
            String label = candidate.characterName().equals(candidate.accountName())
                    ? candidate.accountName() : candidate.characterName() + " (" + candidate.accountName() + ")";
            candidateIdsByLabel.put(label, candidate.playerId().toString());
        }
        List<String> candidateLabels = candidateIdsByLabel.keySet().stream().toList();
        memberCandidateDropdown = addRenderableWidget(new DropdownWidget(left + 30, top + 184, 300, 20, candidateLabels, 0, ignored -> {}));
        if (candidateLabels.isEmpty()) memberCandidateDropdown.active = false;
        addRenderableWidget(Button.builder(Component.literal("Add Manager"), b -> {
            String id = candidateIdsByLabel.get(memberCandidateDropdown == null ? "" : memberCandidateDropdown.value());
            if (id != null) send(SettlementManager.SettlementAction.ADD_MEMBER_COOWNER, id);
        }).bounds(left + 340, top + 184, 142, 20).build());
    }

    private void send(SettlementManager.SettlementAction action) { send(action, ""); }
    private void send(SettlementManager.SettlementAction action, String argument) {
        SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(), action, argument == null ? "" : argument, 0));
    }

    private void switchSettlement(boolean next) {
        SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(),
                next ? SettlementManager.SettlementAction.SELECT_NEXT : SettlementManager.SettlementAction.SELECT_PREVIOUS,
                "SETTLEMENT", 0));
    }

    public void updateSnapshot(SettlementSnapshot value) {
        snapshot = value;
        setTransientMessage(value.message());
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

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean result = super.mouseReleased(mouseX, mouseY, button);
        if (tab == Tab.TREASURY && taxSlider != null) taxSlider.commitIfChanged();
        return result;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double ux = campaignMouseX(mouseX), uy = campaignMouseY(mouseY);
        if (tab == Tab.EFFECTS && snapshot.effects().size() > 7
                && ux >= left + 20 && ux <= left + W - 20 && uy >= top + 100 && uy <= top + 280) {
            int maximumScroll = snapshot.effects().size() - 7;
            effectScroll = Math.max(0, Math.min(maximumScroll, effectScroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int uiMouseX = campaignMouseX(mouseX);
        int uiMouseY = campaignMouseY(mouseY);
        beginCampaignRender(graphics);
        drawCampaignFrame(graphics, left, top, W, H, 62);
        drawCampaignPanel(graphics, left + 12, top + 96, left + W - 12, top + 290);

        BannerRenderer large = "CUSTOM".equals(snapshot.bannerMode()) && settlementBanner != null
                ? settlementBanner : "FACTION".equals(snapshot.bannerMode()) ? factionBanner : null;
        if (large != null) large.renderBanner(graphics, left + 17, top + 7, 20, 40, CAMPAIGN_BANNER_SCALE);
        if (settlementBanner != null && factionBanner != null && large == settlementBanner) {
            factionBanner.renderBanner(graphics, left + 56, top + 17, 20, 40, CAMPAIGN_SMALL_BANNER_SCALE);
        }

        graphics.drawString(font, fitText(snapshot.settlementName().toUpperCase(Locale.ROOT), 360), left + 86, top + 13, CAMPAIGN_TEXT, false);
        graphics.drawString(font, snapshot.rank() + " · " + snapshot.rankSubtitle(), left + 86, top + 31, 0xFFE7D8B0, false);
        graphics.drawString(font, fitText(snapshot.factionName() + " · " + pretty(snapshot.role()), 360), left + 86, top + 47, 0xFFB8B0A0, false);

        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case RESIDENTS -> renderResidents(graphics, uiMouseX, uiMouseY);
            case TREASURY -> renderTreasury(graphics);
            case EFFECTS -> renderEffects(graphics, uiMouseX, uiMouseY);
            case MANAGEMENT -> renderManagement(graphics);
        }
        if (!transientMessage.isBlank() && System.currentTimeMillis() < transientMessageUntil)
            graphics.drawCenteredString(font, fitText(transientMessage, W - 36), left + W / 2, top + 284, CAMPAIGN_MESSAGE);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        endCampaignRender(graphics);
    }

    private void renderOverview(GuiGraphics graphics) {
        if (isLimitedView()) {
            graphics.drawString(font, "LIMITED FACTION SUMMARY", left + 22, top + 108, 0xFFE7D8B0, false);
            stat(graphics, "Population", snapshot.totalPopulation(), 22, 134);
            stat(graphics, "Rank", snapshot.rank(), 22, 156);
            stat(graphics, "Faction", snapshot.factionName(), 22, 178);
            graphics.drawWordWrap(font, Component.literal(snapshot.notice().isBlank() ? "No public settlement notice." : snapshot.notice()),
                    left + 292, top + 134, 220, 0xFFAAAAAA);
            graphics.drawWordWrap(font, Component.literal("Detailed residents, treasury, effects, claims, military information, and management data are private unless you hold a settlement role."),
                    left + 22, top + 216, 490, 0xFFFFC080);
            return;
        }
        stat(graphics, "Population", snapshot.totalPopulation(), 22, 108);
        stat(graphics, "Registered villagers", snapshot.villagerCount(), 22, 128);
        statWithIcon(graphics, "stability", "Stability", snapshot.stability() + "%", 22, 148);
        stat(graphics, "Food", snapshot.food(), 22, 168);
        stat(graphics, "Claims", snapshot.claimLinked() ? snapshot.claimCount() + " linked" : "none", 22, 188);
        if (snapshot.claimLinked()) graphics.drawString(font, fitText(snapshot.claimName(), 220), left + 22, top + 206, CAMPAIGN_MUTED, false);

        statWithIcon(graphics, "treasury", "State Treasury", snapshot.treasury(), 292, 108);
        stat(graphics, "Housing", snapshot.villagerCount() + " / " + snapshot.housingCapacity(), 292, 128);
        status(graphics, "Town Square", snapshot.townSquareSet(), 292, 148);
        status(graphics, "Muster Ground", snapshot.musterGroundSet(), 292, 168);
        status(graphics, "Barracks", snapshot.barracksSet(), 292, 188);
        graphics.drawWordWrap(font, Component.literal(snapshot.notice().isBlank() ? "No settlement notice." : snapshot.notice()),
                left + 292, top + 210, 220, 0xFFAAAAAA);
    }

    private void renderResidents(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isLimitedView()) { renderPrivateTabNotice(graphics, "Resident registry"); return; }
        stat(graphics, "Registered villagers", snapshot.villagerCount(), 22, 110);
        stat(graphics, "Population multiplier", String.format(Locale.ROOT, "×%.2f", snapshot.populationMultiplier()), 22, 132);
        stat(graphics, "Civilian population", snapshot.civilianPopulation(), 22, 154);
        stat(graphics, "Military population", snapshot.militaryPopulation(), 22, 176);
        stat(graphics, "Free housing", snapshot.freeHousing(), 290, 110);
        graphics.drawString(font, "Immigration: " + (snapshot.immigrationEnabled() ? "Enabled" : "Paused"), left + 290, top + 132,
                snapshot.immigrationEnabled() ? 0xFF92D18B : CAMPAIGN_MUTED, false);
        graphics.drawString(font, fitText("Attraction: " + snapshot.immigrationStatus(), 220), left + 290, top + 154, CAMPAIGN_TITLE, false);
        if (mouseX >= left + 20 && mouseX <= left + 235 && mouseY >= top + 126 && mouseY <= top + 146) {
            graphics.renderTooltip(font, Component.literal("Registered villagers are multiplied into civilian population. The multiplier rises as an established settlement grows."), mouseX, mouseY);
        }
    }

    private void renderTreasury(GuiGraphics graphics) {
        if (isLimitedView()) { renderPrivateTabNotice(graphics, "Treasury"); return; }
        stat(graphics, "Food stockpile", snapshot.food(), 22, 110);
        statWithIcon(graphics, "treasury", "State Treasury", snapshot.treasury(), 22, 132);
        stat(graphics, "Mercenary wage cycle", snapshot.mercenaryWages(), 22, 176);
        graphics.drawString(font, "Upkeep chest: " + (snapshot.upkeepChestSet() ? "linked" : "missing"), left + 356, top + 246, snapshot.upkeepChestSet() ? 0xFFA9D18E : 0xFFAAAAAA, false);

        graphics.drawString(font, "TAXATION", left + 22, top + 194, 0xFFE7D8B0, false);
        String taxEffects = "Projected +" + snapshot.projectedTaxIncome() + " Silver/day | Last +" + snapshot.lastTaxIncome()
                + " | Stability " + signed(snapshot.taxStabilityImpact());
        graphics.drawString(font, fitText(snapshot.taxPolicy() + ": " + taxEffects, 480), left + 22, top + 206, CAMPAIGN_MUTED, false);
    }

    private void renderEffects(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isLimitedView()) { renderPrivateTabNotice(graphics, "Settlement effects"); return; }
        graphics.drawString(font, "ACTIVE BONUSES AND PENALTIES", left + 22, top + 108, 0xFFE7D8B0, false);
        int maximumScroll = Math.max(0, snapshot.effects().size() - 7);
        effectScroll = Math.max(0, Math.min(effectScroll, maximumScroll));
        int shown = Math.min(7, snapshot.effects().size() - effectScroll);
        for (int i = 0; i < shown; i++) {
            SettlementSnapshot.EffectEntry effect = snapshot.effects().get(effectScroll + i);
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
            graphics.drawString(font, fitText(effect.name(), 190), left + 44, y, tone, false);
            graphics.drawString(font, fitText(effect.source() + " | " + effect.duration(), 250), left + 264, y, 0xFF999999, false);
            if (mouseX >= left + 18 && mouseX <= left + W - 18 && mouseY >= y - 6 && mouseY <= y + 14) {
                graphics.renderTooltip(font, Component.literal(effect.detail()), mouseX, mouseY);
            }
        }
        if (snapshot.effects().isEmpty()) graphics.drawString(font, "No active settlement modifiers.", left + 22, top + 134, 0xFFAAAAAA, false);
        String effectFooter = snapshot.effects().size() > 7
                ? "Scroll to view all effects | " + (effectScroll + 1) + "-" + (effectScroll + shown) + " / " + snapshot.effects().size()
                : "Hover an entry for the exact mechanical effect.";
        graphics.drawString(font, effectFooter, left + 22, top + 252, 0xFFAA9D7F, false);
    }

    private void renderManagement(GuiGraphics graphics) {
        if (!snapshot.canManage()) {
            graphics.drawWordWrap(font, Component.literal("Read-only faction view. Only the settlement owner, co-owners, or administrators can change banners, claims, housing, military locations, resources, or recruitment."), left + 22, top + 112, W - 44, 0xFFFFC080);
            return;
        }

        switch (managePage) {
            case LOCATIONS -> renderLocationManagement(graphics);
            case IDENTITY -> renderIdentityManagement(graphics);
            case CLAIMS -> renderClaimManagement(graphics);
            case MEMBERS -> renderMemberManagement(graphics);
        }
    }

    private void renderLocationManagement(GuiGraphics graphics) {
        graphics.drawString(font, "SETTLEMENT LOCATIONS", left + 30, top + 128, 0xFFE7D8B0, false);
        graphics.drawString(font, "Town Square: " + (snapshot.townSquareSet() ? "set" : "missing"), left + 30, top + 168, snapshot.townSquareSet() ? 0xFFA9D18E : 0xFFFFA080, false);
        graphics.drawString(font, "Upkeep: " + (snapshot.upkeepChestSet() ? "chest linked" : "missing"), left + 195, top + 168, snapshot.upkeepChestSet() ? 0xFFA9D18E : 0xFFFFA080, false);
        graphics.drawString(font, "Barracks: " + (snapshot.barracksSet() ? snapshot.barracksRadius() + "r | cap " + snapshot.barracksCapacity() : "missing"), left + 360, top + 168, snapshot.barracksSet() ? 0xFFA9D18E : 0xFFFFA080, false);

        graphics.drawString(font, "HOUSING", left + 30, top + 184, 0xFFE7D8B0, false);
        graphics.drawString(font, "Registered capacity: " + snapshot.housingCapacity(), left + 360, top + 184, 0xFFAAAAAA, false);

        graphics.drawString(font, "MUSTER GROUND", left + 30, top + 228, 0xFFE7D8B0, false);
        graphics.drawString(font, snapshot.musterGroundSet() ? "Military spawn region is set." : "Choose two corners for the military spawn region.", left + 30, top + 266, snapshot.musterGroundSet() ? 0xFFA9D18E : 0xFFAAAAAA, false);

        graphics.drawString(font, "PRAYER SITE", left + 300, top + 228, 0xFFE7D8B0, false);
        graphics.drawString(font, "Choose two corners for the settlement prayer region.", left + 300, top + 266, 0xFFAAAAAA, false);
    }

    private void renderIdentityManagement(GuiGraphics graphics) {
        graphics.drawString(font, "SETTLEMENT IDENTITY", left + 30, top + 128, 0xFFE7D8B0, false);
        graphics.drawString(font, "Banner mode: " + pretty(snapshot.bannerMode()), left + 30, top + 138, 0xFFAAAAAA, false);
        graphics.drawString(font, "Faction: " + (snapshot.factionId().isBlank() ? "Independent" : fitText(snapshot.factionName(), 250)), left + 30, top + 184, CAMPAIGN_MUTED, false);
        graphics.drawString(font, "Rename settlement", left + 30, top + 198, 0xFFE7D8B0, false);
        if ("OWNER".equals(snapshot.role())) {
            graphics.drawString(font, "Transfer ownership to an online player", left + 30, top + 242, 0xFFE7D8B0, false);
        } else {
            graphics.drawString(font, "Only the settlement owner may transfer ownership.", left + 30, top + 242, 0xFF888888, false);
        }
    }

    private void renderClaimManagement(GuiGraphics graphics) {
        graphics.drawString(font, "CLAIMS", left + 30, top + 128, 0xFFE7D8B0, false);
        graphics.drawString(font, "Linked claims: " + snapshot.claimCount(), left + 360, top + 130, 0xFFFFFFFF, false);
        if (snapshot.claimLinked()) {
            graphics.drawString(font, "Current/primary: " + fitText(snapshot.claimName(), 150), left + 360, top + 144, CAMPAIGN_MUTED, false);
        }

        graphics.drawWordWrap(font, Component.literal("Links the Recruits claim you are currently standing in to this settlement."),
                left + 198, top + 162, 306, 0xFFAAAAAA);
        graphics.drawWordWrap(font, Component.literal("Removes only the claim you are currently standing in from this settlement."),
                left + 198, top + 204, 306, 0xFFAAAAAA);
        boolean armed = clearClaimsConfirmUntil > System.currentTimeMillis();
        graphics.drawWordWrap(font, Component.literal(armed
                        ? "WARNING: press the red button again to remove every linked claim."
                        : "Removes ALL linked claims. This can break a large settlement's territory setup and requires confirmation."),
                left + 198, top + 246, 306, armed ? 0xFFFF8080 : 0xFFAAAAAA);
    }

    private void renderMemberManagement(GuiGraphics graphics) {
        graphics.drawString(font, "SETTLEMENT MANAGERS", left + 30, top + 128, 0xFFE7D8B0, false);
        graphics.drawString(font, "Add an online player from this settlement's faction", left + 30, top + 164, 0xFFE7D8B0, false);
        if (snapshot.memberCandidates().isEmpty()) {
            graphics.drawString(font, "No eligible online players are currently available.", left + 30, top + 252, 0xFF888888, false);
        }
    }

    private boolean isLimitedView() {
        return "OUTSIDER".equals(snapshot.role()) && !snapshot.canManage() && !snapshot.canContribute();
    }

    private void renderPrivateTabNotice(GuiGraphics graphics, String section) {
        graphics.drawString(font, section.toUpperCase(Locale.ROOT), left + 22, top + 112, 0xFFE7D8B0, false);
        graphics.drawWordWrap(font, Component.literal("This information is private to settlement members. You can view the public faction summary, but you do not hold a role in this settlement."),
                left + 22, top + 142, W - 44, 0xFFFFC080);
    }

    private void stat(GuiGraphics graphics, String label, Object value, int x, int y) {
        graphics.drawString(font, label + ": " + value, left + x, top + y, 0xFFFFFFFF, false);
    }

    private void statWithIcon(GuiGraphics graphics, String icon, String label, Object value, int x, int y) {
        ResourceLocation texture = new ResourceLocation("settlements", "textures/gui/icons/" + icon + ".png");
        graphics.pose().pushPose();
        graphics.pose().translate(left + x, top + y - 4, 0);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.blit(texture, 0, 0, 0, 0, 32, 32, 32, 32);
        graphics.pose().popPose();
        graphics.drawString(font, label + ": " + value, left + x + 19, top + y, 0xFFFFFFFF, false);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    private void status(GuiGraphics graphics, String label, boolean set, int x, int y) {
        graphics.drawString(font, label + ": " + (set ? "set" : "missing"), left + x, top + y,
                set ? 0xFFA9D18E : 0xFFFFA080, false);
    }

    private String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return value.isBlank() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void setTransientMessage(String message) {
        if (message == null || message.isBlank()) return;
        transientMessage = message;
        transientMessageUntil = System.currentTimeMillis() + 4_000L;
    }

    @Override public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (transientMessageUntil > 0L && transientMessageUntil <= now) {
            transientMessage = "";
            transientMessageUntil = 0L;
        }
        if (clearClaimsConfirmUntil > 0L && clearClaimsConfirmUntil <= now) {
            clearClaimsConfirmUntil = 0L;
            if (tab == Tab.MANAGEMENT && managePage == ManagePage.CLAIMS) buildWidgets();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class DangerButton extends AbstractButton {
        private final Runnable action;

        private DangerButton(int x, int y, int width, int height, Component label, Runnable action) {
            super(x, y, width, height, label);
            this.action = action;
        }

        @Override public void onPress() { action.run(); }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = isHoveredOrFocused() ? 0xFFFF8175 : 0xFFB74C45;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF5B201E);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, active ? 0xFFFFE8E5 : 0xFF8D7775);
        }
    }

    private enum Tab { OVERVIEW, RESIDENTS, TREASURY, EFFECTS, MANAGEMENT }
    private enum ManagePage { LOCATIONS, IDENTITY, CLAIMS, MEMBERS }
}
