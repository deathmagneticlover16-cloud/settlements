package ee.lastpagans.settlements.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.function.BiConsumer;

/** Shared bounds checks for variable-length network payloads. */
public final class PacketValidation {
    private PacketValidation() {}

    public static int readCount(FriendlyByteBuf buffer, int maximum, String field) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        }
        return count;
    }

    public static int readInt(FriendlyByteBuf buffer, int minimum, int maximum, String field) {
        int value = buffer.readVarInt();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
        return value;
    }

    public static <T> void writeCollection(FriendlyByteBuf buffer, List<T> values, int maximum,
                                           BiConsumer<FriendlyByteBuf, T> encoder) {
        int count = Math.min(maximum, values == null ? 0 : values.size());
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) encoder.accept(buffer, values.get(i));
    }
}
