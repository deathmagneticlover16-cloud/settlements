package ee.lastpagans.settlements.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets only detached portrait physics, so a Yes/No change is visible in the same frame. */
@Mixin(targets = "com.wildfire.physics.BreastPhysics", remap = false)
public interface WildfirePortraitPhysicsAccessor {
    @Accessor("breastSize") void settlements$setBreastSize(float size);
    @Accessor("preBreastSize") void settlements$setPreviousBreastSize(float size);
}
