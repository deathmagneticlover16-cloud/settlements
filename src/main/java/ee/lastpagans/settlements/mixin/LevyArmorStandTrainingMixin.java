package ee.lastpagans.settlements.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import ee.lastpagans.settlements.SettlementManager;
import ee.lastpagans.settlements.SettlementConfig;
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
    private boolean settlements$suppressArmorStandXp;

    @Inject(
            method = {
                    "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",
                    "m_7327_(Lnet/minecraft/world/entity/Entity;)Z"
            },
            at = @At("HEAD"),
            remap = false
    )
    private void settlements$rememberTrainingTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        AbstractRecruitEntity recruit = (AbstractRecruitEntity) (Object) this;
        settlements$suppressArmorStandXp = SettlementConfig.BLOCK_LEVY_ARMOR_STAND_XP.get()
                && target instanceof ArmorStand
                && "levy".equals(recruit.getPersistentData().getString(SettlementManager.TAG_KIND));
    }

    @Redirect(
            method = {
                    "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",
                    "m_7327_(Lnet/minecraft/world/entity/Entity;)Z"
            },
            at = @At(value = "INVOKE", target = "Lcom/talhanation/recruits/entities/AbstractRecruitEntity;addXp(I)V", remap = false),
            remap = false
    )
    private void settlements$skipArmorStandTrainingXp(AbstractRecruitEntity recruit, int amount) {
        if (!settlements$suppressArmorStandXp) recruit.addXp(amount);
    }

    @Inject(
            method = {
                    "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",
                    "m_7327_(Lnet/minecraft/world/entity/Entity;)Z"
            },
            at = @At("RETURN"),
            remap = false
    )
    private void settlements$clearTrainingTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        settlements$suppressArmorStandXp = false;
    }
}
