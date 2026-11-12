package dev.veryue.vuequests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.quest.QuestConfigStorage;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;
import static dev.veryue.vuequests.platform.CommandPermission.EXPORT;
import static net.minecraft.commands.Commands.literal;

public class QuestExportCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestExportCommand.class);
    private static final DynamicCommandExceptionType EXPORT_FAILED = new DynamicCommandExceptionType(
            reason -> Component.translatableWithFallback("commands.vuequests.export.failed",
                    "Could not export VUE Quests: %s", reason));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(MOD_ID)
                .then(literal("export")
                        .requires(source -> PlatformHelper.INSTANCE.hasPermission(source, EXPORT))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Path directory = VeryUEMod.INSTANCE.getQuestConfigDirectory();
                            QuestData data = QuestReloadDelegate.INSTANCE.getData();
                            try {
                                new QuestConfigStorage(directory).export(data, source.getServer().registryAccess());
                            } catch (IOException | RuntimeException exception) {
                                LOGGER.error("Could not export VUE Quests to {}", directory, exception);
                                throw EXPORT_FAILED.create(exception.getMessage());
                            }
                            source.sendSuccess(() -> Component.translatableWithFallback("commands.vuequests.export.success",
                                    "Exported %s quest sections and %s quests to %s.",
                                    data.sections().size(), data.quests().size(), directory.toString()), true);
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
