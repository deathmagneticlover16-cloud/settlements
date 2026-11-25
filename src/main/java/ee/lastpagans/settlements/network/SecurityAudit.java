package ee.lastpagans.settlements.network;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rate-limited accountability for rejected client-originated state changes. */
public final class SecurityAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long LOG_INTERVAL_NANOS = 30_000_000_000L;
    private static final Map<String, Long> NEXT_LOG = new ConcurrentHashMap<>();

    private SecurityAudit() {}

    public static void reject(ServerPlayer player, String action, String target, String reason) {
        if (player == null) return;
        UUID id = player.getUUID();
        String safeAction = safe(action, 64);
        String key = id + "|" + safeAction + "|" + safe(reason, 96);
        long now = System.nanoTime();
        Long previous = NEXT_LOG.putIfAbsent(key, now + LOG_INTERVAL_NANOS);
        if (previous != null && now < previous) return;
        NEXT_LOG.put(key, now + LOG_INTERVAL_NANOS);
        if (NEXT_LOG.size() > 4096) NEXT_LOG.entrySet().removeIf(entry -> entry.getValue() <= now);
        LOGGER.warn("Security rejection player={} uuid={} action={} target={} reason={}",
                safe(player.getGameProfile().getName(), 32), id, safeAction, safe(target, 96), safe(reason, 160));
    }

    public static void forget(UUID playerId) {
        if (playerId != null) NEXT_LOG.keySet().removeIf(key -> key.startsWith(playerId + "|"));
    }

    public static void clear() { NEXT_LOG.clear(); }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return cleaned.substring(0, Math.min(maximum, cleaned.length()));
    }
}
