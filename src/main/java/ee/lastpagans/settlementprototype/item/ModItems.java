package ee.lastpagans.settlementprototype.item;

import ee.lastpagans.settlementprototype.SettlementPrototype;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SettlementPrototype.MOD_ID);

    public static final RegistryObject<Item> SETTLER_CONTRACT = ITEMS.register(
            "settler_contract",
            () -> new SettlerContractItem(new Item.Properties().stacksTo(16))
    );

    public static final RegistryObject<Item> SILVER_COIN = ITEMS.register(
            "silver_coin",
            () -> new Item(new Item.Properties().stacksTo(64))
    );

    private ModItems() {
    }
}
