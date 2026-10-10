package ee.lastpagans.settlements.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;

/** One non-stacking status line with an explicit lifetime, placed away from the hotbar. */
public final class TransientAlertOverlay {
    private static Component message;
    private static int remainingTicks;

    private TransientAlertOverlay() {}

    public static void show(String text) {
        message = Component.literal(text == null ? "" : text);
        remainingTicks = 60;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || remainingTicks <= 0) return;
        if (--remainingTicks == 0) message = null;
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (message == null || remainingTicks <= 0 || Minecraft.getInstance().options.hideGui
                || Minecraft.getInstance().screen != null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        String raw = message.getString();
        String visible = Minecraft.getInstance().font.plainSubstrByWidth(raw, 264);
        if (visible.length() < raw.length() && visible.length() > 1) visible = visible.substring(0, visible.length() - 1) + "…";
        Component rendered = Component.literal(visible);
        int textWidth = Minecraft.getInstance().font.width(rendered);
        int width = Math.min(280, textWidth + 16);
        int x = 12;
        int y = 66;
        int alpha = remainingTicks < 10 ? Math.max(30, remainingTicks * 22) : 210;
        graphics.fill(x, y, x + width, y + 22, (alpha << 24) | 0x10141B);
        graphics.fill(x, y, x + 3, y + 22, (Math.min(255, alpha + 30) << 24) | 0x66D9FF);
        graphics.drawString(Minecraft.getInstance().font, rendered, x + 9, y + 7, (Math.min(255, alpha + 30) << 24) | 0xFFFFFF, false);
    }

    public static void clear() { message = null; remainingTicks = 0; }
}
