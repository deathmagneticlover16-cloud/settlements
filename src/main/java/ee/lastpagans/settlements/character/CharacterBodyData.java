package ee.lastpagans.settlements.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record CharacterBodyData(
        int version,
        CharacterGender gender,
        BreastAppearance breasts,
        boolean hurtSounds,
        boolean breastPhysics,
        boolean showBreastsInArmor,
        boolean armorPhysicsOverride,
        float bounceMultiplier,
        float floppiness
) {
    public static final int CURRENT_VERSION = 1;

    public static CharacterBodyData defaults() {
        return new CharacterBodyData(CURRENT_VERSION, CharacterGender.OTHER, BreastAppearance.defaults(),
                true, true, true, false, 0.34F, 0.75F);
    }

    public CharacterBodyData sanitized() {
        CharacterBodyData defaults = defaults();
        CharacterGender safeGender = gender == null ? defaults.gender : gender;
        BreastAppearance safeBreasts = (breasts == null ? defaults.breasts : breasts).sanitized();
        if (safeGender == CharacterGender.MALE) safeBreasts = safeBreasts.withEnabled(false);
        return new CharacterBodyData(CURRENT_VERSION, safeGender, safeBreasts,
                hurtSounds, breastPhysics, showBreastsInArmor, armorPhysicsOverride,
                safe(bounceMultiplier, defaults.bounceMultiplier, 0.0F, 0.50F),
                safe(floppiness, defaults.floppiness, 0.25F, 1.0F));
    }

    public boolean hasFiniteValues() {
        return gender != null && breasts != null && breasts.hasFiniteValues()
                && Float.isFinite(bounceMultiplier) && Float.isFinite(floppiness);
    }

    public float effectiveBustSize() {
        CharacterBodyData safe = sanitized();
        return safe.gender != CharacterGender.MALE && safe.breasts.enabled() ? safe.breasts.bustSize() : 0.0F;
    }

    public CharacterBodyData withGender(CharacterGender value) {
        return new CharacterBodyData(version, value, breasts, hurtSounds, breastPhysics,
                showBreastsInArmor, armorPhysicsOverride, bounceMultiplier, floppiness).sanitized();
    }

    public CharacterBodyData withBreasts(BreastAppearance value) {
        return new CharacterBodyData(version, gender, value, hurtSounds, breastPhysics,
                showBreastsInArmor, armorPhysicsOverride, bounceMultiplier, floppiness).sanitized();
    }

    public CompoundTag toNBT() {
        CharacterBodyData safe = sanitized();
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", safe.version);
        tag.putString("Gender", safe.gender.name());
        tag.putBoolean("HasBreasts", safe.breasts.enabled());
        tag.putFloat("BustSize", safe.breasts.bustSize());
        tag.putFloat("XOffset", safe.breasts.xOffset());
        tag.putFloat("YOffset", safe.breasts.yOffset());
        tag.putFloat("ZOffset", safe.breasts.zOffset());
        tag.putFloat("Cleavage", safe.breasts.cleavage());
        tag.putBoolean("Uniboob", safe.breasts.uniboob());
        tag.putBoolean("HurtSounds", safe.hurtSounds);
        tag.putBoolean("BreastPhysics", safe.breastPhysics);
        tag.putBoolean("ShowInArmor", safe.showBreastsInArmor);
        tag.putBoolean("ArmorPhysicsOverride", safe.armorPhysicsOverride);
        tag.putFloat("BounceMultiplier", safe.bounceMultiplier);
        tag.putFloat("Floppiness", safe.floppiness);
        return tag;
    }

    public static CharacterBodyData fromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return defaults();
        CharacterBodyData defaults = defaults();
        CharacterGender gender = CharacterGender.parse(tag.getString("Gender"));
        BreastAppearance breasts = new BreastAppearance(
                tag.getBoolean("HasBreasts"),
                number(tag, "BustSize", defaults.breasts.bustSize()),
                number(tag, "XOffset", defaults.breasts.xOffset()),
                number(tag, "YOffset", defaults.breasts.yOffset()),
                number(tag, "ZOffset", defaults.breasts.zOffset()),
                number(tag, "Cleavage", defaults.breasts.cleavage()),
                !tag.contains("Uniboob", Tag.TAG_BYTE) || tag.getBoolean("Uniboob"));
        return new CharacterBodyData(
                tag.contains("Version", Tag.TAG_ANY_NUMERIC) ? tag.getInt("Version") : CURRENT_VERSION,
                gender == null ? defaults.gender : gender,
                breasts,
                !tag.contains("HurtSounds", Tag.TAG_BYTE) || tag.getBoolean("HurtSounds"),
                !tag.contains("BreastPhysics", Tag.TAG_BYTE) || tag.getBoolean("BreastPhysics"),
                !tag.contains("ShowInArmor", Tag.TAG_BYTE) || tag.getBoolean("ShowInArmor"),
                tag.getBoolean("ArmorPhysicsOverride"),
                number(tag, "BounceMultiplier", defaults.bounceMultiplier),
                number(tag, "Floppiness", defaults.floppiness)).sanitized();
    }

    private static float number(CompoundTag tag, String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    private static float safe(float value, float fallback, float minimum, float maximum) {
        if (!Float.isFinite(value)) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
