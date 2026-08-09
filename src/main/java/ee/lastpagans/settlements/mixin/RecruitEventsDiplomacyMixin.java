package ee.lastpagans.settlements.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.talhanation.recruits.RecruitEvents", remap = false)
public abstract class RecruitEventsDiplomacyMixin {
    @Inject(method = "handleSignificantDamage", at = @At("HEAD"), cancellable = true)
    private void settlements$doNotStartFactionWarFromDamage(LivingEntity victim, LivingEntity attacker, double damage, ServerLevel level, CallbackInfo ci) {
        ci.cancel();
    }
}
