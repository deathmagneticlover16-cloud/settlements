package ee.lastpagans.settlements.mixin;

import ee.lastpagans.settlements.client.wildfire.SettlementsWildfirePreviewSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wildfire.main.config.Configuration", remap = false)
public abstract class WildfireDraftConfigurationMixin {
    @Inject(method = "finish", at = @At("HEAD"), cancellable = true)
    private void settlements$keepDraftConfigurationInMemory(CallbackInfo ci) {
        // Wildfire constructors otherwise load and rewrite the live player's existing config file.
        if (SettlementsWildfirePreviewSession.constructingDraftPlayer()) ci.cancel();
    }
}
