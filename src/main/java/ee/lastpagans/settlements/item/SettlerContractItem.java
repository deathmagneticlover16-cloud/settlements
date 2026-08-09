package ee.lastpagans.settlements.item;

import ee.lastpagans.settlements.SettlementManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public final class SettlerContractItem extends Item {
    public static final String TAG_SETTLEMENT = "SettlementId";
    public static final String TAG_SETTLEMENT_NAME = "SettlementName";
    public static final String TAG_CONTRACT_ID = "ContractId";

    public SettlerContractItem(Properties properties) {
        super(properties);
    }

    public static ItemStack forSettlement(String settlementId, String settlementName) {
        ItemStack stack = new ItemStack(ModItems.SETTLER_CONTRACT.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_SETTLEMENT, settlementId);
        tag.putString(TAG_SETTLEMENT_NAME, settlementName);
        tag.putUUID(TAG_CONTRACT_ID, UUID.randomUUID());
        stack.setHoverName(Component.literal("Settler Contract — " + settlementName));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        CompoundTag tag = stack.getOrCreateTag();
        String settlementId = tag.getString(TAG_SETTLEMENT);
        if (!tag.hasUUID(TAG_CONTRACT_ID)) {
            // Upgrade legacy contract items to a unique, server-tracked redemption token.
            tag.putUUID(TAG_CONTRACT_ID, UUID.randomUUID());
        }
        UUID contractId = tag.getUUID(TAG_CONTRACT_ID);
        SettlementManager.ContractUseResult result = SettlementManager.useSettlerContract(serverPlayer, settlementId, contractId);
        serverPlayer.sendSystemMessage(Component.literal(result.message()));
        if (result.success() && !serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return result.success() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String name = stack.getOrCreateTag().getString(TAG_SETTLEMENT_NAME);
        if (!name.isBlank()) {
            tooltip.add(Component.literal("Brings one registered villager to " + name + ".").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Use to summon the settler at the town square.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
