package ee.lastpagans.settlements.mixin;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WorldMapScreen.class, remap = false)
public interface WorldMapScreenAccessor {
    @Accessor("offsetX") double settlements$getOffsetX();
    @Accessor("offsetZ") double settlements$getOffsetZ();
}
