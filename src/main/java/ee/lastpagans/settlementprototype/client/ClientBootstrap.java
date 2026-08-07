package ee.lastpagans.settlementprototype.client;

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
    }
}
