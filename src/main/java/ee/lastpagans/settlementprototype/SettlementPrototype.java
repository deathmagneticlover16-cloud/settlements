package ee.lastpagans.settlementprototype;

import ee.lastpagans.settlementprototype.client.ClientBootstrap;
import ee.lastpagans.settlementprototype.item.ModItems;
import ee.lastpagans.settlementprototype.network.PrototypeNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SettlementPrototype.MOD_ID)
public final class SettlementPrototype {
    public static final String MOD_ID = "settlementprototype";

    public SettlementPrototype() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SettlementConfig.SPEC, "settlementprototype-common.toml");
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        PrototypeNetwork.register();

        MinecraftForge.EVENT_BUS.addListener(PrototypeCommands::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onClaimRemoved);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onClaimUpdated);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onNameFormat);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onTabListName);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(PrototypeEvents::onServerStopped);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::init);
    }
}
