package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/** 50–150% continuous taxation control. Network commit happens on release, not every drag pixel. */
public final class TaxRateSlider extends AbstractSliderButton {
    private final IntConsumer commit;
    private int lastCommitted;

    public TaxRateSlider(int x, int y, int width, int height, int percent, IntConsumer commit) {
        super(x, y, width, height, Component.empty(), (Mth.clamp(percent, 50, 150) - 50) / 100.0D);
        this.commit = commit;
        this.lastCommitted = percent();
        updateMessage();
    }

    public int percent() { return Mth.clamp(50 + (int)Math.round(value * 100.0D), 50, 150); }

    private String band() {
        int value = percent();
        if (value <= 75) return "Low";
        if (value >= 125) return "High";
        return "Normal";
    }

    @Override protected void updateMessage() { setMessage(Component.literal("Taxation: " + percent() + "% — " + band())); }
    @Override protected void applyValue() { updateMessage(); }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int trackY = getY() + height - 5;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, active ? 0xFF9C8355 : 0xFF4B4740);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF24211C);
        graphics.fill(getX() + 7, trackY, getX() + width - 7, trackY + 2, 0xFF111111);
        int handleX = getX() + 7 + (int)Math.round((width - 14) * value);
        int handleColor = active && isHoveredOrFocused() ? 0xFFFFE2A3 : 0xFFB89A5E;
        graphics.fill(handleX - 2, trackY - 3, handleX + 3, trackY + 5, handleColor);
        var font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int available = Math.max(1, width - 12);
        if (font.width(label) > available) label = font.plainSubstrByWidth(label, Math.max(1, available - font.width("…"))) + "…";
        graphics.drawCenteredString(font, label, getX() + width / 2,
                getY() + 3, active ? 0xFFF4E8CC : 0xFF777777);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) commitIfChanged();
        return handled;
    }

    public void commitIfChanged() {
        int current = percent();
        if (current == lastCommitted) return;
        lastCommitted = current;
        if (commit != null) commit.accept(current);
    }
}
