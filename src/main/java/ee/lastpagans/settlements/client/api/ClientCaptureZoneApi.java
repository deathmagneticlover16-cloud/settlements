package ee.lastpagans.settlements.client.api;

import ee.lastpagans.settlements.api.CaptureZoneView;

import java.util.List;
import java.util.UUID;

/** Client-side synced cache intended for RTS renderers (glowing barriers, objective icons, etc.). */
public final class ClientCaptureZoneApi {
    private static volatile List<CaptureZoneView> zones = List.of();
    private ClientCaptureZoneApi() {}
    public static List<CaptureZoneView> zones() { return zones; }
    public static List<CaptureZoneView> zonesFor(String settlementId) { return zones.stream().filter(zone -> zone.settlementId().equals(settlementId)).toList(); }
    public static CaptureZoneView zone(UUID id) { return zones.stream().filter(zone -> zone.id().equals(id)).findFirst().orElse(null); }
    public static void replace(List<CaptureZoneView> values) { zones = values == null ? List.of() : List.copyOf(values); }
    public static void clear() { zones = List.of(); }
}
