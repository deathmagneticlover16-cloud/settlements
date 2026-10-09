package ee.lastpagans.settlements.mixin;

import com.wildfire.main.GenderPlayer;
import ee.lastpagans.settlements.client.wildfire.SettlementsWildfirePreviewSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wildfire.main.GenderPlayer", remap = false)
public abstract class WildfirePreviewSaveMixin {
    @Inject(method = "saveGenderInfo", at = @At("HEAD"), cancellable = true)
    private static void settlements$suppressPreviewSave(GenderPlayer player, CallbackInfo ci) {
        if (SettlementsWildfirePreviewSession.shouldSuppressSave(player)) ci.cancel();
    }
}
