package ee.lastpagans.settlementprototype.client;

import ee.lastpagans.settlementprototype.CampaignSnapshot;
import ee.lastpagans.settlementprototype.CharacterSnapshot;
import ee.lastpagans.settlementprototype.DiplomacySnapshot;
import ee.lastpagans.settlementprototype.FamilySnapshot;
import ee.lastpagans.settlementprototype.FactionSnapshot;
import ee.lastpagans.settlementprototype.WorldNewsSnapshot;
import ee.lastpagans.settlementprototype.PrototypeSnapshot;
import ee.lastpagans.settlementprototype.MapMarkerSnapshot;
import ee.lastpagans.settlementprototype.network.SnapshotPacket;
import net.minecraft.client.Minecraft;

public final class PrototypeClientHandler {
    private static MapMarkerSnapshot mapMarkers = new MapMarkerSnapshot(java.util.List.of());
    private PrototypeClientHandler() {}

    public static void accept(SnapshotPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!packet.open() && minecraft.screen instanceof MilitaryScreen military) { military.updateSnapshot(packet.snapshot()); return; }
        if (packet.open() || !(minecraft.screen instanceof PrototypeScreen screen)) minecraft.setScreen(new PrototypeScreen(packet.snapshot()));
        else screen.updateSnapshot(packet.snapshot());
    }
    public static void openCharacterCreation() { Minecraft.getInstance().setScreen(new CharacterCreationScreen()); }
    public static void openCharacterSheet(CharacterSnapshot snapshot, boolean returnToMain) { Minecraft.getInstance().setScreen(new CharacterSheetScreen(snapshot, returnToMain)); }
    public static void openCampaign(CampaignSnapshot snapshot) { Minecraft.getInstance().setScreen(new CampaignMainScreen(snapshot)); }
    public static void openDiplomacy(DiplomacySnapshot snapshot) { Minecraft.getInstance().setScreen(new CampaignDiplomacyScreen(snapshot)); }
    public static void openFamily(FamilySnapshot snapshot) { Minecraft.getInstance().setScreen(new FamilyScreen(snapshot)); }
    public static void openFaction(FactionSnapshot snapshot) { Minecraft.getInstance().setScreen(new FactionScreen(snapshot)); }
    public static void openWorldNews(WorldNewsSnapshot snapshot) { Minecraft.getInstance().setScreen(new WorldNewsScreen(snapshot)); }
    public static void openMilitary(PrototypeSnapshot snapshot) { Minecraft.getInstance().setScreen(new MilitaryScreen(snapshot)); }
    public static void cacheMapMarkers(MapMarkerSnapshot snapshot) { mapMarkers = snapshot; }
    public static MapMarkerSnapshot mapMarkers() { return mapMarkers; }
}
