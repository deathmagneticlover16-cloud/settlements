package ee.lastpagans.settlements.client;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import ee.lastpagans.settlements.MapMarkerSnapshot;
import ee.lastpagans.settlements.mixin.WorldMapScreenAccessor;
import ee.lastpagans.settlements.network.OpenCampaignRequestPacket;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;

public final class ClientCampaignEvents {
    private static boolean redirecting;
    private ClientCampaignEvents() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (ClientKeyMappings.MAIN.consumeClick()) request(OpenCampaignRequestPacket.ScreenTarget.MAIN);
        while (ClientKeyMappings.CHARACTER.consumeClick()) request(OpenCampaignRequestPacket.ScreenTarget.CHARACTER);
        while (ClientKeyMappings.SETTLEMENT.consumeClick()) request(OpenCampaignRequestPacket.ScreenTarget.SETTLEMENT);
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (redirecting || event.getNewScreen() == null) return;
        String name = event.getNewScreen().getClass().getName();
        boolean recruitsScreen = name.startsWith("com.talhanation.recruits.client.gui.");
        OpenCampaignRequestPacket.ScreenTarget target = null;
        if (recruitsScreen && name.endsWith(".FactionMainScreen")) {
            target = OpenCampaignRequestPacket.ScreenTarget.FACTION;
        } else if (recruitsScreen && (name.startsWith("com.talhanation.recruits.client.gui.diplomacy.")
                || name.endsWith(".MessengerTreatyScreen")
                || name.endsWith(".MessengerTreatyAnswerScreen"))) {
            target = OpenCampaignRequestPacket.ScreenTarget.DIPLOMACY;
        }
        if (target != null) {
            redirecting = true;
            event.setCanceled(true);
            request(target);
            Minecraft.getInstance().execute(() -> redirecting = false);
        }
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof WorldMapScreen map)) return;
        MapMarkerSnapshot snapshot = SettlementClientHandler.mapMarkers();
        if (snapshot == null || snapshot.markers().isEmpty()) return;
        double offsetX = ((WorldMapScreenAccessor) map).settlements$getOffsetX();
        double offsetZ = ((WorldMapScreenAccessor) map).settlements$getOffsetZ();
        double scale = WorldMapScreen.scale;
        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX(), mouseY = event.getMouseY();
        MapMarkerSnapshot.Marker hovered = null;
        for (MapMarkerSnapshot.Marker marker : snapshot.markers()) {
            if (Minecraft.getInstance().level == null || !marker.dimension().equals(Minecraft.getInstance().level.dimension().location().toString())) continue;
            int x = (int) Math.round(offsetX + marker.x() * scale);
            int y = (int) Math.round(offsetZ + marker.z() * scale);
            int size = marker.capital() ? 9 : switch (marker.rank()) { case "MAJOR_CITY" -> 8; case "CITY" -> 7; case "TOWN" -> 6; default -> 5; };
            int color = marker.factionId().isBlank() ? 0xFFE0D2A2 : 0xFFFFC65D;
            graphics.fill(x - size, y - size, x + size, y + size, 0xCC111111);
            graphics.fill(x - size + 1, y - size + 1, x + size - 1, y + size - 1, color);
            graphics.drawCenteredString(Minecraft.getInstance().font, marker.capital() ? "◆" : marker.rank().equals("MAJOR_CITY") || marker.rank().equals("CITY") ? "■" : marker.rank().equals("TOWN") ? "▪" : "•", x, y - 4, 0xFF222222);
            if (mouseX >= x - size && mouseX <= x + size && mouseY >= y - size && mouseY <= y + size) hovered = marker;
        }
        if (hovered != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, List.of(
                    net.minecraft.network.chat.Component.literal(hovered.name()),
                    net.minecraft.network.chat.Component.literal(pretty(hovered.rank()) + (hovered.capital() ? " · Capital" : "") + " — " + hovered.factionName()),
                    net.minecraft.network.chat.Component.literal("Population " + hovered.population() + " · Stability " + hovered.stability() + "%")
            ), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private static String pretty(String value) { return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT); }
    private static void request(OpenCampaignRequestPacket.ScreenTarget target) { SettlementNetwork.CHANNEL.sendToServer(new OpenCampaignRequestPacket(target)); }
}
