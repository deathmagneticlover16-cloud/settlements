package ee.lastpagans.settlements.compat.wildfire;

import com.wildfire.main.Breasts;
import com.wildfire.main.GenderPlayer;
import com.wildfire.main.WildfireGender;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.character.CharacterGender;

import java.util.UUID;

public final class SettlementsWildfireBridge {
    public static final float EPSILON = 0.0001F;

    private SettlementsWildfireBridge() {}

    public static GenderPlayer getPlayer(UUID playerId) {
        return playerId == null ? null : WildfireGender.getOrAddPlayerById(playerId);
    }

    public static GenderPlayer.Gender toWildfireGender(CharacterGender gender) {
        return switch (gender == null ? CharacterGender.OTHER : gender) {
            case MALE -> GenderPlayer.Gender.MALE;
            case FEMALE -> GenderPlayer.Gender.FEMALE;
            case OTHER -> GenderPlayer.Gender.OTHER;
        };
    }

    public static void applyInMemory(GenderPlayer player, CharacterBodyData body) {
        if (player == null || body == null) return;
        CharacterBodyData safe = body.sanitized();
        player.updateGender(toWildfireGender(safe.gender()));
        player.updateBustSize(safe.effectiveBustSize());
        Breasts breasts = player.getBreasts();
        breasts.updateXOffset(safe.breasts().xOffset());
        breasts.updateYOffset(safe.breasts().yOffset());
        breasts.updateZOffset(safe.breasts().zOffset());
        breasts.updateCleavage(safe.breasts().cleavage());
        breasts.updateUniboob(safe.breasts().uniboob());
        player.updateHurtSounds(safe.hurtSounds());
        player.updateBreastPhysics(safe.breastPhysics());
        player.updateShowBreastsInArmor(safe.showBreastsInArmor());
        player.updateArmorPhysicsOverride(safe.armorPhysicsOverride());
        player.updateBounceMultiplier(safe.bounceMultiplier());
        player.updateFloppiness(safe.floppiness());
    }

    public static boolean matches(GenderPlayer player, CharacterBodyData body) {
        if (player == null || body == null) return false;
        CharacterBodyData safe = body.sanitized();
        Breasts breasts = player.getBreasts();
        return player.getGender() == toWildfireGender(safe.gender())
                && close(player.getBustSize(), safe.effectiveBustSize())
                && close(breasts.getXOffset(), safe.breasts().xOffset())
                && close(breasts.getYOffset(), safe.breasts().yOffset())
                && close(breasts.getZOffset(), safe.breasts().zOffset())
                && close(breasts.getCleavage(), safe.breasts().cleavage())
                && breasts.isUniboob() == safe.breasts().uniboob()
                && player.hasHurtSounds() == safe.hurtSounds()
                && player.hasBreastPhysics() == safe.breastPhysics()
                && player.showBreastsInArmor() == safe.showBreastsInArmor()
                && player.getArmorPhysicsOverride() == safe.armorPhysicsOverride()
                && close(player.getBounceMultiplierRaw(), safe.bounceMultiplier())
                && close(player.getFloppiness(), safe.floppiness());
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) <= EPSILON;
    }
}
