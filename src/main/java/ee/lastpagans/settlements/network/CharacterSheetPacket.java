package ee.lastpagans.settlements.network;

import ee.lastpagans.settlements.CharacterSnapshot;
import ee.lastpagans.settlements.client.SettlementClientHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record CharacterSheetPacket(CharacterSnapshot snapshot, boolean returnToMain) {
    public static void encode(CharacterSheetPacket packet, FriendlyByteBuf buffer) { packet.snapshot.encode(buffer); buffer.writeBoolean(packet.returnToMain); }
    public static CharacterSheetPacket decode(FriendlyByteBuf buffer) { return new CharacterSheetPacket(CharacterSnapshot.decode(buffer), buffer.readBoolean()); }
    public static void handle(CharacterSheetPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SettlementClientHandler.openCharacterSheet(packet.snapshot, packet.returnToMain)));
        context.setPacketHandled(true);
    }
}
