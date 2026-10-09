package ee.lastpagans.settlements.client.wildfire;

import com.mojang.logging.LogUtils;
import com.wildfire.main.GenderPlayer;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireBridge;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.UUID;

public final class ClientCharacterBodyState {
    public enum LockState { UNMANAGED, ONBOARDING, LOCKED }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static LockState state = LockState.UNMANAGED;
    private static UUID playerId;
    private static CharacterBodyData canonicalBody;
    private static int checkTicks;

    private ClientCharacterBodyState() {}

    public static void beginOnboarding() {
        SettlementsWildfirePreviewSession.cancel();
        state = LockState.ONBOARDING;
        canonicalBody = null;
        Minecraft minecraft = Minecraft.getInstance();
        playerId = minecraft.player == null ? null : minecraft.player.getUUID();
    }

    public static void accept(UUID id, CharacterBodyData body) {
        if (id == null || body == null || !body.hasFiniteValues()) return;
        playerId = id;
        canonicalBody = body.sanitized();
        state = LockState.LOCKED;
        applyCanonical(true);
    }

    public static void clear(UUID id) {
        if (id != null && playerId != null && !id.equals(playerId)) return;
        SettlementsWildfirePreviewSession.cancel();
        state = LockState.UNMANAGED;
        playerId = null;
        canonicalBody = null;
        checkTicks = 0;
    }

    public static void onClientTick() {
        if (state != LockState.LOCKED || canonicalBody == null) return;
        if (++checkTicks < 20) return;
        checkTicks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getUUID().equals(playerId)) return;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(playerId);
        if (!SettlementsWildfireBridge.matches(wildfire, canonicalBody)) {
            applyCanonical(true);
            LOGGER.info("[Settlements/Wildfire] Corrected stale local Wildfire body for {}", playerId);
        }
    }

    public static boolean blocksWildfireScreens() {
        return state == LockState.ONBOARDING || state == LockState.LOCKED;
    }

    public static LockState state() { return state; }

    private static void applyCanonical(boolean persistIfChanged) {
        if (canonicalBody == null || playerId == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !playerId.equals(minecraft.player.getUUID())) return;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(playerId);
        boolean changed = !SettlementsWildfireBridge.matches(wildfire, canonicalBody);
        if (changed) SettlementsWildfireBridge.applyInMemory(wildfire, canonicalBody);
        if (changed && persistIfChanged) GenderPlayer.saveGenderInfo(wildfire);
        wildfire.needsSync = false;
    }
}
