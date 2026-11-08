package ee.lastpagans.settlements.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ee.lastpagans.settlements.SettlementConfig;
import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.network.SettlementActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

/** In-world preview and explicit confirmation for the Town Square/Main objective pair. */
public final class TownSquareRelocationEditor {
    private static String settlementId;
    private static boolean consumePauseScreen;
    private TownSquareRelocationEditor() {}

    public static void begin(String id) {
        if (Minecraft.getInstance().player == null) return;
        settlementId = id;
        Minecraft.getInstance().setScreen(null);
        TransientAlertOverlay.show("Move to the desired Town Square centre, then press Enter to confirm.");
    }

    public static void onKey(InputEvent.Key event) {
        if (settlementId == null || event.getAction() != InputConstants.PRESS) return;
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            settlementId = null; consumePauseScreen = true;
            TransientAlertOverlay.show("Town Square relocation cancelled.");
        } else if (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER) {
            SettlementNetwork.CHANNEL.sendToServer(new SettlementActionPacket(settlementId, SettlementManager.SettlementAction.SET_TOWN_SQUARE));
            settlementId = null;
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (settlementId == null || minecraft.player == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        int radius = Math.max(3, SettlementConfig.TOWN_SQUARE_CAPTURE_RADIUS.get());
        int height = Math.max(3, SettlementConfig.TOWN_SQUARE_CAPTURE_HEIGHT.get());
        BlockPos center = minecraft.player.blockPosition();
        AABB bounds = new AABB(center.offset(-radius, 0, -radius), center.offset(radius + 1, height, radius + 1));
        PoseStack pose = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines, bounds, 0.72F, 0.58F, 0.30F, 0.95F);
        LevelRenderer.renderLineBox(pose, lines, new AABB(center), 1.0F, 0.9F, 0.55F, 1.0F);
        buffers.endBatch(RenderType.lines()); pose.popPose();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (settlementId == null) return;
        GuiGraphics graphics = event.getGuiGraphics(); Minecraft minecraft = Minecraft.getInstance();
        int width = event.getWindow().getGuiScaledWidth();
        graphics.fill(width / 2 - 168, 8, width / 2 + 168, 48, 0xD0121417);
        graphics.fill(width / 2 - 168, 8, width / 2 + 168, 10, CampaignTheme.BRASS);
        graphics.drawCenteredString(minecraft.font, "TOWN SQUARE + MAIN OBJECTIVE PREVIEW", width / 2, 16, CampaignTheme.LINEN);
        graphics.drawCenteredString(minecraft.font, "Enter: confirm at your feet   Esc: cancel", width / 2, 31, CampaignTheme.MUTED);
    }

    public static boolean consumePauseScreen() { boolean value = consumePauseScreen; consumePauseScreen = false; return value; }
    public static void cancel() { settlementId = null; consumePauseScreen = false; }
}
