package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

/** Renders the player's full live 3D model inside a framed GUI area. */
public final class PortraitRenderer {
    private PortraitRenderer() {}

    public static void render(GuiGraphics graphics, LivingEntity entity, int x, int y, int width, int height,
                              int mouseX, int mouseY) {
        if (entity == null || width <= 0 || height <= 0) return;
        graphics.enableScissor(x, y, x + width, y + height);
        int centreX = x + width / 2;
        int baseline = y + height - 7;
        int scale = Math.max(20, Math.min(68, Math.min(width / 2, (int) (height * 0.40F))));
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                centreX,
                baseline,
                scale,
                centreX - mouseX,
                y + height / 3 - mouseY,
                entity
        );
        graphics.disableScissor();
    }
}
