package ee.lastpagans.settlements.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ee.lastpagans.settlements.HousingAreasSnapshot;
import ee.lastpagans.settlements.network.HousingAreaActionPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

public final class HousingAreaOverlay {
    private static HousingAreasSnapshot snapshot;
    private static boolean consumePauseScreen;

    private HousingAreaOverlay() {}

    public static void begin(HousingAreasSnapshot value) {
        snapshot = value;
        Minecraft.getInstance().setScreen(null);
    }

    public static boolean active() { return snapshot != null; }
    public static void update(HousingAreasSnapshot value) {
        if (snapshot != null && snapshot.settlementId().equals(value.settlementId())) snapshot = value;
    }
    public static void cancel() { snapshot = null; consumePauseScreen = false; }
    public static boolean consumePauseScreen() {
        if (!consumePauseScreen) return false;
        consumePauseScreen = false;
        return true;
    }

    public static void onKey(InputEvent.Key event) {
        if (snapshot == null || event.getAction() != InputConstants.PRESS) return;
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            snapshot = null;
            consumePauseScreen = true;
        } else if (event.getKey() == GLFW.GLFW_KEY_DELETE && snapshot.canManage()) {
            HousingAreasSnapshot.Entry target = targeted();
            if (target != null) SettlementNetwork.CHANNEL.sendToServer(new HousingAreaActionPacket(snapshot.settlementId(),
                    HousingAreaActionPacket.Action.DELETE, target.index()));
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (snapshot == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        String dimension = minecraft.level.dimension().location().toString();
        HousingAreasSnapshot.Entry targeted = targeted();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (HousingAreasSnapshot.Entry area : snapshot.areas()) {
            if (!dimension.equals(area.dimension())) continue;
            boolean selected = targeted != null && targeted.index() == area.index();
            float r = selected ? 1.0F : 0.25F, g = selected ? 0.28F : 0.9F, b = selected ? 0.22F : 0.52F;
            LevelRenderer.renderLineBox(pose, lines, new AABB(area.min(), area.max().offset(1, 1, 1)), r, g, b, 0.95F);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (snapshot == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        HousingAreasSnapshot.Entry target = targeted();
        int x = 12, y = 12, width = 246;
        graphics.fill(x, y, x + width, y + 48, 0xB010141B);
        graphics.fill(x, y, x + 3, y + 48, 0xFF63D88C);
        graphics.drawString(Minecraft.getInstance().font, "Housing areas: " + snapshot.areas().size(), x + 10, y + 8, 0xFFFFFFFF, false);
        graphics.drawString(Minecraft.getInstance().font, target == null ? "Aim inside an area to select it" : "Selected: Area " + (target.index() + 1), x + 10, y + 21, target == null ? 0xFFB9BEC8 : 0xFFFFB39A, false);
        String controls = snapshot.canManage() ? "Delete: remove selected   Esc: exit" : "Esc: exit";
        graphics.drawString(Minecraft.getInstance().font, controls, x + 10, y + 34, 0xFFB9BEC8, false);
    }

    private static HousingAreasSnapshot.Entry targeted() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return null;
        String dimension = minecraft.level.dimension().location().toString();
        BlockPos pos = hit.getBlockPos();
        for (HousingAreasSnapshot.Entry area : snapshot.areas()) {
            if (dimension.equals(area.dimension()) && pos.getX() >= area.min().getX() && pos.getX() <= area.max().getX()
                    && pos.getY() >= area.min().getY() && pos.getY() <= area.max().getY()
                    && pos.getZ() >= area.min().getZ() && pos.getZ() <= area.max().getZ()) return area;
        }
        return null;
    }
}
