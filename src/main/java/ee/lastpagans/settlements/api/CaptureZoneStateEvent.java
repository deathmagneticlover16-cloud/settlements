package ee.lastpagans.settlements.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/** Fired server-side when capture objective state has a meaningful tactical transition. */
public final class CaptureZoneStateEvent extends Event {
    public enum Transition { CAPTURED, CONTESTED, RECAPTURED }
    private final ServerLevel level;
    private final CaptureZoneView zone;
    private final Transition transition;
    public CaptureZoneStateEvent(ServerLevel level, CaptureZoneView zone, Transition transition) { this.level=level; this.zone=zone; this.transition=transition; }
    public ServerLevel level() { return level; }
    public CaptureZoneView zone() { return zone; }
    public Transition transition() { return transition; }
}
