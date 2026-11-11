package dev.veryue.vuequests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;
import static dev.veryue.vuequests.platform.CommandPermission.RELOAD;
import static net.minecraft.commands.Commands.literal;

public class QuestReloadCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestReloadCommand.class);
    private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(
            reason -> Component.translatableWithFallback("commands.vuequests.reload.failed",
                    "Could not reload the VUE Quests server config: %s", reason));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Consumer<MinecraftServer> reload) {
        dispatcher.register(literal(MOD_ID)
                .then(literal("reload")
                        .requires(source -> PlatformHelper.INSTANCE.hasPermission(source, RELOAD))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            try {
                                reload.accept(source.getServer());
                            } catch (RuntimeException exception) {
                                LOGGER.error("Could not reload the VUE Quests server config", exception);
                                throw RELOAD_FAILED.create(exception.getMessage());
                            }
                            source.sendSuccess(() -> Component.translatableWithFallback("commands.vuequests.reload.success",
                                    "Reloaded the VUE Quests server config and synchronized online players."), true);
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
