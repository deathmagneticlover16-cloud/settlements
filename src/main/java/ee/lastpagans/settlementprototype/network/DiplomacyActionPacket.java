package ee.lastpagans.settlementprototype.network;

import ee.lastpagans.settlementprototype.CampaignManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record DiplomacyActionPacket(String action, String targetFaction, UUID proposalId, UUID warId, String extra) {
    public static void encode(DiplomacyActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action, 40); buffer.writeUtf(packet.targetFaction, 64);
        buffer.writeBoolean(packet.proposalId != null); if (packet.proposalId != null) buffer.writeUUID(packet.proposalId);
        buffer.writeBoolean(packet.warId != null); if (packet.warId != null) buffer.writeUUID(packet.warId);
        buffer.writeUtf(packet.extra == null ? "" : packet.extra, 4096);
    }
    public static DiplomacyActionPacket decode(FriendlyByteBuf buffer) {
        String action = buffer.readUtf(40); String faction = buffer.readUtf(64);
        UUID proposal = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID war = buffer.readBoolean() ? buffer.readUUID() : null;
        return new DiplomacyActionPacket(action, faction, proposal, war, buffer.readUtf(4096));
    }
    public static void handle(DiplomacyActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> CampaignManager.handleDiplomacyAction(player, packet.action, packet.targetFaction, packet.proposalId, packet.warId, packet.extra));
        context.setPacketHandled(true);
    }
}
