package ee.lastpagans.settlementprototype.mixin;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WorldMapScreen.class, remap = false)
public interface WorldMapScreenAccessor {
    @Accessor("offsetX") double settlementprototype$getOffsetX();
    @Accessor("offsetZ") double settlementprototype$getOffsetZ();
}
