package ee.lastpagans.settlements.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ee.lastpagans.settlements.AreaSelection;
import ee.lastpagans.settlements.network.AreaSelectionPacket;
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
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Two-click in-world editor inspired by Effortless Building's live build previews. */
public final class AreaSelectionEditor {
    private static Session session;
    private static boolean consumePauseScreen;

    private AreaSelectionEditor() {}

    public static void begin(String settlementId, AreaSelection.Type type) {
        begin(settlementId, type, -1);
    }

    public static void begin(String settlementId, AreaSelection.Type type, int areaIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        session = new Session(settlementId, type, AreaSelection.Shape.BOX,
                minecraft.level.dimension().location().toString(), null, areaIndex);
        minecraft.setScreen(null);
        message("Area editor opened: left click to set the first corner.");
    }

    public static boolean active() { return session != null; }

    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (session == null || event.getAction() != InputConstants.PRESS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!shapeToggleAllowed(session.type())) {
                message("Only Capture Zones support cylinder selection.");
                event.setCanceled(true);
                return;
            }
            session = session.withShape(session.shape().next());
            message("Selection shape: " + session.shape().label());
            event.setCanceled(true);
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        BlockPos target = target(minecraft);
        if (target == null) {
            message("Aim at a block to place this corner.");
            event.setCanceled(true);
            return;
        }
        if (session.first() == null) {
            session = session.withFirst(target);
            message(session.type().horizontalOnly()
                    ? "First corner set. The opposite corner is locked to this height."
                    : "First corner set. Aim at the opposite corner and left click again.");
        } else {
            BlockPos second = constrained(target);
            SettlementNetwork.CHANNEL.sendToServer(new AreaSelectionPacket(session.settlementId(), session.type(), session.shape(), session.first(), second, session.areaIndex()));
            message(session.type().label() + " selection submitted.");
            session = null;
        }
        event.setCanceled(true);
    }

    public static void onKey(InputEvent.Key event) {
        if (session == null || event.getAction() != InputConstants.PRESS || event.getKey() != GLFW.GLFW_KEY_ESCAPE) return;
        session = null;
        consumePauseScreen = true;
        message("Area editor closed.");
    }

    public static boolean consumePauseScreen() {
        if (!consumePauseScreen) return false;
        consumePauseScreen = false;
        return true;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || session == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !session.dimension().equals(minecraft.level.dimension().location().toString())) session = null;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (session == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos cursor = target(minecraft);
        if (cursor == null) return;
        cursor = constrained(cursor);
        BlockPos first = session.first() == null ? cursor : session.first();
        BlockPos min = AreaSelection.min(first, cursor), max = AreaSelection.max(first, cursor);
        float[] color = color(session.type());
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (session.shape() == AreaSelection.Shape.BOX) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(min, max.offset(1, 1, 1)), color[0], color[1], color[2], 0.95F);
        } else {
            renderCylinder(pose, lines, min, max, color);
        }
        LevelRenderer.renderLineBox(pose, lines, new AABB(cursor), 1.0F, 1.0F, 1.0F, 0.9F);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (session == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        String title = session.type().label() + " · " + session.shape().label()
                + (session.type().horizontalOnly() ? " · Horizontal" : " · Volume");
        String step = session.first() == null ? "Left click: first corner" : "Left click: confirm opposite corner";
        String controls = shapeToggleAllowed(session.type()) ? "Right click: switch shape   Esc: exit" : "Box selection   Esc: exit";
        int panelWidth = Math.max(minecraft.font.width(title), Math.max(minecraft.font.width(step), minecraft.font.width(controls))) + 18;
        int x = (width - panelWidth) / 2;
        graphics.fill(x, 8, x + panelWidth, 52, 0xB010141B);
        graphics.fill(x, 8, x + panelWidth, 10, 0xFF66D9FF);
        graphics.drawCenteredString(minecraft.font, title, width / 2, 15, 0xFFFFFFFF);
        graphics.drawCenteredString(minecraft.font, step, width / 2, 28, 0xFFBDEFFF);
        graphics.drawCenteredString(minecraft.font, controls, width / 2, 40, 0xFFB9BEC8);
    }

    public static void cancel() { session = null; consumePauseScreen = false; }

    private static BlockPos constrained(BlockPos pos) {
        if (session == null || session.first() == null || !session.type().horizontalOnly()) return pos;
        return new BlockPos(pos.getX(), session.first().getY(), pos.getZ());
    }

    private static BlockPos target(Minecraft minecraft) {
        if (minecraft == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getBlockPos().immutable();
    }

    private static void message(String text) {
        TransientAlertOverlay.show(text);
    }

    private static float[] color(AreaSelection.Type type) {
        return switch (type) {
            case MUSTER, BARRACKS -> new float[]{0.95F, 0.63F, 0.22F};
            case CAPTURE_MAIN, CAPTURE_SECONDARY -> new float[]{0.95F, 0.25F, 0.25F};
            case PRAYER -> new float[]{0.72F, 0.46F, 1.0F};
            case HOUSING -> new float[]{0.25F, 0.85F, 0.55F};
        };
    }

    private static boolean shapeToggleAllowed(AreaSelection.Type type) {
        return type == AreaSelection.Type.CAPTURE_MAIN || type == AreaSelection.Type.CAPTURE_SECONDARY;
    }

    private static void renderCylinder(PoseStack pose, VertexConsumer consumer, BlockPos min, BlockPos max, float[] color) {
        double centerX = (min.getX() + max.getX() + 1.0D) * 0.5D;
        double centerZ = (min.getZ() + max.getZ() + 1.0D) * 0.5D;
        double radiusX = (max.getX() - min.getX() + 1.0D) * 0.5D;
        double radiusZ = (max.getZ() - min.getZ() + 1.0D) * 0.5D;
        double bottom = min.getY(), top = max.getY() + 1.0D;
        int segments = Math.max(24, Math.min(96, (int)Math.ceil(Math.max(radiusX, radiusZ) * 6.0D)));
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments, b = Math.PI * 2.0D * (i + 1) / segments;
            double x1 = centerX + Math.cos(a) * radiusX, z1 = centerZ + Math.sin(a) * radiusZ;
            double x2 = centerX + Math.cos(b) * radiusX, z2 = centerZ + Math.sin(b) * radiusZ;
            line(pose, consumer, x1, bottom, z1, x2, bottom, z2, color);
            line(pose, consumer, x1, top, z1, x2, top, z2, color);
            if (i % Math.max(1, segments / 8) == 0) line(pose, consumer, x1, bottom, z1, x1, top, z1, color);
        }
    }

    private static void line(PoseStack pose, VertexConsumer consumer, double x1, double y1, double z1,
                             double x2, double y2, double z2, float[] color) {
        float nx = (float)(x2 - x1), ny = (float)(y2 - y1), nz = (float)(z2 - z1);
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) { nx /= length; ny /= length; nz /= length; }
        consumer.vertex(pose.last().pose(), (float)x1, (float)y1, (float)z1).color(color[0], color[1], color[2], 0.95F).normal(pose.last().normal(), nx, ny, nz).endVertex();
        consumer.vertex(pose.last().pose(), (float)x2, (float)y2, (float)z2).color(color[0], color[1], color[2], 0.95F).normal(pose.last().normal(), nx, ny, nz).endVertex();
    }

    private record Session(String settlementId, AreaSelection.Type type, AreaSelection.Shape shape, String dimension,
                           BlockPos first, int areaIndex) {
        Session withShape(AreaSelection.Shape value) { return new Session(settlementId, type, value, dimension, first, areaIndex); }
        Session withFirst(BlockPos value) { return new Session(settlementId, type, shape, dimension, value.immutable(), areaIndex); }
    }
}
