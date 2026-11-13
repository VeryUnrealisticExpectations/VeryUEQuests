package dev.veryue.vuequests.models.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.VeryUEConstants;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class RewardAttribute extends RewardType<Identifier> {

    private static final String MODIFIER_PREFIX = "reward/";

    private static final Codec<Double> AMOUNT_CODEC = Codec.DOUBLE.validate(amount ->
            Double.isFinite(amount) && amount > 0
                    ? DataResult.success(amount)
                    : DataResult.error(() -> "Expected a finite positive attribute amount"));

    public static final MapCodec<RewardAttribute> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardAttribute::getRewardId),
            Identifier.CODEC.fieldOf("attribute").forGetter(RewardAttribute::getValue),
            AMOUNT_CODEC.fieldOf("amount").forGetter(reward -> reward.amount)
    ).apply(instance, RewardAttribute::new));

    private final double amount;

    public RewardAttribute(UUID rewardId, Identifier attribute, double amount) {
        super(rewardId, attribute);
        this.amount = amount;
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.GOLDEN_APPLE);
    }

    @Override
    public Component getDisplayName() {
        var attribute = BuiltInRegistries.ATTRIBUTE.getValue(getValue());
        Component name = attribute == null ? Component.literal(getValue().toString()) : Component.translatable(attribute.getDescriptionId());
        return Component.translatable("screen.vuequests.reward.attribute", name, amount);
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.ATTRIBUTE;
    }

    public Identifier getModifierId() {
        return Identifier.fromNamespaceAndPath(VeryUEConstants.MOD_ID, MODIFIER_PREFIX + getRewardId());
    }

    public static boolean isRewardModifier(Identifier id) {
        return id.getNamespace().equals(VeryUEConstants.MOD_ID) && id.getPath().startsWith(MODIFIER_PREFIX);
    }

    @Override
    public boolean tryGiveReward(ServerPlayer player) {
        var holder = BuiltInRegistries.ATTRIBUTE.get(ResourceKey.create(Registries.ATTRIBUTE, getValue()));
        if (holder.isEmpty()) {
            return false;
        }
        var attribute = holder.get();
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        var modifier = new AttributeModifier(getModifierId(), amount, AttributeModifier.Operation.ADD_VALUE);
        if (!modifier.equals(instance.getModifier(modifier.id()))) {
            instance.addOrUpdateTransientModifier(modifier);
        }
        return true;
    }
}
