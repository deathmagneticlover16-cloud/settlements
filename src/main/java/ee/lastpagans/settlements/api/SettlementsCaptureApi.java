package ee.lastpagans.settlements.api;

import ee.lastpagans.settlements.CaptureZoneData;
import ee.lastpagans.settlements.CaptureZoneManager;
import ee.lastpagans.settlements.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

/** Public server API. Other mods should use this instead of touching Settlements managers directly. */
public final class SettlementsCaptureApi {
    private SettlementsCaptureApi() {}
    public static List<CaptureZoneView> zones(ServerLevel level, String settlementId) {
        return CaptureZoneData.get(level).zonesFor(settlementId).stream().map(CaptureZoneView::from).toList();
    }
    public static CaptureZoneView zone(ServerLevel level, UUID id) {
        CaptureZoneData.CaptureZoneRecord zone = CaptureZoneData.get(level).get(id);
        return zone == null ? null : CaptureZoneView.from(zone);
    }
    public static CaptureZoneView zoneAt(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        return CaptureZoneData.get(level).allZones().stream().filter(zone -> dimension.equals(zone.dimension()) && zone.contains(pos)).findFirst().map(CaptureZoneView::from).orElse(null);
    }
    public static CaptureZoneManager.ZoneRequirement requirement(SettlementData.SettlementRecord settlement) { return CaptureZoneManager.requirement(settlement); }
    public static boolean requirementsMet(ServerLevel level, SettlementData.SettlementRecord settlement) { return CaptureZoneManager.requirementsMet(level, settlement); }
    public static String occupyingTeam(ServerLevel level, String settlementId) { return CaptureZoneData.get(level).occupyingTeam(settlementId); }
}
