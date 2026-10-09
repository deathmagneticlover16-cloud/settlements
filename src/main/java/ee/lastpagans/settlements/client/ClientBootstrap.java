package ee.lastpagans.settlements.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ClientBootstrap {
    private ClientBootstrap() {}
    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientKeyMappings::register);
        MinecraftForge.EVENT_BUS.addListener(ClientNameEvents::onNameFormat);
        MinecraftForge.EVENT_BUS.addListener(ClientCampaignEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientCampaignEvents::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ClientCampaignEvents::onScreenRender);
        MinecraftForge.EVENT_BUS.addListener(ClientCampaignEvents::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(AreaSelectionEditor::onMouse);
        MinecraftForge.EVENT_BUS.addListener(AreaSelectionEditor::onKey);
        MinecraftForge.EVENT_BUS.addListener(AreaSelectionEditor::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(AreaSelectionEditor::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(AreaSelectionEditor::onRenderGui);
    }
}
