package ee.lastpagans.settlements;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** Accept only registered patterns and dyes, then rebuild a banner without client-supplied extra NBT. */
public final class FactionBannerUtil {
    private FactionBannerUtil() {}

    public static ItemStack sanitize(ItemStack input) {
        if (input == null || !(input.getItem() instanceof BannerItem)) return ItemStack.EMPTY;
        CompoundTag source = input.getTagElement("BlockEntityTag");
        if (source == null) return ItemStack.EMPTY;
        ListTag patterns = source.getList("Patterns", Tag.TAG_COMPOUND);
        if (patterns.isEmpty() || patterns.size() > 6) return ItemStack.EMPTY;
        ListTag clean = new ListTag();
        for (Tag raw : patterns) {
            CompoundTag layer = (CompoundTag) raw;
            if (!layer.contains("Pattern", Tag.TAG_STRING) || !layer.contains("Color", Tag.TAG_INT)) return ItemStack.EMPTY;
            String hash = layer.getString("Pattern");
            boolean knownPattern = BuiltInRegistries.BANNER_PATTERN.stream()
                    .anyMatch(pattern -> !"b".equals(hash) && pattern.getHashname().equals(hash));
            int colorId = layer.getInt("Color");
            boolean knownColor = java.util.Arrays.stream(DyeColor.values()).anyMatch(dye -> dye.getId() == colorId);
            if (!knownPattern || !knownColor) return ItemStack.EMPTY;
            CompoundTag entry = new CompoundTag();
            entry.putString("Pattern", hash);
            entry.putInt("Color", colorId);
            clean.add(entry);
        }
        ItemStack banner = new ItemStack(input.getItem());
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Patterns", clean);
        banner.addTagElement("BlockEntityTag", blockEntity);
        return banner;
    }
}
