package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
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

    public void commitIfChanged() {
        int current = percent();
        if (current == lastCommitted) return;
        lastCommitted = current;
        if (commit != null) commit.accept(current);
    }
}
