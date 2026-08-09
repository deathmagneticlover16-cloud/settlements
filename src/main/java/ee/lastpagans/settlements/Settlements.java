package ee.lastpagans.settlements;

import ee.lastpagans.settlements.client.ClientBootstrap;
import ee.lastpagans.settlements.item.ModItems;
import ee.lastpagans.settlements.network.SettlementNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Settlements.MOD_ID)
public final class Settlements {
    public static final String MOD_ID = "settlements";

    public Settlements() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SettlementConfig.SPEC, "settlements-common.toml");
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SettlementNetwork.register();

        MinecraftForge.EVENT_BUS.addListener(SettlementCommands::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onClaimRemoved);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onClaimUpdated);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onNameFormat);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onTabListName);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(SettlementEvents::onServerStopped);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::init);
    }
}
