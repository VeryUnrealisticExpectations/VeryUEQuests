package dev.veryue.vuequests.models.reward;

import com.mojang.brigadier.context.ContextChain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veryue.vuequests.registry.RewardTypes;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class RewardCommand extends RewardType<String> {

    private static final Codec<String> COMMAND_CODEC = Codec.STRING.validate(command ->
            !command.isBlank() && !command.strip().equals("/")
                    && command.indexOf('\n') < 0 && command.indexOf('\r') < 0
                    ? DataResult.success(command)
                    : DataResult.error(() -> "Expected a single Minecraft command"));

    public static final MapCodec<RewardCommand> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("reward_id").forGetter(RewardCommand::getRewardId),
            COMMAND_CODEC.fieldOf("command").forGetter(RewardCommand::getValue)
    ).apply(instance, RewardCommand::new));

    public RewardCommand(UUID rewardId, String command) {
        super(rewardId, command);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.COMMAND_BLOCK);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.vuequests.reward.command");
    }

    @Override
    public RewardTypeSerializer getType() {
        return RewardTypes.COMMAND;
    }

    @Override
    public boolean tryGiveReward(ServerPlayer player) {
        String command = getValue().strip();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        var source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
        Commands commands = player.level().getServer().getCommands();
        var parsed = commands.getDispatcher().parse(command, source);
        if (Commands.getParseException(parsed) != null || ContextChain.tryFlatten(parsed.getContext().build(command)).isEmpty()) {
            source.sendFailure(Component.translatable("screen.vuequests.reward.command_invalid"));
            return false;
        }
        commands.performCommand(parsed, command);
        // A dispatched command can have partial effects even when it reports failure.
        return true;
    }
}
