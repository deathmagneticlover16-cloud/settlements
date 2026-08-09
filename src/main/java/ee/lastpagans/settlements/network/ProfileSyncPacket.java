package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.client.ClientProfileCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public record ProfileSyncPacket(Map<UUID, String> profiles) {
    public static void encode(ProfileSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.profiles.size());
        packet.profiles.forEach((uuid, name) -> {
            buffer.writeUUID(uuid);
            buffer.writeUtf(name, 80);
        });
    }

    public static ProfileSyncPacket decode(FriendlyByteBuf buffer) {
        int size = Math.min(1024, buffer.readVarInt());
        Map<UUID, String> profiles = new HashMap<>();
        for (int i = 0; i < size; i++) profiles.put(buffer.readUUID(), buffer.readUtf(80));
        return new ProfileSyncPacket(profiles);
    }

    public static void handle(ProfileSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientProfileCache.replace(packet.profiles)
        ));
        context.setPacketHandled(true);
    }
}
