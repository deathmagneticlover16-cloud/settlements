package ee.lastpagans.settlements.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Small, server-thread-only flood guard for client-initiated actions. Game-state authorization
 * still belongs to the managers; this class only prevents packet spam from monopolising a tick.
 */
public final class ClientActionLimiter {
    private static final int WINDOW_TICKS = 20;
    private static final int MAX_ACTIONS_PER_WINDOW = 24;
    private static final Map<UUID, PlayerBudget> BUDGETS = new HashMap<>();

    private ClientActionLimiter() {}

    public static boolean allow(ServerPlayer player, String route, int cooldownTicks) {
        if (player == null || route == null || route.isBlank()) return false;
        long now = player.serverLevel().getGameTime();
        PlayerBudget budget = BUDGETS.computeIfAbsent(player.getUUID(), ignored -> new PlayerBudget(now));
        if (now < budget.windowStart || now - budget.windowStart >= WINDOW_TICKS) budget.reset(now);
        if (budget.actions >= MAX_ACTIONS_PER_WINDOW) return false;
        long nextAllowed = budget.nextAllowed.getOrDefault(route, Long.MIN_VALUE);
        if (now < nextAllowed) return false;
        budget.actions++;
        budget.nextAllowed.put(route, now + Math.max(1, cooldownTicks));
        return true;
    }

    public static void forget(UUID playerId) {
        if (playerId != null) BUDGETS.remove(playerId);
    }

    public static void clear() {
        BUDGETS.clear();
    }

    private static final class PlayerBudget {
        private long windowStart;
        private int actions;
        private final Map<String, Long> nextAllowed = new HashMap<>();

        private PlayerBudget(long now) {
            windowStart = now;
        }

        private void reset(long now) {
            windowStart = now;
            actions = 0;
            nextAllowed.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
    }
}
