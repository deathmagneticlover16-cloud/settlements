package ee.lastpagans.settlements.mixin;

import ee.lastpagans.settlements.client.ScaledCampaignScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives every Settlements campaign action one coherent, asset-independent visual language. */
@Mixin(AbstractWidget.class)
public abstract class CampaignButtonMixin {
    @Inject(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At("TAIL"),
            remap = false
    )
    private void settlements$renderCampaignButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ScaledCampaignScreen) || !((Object) this instanceof Button button)) return;
        int x = button.getX(), y = button.getY(), width = button.getWidth(), height = button.getHeight();
        boolean highlighted = button.active && button.isHoveredOrFocused();
        int border = button.active ? (highlighted ? 0xFFF0D18A : 0xFF9C8355) : 0xFF4B4740;
        int fill = button.active ? (highlighted ? 0xFF51452F : 0xFF302C25) : 0xFF252525;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.fill(x + 2, y + 2, x + 4, y + height - 2,
                button.active ? (highlighted ? 0xFFFFE2A3 : 0xFFB89A5E) : 0xFF555555);
        int textColor = button.active ? 0xFFF4E8CC : 0xFF777777;
        String label = button.getMessage().getString();
        int available = Math.max(1, width - 12);
        if (minecraft.font.width(label) > available) {
            label = minecraft.font.plainSubstrByWidth(label, Math.max(1, available - minecraft.font.width("…"))) + "…";
        }
        graphics.drawCenteredString(minecraft.font, Component.literal(label), x + width / 2,
                y + Math.max(2, (height - 8) / 2), textColor);
    }
}
