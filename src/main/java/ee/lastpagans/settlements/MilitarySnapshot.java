package ee.lastpagans.settlements;

import ee.lastpagans.settlements.network.PacketValidation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Complete server-owned state for the three military workspaces. */
public record MilitarySnapshot(
        String settlementId, String settlementName, String factionName, boolean canManage,
        int nativeManpowerMilli, int foreignManpowerMilli, int capturedForeignManpowerMilli, int treasury,
        int inTraining, int readyInReserve, int deployedWorld, int deployedCampaign,
        int deployedCap, int pendingDeployment, boolean musterGroundSet, String musterGroundName,
        List<UnitEntry> units, List<TrainingEntry> training, List<MusterEntry> musters,
        long serverGameTime, String message
) {
    public static MilitarySnapshot from(ServerPlayer player, SettlementData.SettlementRecord settlement, String message) {
        MilitaryData military = MilitaryData.get(player.serverLevel());
        List<UnitEntry> units = new ArrayList<>();
        int ready = 0, trainingCount = 0;
        for (CampaignData.UnitType type : CampaignData.UnitType.selectableValues()) {
            MilitaryData.ReservePool pool = military.pool(settlement.getId(), type);
            int pending = military.training(settlement.getId()).stream().filter(value -> value.type == type).mapToInt(MilitaryData.TrainingOrder::remaining).sum();
            ready += pool.total(); trainingCount += pending;
            units.add(new UnitEntry(type.name(), type.displayName(), description(type), icon(type),
                    pool.nativeCount(), pool.foreignCount(), SettlementConfig.RESERVE_CAPACITY.get(type).get(), pending,
                    SettlementConfig.TRAINING_MP_MILLI_COST.get(type).get(), SettlementConfig.TRAINING_SILVER_COST.get(type).get(),
                    SettlementConfig.TRAINING_SECONDS.get(type).get() * 20));
        }
        List<MilitaryData.TrainingOrder> queue = military.training(settlement.getId());
        List<TrainingEntry> training = new ArrayList<>();
        long queuedTicks = 0L;
        for (int i = 0; i < queue.size(); i++) {
            MilitaryData.TrainingOrder order = queue.get(i);
            long ownTicks = Math.max(0L, (long)order.remaining() * order.durationTicks - (i == 0 ? order.progressTicks : 0));
            queuedTicks += ownTicks;
            training.add(new TrainingEntry(order.id, order.type.name(), order.type.displayName(), order.total, order.completed,
                    order.progressTicks, order.durationTicks, i == 0, ownTicks, queuedTicks));
        }
        long activeMusterSequence = military.musters(settlement.getId()).stream()
                .filter(value -> value.phase == MilitaryData.MusterPhase.MUSTERING)
                .mapToLong(value -> value.queueSequence).min().orElse(Long.MIN_VALUE);
        List<MusterEntry> musters = military.musters(settlement.getId()).stream()
                .sorted(Comparator.comparingInt((MilitaryData.MusterOrder value) -> value.phase == MilitaryData.MusterPhase.MUSTERING ? 0 : 1)
                        .thenComparingLong(value -> value.queueSequence))
                .map(value -> new MusterEntry(
                value.id, value.groupId, value.displayName, value.total(), value.spawned,
                value.phase.name(), value.acceptanceTicks, value.ownerId, ownerName(player, value.ownerId), value.mercenary,
                value.phase == MilitaryData.MusterPhase.MUSTERING && value.queueSequence == activeMusterSequence
        )).toList();
        MilitaryData.DeploymentCount deployed = military.deployed(player.getUUID());
        String faction = settlement.getFactionTeam().isBlank() ? "Independent" : CampaignManager.factionName(settlement.getFactionTeam());
        int nativeMilli = settlement.getAvailableManpower() * 1000 + military.nativeCredit(settlement.getId());
        int foreignMilli = military.integratedForeignMilli(settlement.getId());
        long capturedValue = (long)CampaignSystemsData.get(player.serverLevel()).foreignManpower(settlement.getId()) * 1000L
                + military.capturedForeignCredit(settlement.getId());
        int capturedMilli = (int)Math.min(Integer.MAX_VALUE, Math.max(0L, capturedValue));
        return new MilitarySnapshot(settlement.getId(), settlement.getName(), faction, SettlementManager.canManage(player, settlement),
                nativeMilli, foreignMilli, capturedMilli, StateTreasury.balance(player.serverLevel(), settlement), trainingCount, ready,
                deployed.world(), deployed.campaign(), SettlementConfig.PLAYER_DEPLOYED_TROOP_CAP.get(), military.pendingForPlayer(player.getUUID()),
                settlement.hasMusterGround(), settlement.getName() + " Muster Ground", List.copyOf(units), List.copyOf(training), musters,
                player.serverLevel().getGameTime(), message == null ? "" : message);
    }

    private static String ownerName(ServerPlayer viewer, UUID owner) {
        if (owner == null) return "Unknown";
        ServerPlayer online = viewer.getServer() == null ? null : viewer.getServer().getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        return viewer.getServer() == null ? owner.toString().substring(0, 8)
                : viewer.getServer().getProfileCache().get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString().substring(0, 8));
    }

    private static String description(CampaignData.UnitType type) {
        return switch (type) {
            case LEVY -> "Mass troops from the populace";
            case RECRUIT -> "General-purpose foot soldiers";
            case SHIELDMAN -> "Disciplined defensive infantry";
            case BOWMAN -> "Mobile ranged troops";
            case CROSSBOWMAN -> "Armored ranged specialists";
            case HORSEMAN -> "Elite mounted troops";
            case NOMAD -> "Fast mounted skirmishers";
        };
    }

    private static String icon(CampaignData.UnitType type) {
        return switch (type) {
            case LEVY, RECRUIT -> "minecraft:iron_helmet";
            case SHIELDMAN -> "minecraft:shield";
            case BOWMAN -> "minecraft:bow";
            case CROSSBOWMAN -> "minecraft:crossbow";
            case HORSEMAN, NOMAD -> "minecraft:saddle";
        };
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(settlementId, 64); buffer.writeUtf(settlementName, 96); buffer.writeUtf(factionName, 96); buffer.writeBoolean(canManage);
        buffer.writeInt(nativeManpowerMilli); buffer.writeInt(foreignManpowerMilli); buffer.writeInt(capturedForeignManpowerMilli); buffer.writeInt(treasury);
        buffer.writeVarInt(inTraining); buffer.writeVarInt(readyInReserve); buffer.writeVarInt(deployedWorld); buffer.writeVarInt(deployedCampaign);
        buffer.writeVarInt(deployedCap); buffer.writeVarInt(pendingDeployment); buffer.writeBoolean(musterGroundSet); buffer.writeUtf(musterGroundName, 128);
        PacketValidation.writeCollection(buffer, units, 32, (buf, value) -> value.encode(buf));
        PacketValidation.writeCollection(buffer, training, 256, (buf, value) -> value.encode(buf));
        PacketValidation.writeCollection(buffer, musters, 256, (buf, value) -> value.encode(buf));
        buffer.writeLong(serverGameTime); buffer.writeUtf(message, 512);
    }

    public static MilitarySnapshot decode(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(64), name = buffer.readUtf(96), faction = buffer.readUtf(96); boolean manage = buffer.readBoolean();
        int nativeMp = buffer.readInt(), foreignMp = buffer.readInt(), capturedMp = buffer.readInt(), treasury = buffer.readInt();
        int inTraining = buffer.readVarInt(), ready = buffer.readVarInt(), world = buffer.readVarInt(), campaign = buffer.readVarInt();
        int cap = buffer.readVarInt(), pending = buffer.readVarInt(); boolean ground = buffer.readBoolean(); String groundName = buffer.readUtf(128);
        List<UnitEntry> units = readList(buffer, 32, UnitEntry::decode); List<TrainingEntry> training = readList(buffer, 256, TrainingEntry::decode);
        List<MusterEntry> musters = readList(buffer, 256, MusterEntry::decode);
        return new MilitarySnapshot(id, name, faction, manage, nativeMp, foreignMp, capturedMp, treasury, inTraining, ready, world, campaign,
                cap, pending, ground, groundName, units, training, musters, buffer.readLong(), buffer.readUtf(512));
    }

    private static <T> List<T> readList(FriendlyByteBuf buffer, int maximum, Decoder<T> decoder) {
        int count = PacketValidation.readCount(buffer, maximum, "military entry"); List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(decoder.decode(buffer)); return List.copyOf(values);
    }
    private interface Decoder<T> { T decode(FriendlyByteBuf buffer); }

    public record UnitEntry(String type, String name, String description, String iconItem, int nativeReady, int foreignReady,
                            int capacity, int pendingTraining, int manpowerMilliCost, int silverCost, int trainingTicks) {
        public int ready() { return nativeReady + foreignReady; }
        private void encode(FriendlyByteBuf b) { b.writeUtf(type,32);b.writeUtf(name,64);b.writeUtf(description,128);b.writeUtf(iconItem,128);
            b.writeVarInt(nativeReady);b.writeVarInt(foreignReady);b.writeVarInt(capacity);b.writeVarInt(pendingTraining);b.writeVarInt(manpowerMilliCost);b.writeVarInt(silverCost);b.writeVarInt(trainingTicks); }
        private static UnitEntry decode(FriendlyByteBuf b) { return new UnitEntry(b.readUtf(32),b.readUtf(64),b.readUtf(128),b.readUtf(128),
                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()); }
    }

    public record TrainingEntry(UUID id, String type, String name, int total, int completed, int progressTicks, int durationTicks,
                                boolean active, long orderRemainingTicks, long queueRemainingTicks) {
        private void encode(FriendlyByteBuf b) { b.writeUUID(id);b.writeUtf(type,32);b.writeUtf(name,64);b.writeVarInt(total);b.writeVarInt(completed);
            b.writeVarInt(progressTicks);b.writeVarInt(durationTicks);b.writeBoolean(active);b.writeLong(orderRemainingTicks);b.writeLong(queueRemainingTicks); }
        private static TrainingEntry decode(FriendlyByteBuf b) { return new TrainingEntry(b.readUUID(),b.readUtf(32),b.readUtf(64),b.readVarInt(),b.readVarInt(),
                b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readLong(),b.readLong()); }
    }

    public record MusterEntry(UUID id, UUID groupId, String name, int total, int spawned, String phase, int acceptanceTicks,
                              UUID ownerId, String ownerName, boolean mercenary, boolean active) {
        private void encode(FriendlyByteBuf b) { b.writeUUID(id);b.writeUUID(groupId);b.writeUtf(name,96);b.writeVarInt(total);b.writeVarInt(spawned);
            b.writeUtf(phase,32);b.writeVarInt(acceptanceTicks);b.writeUUID(ownerId);b.writeUtf(ownerName,64);b.writeBoolean(mercenary);b.writeBoolean(active); }
        private static MusterEntry decode(FriendlyByteBuf b) { return new MusterEntry(b.readUUID(),b.readUUID(),b.readUtf(96),b.readVarInt(),b.readVarInt(),
                b.readUtf(32),b.readVarInt(),b.readUUID(),b.readUtf(64),b.readBoolean(),b.readBoolean()); }
    }
}
