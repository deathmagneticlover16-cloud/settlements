package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.MilitarySnapshot;
import ee.lastpagans.settlements.SettlementConfig;
import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Overview, training and muster workspaces backed entirely by server snapshots. */
public final class MilitaryScreen extends ScaledCampaignScreen {
    private static final int W = 700, H = 430;
    private static final int OVERVIEW = 0, TRAIN = 1, MUSTER = 2;
    private static final int NATIVE = 0xFFF0E6D7, FOREIGN = 0xFFC55B53, EMPTY = 0xFF211B16;
    private MilitarySnapshot snapshot;
    private int left, top, page, selected, trainQuantity = 1, musterQuantity = 1, queueScroll;
    private long receivedAt = System.currentTimeMillis();
    private Component hoverTooltip;
    private Button musterButton;

    public MilitaryScreen(MilitarySnapshot snapshot) { super(Component.literal("Military")); this.snapshot = snapshot; }

    @Override protected void init() { setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H); rebuildWidgets(); }

    public void updateSnapshot(MilitarySnapshot value) {
        String selectedType = selectedUnit() == null ? "" : selectedUnit().type();
        snapshot = value; receivedAt = System.currentTimeMillis();
        for (int i = 0; i < snapshot.units().size(); i++) if (snapshot.units().get(i).type().equals(selectedType)) selected = i;
        selected = Math.max(0, Math.min(selected, Math.max(0, snapshot.units().size() - 1)));
        clampQuantities(); rebuildWidgets();
    }

    @Override protected void rebuildWidgets() {
        setupCampaignScale(W, H); left = campaignLeft(W); top = campaignTop(H); clearWidgets(); musterButton = null; clampQuantities();
        addCampaignTabs(new String[]{"Overview", "Train Troops", "Muster Troops"}, page, left + 18, top + 62, 150, 20, 7,
                value -> { page = value; rebuildWidgets(); });
        if (page == OVERVIEW) overviewWidgets(); else if (page == TRAIN) trainWidgets(); else musterWidgets();
        addRenderableWidget(Button.builder(Component.literal("Mercenary Contracts"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MERCENARIES)))
                .bounds(left + 18, top + 398, 142, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Return to Main"), button ->
                SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.MAIN)))
                .bounds(left + W - 126, top + 398, 108, 18).build());
    }

    private void overviewWidgets() {
        addRenderableWidget(Button.builder(Component.literal("TRAIN TROOPS"), button -> { page = TRAIN; rebuildWidgets(); })
                .bounds(left + 360, top + 310, 148, 42).build());
        addRenderableWidget(Button.builder(Component.literal("MUSTER TROOPS"), button -> { page = MUSTER; rebuildWidgets(); })
                .bounds(left + 520, top + 310, 148, 42).build());
        if (snapshot.capturedForeignManpowerMilli() > 0) {
            Button ransom = addRenderableWidget(Button.builder(Component.literal("Ransom All"), button -> send(SettlementManager.SettlementAction.RANSOM_FOREIGN_MP, "", 0))
                    .bounds(left + 24, top + 336, 108, 20).build());
            ransom.active = snapshot.capturedForeignManpowerMilli() >= 1000;
            addRenderableWidget(Button.builder(Component.literal("Integrate 75%"), button -> send(SettlementManager.SettlementAction.INTEGRATE_FOREIGN_MP, "", 0))
                    .bounds(left + 140, top + 336, 112, 20).build());
        }
    }

    private void trainWidgets() {
        int controlY = top + 342;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> { trainQuantity = Math.max(1, trainQuantity - 1); rebuildWidgets(); })
                .bounds(left + 22, controlY, 24, 22).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> { trainQuantity = Math.min(maxTrain(), trainQuantity + 1); rebuildWidgets(); })
                .bounds(left + 94, controlY, 24, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Max"), button -> { trainQuantity = Math.max(1, maxTrain()); rebuildWidgets(); })
                .bounds(left + 124, controlY, 40, 22).build());
        MilitarySnapshot.UnitEntry unit = selectedUnit();
        Button train = addRenderableWidget(Button.builder(Component.literal(unit == null ? "TRAIN" : "TRAIN " + trainQuantity + " " + unit.name().toUpperCase(Locale.ROOT)),
                        button -> send(SettlementManager.SettlementAction.TRAIN_TROOPS, unit.type(), trainQuantity))
                .bounds(left + 176, controlY, 198, 22).build());
        train.active = snapshot.canManage() && unit != null && trainQuantity > 0 && trainQuantity <= maxTrain();
        int shown = Math.min(6, Math.max(0, snapshot.training().size() - queueScroll));
        for (int row = 0; row < shown; row++) {
            MilitarySnapshot.TrainingEntry order = snapshot.training().get(queueScroll + row);
            Button cancel = addRenderableWidget(Button.builder(Component.literal("×"), button ->
                            send(SettlementManager.SettlementAction.CANCEL_TRAINING, order.id().toString(), 0))
                    .bounds(left + 655, top + 141 + row * 38, 22, 22).build());
            cancel.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cancel remaining training")));
        }
    }

    private void musterWidgets() {
        MilitarySnapshot.UnitEntry unit = selectedUnit();
        int max = maxMuster();
        QuantitySlider slider = addRenderableWidget(new QuantitySlider(left + 432, top + 246, 150, 22, max));
        slider.active = max > 0;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> { musterQuantity = Math.max(1, musterQuantity - 1); rebuildWidgets(); })
                .bounds(left + 400, top + 246, 24, 22).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> { musterQuantity = Math.min(max, musterQuantity + 1); rebuildWidgets(); })
                .bounds(left + 590, top + 246, 24, 22).build());
        int[] quick = {1, 5, 10, 25};
        for (int i = 0; i < quick.length; i++) {
            int amount = quick[i]; Button button = addRenderableWidget(Button.builder(Component.literal(Integer.toString(amount)), b -> { musterQuantity = amount; rebuildWidgets(); })
                    .bounds(left + 400 + i * 45, top + 274, 40, 20).build()); button.active = max >= amount;
        }
        Button all = addRenderableWidget(Button.builder(Component.literal("All"), b -> { musterQuantity = Math.max(1, max); rebuildWidgets(); })
                .bounds(left + 580, top + 274, 40, 20).build()); all.active = max > 0;
        Button muster = addRenderableWidget(Button.builder(Component.literal(unit == null ? "MUSTER" : "MUSTER " + (max == 0 ? 0 : musterQuantity) + " " + unit.name().toUpperCase(Locale.ROOT)),
                        b -> send(SettlementManager.SettlementAction.MUSTER_TROOPS, unit.type(), musterQuantity))
                .bounds(left + 400, top + 299, 276, 25).build());
        musterButton = muster;
        muster.active = snapshot.canManage() && snapshot.musterGroundSet() && unit != null && max > 0;
    }

    private void send(SettlementManager.SettlementAction action, String argument, int amount) {
        SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(snapshot.settlementId(), action, argument, amount));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = campaignMouseX(mouseX), y = campaignMouseY(mouseY);
        if (button == 0 && (page == TRAIN || page == MUSTER) && x >= left + 20 && x <= left + 376) {
            int firstY = top + 137, row = (int)Math.floor((y - firstY) / 28D);
            if (y >= firstY && row >= 0 && row < snapshot.units().size()) { selected = row; clampQuantities(); rebuildWidgets(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = campaignMouseX(mouseX), y = campaignMouseY(mouseY);
        if (page == TRAIN && x >= left + 400 && x <= left + 682 && y >= top + 132 && y <= top + 372 && snapshot.training().size() > 6) {
            queueScroll = Math.max(0, Math.min(snapshot.training().size() - 6, queueScroll + (delta < 0 ? 1 : -1))); rebuildWidgets(); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); int x = campaignMouseX(mouseX), y = campaignMouseY(mouseY); hoverTooltip = null;
        beginCampaignRender(graphics); drawCampaignFrame(graphics, left, top, W, H, 52);
        graphics.drawString(font, "REALM OF " + snapshot.factionName().toUpperCase(Locale.ROOT), left + 20, top + 10, 0xFFDFBD7C, false);
        graphics.drawString(font, "MILITARY · " + (page == OVERVIEW ? "OVERVIEW" : page == TRAIN ? "RECRUITMENT" : "MUSTER"), left + 20, top + 27, 0xFFF2E6D0, false);
        graphics.drawString(font, fitText(snapshot.settlementName().toUpperCase(Locale.ROOT) + " · MILITARY ADMINISTRATION", 315), left + 365, top + 14, 0xFFDFBD7C, false);
        renderStats(graphics);
        if (page == OVERVIEW) renderOverview(graphics, x, y); else if (page == TRAIN) renderTrain(graphics, x, y); else renderMuster(graphics, x, y);
        if (!snapshot.message().isBlank()) graphics.drawCenteredString(font, fitText(snapshot.message(), W - 40), left + W / 2, top + 382, CAMPAIGN_MESSAGE);
        super.render(graphics, x, y, partialTick); if (hoverTooltip != null) graphics.renderTooltip(font, hoverTooltip, x, y); endCampaignRender(graphics);
    }

    private void renderStats(GuiGraphics g) {
        if (page == TRAIN) {
            statCard(g, left + 18, "AVAILABLE MANPOWER", formatMp((long)snapshot.nativeManpowerMilli() + snapshot.foreignManpowerMilli()) + " MP",
                    formatMp(snapshot.nativeManpowerMilli()) + " native · " + formatMp(snapshot.foreignManpowerMilli()) + " foreign");
            statCard(g, left + 246, "SETTLEMENT TREASURY", snapshot.treasury() + " Silver", "Training is paid upfront");
            statCard(g, left + 474, "TROOPS IN TRAINING", Integer.toString(snapshot.inTraining()), "One global FIFO queue");
        } else if (page == MUSTER) {
            MilitarySnapshot.UnitEntry unit = selectedUnit();
            statCard(g, left + 18, "SELECTED RESERVE", unit == null ? "0" : Integer.toString(unit.ready()), unit == null ? "No troop type" : unit.name());
            statCard(g, left + 246, "PLAYER DEPLOYED", (snapshot.deployedWorld() + snapshot.deployedCampaign()) + " / " + snapshot.deployedCap(),
                    "World " + snapshot.deployedWorld() + " · Campaign " + snapshot.deployedCampaign());
            statCard(g, left + 474, "PENDING ARRIVALS", Integer.toString(snapshot.pendingDeployment()), "Held against the cap");
        } else {
            statCard(g, left + 18, "IN TRAINING", Integer.toString(snapshot.inTraining()), "Chronological queue");
            statCard(g, left + 246, "READY IN RESERVE", Integer.toString(snapshot.readyInReserve()), "Native and foreign");
            statCard(g, left + 474, "TOTAL DEPLOYED", (snapshot.deployedWorld() + snapshot.deployedCampaign()) + " / " + snapshot.deployedCap(),
                    "World " + snapshot.deployedWorld() + " · Campaign " + snapshot.deployedCampaign() + " · Pending " + snapshot.pendingDeployment());
        }
    }

    private void statCard(GuiGraphics g, int x, String label, String value, String detail) {
        g.fill(x, top + 90, x + 210, top + 128, 0xFF352D25); g.fill(x, top + 90, x + 210, top + 91, 0xFF68563D);
        g.drawString(font, label, x + 9, top + 97, 0xFFB7A992, false); g.drawString(font, value, x + 9, top + 109, 0xFFF2E6D0, false);
        g.drawString(font, fitText(detail, 130), x + 70, top + 110, 0xFFB7A992, false);
    }

    private void renderOverview(GuiGraphics g, int mouseX, int mouseY) {
        panel(g, left + 18, top + 137, left + 340, top + 360, "SETTLEMENT TROOP RESERVES");
        for (int i = 0; i < snapshot.units().size(); i++) renderReserve(g, snapshot.units().get(i), left + 28, top + 160 + i * 25, 300, false, mouseX, mouseY);
        panel(g, left + 350, top + 137, left + 682, top + 300, "COMMAND OVERVIEW");
        g.drawString(font, "Available manpower", left + 364, top + 163, 0xFFB7A992, false);
        g.drawString(font, formatMp(snapshot.nativeManpowerMilli()) + " native · " + formatMp(snapshot.foreignManpowerMilli()) + " foreign", left + 364, top + 178, 0xFFF2E6D0, false);
        g.drawString(font, "State Treasury", left + 364, top + 202, 0xFFB7A992, false); g.drawString(font, snapshot.treasury() + " Silver", left + 364, top + 217, 0xFFF2E6D0, false);
        g.drawString(font, "DEPLOYMENT", left + 364, top + 244, 0xFFDFBD7C, false);
        g.drawWordWrap(font, Component.literal("Training creates reserves. Muster deploys one soldier each second into one group owned by the musterer."), left + 364, top + 259, 300, 0xFFB7A992);
        if (snapshot.capturedForeignManpowerMilli() > 0) g.drawString(font, "Captured foreign MP " + formatMp(snapshot.capturedForeignManpowerMilli()) + ": ransom or integrate 75%.", left + 24, top + 320, 0xFFC7B69A, false);
    }

    private void renderTrain(GuiGraphics g, int mouseX, int mouseY) {
        panel(g, left + 18, top + 132, left + 386, top + 374, "TROOP RESERVES");
        for (int i = 0; i < snapshot.units().size(); i++) renderReserve(g, snapshot.units().get(i), left + 24, top + 137 + i * 28, 356, i == selected, mouseX, mouseY);
        MilitarySnapshot.UnitEntry unit = selectedUnit(); if (unit != null) {
            g.drawString(font, "TRAIN " + unit.name().toUpperCase(Locale.ROOT), left + 24, top + 326, 0xFFDFBD7C, false);
            g.drawCenteredString(font, Integer.toString(trainQuantity), left + 82, top + 349, 0xFFF2E6D0);
            long mp = (long)unit.manpowerMilliCost() * trainQuantity, silver = (long)unit.silverCost() * trainQuantity;
            g.drawString(font, formatMp(mp) + " MP · " + silver + " Silver · " + duration((long)unit.trainingTicks() * trainQuantity), left + 176, top + 326, 0xFFC7B69A, false);
        }
        panel(g, left + 396, top + 132, left + 682, top + 374, "TRAINING QUEUE");
        if (snapshot.training().isEmpty()) g.drawWordWrap(font, Component.literal("No troops in training. Choose a troop type and press Train Troops."), left + 420, top + 185, 235, 0xFFB7A992);
        int elapsed = elapsedTicks(), shown = Math.min(6, Math.max(0, snapshot.training().size() - queueScroll));
        for (int row = 0; row < shown; row++) {
            MilitarySnapshot.TrainingEntry order = snapshot.training().get(queueScroll + row); int y = top + 137 + row * 38;
            g.fill(left + 404, y, left + 678, y + 34, 0xFF3A3025);
            g.drawString(font, order.name() + " ×" + order.total(), left + 412, y + 5, 0xFFF2E6D0, false);
            long remaining = Math.max(0L, order.orderRemainingTicks() - (order.active() ? elapsed : 0));
            g.drawString(font, (order.active() ? "In training" : "Waiting") + " · " + order.completed() + "/" + order.total(), left + 412, y + 17, 0xFFB7A992, false);
            g.drawString(font, duration(remaining), left + 596, y + 5, 0xFFDFBD7C, false);
            double progress = (order.completed() + (order.active() ? Math.min(order.durationTicks(), order.progressTicks() + elapsed) / (double)order.durationTicks() : 0D)) / order.total();
            bar(g, left + 526, y + 20, 120, 6, progress, 0D);
        }
    }

    private void renderMuster(GuiGraphics g, int mouseX, int mouseY) {
        panel(g, left + 18, top + 132, left + 386, top + 374, "CHOOSE TROOPS TO MUSTER");
        for (int i = 0; i < snapshot.units().size(); i++) renderReserve(g, snapshot.units().get(i), left + 24, top + 137 + i * 28, 356, i == selected, mouseX, mouseY);
        panel(g, left + 396, top + 132, left + 682, top + 374, "MUSTER ORDER"); MilitarySnapshot.UnitEntry unit = selectedUnit();
        if (unit != null) {
            g.drawString(font, unit.name().toUpperCase(Locale.ROOT) + "  " + unit.ready() + "/" + unit.capacity(), left + 406, top + 155, 0xFFF2E6D0, false);
            bar(g, left + 406, top + 172, 260, 9, unit.nativeReady() / (double)Math.max(1, unit.capacity()), unit.foreignReady() / (double)Math.max(1, unit.capacity()));
            g.drawString(font, "Native " + unit.nativeReady() + " · Foreign " + unit.foreignReady(), left + 406, top + 186, 0xFFB7A992, false);
        }
        g.drawString(font, "Soldiers to muster", left + 406, top + 221, 0xFFDFBD7C, false);
        g.drawString(font, "Max " + maxMuster(), left + 622, top + 227, 0xFFB7A992, false);
        g.drawString(font, "⌖ " + snapshot.musterGroundName(), left + 406, top + 329, snapshot.musterGroundSet() ? 0xFFF2E6D0 : 0xFFE4A08A, false);
        g.drawString(font, "Free · one owned group · one arrival per second", left + 406, top + 342, 0xFFB7A992, false);
        int y = top + 355;
        if (snapshot.musters().isEmpty()) g.drawString(font, "No active muster or acceptance orders.", left + 406, y, 0xFFB7A992, false);
        else {
            MilitarySnapshot.MusterEntry order = snapshot.musters().get(0); String phase = order.phase().equals("ACCEPTING_CONTRACT")
                    ? "Accepting · " + duration(Math.max(0, order.acceptanceTicks() - elapsedTicks()))
                    : order.spawned() + "/" + order.total() + " · " + duration((long)(order.total() - order.spawned()) * 20L)
                    + " · " + (order.active() ? "Mustering" : "Queued");
            String more = snapshot.musters().size() > 1 ? " · +" + (snapshot.musters().size() - 1) + " queued" : "";
            g.drawString(font, fitText(order.name() + " · " + phase + " · " + order.ownerName() + more, 265), left + 406, y, 0xFFC7B69A, false);
            double progress = order.phase().equals("ACCEPTING_CONTRACT")
                    ? 1D - Math.max(0, order.acceptanceTicks() - elapsedTicks()) / (double)Math.max(1, SettlementConfig.MERCENARY_ACCEPTANCE_SECONDS.get() * 20)
                    : order.spawned() / (double)Math.max(1, order.total());
            bar(g, left + 406, y + 12, 260, 6, progress, 0D);
        }
    }

    private void renderReserve(GuiGraphics g, MilitarySnapshot.UnitEntry unit, int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + 24, selected ? 0xFF51412D : 0xFF352D25); if (selected) outline(g, x, y, x + width, y + 24, 0xFFE3BC74);
        g.renderItem(icon(unit.iconItem()), x + 4, y + 4); g.drawString(font, unit.name().toUpperCase(Locale.ROOT), x + 24, y + 4, 0xFFF2E6D0, false);
        g.drawString(font, unit.ready() + "/" + unit.capacity(), x + width - 55, y + 4, 0xFFF2E6D0, false);
        bar(g, x + 24, y + 15, width - 84, 6, unit.nativeReady() / (double)Math.max(1, unit.capacity()), unit.foreignReady() / (double)Math.max(1, unit.capacity()));
        if (unit.pendingTraining() > 0) g.drawString(font, "+" + unit.pendingTraining(), x + width - 24, y + 15, 0xFFDFBD7C, false);
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 24)
            hoverTooltip = Component.literal(unit.description() + "\nNative " + unit.nativeReady() + " · Foreign " + unit.foreignReady() + " · " + unit.pendingTraining() + " training");
    }

    private void panel(GuiGraphics g, int x1, int y1, int x2, int y2, String title) { g.fill(x1, y1, x2, y2, 0xFF29231E); outline(g, x1, y1, x2, y2, 0xFF68563D); g.drawString(font, title, x1 + 8, y1 - 12, 0xFFDFBD7C, false); }
    private void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) { g.fill(x1,y1,x2,y1+1,color);g.fill(x1,y2-1,x2,y2,color);g.fill(x1,y1,x1+1,y2,color);g.fill(x2-1,y1,x2,y2,color); }
    private void bar(GuiGraphics g, int x, int y, int width, int height, double nativePart, double foreignPart) {
        int nativeWidth = (int)Math.round(width * Math.max(0D, Math.min(1D, nativePart))); int foreignWidth = (int)Math.round(width * Math.max(0D, Math.min(1D-nativePart, foreignPart)));
        g.fill(x,y,x+width,y+height,EMPTY); if(nativeWidth>0)g.fill(x,y,x+nativeWidth,y+height,NATIVE); if(foreignWidth>0)g.fill(x+nativeWidth,y,x+nativeWidth+foreignWidth,y+height,FOREIGN);
    }

    private MilitarySnapshot.UnitEntry selectedUnit() { return snapshot.units().isEmpty() ? null : snapshot.units().get(Math.max(0, Math.min(selected, snapshot.units().size()-1))); }
    private int maxTrain() { MilitarySnapshot.UnitEntry u=selectedUnit(); if(u==null)return 0; int capacity=Math.max(0,u.capacity()-u.ready()-u.pendingTraining());
        int mpCost=Math.max(1,u.manpowerMilliCost()); long affordableMp=((long)snapshot.nativeManpowerMilli()+snapshot.foreignManpowerMilli())/mpCost;
        long affordableSilver=u.silverCost()==0?Integer.MAX_VALUE:snapshot.treasury()/u.silverCost(); return (int)Math.max(0,Math.min(capacity,Math.min(affordableMp,affordableSilver))); }
    private int maxMuster() { MilitarySnapshot.UnitEntry u=selectedUnit(); return u==null?0:Math.max(0,Math.min(u.ready(),snapshot.deployedCap()-snapshot.deployedWorld()-snapshot.deployedCampaign()-snapshot.pendingDeployment())); }
    private void clampQuantities() { trainQuantity=Math.max(1,Math.min(trainQuantity,Math.max(1,maxTrain())));musterQuantity=Math.max(1,Math.min(musterQuantity,Math.max(1,maxMuster()))); }
    private int elapsedTicks() { return (int)Math.min(Integer.MAX_VALUE, Math.max(0L,System.currentTimeMillis()-receivedAt)/50L); }
    private static String formatMp(long milli) { return milli%1000==0?Long.toString(milli/1000):String.format(Locale.ROOT,"%.1f",milli/1000D); }
    private static String duration(long ticks) { long sec=Math.max(0,(ticks+19)/20),h=sec/3600,m=(sec/60)%60,s=sec%60;return h>0?String.format(Locale.ROOT,"%02d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%02d:%02d",m,s); }
    private ItemStack icon(String raw) { ResourceLocation id=ResourceLocation.tryParse(raw);Item item=id==null?Items.IRON_SWORD:BuiltInRegistries.ITEM.get(id);return new ItemStack(item==Items.AIR?Items.IRON_SWORD:item); }

    private final class QuantitySlider extends AbstractSliderButton {
        private int maximum;

        private QuantitySlider(int x, int y, int width, int height, int maximum) {
            super(x, y, width, height, Component.empty(), maximum <= 1 ? 0D : (musterQuantity - 1D) / (maximum - 1D));
            this.maximum = Math.max(0, maximum); updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal(Integer.toString(maximum == 0 ? 0 : musterQuantity)));
        }

        @Override protected void applyValue() {
            musterQuantity = maximum <= 1 ? 1 : 1 + (int)Math.round(value * (maximum - 1D));
            updateMessage();
            MilitarySnapshot.UnitEntry unit = selectedUnit();
            if (musterButton != null && unit != null)
                musterButton.setMessage(Component.literal("MUSTER " + (maximum == 0 ? 0 : musterQuantity) + " " + unit.name().toUpperCase(Locale.ROOT)));
        }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int trackY = getY() + height / 2;
            graphics.fill(getX(), trackY - 2, getX() + width, trackY + 2, 0xFF211B16);
            for (int i = 0; i <= 4; i++) {
                int tick = getX() + i * (width - 1) / 4;
                graphics.fill(tick, trackY - 4, tick + 1, trackY + 5, 0xFFF0E6D7);
            }
            int handle = getX() + (int)Math.round(value * (width - 8));
            graphics.fill(handle, getY() + 2, handle + 8, getY() + height - 2, isHoveredOrFocused() ? 0xFFEBC785 : 0xFFAF8950);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 7, 0xFFFFFFFF);
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
