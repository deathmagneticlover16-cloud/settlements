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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PrototypeScreen extends ScaledCampaignScreen {
    private static final int W = 540, H = 330;
    private PrototypeSnapshot snapshot;
    private int left, top;
    private Tab tab = Tab.OVERVIEW;
    private BannerRenderer factionBanner, settlementBanner;
    private boolean confirmClearClaims;
    private ManagePage managePage = ManagePage.LOCATIONS;
    private int effectScroll;
    private EditBox renameBox, transferBox;
    private DropdownWidget memberDropdown, memberCandidateDropdown;
    private final Map<String, String> memberIdsByLabel = new LinkedHashMap<>();
    private final Map<String, String> candidateIdsByLabel = new LinkedHashMap<>();

    public PrototypeScreen(PrototypeSnapshot snapshot) {
        super(Component.literal("Settlement"));
        this.snapshot = snapshot;
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
        if (!isLimitedView()) {
            addRenderableWidget(Button.builder(Component.literal("Military"), button -> open(OpenCampaignRequestPacket.ScreenTarget.MILITARY))
                    .bounds(left + 322, top + 301, 92, 18).build());
        }
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
        if (snapshot.canContribute()) {
            addRenderableWidget(Button.builder(Component.literal("Claim Settler Contract"), button -> send(PrototypeManager.SettlementAction.CLAIM_CONTRACT))
                    .bounds(left + 20, top + 260, 170, 20).build());
        }
    }

    private void treasuryButtons() {
        if (snapshot.canManage()) {
            Button low = addRenderableWidget(Button.builder(Component.literal("Low"), button -> send(PrototypeManager.SettlementAction.SET_TAX_POLICY, "LOW"))
                    .bounds(left + 20, top + 218, 100, 18).build());
            Button normal = addRenderableWidget(Button.builder(Component.literal("Normal"), button -> send(PrototypeManager.SettlementAction.SET_TAX_POLICY, "NORMAL"))
                    .bounds(left + 126, top + 218, 100, 18).build());
            Button high = addRenderableWidget(Button.builder(Component.literal("High"), button -> send(PrototypeManager.SettlementAction.SET_TAX_POLICY, "HIGH"))
                    .bounds(left + 232, top + 218, 100, 18).build());
            low.active = !"Low".equalsIgnoreCase(snapshot.taxPolicy());
            normal.active = !"Normal".equalsIgnoreCase(snapshot.taxPolicy());
            high.active = !"High".equalsIgnoreCase(snapshot.taxPolicy());
        }
        if (snapshot.canContribute()) {
            addRenderableWidget(Button.builder(Component.literal("Deposit Held Food"), button -> send(PrototypeManager.SettlementAction.DEPOSIT_FOOD))
                    .bounds(left + 20, top + 242, 154, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Deposit Held Silver"), button -> send(PrototypeManager.SettlementAction.DEPOSIT_SILVER))
                    .bounds(left + 180, top + 242, 154, 20).build());
        }
        if (snapshot.canManage()) {
            addRenderableWidget(Button.builder(Component.literal("Withdraw 1 Silver"), button -> send(PrototypeManager.SettlementAction.WITHDRAW_SILVER_ONE))
                    .bounds(left + 20, top + 266, 154, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Withdraw 10 Silver"), button -> send(PrototypeManager.SettlementAction.WITHDRAW_SILVER_TEN))
                    .bounds(left + 180, top + 266, 154, 20).build());
        }
    }

    private void effectButtons() {
        addRenderableWidget(Button.builder(Component.literal("Refresh effects"), button -> send(PrototypeManager.SettlementAction.REFRESH))
                .bounds(left + 20, top + 260, 116, 20).build());
    }

    private void managementButtons() {
        if (!snapshot.canManage()) return;

        addRenderableWidget(Button.builder(Component.literal("Locations"), b -> { managePage = ManagePage.LOCATIONS; buildWidgets(); })
                .bounds(left + 20, top + 102, 112, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Identity"), b -> { managePage = ManagePage.IDENTITY; buildWidgets(); })
                .bounds(left + 138, top + 102, 112, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Claims"), b -> { managePage = ManagePage.CLAIMS; buildWidgets(); })
                .bounds(left + 256, top + 102, 112, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Members"), b -> { managePage = ManagePage.MEMBERS; buildWidgets(); })
                .bounds(left + 374, top + 102, 112, 18).build());

        switch (managePage) {
            case LOCATIONS -> locationManagementButtons();
            case IDENTITY -> identityManagementButtons();
            case CLAIMS -> claimManagementButtons();
            case MEMBERS -> memberManagementButtons();
        }
    }

    private void locationManagementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Set Town Square Here"), b -> send(PrototypeManager.SettlementAction.SET_TOWN_SQUARE))
                .bounds(left + 30, top + 146, 150, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Set Barracks Here"), b -> send(PrototypeManager.SettlementAction.SET_BARRACKS))
                .bounds(left + 360, top + 146, 150, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Housing Pos 1"), b -> send(PrototypeManager.SettlementAction.HOUSING_POS1))
                .bounds(left + 30, top + 190, 104, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Housing Pos 2"), b -> send(PrototypeManager.SettlementAction.HOUSING_POS2))
                .bounds(left + 140, top + 190, 104, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Recalculate"), b -> send(PrototypeManager.SettlementAction.RECALCULATE_HOUSING))
                .bounds(left + 250, top + 190, 92, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Muster Pos 1"), b -> send(PrototypeManager.SettlementAction.MUSTER_POS1))
                .bounds(left + 30, top + 234, 104, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Muster Pos 2"), b -> send(PrototypeManager.SettlementAction.MUSTER_POS2))
                .bounds(left + 140, top + 234, 104, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Prayer Pos 1"), b -> send(PrototypeManager.SettlementAction.PRAYER_POS1))
                .bounds(left + 300, top + 234, 104, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Prayer Pos 2"), b -> send(PrototypeManager.SettlementAction.PRAYER_POS2))
                .bounds(left + 410, top + 234, 104, 18).build());
    }

    private void identityManagementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Set Held Banner"), button -> send(PrototypeManager.SettlementAction.SET_CUSTOM_BANNER))
                .bounds(left + 30, top + 154, 142, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use Faction Banner"), button -> send(PrototypeManager.SettlementAction.USE_FACTION_BANNER))
                .bounds(left + 178, top + 154, 152, 18).build());
        addRenderableWidget(Button.builder(Component.literal("No Banner"), button -> send(PrototypeManager.SettlementAction.CLEAR_BANNER))
                .bounds(left + 336, top + 154, 112, 18).build());

        renameBox = new EditBox(font, left + 30, top + 208, 224, 20, Component.literal("New settlement name"));
        renameBox.setMaxLength(64);
        addRenderableWidget(renameBox);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> send(PrototypeManager.SettlementAction.RENAME_SETTLEMENT, renameBox.getValue()))
                .bounds(left + 260, top + 208, 78, 20).build());

        if ("OWNER".equals(snapshot.role())) {
            transferBox = new EditBox(font, left + 30, top + 252, 224, 20, Component.literal("Online player"));
            transferBox.setMaxLength(32);
            addRenderableWidget(transferBox);
            addRenderableWidget(Button.builder(Component.literal("Transfer Ownership"), b -> send(PrototypeManager.SettlementAction.TRANSFER_SETTLEMENT, transferBox.getValue()))
                    .bounds(left + 260, top + 252, 128, 20).build());
        }
    }

    private void claimManagementButtons() {
        addRenderableWidget(Button.builder(Component.literal("Link Claim Here"), button -> send(PrototypeManager.SettlementAction.LINK_CURRENT_CLAIM))
                .bounds(left + 30, top + 164, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unlink Current Claim"), button -> send(PrototypeManager.SettlementAction.UNLINK_CURRENT_CLAIM))
                .bounds(left + 30, top + 206, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal(confirmClearClaims ? "CONFIRM UNLINK ALL" : "UNLINK ALL CLAIMS"), button -> {
                    if (confirmClearClaims) { send(PrototypeManager.SettlementAction.CLEAR_CLAIMS); confirmClearClaims = false; }
                    else { confirmClearClaims = true; buildWidgets(); }
                }).bounds(left + 30, top + 248, 150, 20).build());
    }


    private void open(OpenCampaignRequestPacket.ScreenTarget target) {
        PrototypeNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target));
    }

    private void memberManagementButtons() {
        memberIdsByLabel.clear();
        candidateIdsByLabel.clear();

        for (PrototypeSnapshot.MemberEntry member : snapshot.members()) {
            if ("OWNER".equals(member.role())) continue;
            String label = member.characterName() + " — " + pretty(member.role()) + (member.online() ? "" : " (offline)");
            memberIdsByLabel.put(label, member.playerId().toString());
        }
        List<String> memberLabels = memberIdsByLabel.keySet().stream().toList();
        memberDropdown = addRenderableWidget(new DropdownWidget(left + 30, top + 150, 300, 20, memberLabels, 0, ignored -> {}));
        if (memberLabels.isEmpty()) memberDropdown.active = false;
        addRenderableWidget(Button.builder(Component.literal("Remove Member"), b -> {
            String id = memberIdsByLabel.get(memberDropdown == null ? "" : memberDropdown.value());
            if (id != null) send(PrototypeManager.SettlementAction.REMOVE_MEMBER, id);
        }).bounds(left + 340, top + 150, 142, 20).build());

        for (PrototypeSnapshot.PlayerChoice candidate : snapshot.memberCandidates()) {
            String label = candidate.characterName().equals(candidate.accountName())
                    ? candidate.accountName() : candidate.characterName() + " (" + candidate.accountName() + ")";
            candidateIdsByLabel.put(label, candidate.playerId().toString());
        }
        List<String> candidateLabels = candidateIdsByLabel.keySet().stream().toList();
        memberCandidateDropdown = addRenderableWidget(new DropdownWidget(left + 30, top + 220, 300, 20, candidateLabels, 0, ignored -> {}));
        if (candidateLabels.isEmpty()) memberCandidateDropdown.active = false;
        addRenderableWidget(Button.builder(Component.literal("Add Citizen"), b -> {
            String id = candidateIdsByLabel.get(memberCandidateDropdown == null ? "" : memberCandidateDropdown.value());
            if (id != null) send(PrototypeManager.SettlementAction.ADD_MEMBER_CITIZEN, id);
        }).bounds(left + 340, top + 212, 142, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Add Co-owner"), b -> {
            String id = candidateIdsByLabel.get(memberCandidateDropdown == null ? "" : memberCandidateDropdown.value());
            if (id != null) send(PrototypeManager.SettlementAction.ADD_MEMBER_COOWNER, id);
        }).bounds(left + 340, top + 234, 142, 18).build());
    }

    private void send(PrototypeManager.SettlementAction action) { send(action, ""); }
    private void send(PrototypeManager.SettlementAction action, String argument) {
        PrototypeNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(), action, argument == null ? "" : argument, 0));
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

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.EFFECTS && snapshot.effects().size() > 7) {
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
        graphics.fill(left, top, left + W, top + H, 0xF0171717);
        graphics.fill(left + 3, top + 3, left + W - 3, top + 62, 0xFF3B3023);
        graphics.fill(left + 12, top + 96, left + W - 12, top + 290, 0xFF242424);

        BannerRenderer large = "CUSTOM".equals(snapshot.bannerMode()) && settlementBanner != null
                ? settlementBanner : "FACTION".equals(snapshot.bannerMode()) ? factionBanner : null;
        if (large != null) large.renderBanner(graphics, left + 17, top + 7, 20, 40, CAMPAIGN_BANNER_SCALE);
        if (settlementBanner != null && factionBanner != null && large == settlementBanner) {
            factionBanner.renderBanner(graphics, left + 56, top + 17, 20, 40, CAMPAIGN_SMALL_BANNER_SCALE);
        }

        graphics.drawString(font, fit(snapshot.settlementName().toUpperCase(Locale.ROOT), 360), left + 86, top + 13, 0xFFFFFFFF, false);
        graphics.drawString(font, snapshot.rank() + " · " + snapshot.rankSubtitle(), left + 86, top + 31, 0xFFE7D8B0, false);
        graphics.drawString(font, fit(snapshot.factionName() + " · " + pretty(snapshot.role()), 360), left + 86, top + 47, 0xFFB8B0A0, false);

        switch (tab) {
            case OVERVIEW -> renderOverview(graphics);
            case RESIDENTS -> renderResidents(graphics);
            case TREASURY -> renderTreasury(graphics);
            case EFFECTS -> renderEffects(graphics, uiMouseX, uiMouseY);
            case MANAGEMENT -> renderManagement(graphics);
        }
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fit(snapshot.message(), W - 36), left + W / 2, top + 284, 0xFFFFD27A);
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
        if (isLimitedView()) { renderPrivateTabNotice(graphics, "Resident registry"); return; }
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
        if (isLimitedView()) { renderPrivateTabNotice(graphics, "Treasury"); return; }
        stat(graphics, "Food stockpile", snapshot.food(), 22, 110);
        stat(graphics, "Settlement wealth", snapshot.wealth(), 22, 132);
        stat(graphics, "Prosperity", snapshot.prosperity() + "/100", 22, 154);
        stat(graphics, "Mercenary wage cycle", snapshot.mercenaryWages(), 22, 176);

        graphics.drawString(font, "TAXATION", left + 22, top + 194, 0xFFE7D8B0, false);
        String taxEffects = "Projected +" + snapshot.projectedTaxIncome() + " wealth/day · Last +" + snapshot.lastTaxIncome()
                + " · Stability " + signed(snapshot.taxStabilityPerDay()) + "/day · Prosperity " + signed(snapshot.taxProsperityModifier());
        graphics.drawString(font, fit(snapshot.taxPolicy() + " — " + taxEffects, 310), left + 22, top + 206, 0xFFAAAAAA, false);
        renderTaxBar(graphics, left + 20, top + 211, 312, snapshot.taxPolicy());

        graphics.drawString(font, "RECENT CONTRIBUTIONS", left + 356, top + 110, 0xFFE7D8B0, false);
        for (int i = 0; i < Math.min(9, snapshot.contributions().size()); i++) {
            graphics.drawString(font, fit(snapshot.contributions().get(i), 164), left + 356, top + 130 + i * 15, 0xFFBBBBBB, false);
        }
    }

    private void renderTaxBar(GuiGraphics graphics, int x, int y, int width, String policy) {
        graphics.fill(x, y, x + width, y + 5, 0xFF111111);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 4, 0xFF6E5A38);
        int position = "Low".equalsIgnoreCase(policy) ? x + 2 : "High".equalsIgnoreCase(policy) ? x + width - 3 : x + width / 2;
        graphics.fill(position - 2, y - 2, position + 3, y + 7, 0xFFFFD27A);
    }

    private void renderEffects(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isLimitedView()) { renderPrivateTabNotice(graphics, "Settlement effects"); return; }
        graphics.drawString(font, "ACTIVE BONUSES AND PENALTIES", left + 22, top + 108, 0xFFE7D8B0, false);
        int maximumScroll = Math.max(0, snapshot.effects().size() - 7);
        effectScroll = Math.max(0, Math.min(effectScroll, maximumScroll));
        int shown = Math.min(7, snapshot.effects().size() - effectScroll);
        for (int i = 0; i < shown; i++) {
            PrototypeSnapshot.EffectEntry effect = snapshot.effects().get(effectScroll + i);
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
        String effectFooter = snapshot.effects().size() > 7
                ? "Scroll to view all effects · " + (effectScroll + 1) + "-" + (effectScroll + shown) + " / " + snapshot.effects().size()
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
        graphics.drawString(font, "Barracks: " + (snapshot.barracksSet() ? snapshot.barracksRadius() + " block radius · capacity " + snapshot.barracksCapacity() : "missing"), left + 360, top + 168, snapshot.barracksSet() ? 0xFFA9D18E : 0xFFFFA080, false);

        graphics.drawString(font, "HOUSING", left + 30, top + 182, 0xFFE7D8B0, false);
        graphics.drawString(font, "Registered capacity: " + snapshot.housingCapacity(), left + 360, top + 194, 0xFFAAAAAA, false);

        graphics.drawString(font, "MUSTER GROUND", left + 30, top + 226, 0xFFE7D8B0, false);
        graphics.drawString(font, snapshot.musterGroundSet() ? "Military spawn region is set." : "Choose two corners for the military spawn region.", left + 30, top + 256, snapshot.musterGroundSet() ? 0xFFA9D18E : 0xFFAAAAAA, false);

        graphics.drawString(font, "PRAYER SITE", left + 300, top + 226, 0xFFE7D8B0, false);
        graphics.drawString(font, "Choose two corners for the settlement prayer region.", left + 300, top + 256, 0xFFAAAAAA, false);
    }

    private void renderIdentityManagement(GuiGraphics graphics) {
        graphics.drawString(font, "SETTLEMENT IDENTITY", left + 30, top + 128, 0xFFE7D8B0, false);
        graphics.drawString(font, "Banner mode: " + pretty(snapshot.bannerMode()), left + 30, top + 138, 0xFFAAAAAA, false);
        graphics.drawString(font, "Faction: " + (snapshot.factionId().isBlank() ? "Independent" : fit(snapshot.factionName(), 250)), left + 30, top + 184, 0xFFAAAAAA, false);
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
            graphics.drawString(font, "Current/primary: " + fit(snapshot.claimName(), 150), left + 360, top + 144, 0xFFAAAAAA, false);
        }

        graphics.drawWordWrap(font, Component.literal("Links the Recruits claim you are currently standing in to this settlement."),
                left + 198, top + 162, 306, 0xFFAAAAAA);
        graphics.drawWordWrap(font, Component.literal("Removes only the claim you are currently standing in from this settlement."),
                left + 198, top + 204, 306, 0xFFAAAAAA);
        graphics.drawWordWrap(font, Component.literal(confirmClearClaims
                        ? "WARNING: press the red button again to remove every linked claim."
                        : "Removes ALL linked claims. This can break a large settlement's territory setup and requires confirmation."),
                left + 198, top + 246, 306, confirmClearClaims ? 0xFFFF8080 : 0xFFAAAAAA);
    }

    private void renderMemberManagement(GuiGraphics graphics) {
        graphics.drawString(font, "SETTLEMENT MEMBERS", left + 30, top + 128, 0xFFE7D8B0, false);
        graphics.drawString(font, "Current members", left + 30, top + 140, 0xFFAAAAAA, false);
        graphics.drawWordWrap(font, Component.literal("Citizens may view and contribute. Co-owners have settlement management permissions but cannot take ownership away from the owner."),
                left + 30, top + 178, 452, 0xFFAAAAAA);
        graphics.drawString(font, "Add an online player from this settlement's polity", left + 30, top + 206, 0xFFE7D8B0, false);
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

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    private void status(GuiGraphics graphics, String label, boolean set, int x, int y) {
        graphics.drawString(font, label + ": " + (set ? "set" : "missing"), left + x, top + y,
                set ? 0xFFA9D18E : 0xFFFFA080, false);
    }

    private String pretty(String raw) {
        String value = raw == null ? "" : raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return value.isBlank() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String fit(String value, int pixelWidth) {
        return scrollingText(value, pixelWidth);
    }

    @Override public boolean isPauseScreen() { return false; }
    private enum Tab { OVERVIEW, RESIDENTS, TREASURY, EFFECTS, MANAGEMENT }
    private enum ManagePage { LOCATIONS, IDENTITY, CLAIMS, MEMBERS }
}
