package ee.lastpagans.settlements.character;

public record BreastAppearance(
        boolean enabled,
        float bustSize,
        float xOffset,
        float yOffset,
        float zOffset,
        float cleavage,
        boolean uniboob
) {
    public static final float DEFAULT_BUST = 0.60F;

    public static BreastAppearance defaults() {
        return new BreastAppearance(false, DEFAULT_BUST, 0.0F, 0.0F, 0.0F, 0.0F, true);
    }

    public BreastAppearance sanitized() {
        BreastAppearance defaults = defaults();
        return new BreastAppearance(enabled,
                safe(bustSize, defaults.bustSize, 0.0F, 0.80F),
                safe(xOffset, defaults.xOffset, -1.0F, 1.0F),
                safe(yOffset, defaults.yOffset, -1.0F, 1.0F),
                safe(zOffset, defaults.zOffset, -1.0F, 0.0F),
                safe(cleavage, defaults.cleavage, 0.0F, 0.10F),
                uniboob);
    }

    public boolean hasFiniteValues() {
        return Float.isFinite(bustSize) && Float.isFinite(xOffset) && Float.isFinite(yOffset)
                && Float.isFinite(zOffset) && Float.isFinite(cleavage);
    }

    public BreastAppearance withEnabled(boolean value) {
        return new BreastAppearance(value, bustSize, xOffset, yOffset, zOffset, cleavage, uniboob);
    }

    private static float safe(float value, float fallback, float minimum, float maximum) {
        if (!Float.isFinite(value)) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
