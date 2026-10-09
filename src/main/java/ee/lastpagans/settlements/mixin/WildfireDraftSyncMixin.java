package ee.lastpagans.settlements.mixin;

import com.wildfire.main.GenderPlayer;
import ee.lastpagans.settlements.client.wildfire.ClientCharacterBodyState;
import ee.lastpagans.settlements.client.wildfire.SettlementsWildfirePreviewSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wildfire.main.networking.WildfireSync", remap = false)
public abstract class WildfireDraftSyncMixin {
    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true)
    private static void settlements$suppressDraftSync(GenderPlayer player, CallbackInfo ci) {
        if (player != null && (SettlementsWildfirePreviewSession.shouldSuppressSave(player)
                || ClientCharacterBodyState.suppressWildfirePacket(player.uuid))) ci.cancel();
    }
}
