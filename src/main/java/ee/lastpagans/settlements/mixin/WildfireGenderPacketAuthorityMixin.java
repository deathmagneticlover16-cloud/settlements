package ee.lastpagans.settlements.mixin;

import com.wildfire.main.networking.PacketSendGenderInfo;
import ee.lastpagans.settlements.compat.wildfire.SettlementsWildfireAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(targets = "com.wildfire.main.networking.PacketSendGenderInfo", remap = false)
public abstract class WildfireGenderPacketAuthorityMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void settlements$keepCharacterAuthority(PacketSendGenderInfo packet,
                                                            Supplier<NetworkEvent.Context> supplier,
                                                            CallbackInfo ci) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (!SettlementsWildfireAuthority.isManaged(sender)) return;
        context.enqueueWork(() -> SettlementsWildfireAuthority.reassert(sender, true));
        context.setPacketHandled(true);
        ci.cancel();
    }
}
