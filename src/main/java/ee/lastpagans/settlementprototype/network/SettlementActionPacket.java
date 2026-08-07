package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.PrototypeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record SettlementActionPacket(String settlementId, PrototypeManager.SettlementAction action, String argument, int amount) {
    public SettlementActionPacket(String settlementId, PrototypeManager.SettlementAction action) { this(settlementId, action, "", 0); }
    public static void encode(SettlementActionPacket packet, FriendlyByteBuf buffer) { buffer.writeUtf(packet.settlementId,64);buffer.writeEnum(packet.action);buffer.writeUtf(packet.argument==null?"":packet.argument,64);buffer.writeVarInt(Math.max(0,packet.amount)); }
    public static SettlementActionPacket decode(FriendlyByteBuf buffer) { return new SettlementActionPacket(buffer.readUtf(64),buffer.readEnum(PrototypeManager.SettlementAction.class),buffer.readUtf(64),buffer.readVarInt()); }
    public static void handle(SettlementActionPacket packet, Supplier<NetworkEvent.Context> supplier) {NetworkEvent.Context context=supplier.get();ServerPlayer sender=context.getSender();if(sender!=null)context.enqueueWork(()->PrototypeManager.handleAction(sender,packet.settlementId,packet.action,packet.argument,packet.amount));context.setPacketHandled(true);}
}
