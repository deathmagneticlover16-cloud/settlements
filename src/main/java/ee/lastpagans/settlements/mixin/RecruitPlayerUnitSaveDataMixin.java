package ee.lastpagans.settlements.mixin;

import com.talhanation.recruits.world.RecruitPlayerUnitSaveData;
import ee.lastpagans.settlements.compat.recruits.RecruitCountGuard;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(RecruitPlayerUnitSaveData.class)
public abstract class RecruitPlayerUnitSaveDataMixin {
    @Shadow(remap = false) @Final public static Map<UUID, Integer> recruitCountMap;

    @Inject(method = "setRecruitCount", at = @At("HEAD"), cancellable = true, remap = false)
    private void settlements$rejectOwnerlessCount(UUID playerUuid, int count, CallbackInfo ci) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitPlayerUnitSaveData.setRecruitCount")) ci.cancel();
    }

    @Inject(
            method = {
                    "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
                    "m_7176_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"
            },
            at = @At("HEAD"),
            remap = false
    )
    private void settlements$sanitizeCountsBeforeSerialization(CompoundTag tag,
                                                               CallbackInfoReturnable<CompoundTag> cir) {
        RecruitCountGuard.removeInvalidEntry(recruitCountMap, "RecruitPlayerUnitSaveData.save");
    }
}
