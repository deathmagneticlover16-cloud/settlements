package ee.lastpagans.settlements.client;

import ee.lastpagans.settlements.CampaignSnapshot;
import ee.lastpagans.settlements.CharacterSnapshot;
import ee.lastpagans.settlements.DiplomacySnapshot;
import ee.lastpagans.settlements.FamilySnapshot;
import ee.lastpagans.settlements.FactionSnapshot;
import ee.lastpagans.settlements.WorldNewsSnapshot;
import ee.lastpagans.settlements.ResearchSnapshot;
import ee.lastpagans.settlements.SettlementSnapshot;
import ee.lastpagans.settlements.MapMarkerSnapshot;
import ee.lastpagans.settlements.network.SnapshotPacket;
import net.minecraft.client.Minecraft;

public final class SettlementClientHandler {
    private static MapMarkerSnapshot mapMarkers = new MapMarkerSnapshot(java.util.List.of());
    private SettlementClientHandler() {}

    public static void accept(SnapshotPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!packet.open() && minecraft.screen instanceof MilitaryScreen military) { military.updateSnapshot(packet.snapshot()); return; }
        if (packet.open() || !(minecraft.screen instanceof SettlementScreen screen)) minecraft.setScreen(new SettlementScreen(packet.snapshot()));
        else screen.updateSnapshot(packet.snapshot());
    }
    public static void openCharacterCreation() { Minecraft.getInstance().setScreen(new CharacterCreationScreen()); }
    public static void openCharacterSheet(CharacterSnapshot snapshot, boolean returnToMain) { Minecraft.getInstance().setScreen(new CharacterSheetScreen(snapshot, returnToMain)); }
    public static void openCampaign(CampaignSnapshot snapshot) { Minecraft.getInstance().setScreen(new CampaignMainScreen(snapshot)); }
    public static void openDiplomacy(DiplomacySnapshot snapshot) { Minecraft.getInstance().setScreen(new CampaignDiplomacyScreen(snapshot)); }
    public static void openFamily(FamilySnapshot snapshot) { Minecraft.getInstance().setScreen(new FamilyScreen(snapshot)); }
    public static void openFaction(FactionSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FactionScreen screen && screen.updateCreationSnapshot(snapshot)) return;
        minecraft.setScreen(new FactionScreen(snapshot));
    }
    public static void openWorldNews(WorldNewsSnapshot snapshot) { Minecraft.getInstance().setScreen(new WorldNewsScreen(snapshot)); }
    public static void openMilitary(SettlementSnapshot snapshot) { Minecraft.getInstance().setScreen(new MilitaryScreen(snapshot)); }
    public static void openMercenaryRosters(ee.lastpagans.settlements.MercenaryRosterSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MercenaryRosterScreen screen) screen.updateSnapshot(snapshot);
        else minecraft.setScreen(new MercenaryRosterScreen(snapshot));
    }
    public static void openResearch(ResearchSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ResearchScreen screen) screen.updateSnapshot(snapshot);
        else minecraft.setScreen(new ResearchScreen(snapshot));
    }
    public static void cacheMapMarkers(MapMarkerSnapshot snapshot) { mapMarkers = snapshot; }
    public static MapMarkerSnapshot mapMarkers() { return mapMarkers; }
}
