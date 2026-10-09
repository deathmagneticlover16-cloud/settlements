package ee.lastpagans.settlements.mixin;

import com.wildfire.main.GenderPlayer;
import ee.lastpagans.settlements.client.wildfire.ClientCharacterBodyState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wildfire.main.networking.PacketGenderInfo", remap = false)
public abstract class WildfireCanonicalSyncMixin {
    @Inject(method = "updatePlayerFromPacket", at = @At("HEAD"), cancellable = true)
    private void settlements$keepCanonicalBody(GenderPlayer player, CallbackInfo ci) {
        // The shared packet base also runs on an integrated server's thread.
        if (Minecraft.getInstance().isSameThread() && player != null
                && ClientCharacterBodyState.suppressWildfirePacket(player.uuid)) ci.cancel();
    }
}
