package ee.lastpagans.settlements.api;

import net.minecraft.core.BlockPos;

/** Passive barracks geometry exposed to Garrison and other military integrations. */
public record BarracksView(String settlementId, String dimension, BlockPos min, BlockPos max, BlockPos chest, int capacity) {
    public boolean contains(String candidateDimension, BlockPos position) {
        return position != null && dimension.equals(candidateDimension)
                && position.getX() >= min.getX() && position.getX() <= max.getX()
                && position.getY() >= min.getY() && position.getY() <= max.getY()
                && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }
}
