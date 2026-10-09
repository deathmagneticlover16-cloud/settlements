package ee.lastpagans.settlements.client.wildfire;

import com.wildfire.gui.screen.WildfireBreastCustomizationScreen;
import com.wildfire.main.Breasts;
import com.wildfire.main.GenderPlayer;
import com.wildfire.main.WildfireHelper;
import com.wildfire.physics.BreastPhysics;
import ee.lastpagans.settlements.character.BreastAppearance;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.client.CharacterCreationDraftSession;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireBridge;
import ee.lastpagans.settlements.mixin.WildfirePortraitPhysicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.UUID;

/** Exposes detached draft objects to Wildfire; the live object and its config are never edited. */
public final class SettlementsWildfirePreviewSession {
    private static UUID playerId;
    private static Screen parent;
    private static WildfireBreastCustomizationScreen editor;
    private static CharacterCreationDraftSession session;
    private static GenderPlayer temporary;
    private static GenderPlayer portrait;
    private static CharacterCreationDraftSession portraitSession;
    private static boolean rendering;
    private static final ThreadLocal<Boolean> constructingDraft = ThreadLocal.withInitial(() -> false);

    private SettlementsWildfirePreviewSession() {}

    public static boolean begin(Screen parentScreen, CharacterCreationDraftSession draftSession) {
        Minecraft minecraft = Minecraft.getInstance();
        if (editor != null || minecraft.player == null || parentScreen == null
                || draftSession != ClientCharacterBodyState.draftSession() || !draftSession.beginEditor()) return false;
        playerId = minecraft.player.getUUID();
        parent = parentScreen;
        session = draftSession;
        try {
            temporary = createDraftPlayer(playerId);
            SettlementsWildfireBridge.applyInMemory(temporary, session.draft().body());
            temporary.needsSync = false;
            editor = new WildfireBreastCustomizationScreen(parentScreen, playerId);
            minecraft.setScreen(editor);
            if (minecraft.screen != editor) cancel();
        } catch (RuntimeException | Error failure) {
            cancel();
            throw failure;
        }
        return editor != null;
    }

    public static boolean shouldSuppressSave(GenderPlayer player) {
        return Minecraft.getInstance().isSameThread() && player != null && (rendering && player == portrait || player == temporary
                || ClientCharacterBodyState.isOnboardingPlayer(player.uuid));
    }

    public static GenderPlayer draftPlayerForLookup(UUID id) {
        if (id == null || !Minecraft.getInstance().isSameThread()) return null;
        if (rendering && portrait != null && id.equals(portrait.uuid)) return portrait;
        return temporary != null && id.equals(playerId) ? temporary : null;
    }

    public static boolean isAuthorized(Screen screen) { return screen != null && screen == editor; }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (temporary == null || minecraft.player == null) return;
        var armor = WildfireHelper.getArmorConfig(minecraft.player.getItemBySlot(EquipmentSlot.CHEST));
        temporary.getLeftBreastPhysics().update(minecraft.player, armor);
        temporary.getRightBreastPhysics().update(minecraft.player, armor);
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.isCanceled() || editor == null || event.getCurrentScreen() != editor || event.getNewScreen() == editor) return;
        boolean returned = event.getNewScreen() == parent;
        CharacterBodyData edited = returned ? capture(temporary, session.draft().body()) : null;
        session.finishEditor(edited, returned);
        restoreEditor();
    }

    public static void cancel() {
        if (session != null) session.finishEditor(null, false);
        restoreEditor();
        portrait = null;
        portraitSession = null;
    }

    private static void restoreEditor() {
        playerId = null;
        parent = null;
        editor = null;
        session = null;
        temporary = null;
    }

    public static void renderDraft(CharacterCreationDraftSession draftSession, Runnable render) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || draftSession.draft() == null) return;
        UUID id = minecraft.player.getUUID();
        if (portraitSession != draftSession || portrait == null) {
            portrait = createDraftPlayer(id);
            portraitSession = draftSession;
        }
        SettlementsWildfireBridge.applyInMemory(portrait, draftSession.draft().body());
        float size = draftSession.draft().body().effectiveBustSize();
        if (!portrait.getArmorPhysicsOverride()) {
            float tightness = WildfireHelper.getArmorConfig(minecraft.player.getItemBySlot(EquipmentSlot.CHEST)).tightness();
            size *= 1.0F - 0.15F * Math.max(0.0F, Math.min(1.0F, tightness));
        }
        setPortraitSize(portrait.getLeftBreastPhysics(), size);
        setPortraitSize(portrait.getRightBreastPhysics(), size);
        portrait.needsSync = false;
        rendering = true;
        try {
            render.run();
        } finally {
            rendering = false;
        }
    }

    private static void setPortraitSize(BreastPhysics physics, float size) {
        WildfirePortraitPhysicsAccessor access = (WildfirePortraitPhysicsAccessor) physics;
        access.settlements$setBreastSize(size);
        access.settlements$setPreviousBreastSize(size);
    }

    public static boolean constructingDraftPlayer() { return constructingDraft.get(); }

    private static GenderPlayer createDraftPlayer(UUID id) {
        constructingDraft.set(true);
        try {
            return new GenderPlayer(id);
        } finally {
            constructingDraft.remove();
        }
    }

    private static CharacterBodyData capture(GenderPlayer player, CharacterBodyData draft) {
        Breasts breasts = player.getBreasts();
        return new CharacterBodyData(CharacterBodyData.CURRENT_VERSION, draft.gender(),
                new BreastAppearance(draft.breasts().enabled(), player.getBustSize(), breasts.getXOffset(),
                        breasts.getYOffset(), breasts.getZOffset(), breasts.getCleavage(), breasts.isUniboob()),
                player.hasHurtSounds(), player.hasBreastPhysics(), player.showBreastsInArmor(),
                player.getArmorPhysicsOverride(), player.getBounceMultiplierRaw(), player.getFloppiness()).sanitized();
    }
}
