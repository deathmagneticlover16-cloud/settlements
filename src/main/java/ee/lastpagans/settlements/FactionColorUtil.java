package ee.lastpagans.settlements;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;

import java.util.Locale;

/** Color bridge between Settlements' dynamic DyeColor palette and Recruits' legacy color formats. */
public final class FactionColorUtil {
    // Recruits 1.15.2 FactionEditScreen palette, index-for-index with RecruitsFaction.unitColor.
    private static final int[] RECRUITS_UNIT_RGB = {
            0xFFFFFF, 0x000000, 0xD3D3D3, 0x808080, 0x404040,
            0x337B9A, 0x335C9A, 0x15253E,
            0x269C08, 0x008000, 0x0E3903,
            0xB91515, 0x941010, 0x4A0808,
            0x8C4C18, 0x5E3B1D, 0x3F2713,
            0x009696, 0x174444, 0x0B2222,
            0xEDCA12, 0xE56A11, 0xFF00FF, 0x570265, 0xDD9A19
    };

    private FactionColorUtil() {}

    public static DyeColor dye(String name) {
        String wanted = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (DyeColor color : DyeColor.values()) {
            if (color.getName().equalsIgnoreCase(wanted) || color.name().equalsIgnoreCase(wanted)) return color;
        }
        return DyeColor.WHITE;
    }

    public static ChatFormatting closestChatFormatting(DyeColor dye) {
        int rgb = dye == null ? DyeColor.WHITE.getTextColor() : dye.getTextColor();
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (ChatFormatting formatting : ChatFormatting.values()) {
            Integer candidate = formatting.getColor();
            if (candidate == null) continue;
            long distance = distance(rgb, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = formatting;
            }
        }
        return best;
    }

    public static byte closestRecruitsUnitColor(DyeColor dye) {
        int rgb = dye == null ? DyeColor.WHITE.getTextColor() : dye.getTextColor();
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < RECRUITS_UNIT_RGB.length; i++) {
            long distance = distance(rgb, RECRUITS_UNIT_RGB[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return (byte)best;
    }

    public static int rgb(DyeColor dye) {
        return dye == null ? DyeColor.WHITE.getTextColor() : dye.getTextColor();
    }

    private static long distance(int a, int b) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        long dr = ar - br, dg = ag - bg, db = ab - bb;
        return dr * dr + dg * dg + db * db;
    }
}
