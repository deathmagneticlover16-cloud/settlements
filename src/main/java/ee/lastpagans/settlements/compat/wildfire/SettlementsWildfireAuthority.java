package ee.lastpagans.settlements.compat.wildfire;

import com.mojang.logging.LogUtils;
import com.wildfire.main.GenderPlayer;
import com.wildfire.main.networking.WildfireSync;
import ee.lastpagans.settlements.SettlementData;
import ee.lastpagans.settlements.character.CharacterBodyData;
import ee.lastpagans.settlements.network.ApplyCharacterBodyPacket;
import ee.lastpagans.settlements.network.ClearCharacterBodyPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class SettlementsWildfireAuthority {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SettlementsWildfireAuthority() {}

    public static boolean isManaged(ServerPlayer player) {
        if (player == null) return false;
        SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
        return character != null && character.isCreated() && character.hasBodyData();
    }

    public static void applyCommittedBody(ServerPlayer player, CharacterBodyData body) {
        if (player == null || body == null) return;
        apply(player, body.sanitized(), true, true);
        LOGGER.info("[Settlements/Wildfire] Committed canonical body for {}", player.getUUID());
    }

    public static void reassert(ServerPlayer player) {
        reassert(player, false);
    }

    public static void reassert(ServerPlayer player, boolean sendOwner) {
        if (!isManaged(player)) return;
        SettlementData.CharacterRecord character = SettlementData.get(player.serverLevel()).getCharacter(player.getUUID());
        apply(player, character.getBody().sanitized(), sendOwner, false);
    }

    public static void clearAuthority(ServerPlayer player) {
        if (player == null) return;
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClearCharacterBodyPacket(player.getUUID()));
        SettlementNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new ClearCharacterBodyPacket(player.getUUID()));
    }

    public static void syncToObserver(ServerPlayer subject, ServerPlayer observer) {
        if (!isManaged(subject)) {
            // An observer may have cached authority while an admin reset occurred out of range.
            SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer),
                    new ClearCharacterBodyPacket(subject.getUUID()));
            return;
        }
        CharacterBodyData body = SettlementData.get(subject.serverLevel()).getCharacter(subject.getUUID()).getBody();
        SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer),
                new ApplyCharacterBodyPacket(subject.getUUID(), body.sanitized()));
    }

    private static void apply(ServerPlayer player, CharacterBodyData body, boolean sendOwner, boolean forceObservers) {
        GenderPlayer wildfire = SettlementsWildfireBridge.getPlayer(player.getUUID());
        boolean changed = !SettlementsWildfireBridge.matches(wildfire, body);
        if (changed) SettlementsWildfireBridge.applyInMemory(wildfire, body);
        if (sendOwner || changed) {
            SettlementNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ApplyCharacterBodyPacket(player.getUUID(), body));
        }
        if (changed || forceObservers) {
            SettlementNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                    new ApplyCharacterBodyPacket(player.getUUID(), body));
            WildfireSync.sendToOtherClients(player, wildfire);
        }
    }
}
