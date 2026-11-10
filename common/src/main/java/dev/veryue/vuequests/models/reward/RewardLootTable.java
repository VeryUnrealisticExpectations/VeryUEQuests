package dev.veryue.vuequests.models.reward;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

public class RewardLootTable extends RewardType<ResourceLocation> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<RewardLootTable> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardLootTable::getRewardId),
            ResourceLocation.CODEC.fieldOf("loot_table").forGetter(RewardLootTable::getValue)
    ).apply(instance, RewardLootTable::new));

    public RewardLootTable(UUID rewardId, ResourceLocation lootTable) {
        super(rewardId, lootTable);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.CHEST);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.vuequests.reward.loot_table", getValue().toString());
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.LOOT_TABLE;
    }

    @Override
    public boolean tryGiveReward(ServerPlayer player) {
        var key = ResourceKey.create(Registries.LOOT_TABLE, getValue());
        LootTable table = player.server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
                .flatMap(registry -> registry.get(key)).map(Holder::value).orElse(null);
        if (table == null) {
            return false;
        }

        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        List<ItemStack> items;
        try {
            items = table.getRandomItems(params);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not roll loot table {} for quest reward {}", getValue(), getRewardId(), exception);
            return false;
        }
        items.forEach(player.getInventory()::placeItemBackInInventory);
        return true;
    }
}
