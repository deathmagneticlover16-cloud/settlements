package ee.lastpagans.settlements.mixin;

import com.talhanation.recruits.world.RecruitsPlayerUnitManager;
import ee.lastpagans.settlements.compat.recruits.RecruitCountGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(value = RecruitsPlayerUnitManager.class, remap = false)
public abstract class RecruitsPlayerUnitManagerMixin {
    @Shadow private Map<UUID, Integer> recruitCountMap;

    @Inject(method = "load", at = @At("RETURN"))
    private void settlements$sanitizeLoadedCounts(ServerLevel level, CallbackInfo ci) {
        RecruitCountGuard.removeInvalidEntry(recruitCountMap, "RecruitsPlayerUnitManager.load");
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void settlements$sanitizeCountsBeforeSave(ServerLevel level, CallbackInfo ci) {
        RecruitCountGuard.removeInvalidEntry(recruitCountMap, "RecruitsPlayerUnitManager.save");
    }

    @Inject(method = "getRecruitCount", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessLookup(UUID playerUuid, CallbackInfoReturnable<Integer> cir) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitsPlayerUnitManager.getRecruitCount")) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "setRecruitCount", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessSet(Player player, int count, CallbackInfo ci) {
        if (RecruitCountGuard.rejectNullPlayer(player, "RecruitsPlayerUnitManager.setRecruitCount")) ci.cancel();
    }

    @Inject(method = "addRecruits", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessAdd(UUID playerUuid, int count, CallbackInfo ci) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitsPlayerUnitManager.addRecruits")) ci.cancel();
    }

    @Inject(method = "removeRecruits", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessRemove(UUID playerUuid, int count, CallbackInfo ci) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitsPlayerUnitManager.removeRecruits")) ci.cancel();
    }

    @Inject(method = "recountRecruits", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessRecount(MinecraftServer server, UUID playerUuid, CallbackInfo ci) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitsPlayerUnitManager.recountRecruits")) ci.cancel();
    }

    @Inject(method = "canPlayerRecruit", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessRecruitCheck(String factionId, UUID playerUuid,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitsPlayerUnitManager.canPlayerRecruit")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getRemainingRecruitSlots", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessSlotCheck(String factionId, UUID playerUuid,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (RecruitCountGuard.rejectNullUuid(playerUuid, "RecruitsPlayerUnitManager.getRemainingRecruitSlots")) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "broadCastUnitInfoToPlayer", at = @At("HEAD"), cancellable = true)
    private void settlements$rejectOwnerlessBroadcast(Player player, CallbackInfo ci) {
        if (RecruitCountGuard.rejectNullPlayer(player, "RecruitsPlayerUnitManager.broadCastUnitInfoToPlayer")) ci.cancel();
    }
}
