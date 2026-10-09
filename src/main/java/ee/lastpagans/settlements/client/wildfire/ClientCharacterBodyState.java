package ee.lastpagans.settlements.client.wildfire;

import com.mojang.logging.LogUtils;
import com.wildfire.main.GenderPlayer;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.client.CharacterCreationDraftSession;
import ee.lastpagans.settlements.client.CharacterDraftScreen;
import ee.lastpagans.settlements.client.TraitSelectionScreen;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireBridge;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public final class ClientCharacterBodyState {
    public enum LockState { UNMANAGED, ONBOARDING, LOCKED }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static LockState state = LockState.UNMANAGED;
    private static UUID playerId;
    private static CharacterBodyData canonicalBody;
    private static int checkTicks;
    private static CharacterCreationDraftSession draftSession;
    private static final Map<UUID, CharacterBodyData> observedBodies = new HashMap<>();

    private ClientCharacterBodyState() {}

    public static void beginOnboarding() {
        if (state == LockState.ONBOARDING && draftSession != null) return;
        cancelDraft();
        state = LockState.ONBOARDING;
        canonicalBody = null;
        Minecraft minecraft = Minecraft.getInstance();
        playerId = minecraft.player == null ? null : minecraft.player.getUUID();
        draftSession = new CharacterCreationDraftSession(playerId);
    }

    public static CharacterCreationDraftSession draftSession() { return draftSession; }

    public static boolean isOnboardingPlayer(UUID id) {
        return state == LockState.ONBOARDING && id != null && id.equals(playerId);
    }

    public static boolean suppressWildfirePacket(UUID id) {
        return isOnboardingPlayer(id) || observedBodies.containsKey(id);
    }

    public static void reject(UUID sessionId, String message) {
        if (draftSession == null || !draftSession.id().equals(sessionId)
                || draftSession.state() != CharacterCreationDraftSession.State.SUBMITTING) return;
        draftSession.reject(sessionId, message);
        Minecraft.getInstance().setScreen(new TraitSelectionScreen(draftSession));
    }

    public static void onScreenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        if (event.isCanceled() || draftSession == null) return;
        if (event.getNewScreen() instanceof CharacterDraftScreen screen && screen.draftSession() == draftSession
                || SettlementsWildfirePreviewSession.isAuthorized(event.getNewScreen())) return;
        cancelDraft();
    }

    private static void cancelDraft() {
        SettlementsWildfirePreviewSession.cancel();
        if (draftSession != null) draftSession.cancel();
        draftSession = null;
        if (state == LockState.ONBOARDING) state = LockState.UNMANAGED;
    }

    public static void accept(UUID id, CharacterBodyData body) {
        if (id == null || body == null || !body.hasFiniteValues()) return;
        observedBodies.put(id, body.sanitized());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !id.equals(minecraft.player.getUUID())) {
            SettlementsWildfireBridge.applyInMemory(SettlementsWildfireBridge.getPlayer(id), body);
            return;
        }
        // Restore the original Wildfire object before applying canonical state; never afterwards.
        SettlementsWildfirePreviewSession.cancel();
        if (draftSession != null) draftSession.accept(body.sanitized());
        draftSession = null;
        playerId = id;
        canonicalBody = body.sanitized();
        state = LockState.LOCKED;
        applyCanonical(true);
    }

    public static void clear(UUID id) {
        if (id == null) observedBodies.clear();
        else observedBodies.remove(id);
        if (id != null && playerId != null && !id.equals(playerId)) return;
        cancelDraft();
        state = LockState.UNMANAGED;
        playerId = null;
        canonicalBody = null;
        checkTicks = 0;
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (state == LockState.ONBOARDING && minecraft.player == null) { clear(null); return; }
        if (++checkTicks < 20) return;
        checkTicks = 0;
        observedBodies.forEach((id, body) -> {
            if (minecraft.player != null && id.equals(minecraft.player.getUUID())) return;
            GenderPlayer observed = SettlementsWildfireBridge.getPlayer(id);
            if (!SettlementsWildfireBridge.matches(observed, body)) {
                SettlementsWildfireBridge.applyInMemory(observed, body);
                observed.needsSync = false;
            }
        });
        if (state != LockState.LOCKED || canonicalBody == null) return;
        if (minecraft.player == null || !minecraft.player.getUUID().equals(playerId)) return;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(playerId);
        if (!SettlementsWildfireBridge.matches(wildfire, canonicalBody)) {
            applyCanonical(false);
            LOGGER.info("[Settlements/Wildfire] Corrected stale local Wildfire body for {}", playerId);
        }
    }

    public static boolean blocksWildfireScreens() {
        return state == LockState.ONBOARDING || state == LockState.LOCKED;
    }

    public static LockState state() { return state; }

    private static void applyCanonical(boolean persistEvenIfUnchanged) {
        if (canonicalBody == null || playerId == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !playerId.equals(minecraft.player.getUUID())) return;
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(playerId);
        boolean changed = !SettlementsWildfireBridge.matches(wildfire, canonicalBody);
        if (changed) SettlementsWildfireBridge.applyInMemory(wildfire, canonicalBody);
        if (changed || persistEvenIfUnchanged) GenderPlayer.saveGenderInfo(wildfire);
        wildfire.needsSync = false;
    }
}
