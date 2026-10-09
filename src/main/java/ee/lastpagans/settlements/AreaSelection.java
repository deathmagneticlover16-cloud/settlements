package ee.lastpagans.settlements;

import net.minecraft.core.BlockPos;

/** Shared geometry and editor vocabulary for settlement areas. */
public final class AreaSelection {
    private AreaSelection() {}

    public enum Type {
        HOUSING("Housing Area", false),
        MUSTER("Muster Ground", true),
        PRAYER("Prayer Site", false),
        BARRACKS("Barracks", true),
        CAPTURE_MAIN("Main Capture Zone", true),
        CAPTURE_SECONDARY("Secondary Capture Zone", true);

        private final String label;
        private final boolean horizontalOnly;

        Type(String label, boolean horizontalOnly) {
            this.label = label;
            this.horizontalOnly = horizontalOnly;
        }

        public String label() { return label; }
        public boolean horizontalOnly() { return horizontalOnly; }
    }

    public enum Shape {
        BOX("Box"), CYLINDER("Cylinder");
        private final String label;
        Shape(String label) { this.label = label; }
        public String label() { return label; }
        public Shape next() { return this == BOX ? CYLINDER : BOX; }
        public static Shape parse(String value) {
            try { return value == null ? BOX : valueOf(value); }
            catch (IllegalArgumentException ignored) { return BOX; }
        }
    }

    public static BlockPos min(BlockPos first, BlockPos second) {
        return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
    }

    public static BlockPos max(BlockPos first, BlockPos second) {
        return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
    }

    public static boolean contains(Shape shape, BlockPos min, BlockPos max, BlockPos pos, boolean checkY) {
        if (min == null || max == null || pos == null) return false;
        if (checkY && (pos.getY() < min.getY() || pos.getY() > max.getY())) return false;
        if (pos.getX() < min.getX() || pos.getX() > max.getX() || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) return false;
        if (shape != Shape.CYLINDER) return true;
        double centerX = (min.getX() + max.getX() + 1.0D) * 0.5D;
        double centerZ = (min.getZ() + max.getZ() + 1.0D) * 0.5D;
        double radiusX = Math.max(0.5D, (max.getX() - min.getX() + 1.0D) * 0.5D);
        double radiusZ = Math.max(0.5D, (max.getZ() - min.getZ() + 1.0D) * 0.5D);
        double dx = (pos.getX() + 0.5D - centerX) / radiusX;
        double dz = (pos.getZ() + 0.5D - centerZ) / radiusZ;
        return dx * dx + dz * dz <= 1.0D;
    }

    public static long horizontalArea(Shape shape, BlockPos min, BlockPos max) {
        if (min == null || max == null) return 0L;
        if (shape != Shape.CYLINDER) return (long)(max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1);
        long count = 0L;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (contains(shape, min, max, new BlockPos(x, min.getY(), z), false)) count++;
            }
        }
        return count;
    }
}
