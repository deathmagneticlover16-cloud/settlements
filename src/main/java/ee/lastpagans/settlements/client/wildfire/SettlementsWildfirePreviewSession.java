package ee.lastpagans.settlements.client.wildfire;

import com.mojang.logging.LogUtils;
import com.wildfire.gui.screen.WildfireBreastCustomizationScreen;
import com.wildfire.main.Breasts;
import com.wildfire.main.GenderPlayer;
import com.wildfire.main.networking.WildfireSync;
import ee.lastpagans.settlements.character.BreastAppearance;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import org.slf4j.Logger;

import java.util.UUID;

public final class SettlementsWildfirePreviewSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static UUID playerId;
    private static Screen parent;
    private static WildfireBreastCustomizationScreen editor;
    private static WildfireBodySnapshot snapshot;
    private static boolean active;

    private SettlementsWildfirePreviewSession() {}

    public static boolean begin(Screen parentScreen, CharacterBodyData draft) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active || minecraft.player == null || parentScreen == null || draft == null) return false;
        CharacterBodyData safe = draft.sanitized();
        if (safe.gender() == ee.lastpagans.settlements.character.CharacterGender.MALE) return false;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(minecraft.player.getUUID());
        if (wildfire.needsSync) {
            WildfireSync.sendToServer(wildfire);
            wildfire.needsSync = false;
        }
        playerId = minecraft.player.getUUID();
        parent = parentScreen;
        snapshot = WildfireBodySnapshot.capture(wildfire);
        active = true;
        SettlementsWildfireBridge.applyInMemory(wildfire, safe.withBreasts(safe.breasts().withEnabled(true)));
        wildfire.needsSync = false;
        editor = new WildfireBreastCustomizationScreen(parentScreen, playerId);
        LOGGER.info("[Settlements/Wildfire] Beginning temporary breast preview for {}", playerId);
        minecraft.setScreen(editor);
        return true;
    }

    public static boolean shouldSuppressSave(GenderPlayer player) {
        return active && player != null && playerId != null && playerId.equals(player.uuid);
    }

    public static boolean isAuthorized(Screen screen) {
        return active && screen != null && screen == editor;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!active || event.getCurrentScreen() != editor || event.getNewScreen() == editor) return;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(playerId);
        if (event.getNewScreen() == parent && parent instanceof BreastDraftReceiver receiver && wildfire != null) {
            Breasts breasts = wildfire.getBreasts();
            receiver.settlements$acceptBreastDraft(new BreastAppearance(true, wildfire.getBustSize(),
                    breasts.getXOffset(), breasts.getYOffset(), breasts.getZOffset(), breasts.getCleavage(),
                    breasts.isUniboob()).sanitized());
        }
        restoreAndClear();
    }

    public static void cancel() {
        if (active) restoreAndClear();
    }

    private static void restoreAndClear() {
        UUID restoredId = playerId;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(playerId);
        if (wildfire != null && snapshot != null) {
            snapshot.restore(wildfire);
            wildfire.needsSync = false;
        }
        active = false;
        playerId = null;
        parent = null;
        editor = null;
        snapshot = null;
        LOGGER.info("[Settlements/Wildfire] Restored pre-preview state for {}", restoredId);
    }

    private record WildfireBodySnapshot(
            GenderPlayer.Gender gender,
            float bustSize,
            float xOffset,
            float yOffset,
            float zOffset,
            float cleavage,
            boolean uniboob,
            boolean hurtSounds,
            boolean breastPhysics,
            boolean showInArmor,
            boolean armorPhysicsOverride,
            float bounceMultiplier,
            float floppiness
    ) {
        static WildfireBodySnapshot capture(GenderPlayer player) {
            Breasts breasts = player.getBreasts();
            return new WildfireBodySnapshot(player.getGender(), player.getBustSize(), breasts.getXOffset(),
                    breasts.getYOffset(), breasts.getZOffset(), breasts.getCleavage(), breasts.isUniboob(),
                    player.hasHurtSounds(), player.hasBreastPhysics(), player.showBreastsInArmor(),
                    player.getArmorPhysicsOverride(), player.getBounceMultiplierRaw(), player.getFloppiness());
        }

        void restore(GenderPlayer player) {
            player.updateGender(gender);
            player.updateBustSize(bustSize);
            Breasts breasts = player.getBreasts();
            breasts.updateXOffset(xOffset);
            breasts.updateYOffset(yOffset);
            breasts.updateZOffset(zOffset);
            breasts.updateCleavage(cleavage);
            breasts.updateUniboob(uniboob);
            player.updateHurtSounds(hurtSounds);
            player.updateBreastPhysics(breastPhysics);
            player.updateShowBreastsInArmor(showInArmor);
            player.updateArmorPhysicsOverride(armorPhysicsOverride);
            player.updateBounceMultiplier(bounceMultiplier);
            player.updateFloppiness(floppiness);
        }
    }
}
