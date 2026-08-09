package ee.lastpagans.settlements.mixin;

import com.talhanation.recruits.init.ModShortcuts;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.talhanation.recruits.client.events.KeyEvents", remap = false)
public abstract class RecruitsTeamKeyMixin {
    @Inject(method = "onKeyInput", at = @At("HEAD"), cancellable = true)
    private void settlements$redirectFactionKey(InputEvent.Key event, CallbackInfo ci) {
        if (ModShortcuts.TEAM_SCREEN_KEY != null && ModShortcuts.TEAM_SCREEN_KEY.consumeClick()) {
            SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(OpenCampaignRequestPacket.ScreenTarget.FACTION));
            ci.cancel();
        }
    }
}
