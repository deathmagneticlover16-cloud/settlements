package ee.lastpagans.settlementprototype.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import ee.lastpagans.settlementprototype.PrototypeManager;
import ee.lastpagans.settlementprototype.SettlementConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recruits awards one XP from AbstractRecruitEntity#doHurtTarget for every successful melee hit,
 * including armor stands. Levies are intentionally allowed to become slightly seasoned in real
 * combat, but not by parking them next to training dummies indefinitely.
 */
@Mixin(value = AbstractRecruitEntity.class)
public abstract class LevyArmorStandTrainingMixin {
    @Unique
    private boolean settlementprototype$suppressArmorStandXp;

    @Inject(method = "doHurtTarget", at = @At("HEAD"))
    private void settlementprototype$rememberTrainingTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        AbstractRecruitEntity recruit = (AbstractRecruitEntity) (Object) this;
        settlementprototype$suppressArmorStandXp = SettlementConfig.BLOCK_LEVY_ARMOR_STAND_XP.get()
                && target instanceof ArmorStand
                && "levy".equals(recruit.getPersistentData().getString(PrototypeManager.TAG_KIND));
    }

    @Redirect(
            method = "doHurtTarget",
            at = @At(value = "INVOKE", target = "Lcom/talhanation/recruits/entities/AbstractRecruitEntity;addXp(I)V", remap = false)
    )
    private void settlementprototype$skipArmorStandTrainingXp(AbstractRecruitEntity recruit, int amount) {
        if (!settlementprototype$suppressArmorStandXp) recruit.addXp(amount);
    }

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void settlementprototype$clearTrainingTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        settlementprototype$suppressArmorStandXp = false;
    }
}
