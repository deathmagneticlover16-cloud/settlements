package ee.lastpagans.settlements.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "com.talhanation.recruits.network.MessageChangeDiplomacyStatus",
        "com.talhanation.recruits.network.MessageSendTreaty",
        "com.talhanation.recruits.network.MessageAnswerTreaty",
        "com.talhanation.recruits.network.MessageAddEmbargo",
        "com.talhanation.recruits.network.MessageAddEmbargoFaction",
        "com.talhanation.recruits.network.MessageRemoveEmbargo"
}, remap = false)
public abstract class RecruitsDiplomacyPacketMixin {
    @Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void settlements$blockOriginalDiplomacy(NetworkEvent.Context context, CallbackInfo ci) {
        ServerPlayer sender = context.getSender();
        if (sender != null) sender.sendSystemMessage(Component.literal("Diplomacy is managed through the Campaign menu."));
        ci.cancel();
    }
}
