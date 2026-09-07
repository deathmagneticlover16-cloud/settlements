package ee.lastpagans.settlements.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/** Main-zone result. Occupation is military control, not permanent legal ownership. */
public final class SettlementOccupationEvent extends Event {
    public enum Transition { OCCUPIED, LIBERATED }
    private final ServerLevel level;
    private final String settlementId;
    private final String occupyingTeam;
    private final Transition transition;
    public SettlementOccupationEvent(ServerLevel level, String settlementId, String occupyingTeam, Transition transition) {
        this.level=level; this.settlementId=settlementId; this.occupyingTeam=occupyingTeam == null ? "" : occupyingTeam; this.transition=transition;
    }
    public ServerLevel level() { return level; }
    public String settlementId() { return settlementId; }
    public String occupyingTeam() { return occupyingTeam; }
    public Transition transition() { return transition; }
}
