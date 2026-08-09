package ee.lastpagans.settlements.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientProfileCache {
    private static final Map<UUID, String> PROFILES = new HashMap<>();

    private ClientProfileCache() {
    }

    public static void replace(Map<UUID, String> profiles) {
        PROFILES.clear();
        PROFILES.putAll(profiles);
    }

    public static String get(UUID playerId) {
        return PROFILES.get(playerId);
    }

    public static void clear() {
        PROFILES.clear();
    }
}
