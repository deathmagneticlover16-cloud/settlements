package ee.lastpagans.settlements.compat.recruits;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

/** Guards Recruits' mutable per-player count maps from ownerless entries. */
public final class RecruitCountGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RecruitCountGuard() {}

    public static boolean rejectNullUuid(UUID playerUuid, String operation) {
        if (playerUuid != null) return false;

        LOGGER.error("[Settlements/Recruits] Rejected a null player UUID during {}. "
                        + "The attached stack trace identifies the caller that supplied the invalid owner.",
                operation, new IllegalArgumentException("Null Recruits soldier owner UUID"));
        return true;
    }

    public static boolean rejectNullPlayer(Object player, String operation) {
        if (player != null) return false;

        LOGGER.error("[Settlements/Recruits] Rejected a null player during {}. "
                        + "The attached stack trace identifies the caller that supplied the invalid owner.",
                operation, new IllegalArgumentException("Null Recruits soldier owner player"));
        return true;
    }

    public static void removeInvalidEntry(Map<UUID, Integer> counts, String operation) {
        if (counts == null || !counts.containsKey(null)) return;

        Integer removedCount = counts.remove(null);
        LOGGER.error("[Settlements/Recruits] Removed legacy null-UUID soldier count {} during {} to keep the world save valid.",
                removedCount, operation, new IllegalStateException("Invalid Recruits soldier count was present"));
    }
}
