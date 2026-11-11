package dev.veryue.vuequests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;
import static dev.veryue.vuequests.platform.CommandPermission.DEBUG;
import static net.minecraft.commands.Commands.literal;

public class QuestDebugCommand {

    private static final SimpleCommandExceptionType UNSUPPORTED_CLIENT = new SimpleCommandExceptionType(
            Component.translatableWithFallback("commands.vuequests.debug.unsupported",
                    "Quest debug mode requires an updated VUE Quests client."));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Predicate<ServerPlayer> sendToggle) {
        dispatcher.register(literal(MOD_ID)
                .then(literal("debug")
                        .requires(source -> PlatformHelper.INSTANCE.hasPermission(source, DEBUG))
                        .executes(context -> {
                            if (!sendToggle.test(context.getSource().getPlayerOrException())) {
                                throw UNSUPPORTED_CLIENT.create();
                            }
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
