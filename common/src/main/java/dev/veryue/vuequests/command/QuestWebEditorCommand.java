package dev.veryue.vuequests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.veryue.vuequests.editor.QuestEditorServer;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import java.net.URI;
import net.minecraft.network.chat.Component;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;
import static dev.veryue.vuequests.platform.CommandPermission.WEBEDITOR;
import static net.minecraft.commands.Commands.literal;

public class QuestWebEditorCommand {

    private static final SimpleCommandExceptionType DEDICATED_SERVER = new SimpleCommandExceptionType(
            Component.translatableWithFallback("commands.vuequests.webeditor.dedicated_server",
                    "The VUE Quests web editor is only available in single-player worlds and cannot run on dedicated servers."));
    private static final SimpleCommandExceptionType START_FAILED = new SimpleCommandExceptionType(
            Component.translatableWithFallback("commands.vuequests.webeditor.failed",
                    "Could not start the VUE Quests web editor. Check the server log for details."));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(MOD_ID)
                .then(literal("webeditor")
                        .requires(source -> PlatformHelper.INSTANCE.hasPermission(source, WEBEDITOR))
                        .then(literal("start").executes(context -> start(context.getSource())))
                        .then(literal("stop").executes(context -> stop(context.getSource())))));
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getServer().isDedicatedServer()) {
            throw DEDICATED_SERVER.create();
        }
        boolean running = QuestEditorServer.isRunning();
        if (!running && !QuestEditorServer.start(source.getServer())) {
            throw START_FAILED.create();
        }
        String url = QuestEditorServer.getUrl();
        Component link = Component.literal(url).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
        source.sendSuccess(() -> Component.translatableWithFallback(
                running ? "commands.vuequests.webeditor.on" : "commands.vuequests.webeditor.starting",
                running ? "The VUE Quests web editor is already on: %s" : "The VUE Quests web editor is starting: %s",
                link), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandSourceStack source) {
        boolean running = QuestEditorServer.isRunning();
        if (running) {
            QuestEditorServer.stop();
        }
        source.sendSuccess(() -> Component.translatableWithFallback(
                running ? "commands.vuequests.webeditor.stopping" : "commands.vuequests.webeditor.off",
                running ? "The VUE Quests web editor is shutting down." : "The VUE Quests web editor is already off."),
                false);
        return Command.SINGLE_SUCCESS;
    }
}
