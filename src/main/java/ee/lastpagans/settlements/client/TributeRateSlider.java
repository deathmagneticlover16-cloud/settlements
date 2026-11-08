package ee.lastpagans.settlements.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Four-stop tribute control. It can never emit an unsupported percentage. */
public final class TributeRateSlider extends AbstractSliderButton {
    private static final int[] RATES = {5, 10, 25, 50};
    private final IntConsumer listener;
    private int rate;

    public TributeRateSlider(int x, int y, int width, int height, int initialRate, IntConsumer listener) {
        super(x, y, width, height, Component.empty(), indexOf(initialRate) / 3.0D);
        this.rate = RATES[indexOf(initialRate)];
        this.listener = listener;
        updateMessage();
    }

    public int rate() { return rate; }

    @Override protected void updateMessage() {
        setMessage(Component.literal("Tribute  ·  5%   |   10%   |   25%   |   50%     Selected: " + rate + "%"));
    }

    @Override protected void applyValue() {
        int index = Math.max(0, Math.min(3, (int)Math.round(value * 3.0D)));
        value = index / 3.0D;
        int next = RATES[index];
        if (next != rate) {
            rate = next;
            if (listener != null) listener.accept(rate);
        }
        updateMessage();
    }

    private static int indexOf(int rate) {
        for (int i = 0; i < RATES.length; i++) if (RATES[i] == rate) return i;
        return 1;
    }
}
