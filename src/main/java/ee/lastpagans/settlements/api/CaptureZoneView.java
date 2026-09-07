package ee.lastpagans.settlements.api;

import ee.lastpagans.settlements.CaptureZoneData;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Stable read-only capture objective representation for RTS/campaign-map integrations. */
public record CaptureZoneView(
        UUID id, String settlementId, CaptureZoneData.ZoneType type, String dimension,
        BlockPos pos1, BlockPos pos2, double progress, CaptureZoneData.ZoneState state,
        String attackerTeam, boolean locked, boolean captured
) {
    public static CaptureZoneView from(CaptureZoneData.CaptureZoneRecord zone) {
        return new CaptureZoneView(zone.id(), zone.settlementId(), zone.type(), zone.dimension(), zone.pos1(), zone.pos2(),
                zone.progress(), zone.state(), zone.attackerTeam(), zone.state() == CaptureZoneData.ZoneState.LOCKED, zone.captured());
    }
}
