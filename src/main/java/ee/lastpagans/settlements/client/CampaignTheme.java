package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared original dark-brass strategy UI palette and primitive rendering. */
public final class CampaignTheme {
    public static final int BACKDROP = 0xF3121417;
    public static final int SURFACE = 0xF51C2025;
    public static final int SURFACE_RAISED = 0xF5282B30;
    public static final int INSET = 0xF516181B;
    public static final int BRASS = 0xFFB79A5B;
    public static final int BRASS_DARK = 0xFF66512E;
    public static final int LINEN = 0xFFF0E6CF;
    public static final int MUTED = 0xFFA9A294;
    public static final int GOOD = 0xFF91B782;
    public static final int WARN = 0xFFD0A25D;
    public static final int DANGER = 0xFFC66A62;

    private CampaignTheme() {}

    public static void frame(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, BRASS_DARK);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, BACKDROP);
        graphics.fill(left + 5, top + 5, right - 5, bottom - 5, SURFACE);
    }

    public static void panel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, BRASS_DARK);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, SURFACE_RAISED);
    }

    public static void inset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0xFF34383D);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, INSET);
    }
}
